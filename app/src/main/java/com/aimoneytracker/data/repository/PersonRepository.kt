package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.PersonDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.PersonEntity
import com.aimoneytracker.data.local.result.PersonBalance
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepository @Inject constructor(
    private val personDao: PersonDao,
    private val txnDao: TransactionDao,
) {
    fun observeAll(): Flow<List<PersonEntity>> = personDao.observeAll()
    fun observeById(id: Long): Flow<PersonEntity?> = personDao.observeById(id)
    fun observeByRelationship(rel: String): Flow<List<PersonEntity>> = personDao.observeByRelationship(rel)
    suspend fun getById(id: Long): PersonEntity? = personDao.getById(id)
    suspend fun getAll(): List<PersonEntity> = personDao.getAll()

    suspend fun add(person: PersonEntity): Long = personDao.insert(person.copy(createdAt = DateUtil.now()))
    suspend fun update(person: PersonEntity) = personDao.update(person)

    suspend fun findOrCreateByName(name: String): Long {
        personDao.findByName(name)?.let { return it.id }
        return personDao.insert(PersonEntity(name = name, createdAt = DateUtil.now()))
    }

    suspend fun linkHandle(personId: Long, handle: String, displayName: String?) {
        val existing = personDao.findHandle(handle.lowercase())
        if (existing == null) {
            personDao.insertHandle(
                com.aimoneytracker.data.local.entity.PersonHandleEntity(
                    personId = personId, handle = handle.lowercase(),
                    displayName = displayName, lastSeen = DateUtil.now(),
                )
            )
        } else {
            personDao.updateHandle(existing.copy(personId = personId, lastSeen = DateUtil.now()))
        }
    }

    /** Merge [sourceId] into [targetId]: move handles and re-point transactions, then delete source. */
    suspend fun merge(sourceId: Long, targetId: Long) {
        personDao.reassignHandles(sourceId, targetId)
        personDao.getById(sourceId)?.let { src ->
            // Move transactions one by one (kept simple; volumes per person are small).
            txnReassign(sourceId, targetId)
            personDao.delete(src)
        }
    }

    private suspend fun txnReassign(sourceId: Long, targetId: Long) {
        val list = com.aimoneytracker.data.repository.TransactionFilter(personId = sourceId, includeIgnored = true, includeArchived = true)
        txnDao.queryRaw(list.toQuery()).forEach {
            txnDao.update(it.copy(relatedPersonId = targetId, updatedAt = DateUtil.now()))
        }
    }

    /** Net sent/received with a person (§8). Sent = debits to them, received = credits from them. */
    suspend fun balanceWith(personId: Long): PersonBalance {
        val sent = txnDao.personSumByType(personId, TransactionType.DEBIT.name)
        val received = txnDao.personSumByType(personId, TransactionType.CREDIT.name)
        return PersonBalance(personId, sent, received, 0, 0)
    }
}
