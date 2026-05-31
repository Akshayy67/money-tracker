package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.AccountDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.AccountEntity
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val txnDao: TransactionDao,
) {
    fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll()
    fun observeById(id: Long): Flow<AccountEntity?> = accountDao.observeById(id)
    suspend fun getById(id: Long): AccountEntity? = accountDao.getById(id)
    suspend fun getAll(): List<AccountEntity> = accountDao.getAll()

    suspend fun add(account: AccountEntity): Long = accountDao.insert(account.copy(createdAt = DateUtil.now()))
    suspend fun update(account: AccountEntity) = accountDao.update(account)
    suspend fun delete(account: AccountEntity) = accountDao.delete(account)

    /** Computed balance = opening + (credits − debits) across all activity. */
    suspend fun computedBalance(accountId: Long): Long {
        val acc = accountDao.getById(accountId) ?: return 0
        return acc.openingBalance + txnDao.accountBalanceDelta(accountId)
    }

    suspend fun recomputeBalances() {
        accountDao.getAll().forEach { acc ->
            val balance = acc.openingBalance + txnDao.accountBalanceDelta(acc.id)
            accountDao.updateCachedBalance(acc.id, balance)
        }
    }

    /** Total balance across tracked accounts — the forecast's "current balance". */
    suspend fun totalTrackedBalance(): Long {
        return accountDao.getTracked().sumOf { it.openingBalance + txnDao.accountBalanceDelta(it.id) }
    }

    /** Available-balance reconciliation (§18): drift between computed and bank-reported balance. */
    suspend fun balanceDrift(accountId: Long): Long? {
        val acc = accountDao.getById(accountId) ?: return null
        val reported = acc.reportedBalance ?: return null
        return computedBalance(accountId) - reported
    }
}
