package com.aimoneytracker.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.aimoneytracker.data.local.dao.AuditLogDao
import com.aimoneytracker.data.local.dao.MerchantMapDao
import com.aimoneytracker.data.local.dao.PersonDao
import com.aimoneytracker.data.local.dao.RawMessageDao
import com.aimoneytracker.data.local.dao.RuleDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.AuditLogEntity
import com.aimoneytracker.data.local.entity.MerchantMapEntity
import com.aimoneytracker.data.local.entity.PersonHandleEntity
import com.aimoneytracker.data.local.entity.RuleEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.parser.MerchantNormalizer
import com.aimoneytracker.data.parser.ParserRegistry
import com.aimoneytracker.data.processor.TransactionProcessor
import com.aimoneytracker.domain.model.CategorizationSource
import com.aimoneytracker.domain.model.TransactionSource
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single entry point for transaction reads, writes, correction-learning and reprocessing. */
@Singleton
class TransactionRepository @Inject constructor(
    private val txnDao: TransactionDao,
    private val ruleDao: RuleDao,
    private val merchantMapDao: MerchantMapDao,
    private val personDao: PersonDao,
    private val rawDao: RawMessageDao,
    private val auditDao: AuditLogDao,
    private val processor: TransactionProcessor,
    private val parser: ParserRegistry,
) {
    fun observeAll(): Flow<List<TransactionEntity>> = txnDao.observeAll()
    fun observeNeedsReview(): Flow<List<TransactionEntity>> = txnDao.observeNeedsReview()
    fun observeNeedsReviewCount(): Flow<Int> = txnDao.observeNeedsReviewCount()
    fun observeById(id: Long): Flow<TransactionEntity?> = txnDao.observeById(id)
    fun observeByPerson(personId: Long): Flow<List<TransactionEntity>> = txnDao.observeByPerson(personId)

    suspend fun getById(id: Long): TransactionEntity? = txnDao.getById(id)
    suspend fun count(): Int = txnDao.count()

    fun paging(filter: TransactionFilter): Flow<PagingData<TransactionEntity>> =
        Pager(PagingConfig(pageSize = 40, enablePlaceholders = false)) {
            TransactionPagingSource(txnDao, filter)
        }.flow

    fun observeFiltered(filter: TransactionFilter): Flow<List<TransactionEntity>> =
        txnDao.observeFiltered(filter.toQuery())

    suspend fun query(filter: TransactionFilter): List<TransactionEntity> =
        txnDao.queryRaw(filter.toQuery())

    suspend fun addManual(transaction: TransactionEntity): Long {
        val now = DateUtil.now()
        val id = txnDao.insert(transaction.copy(
            source = TransactionSource.MANUAL,
            isReviewed = true,
            createdAt = now,
            updatedAt = now,
        ))
        audit("transaction", id, "CREATE")
        return id
    }

    suspend fun update(transaction: TransactionEntity) {
        txnDao.update(transaction.copy(updatedAt = DateUtil.now()))
        audit("transaction", transaction.id, "UPDATE")
    }

    suspend fun delete(id: Long) {
        txnDao.deleteById(id)
        audit("transaction", id, "DELETE")
    }

    suspend fun setIgnored(id: Long, ignored: Boolean) {
        txnDao.getById(id)?.let { txnDao.update(it.copy(isIgnored = ignored, isReviewed = true, updatedAt = DateUtil.now())) }
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        txnDao.getById(id)?.let { txnDao.update(it.copy(isArchived = archived, updatedAt = DateUtil.now())) }
    }

    suspend fun markReviewed(id: Long) {
        txnDao.getById(id)?.let { txnDao.update(it.copy(isReviewed = true, updatedAt = DateUtil.now())) }
    }

    /**
     * Apply a user correction (§6, §7) and LEARN from it:
     *  - update this transaction,
     *  - strengthen/create a categorization rule on the merchant or handle,
     *  - update the learned merchant dictionary,
     *  - optionally link a UPI handle to a person,
     *  - recategorize past similar transactions.
     */
    suspend fun applyCorrection(
        transactionId: Long,
        category: String? = null,
        subcategory: String? = null,
        personId: Long? = null,
        note: String? = null,
        markAsBusiness: Boolean = false,
        handle: String? = null,
        learn: Boolean = true,
    ) {
        val txn = txnDao.getById(transactionId) ?: return
        val now = DateUtil.now()
        var updated = txn.copy(
            category = category ?: txn.category,
            subcategory = subcategory ?: txn.subcategory,
            relatedPersonId = personId ?: txn.relatedPersonId,
            notes = note ?: txn.notes,
            categorizationSource = CategorizationSource.USER,
            categoryConfidence = 1.0,
            isReviewed = true,
            updatedAt = now,
        )
        txnDao.update(updated)
        audit("transaction", transactionId, "CATEGORIZE", newValue = updated.category)

        if (!learn) return

        val merchantKey = MerchantNormalizer.key(txn.merchantRaw)

        // 1) Link handle -> person permanently.
        if (handle != null && personId != null) {
            val existing = personDao.findHandle(handle.lowercase())
            if (existing == null) {
                personDao.insertHandle(
                    PersonHandleEntity(personId = personId, handle = handle.lowercase(),
                        displayName = txn.merchantNormalized, seenCount = 1, lastSeen = now)
                )
            } else {
                personDao.updateHandle(existing.copy(personId = personId, lastSeen = now))
            }
        }

        // 2) Learn the merchant/handle -> category mapping (silent auto-categorization next time).
        if (category != null && (markAsBusiness || personId == null)) {
            val key = handle?.lowercase() ?: merchantKey
            val existing = merchantMapDao.get(key)
            merchantMapDao.upsert(
                MerchantMapEntity(
                    key = key,
                    displayName = txn.merchantNormalized,
                    category = category,
                    subcategory = subcategory,
                    isBusiness = markAsBusiness || existing?.isBusiness ?: true,
                    hitCount = (existing?.hitCount ?: 0) + 1,
                    updatedAt = now,
                )
            )
        }

        // 3) Create/strengthen a rule so the categorization engine prefers this in future.
        if (category != null) {
            val field = if (handle != null) "HANDLE" else "MERCHANT"
            val value = handle ?: txn.merchantNormalized
            val rule = ruleDao.findExact(field, value)
            if (rule == null) {
                ruleDao.insert(
                    RuleEntity(
                        matchType = if (handle != null) "HANDLE" else "CONTAINS",
                        matchField = field,
                        matchValue = value,
                        assignCategory = category,
                        assignSubcategory = subcategory,
                        assignPersonId = personId,
                        priority = 200,
                        strength = 1,
                        isUserCreated = true,
                        createdAt = now,
                    )
                )
            } else {
                ruleDao.update(rule.copy(strength = rule.strength + 1, assignCategory = category,
                    assignSubcategory = subcategory, assignPersonId = personId ?: rule.assignPersonId))
            }
        }

        // 4) Recategorize past similar transactions (same merchant) that the user hasn't hand-edited.
        if (category != null) {
            val similar = txnDao.queryRaw(
                TransactionFilter(merchant = txn.merchantNormalized, includeIgnored = true).toQuery()
            )
            similar.filter { it.id != transactionId && it.categorizationSource != CategorizationSource.USER }
                .forEach {
                    txnDao.update(it.copy(category = category, subcategory = subcategory,
                        relatedPersonId = personId ?: it.relatedPersonId,
                        categorizationSource = CategorizationSource.RULE, isReviewed = true, updatedAt = now))
                }
        }
    }

    /** Reprocess every stored raw message with the current parser rules (§3). */
    suspend fun reprocessAllRawMessages(): Int {
        val all = rawDao.getAll()
        var produced = 0
        for (raw in all) {
            val outcome = processor.processRawMessage(
                sender = raw.sender, body = raw.body, receivedAt = raw.receivedAt,
                source = raw.source, packageName = raw.packageName, storeRaw = false,
            )
            if (outcome.transactionId != null && !outcome.merged) produced++
        }
        return produced
    }

    private suspend fun audit(entityType: String, id: Long, action: String, field: String? = null,
                              oldValue: String? = null, newValue: String? = null) {
        auditDao.insert(
            AuditLogEntity(entityType = entityType, entityId = id, action = action,
                field = field, oldValue = oldValue, newValue = newValue, timestamp = DateUtil.now())
        )
    }
}
