package com.aimoneytracker.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.local.result.AccountSum
import com.aimoneytracker.data.local.result.CategorySum
import com.aimoneytracker.data.local.result.DaySum
import com.aimoneytracker.data.local.result.DayOfWeekSum
import com.aimoneytracker.data.local.result.HourSum
import com.aimoneytracker.data.local.result.MerchantSum
import com.aimoneytracker.data.local.result.PersonSum
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query(
        """
        SELECT * FROM transactions
        WHERE isArchived = 0 AND isIgnored = 0
        ORDER BY dateTime DESC
        """
    )
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isArchived = 0 ORDER BY dateTime DESC")
    fun pagingAll(): PagingSource<Int, TransactionEntity>

    /**
     * Dynamic filtering/search built from a filter spec. Paging over this is handled manually by
     * [com.aimoneytracker.data.repository.TransactionPagingSource] (Room's @RawQuery cannot return a
     * PagingSource), using LIMIT/OFFSET via [queryRaw].
     */
    @RawQuery(observedEntities = [TransactionEntity::class])
    fun observeFiltered(query: SupportSQLiteQuery): Flow<List<TransactionEntity>>

    @RawQuery
    suspend fun queryRaw(query: SupportSQLiteQuery): List<TransactionEntity>

    // ---- Review queue (§7) ----
    @Query(
        """
        SELECT * FROM transactions
        WHERE isReviewed = 0 AND isIgnored = 0 AND isArchived = 0
        ORDER BY dateTime DESC
        """
    )
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE isReviewed = 0 AND isIgnored = 0 AND isArchived = 0")
    fun observeNeedsReviewCount(): Flow<Int>

    // ---- Date-range list (forecast, digest, analytics) ----
    @Query(
        """
        SELECT * FROM transactions
        WHERE dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        ORDER BY dateTime ASC
        """
    )
    suspend fun getInRange(start: Long, end: Long): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE dateTime BETWEEN :start AND :end AND isIgnored = 0 AND isArchived = 0
        ORDER BY dateTime ASC
        """
    )
    suspend fun getInRangeIncludingTransfers(start: Long, end: Long): List<TransactionEntity>

    // ---- Aggregates ----
    @Query(
        """
        SELECT COALESCE(SUM(amount),0) FROM transactions
        WHERE type = :type AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        """
    )
    suspend fun sumByType(type: String, start: Long, end: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(amount),0) FROM transactions
        WHERE type = :type AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        """
    )
    fun observeSumByType(type: String, start: Long, end: Long): Flow<Long>

    @Query(
        """
        SELECT category AS category, COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = :type AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        GROUP BY category ORDER BY total DESC
        """
    )
    suspend fun sumByCategory(type: String, start: Long, end: Long): List<CategorySum>

    @Query(
        """
        SELECT category AS category, COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = :type AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        GROUP BY category ORDER BY total DESC
        """
    )
    fun observeSumByCategory(type: String, start: Long, end: Long): Flow<List<CategorySum>>

    @Query(
        """
        SELECT merchantNormalized AS merchantNormalized, COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = 'DEBIT' AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        GROUP BY merchantNormalized ORDER BY total DESC LIMIT :limit
        """
    )
    suspend fun topMerchants(start: Long, end: Long, limit: Int): List<MerchantSum>

    @Query(
        """
        SELECT relatedPersonId AS relatedPersonId, COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE relatedPersonId IS NOT NULL AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0
        GROUP BY relatedPersonId ORDER BY total DESC
        """
    )
    suspend fun sumByPerson(start: Long, end: Long): List<PersonSum>

    @Query(
        """
        SELECT accountId AS accountId, COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = :type AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        GROUP BY accountId
        """
    )
    suspend fun sumByAccount(type: String, start: Long, end: Long): List<AccountSum>

    // Daily totals for calendar heatmap / trends (date stored as epoch millis -> local day string).
    @Query(
        """
        SELECT strftime('%Y-%m-%d', dateTime/1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = 'DEBIT' AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        GROUP BY day ORDER BY day ASC
        """
    )
    suspend fun dailyExpenseTotals(start: Long, end: Long): List<DaySum>

    // Day-of-week seasonality (0=Sunday..6=Saturday per strftime %w).
    @Query(
        """
        SELECT CAST(strftime('%w', dateTime/1000, 'unixepoch', 'localtime') AS INTEGER) AS dow,
               COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = 'DEBIT' AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        GROUP BY dow
        """
    )
    suspend fun expenseByDayOfWeek(start: Long, end: Long): List<DayOfWeekSum>

    // Hour-of-day (for late-night spending insight).
    @Query(
        """
        SELECT CAST(strftime('%H', dateTime/1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = 'DEBIT' AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        GROUP BY hour
        """
    )
    suspend fun expenseByHour(start: Long, end: Long): List<HourSum>

    // ---- Person ----
    @Query(
        """
        SELECT * FROM transactions
        WHERE relatedPersonId = :personId AND isArchived = 0
        ORDER BY dateTime DESC
        """
    )
    fun observeByPerson(personId: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(amount),0) FROM transactions
        WHERE relatedPersonId = :personId AND type = :type AND isIgnored = 0 AND isArchived = 0
        """
    )
    suspend fun personSumByType(personId: Long, type: String): Long

    // ---- Dedup window (§4) ----
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount = :amount AND type = :type
          AND dateTime BETWEEN :from AND :to
          AND isArchived = 0
        """
    )
    suspend fun findCandidatesForDedup(amount: Long, type: String, from: Long, to: Long): List<TransactionEntity>

    // ---- Recent merchants / payees for quick-pick in the review card ----
    @Query(
        """
        SELECT merchantNormalized AS merchantNormalized, COALESCE(SUM(amount),0) AS total, COUNT(*) AS count
        FROM transactions
        WHERE isArchived = 0
        GROUP BY merchantNormalized ORDER BY MAX(dateTime) DESC LIMIT :limit
        """
    )
    suspend fun recentMerchants(limit: Int): List<MerchantSum>

    // ---- History for forecasting (trailing months per category) ----
    @Query(
        """
        SELECT * FROM transactions
        WHERE type = 'DEBIT' AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        ORDER BY dateTime ASC
        """
    )
    suspend fun expensesForHistory(start: Long, end: Long): List<TransactionEntity>

    /**
     * Net balance delta for an account = credits − debits (transfers included, since a transfer
     * genuinely moves money in/out of this specific account). Added to the account's opening balance.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END), 0)
        FROM transactions
        WHERE accountId = :accountId AND isIgnored = 0 AND isArchived = 0
        """
    )
    suspend fun accountBalanceDelta(accountId: Long): Long

    @Query(
        """
        SELECT * FROM transactions
        WHERE type = 'DEBIT' AND dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        ORDER BY amount DESC LIMIT :limit
        """
    )
    suspend fun topExpenses(start: Long, end: Long, limit: Int): List<TransactionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE dateTime BETWEEN :start AND :end
          AND isIgnored = 0 AND isArchived = 0 AND processingFlag != 'TRANSFER'
        """
    )
    suspend fun countInRange(start: Long, end: Long): Int

    @Query("SELECT * FROM transactions WHERE rawMessage IS NOT NULL")
    suspend fun allWithRaw(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
