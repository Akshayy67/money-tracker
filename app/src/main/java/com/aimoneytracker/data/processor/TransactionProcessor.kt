package com.aimoneytracker.data.processor

import com.aimoneytracker.data.local.dao.AccountDao
import com.aimoneytracker.data.local.dao.MerchantMapDao
import com.aimoneytracker.data.local.dao.PersonDao
import com.aimoneytracker.data.local.dao.RawMessageDao
import com.aimoneytracker.data.local.dao.RuleDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.AccountEntity
import com.aimoneytracker.data.local.entity.RawMessageEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.parser.MerchantNormalizer
import com.aimoneytracker.data.parser.ParsedTransaction
import com.aimoneytracker.data.parser.ParserRegistry
import com.aimoneytracker.data.preferences.SettingsRepository
import com.aimoneytracker.domain.ai.AiService
import com.aimoneytracker.domain.categorize.CategorizationEngine
import com.aimoneytracker.domain.categorize.CategorizationInput
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.domain.model.AccountType
import com.aimoneytracker.domain.model.CategorizationSource
import com.aimoneytracker.domain.model.ProcessingFlag
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.domain.process.DuplicateDetector
import com.aimoneytracker.domain.process.RefundDetector
import com.aimoneytracker.domain.process.TransferDetector
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The end-to-end ingestion pipeline (§3, §4, §6, §7). A captured message becomes a clean, categorized,
 * de-duplicated transaction here:
 *
 *   raw store → parse → build → resolve account → categorize → resolve person → dedup/merge →
 *   transfer/refund linking → confidence-based review flag → insert → reconcile balances.
 *
 * Returns the resulting transaction id, or null if the message wasn't a transaction or was a merged
 * duplicate.
 */
