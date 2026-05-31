package com.aimoneytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aimoneytracker.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isTracked = 1")
    suspend fun getTracked(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isOwnAccount = 1")
    suspend fun getOwnAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE maskedNumber = :masked LIMIT 1")
    suspend fun findByMasked(masked: String): AccountEntity?

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<AccountEntity>

    @Query("UPDATE accounts SET reportedBalance = :balance, reportedBalanceAt = :at WHERE id = :id")
    suspend fun updateReportedBalance(id: Long, balance: Long, at: Long)

    @Query("UPDATE accounts SET currentBalanceCached = :balance WHERE id = :id")
    suspend fun updateCachedBalance(id: Long, balance: Long)
}