@Singleton
class TransactionProcessor @Inject constructor(
    private val parser: ParserRegistry,
    private val txnDao: TransactionDao,
    private val accountDao: AccountDao,
    private val personDao: PersonDao,
    private val ruleDao: RuleDao,
    private val merchantMapDao: MerchantMapDao,
    private val rawDao: RawMessageDao,
    private val categorizer: CategorizationEngine,
    private val duplicateDetector: DuplicateDetector,
    private val transferDetector: TransferDetector,
    private val refundDetector: RefundDetector,
    private val settings: SettingsRepository,
    private val aiService: AiService,
) {

    data class Outcome(
        val transactionId: Long?,
        val isFinancial: Boolean,
        val needsReview: Boolean,
        val merged: Boolean,
        val amount: Long = 0,
        val merchant: String = "",
        val isCredit: Boolean = false,
        val payeeHandle: String? = null,
    )

    suspend fun processRawMessage(
        sender: String?,
        body: String,
        receivedAt: Long,
        source: com.aimoneytracker.domain.model.TransactionSource,
        packageName: String? = null,
        storeRaw: Boolean = true,
    ): Outcome {
        // 1) Store the raw message verbatim (idempotent by content hash) for future reprocessing.
        if (storeRaw) {
            val hash = sha256("${sender}|${body}|${receivedAt / 60000}")
            if (rawDao.findByHash(hash) != null) {
                return Outcome(null, isFinancial = false, needsReview = false, merged = false)
            }
            rawDao.insert(
                RawMessageEntity(
                    sender = sender, body = body, receivedAt = receivedAt,
                    source = source, packageName = packageName, contentHash = hash, parsed = false,
                )
            )
        }

        // 2) Parse.
        val parsed = parser.parse(sender, body, receivedAt)
        if (!parsed.isFinancial || parsed.amount == null) {
            return Outcome(null, isFinancial = false, needsReview = false, merged = false)
        }

        // 3) Build the base transaction.
        val now = DateUtil.now()
        val merchantNorm = MerchantNormalizer.normalize(parsed.merchantRaw)
        val merchantKey = MerchantNormalizer.key(parsed.merchantRaw)
        val account = resolveAccount(parsed)

        var txn = TransactionEntity(
            amount = parsed.amount,
            type = parsed.type ?: TransactionType.DEBIT,
            currency = parsed.currency,
            merchantNormalized = merchantNorm,
            merchantRaw = parsed.merchantRaw ?: merchantNorm,
            category = CategoryCatalog.UNCATEGORIZED,
            dateTime = parsed.dateTime.takeIf { it > 0 } ?: receivedAt,
            accountId = account?.id,
            paymentMethod = parsed.paymentMethod,
            availableBalance = parsed.availableBalance,
            confidence = parsed.confidence,
            processingFlag = parsed.flag,
            rawMessage = parsed.raw,
            source = source,
            senderId = sender,
            createdAt = now,
            updatedAt = now,
        )
        txn = txn.copy(dedupKey = duplicateDetector.dedupKey(txn.amount, txn.merchantNormalized, txn.dateTime))

        // Failed transactions are recorded but ignored from totals.
        if (parsed.flag == ProcessingFlag.FAILED) {
            txn = txn.copy(isIgnored = true, isReviewed = true)
        }

        // 4) Categorize (rule → merchant-map → keyword; AI fallback below).
        val rules = ruleDao.getAll()
        val merchantEntry = merchantMapDao.get(merchantKey)
            ?: parsed.payeeHandle?.let { merchantMapDao.get(it) }
        val catResult = categorizer.categorize(
            CategorizationInput(
                merchantNormalized = merchantNorm,
                merchantKey = merchantKey,
                rawMessage = parsed.raw,
                vpa = parsed.payeeHandle,
                type = txn.type,
            ),
            rules = rules,
            merchantEntry = merchantEntry,
        )
        txn = txn.copy(
            category = catResult.category,
            subcategory = catResult.subcategory,
            categorizationSource = catResult.source,
            categoryConfidence = catResult.confidence,
            relatedPersonId = catResult.assignPersonId,
            isIgnored = txn.isIgnored || catResult.ignore,
        )

        // 5) Resolve person from a known UPI handle.
        if (txn.relatedPersonId == null && parsed.payeeHandle != null) {
            personDao.findHandle(parsed.payeeHandle)?.let { handle ->
                txn = txn.copy(relatedPersonId = handle.personId)
                personDao.updateHandle(handle.copy(seenCount = handle.seenCount + 1, lastSeen = now))
            }
        }

        // 6) Optional AI categorization for genuinely unknown merchants.
        if (catResult.needsAi && txn.category == CategoryCatalog.UNCATEGORIZED && aiService.isAvailable()) {
            val suggestion = aiService.categorizeMerchant(
                merchant = merchantNorm,
                rawMessage = parsed.raw,
                candidateKeys = CategoryCatalog.defaults.map { it.key },
            )
            if (suggestion != null && suggestion.confidence >= 0.6) {
                txn = txn.copy(
                    category = suggestion.categoryKey,
                    categorizationSource = CategorizationSource.AI,
                    categoryConfidence = suggestion.confidence,
                )
            }
        }

        // 7) Duplicate detection & merge.
        val (from, to) = duplicateDetector.windowFor(txn.dateTime)
        val dupCandidates = txnDao.findCandidatesForDedup(txn.amount, txn.type.name, from, to)
        val duplicate = duplicateDetector.findDuplicate(txn, dupCandidates)
        if (duplicate != null) {
            val merged = duplicateDetector.merge(duplicate, txn)
            txnDao.update(merged.copy(updatedAt = now))
            account?.let { reconcileBalance(it, parsed) }
            return Outcome(merged.id, isFinancial = true, needsReview = !merged.isReviewed, merged = true,
                amount = merged.amount, merchant = merged.merchantNormalized,
                isCredit = merged.type == TransactionType.CREDIT, payeeHandle = parsed.payeeHandle)
        }

        // 8) Review flag: low parse confidence, uncategorized, or an unknown person handle.
        val threshold = settings.settings.first().reviewThresholdPct / 100.0
        val unknownHandle = parsed.payeeHandle != null && txn.relatedPersonId == null && merchantEntry == null
        val needsReview = !txn.isIgnored && (
            parsed.confidence < threshold ||
                txn.category == CategoryCatalog.UNCATEGORIZED ||
                unknownHandle
            )
        txn = txn.copy(isReviewed = !needsReview)

        // 9) Insert.
        val id = txnDao.insert(txn)
        txn = txn.copy(id = id)

        // 10) Transfer detection against recent own-account activity.
        linkTransferIfAny(txn)

        // 11) Refund/reversal linking.
        linkRefundIfAny(txn)

        // 12) Reconcile account balance with the bank-reported one.
        account?.let { reconcileBalance(it, parsed) }

        return Outcome(id, isFinancial = true, needsReview = needsReview, merged = false,
            amount = txn.amount, merchant = txn.merchantNormalized,
            isCredit = txn.type == TransactionType.CREDIT, payeeHandle = parsed.payeeHandle)
    }

    /** Resolve (or lazily create) the account this message belongs to, by masked number. */
    private suspend fun resolveAccount(parsed: ParsedTransaction): AccountEntity? {
        val masked = parsed.accountMasked ?: return null
        accountDao.findByMasked(masked)?.let { return it }
        val type = if (parsed.paymentMethod == com.aimoneytracker.domain.model.PaymentMethod.CARD)
            AccountType.CREDIT_CARD else AccountType.BANK
        val newAcc = AccountEntity(
            name = "${parsed.bankName ?: "Account"} $masked",
            type = type,
            bankName = parsed.bankName,
            maskedNumber = masked,
            isTracked = true,
            isOwnAccount = true,
            createdAt = DateUtil.now(),
        )
        val id = accountDao.insert(newAcc)
        return newAcc.copy(id = id)
    }

    private suspend fun reconcileBalance(account: AccountEntity, parsed: ParsedTransaction) {
        val reported = parsed.availableBalance ?: return
        accountDao.updateReportedBalance(account.id, reported, DateUtil.now())
    }

    private suspend fun linkTransferIfAny(txn: TransactionEntity) {
        val ownAccounts = accountDao.getOwnAccounts()
        if (txn.accountId == null) return
        val (from, to) = (txn.dateTime - 30 * 60 * 1000L) to (txn.dateTime + 30 * 60 * 1000L)
        val recent = txnDao.findCandidatesForDedup(txn.amount,
            if (txn.type == TransactionType.DEBIT) TransactionType.CREDIT.name else TransactionType.DEBIT.name,
            from, to)
        val counterpart = transferDetector.findCounterpart(txn, recent, ownAccounts) ?: return
        val now = DateUtil.now()
        // Keep each leg's DEBIT/CREDIT direction (per-account balances need it); just flag as TRANSFER
        // and recategorize so it's excluded from income/expense aggregates.
        txnDao.update(txn.copy(processingFlag = ProcessingFlag.TRANSFER,
            category = CategoryCatalog.TRANSFERS, relatedTransactionId = counterpart.id, updatedAt = now))
        txnDao.update(counterpart.copy(processingFlag = ProcessingFlag.TRANSFER,
            category = CategoryCatalog.TRANSFERS, relatedTransactionId = txn.id, updatedAt = now))
    }

    private suspend fun linkRefundIfAny(txn: TransactionEntity) {
        if (txn.type != TransactionType.CREDIT) return
        val start = txn.dateTime - 45L * 24 * 60 * 60 * 1000
        val priorDebits = txnDao.getInRange(start, txn.dateTime)
        val original = refundDetector.findOriginalPurchase(txn, priorDebits) ?: return
        txnDao.update(txn.copy(processingFlag = ProcessingFlag.REFUND,
            relatedTransactionId = original.id, category = original.category, updatedAt = DateUtil.now()))
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
