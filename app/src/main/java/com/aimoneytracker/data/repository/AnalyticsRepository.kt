package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.result.CategorySum
import com.aimoneytracker.data.local.result.DayOfWeekSum
import com.aimoneytracker.data.local.result.DaySum
import com.aimoneytracker.data.local.result.HourSum
import com.aimoneytracker.data.local.result.MerchantSum
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val txnDao: TransactionDao,
    private val accountRepository: AccountRepository,
) {
    data class PeriodSummary(
        val income: Long,
        val expense: Long,
        val net: Long,
        val savingsRatePct: Double,
    )

    suspend fun summary(start: Long, end: Long): PeriodSummary {
        val income = txnDao.sumByType(TransactionType.CREDIT.name, start, end)
        val expense = txnDao.sumByType(TransactionType.DEBIT.name, start, end)
        val net = income - expense
        val rate = if (income > 0) (net.toDouble() / income * 100) else 0.0
        return PeriodSummary(income, expense, net, rate)
    }

    fun observeMonthSummary(): Flow<PeriodSummary> {
        val start = DateUtil.startOfMonth()
        val end = DateUtil.endOfMonth()
        return combine(
            txnDao.observeSumByType(TransactionType.CREDIT.name, start, end),
            txnDao.observeSumByType(TransactionType.DEBIT.name, start, end),
        ) { income, expense ->
            val net = income - expense
            PeriodSummary(income, expense, net, if (income > 0) net.toDouble() / income * 100 else 0.0)
        }
    }

    fun observeCategoryBreakdown(start: Long, end: Long): Flow<List<CategorySum>> =
        txnDao.observeSumByCategory(TransactionType.DEBIT.name, start, end)

    suspend fun categoryBreakdown(start: Long, end: Long): List<CategorySum> =
        txnDao.sumByCategory(TransactionType.DEBIT.name, start, end)

    suspend fun topMerchants(start: Long, end: Long, limit: Int = 10): List<MerchantSum> =
        txnDao.topMerchants(start, end, limit)

    suspend fun dailyExpenses(start: Long, end: Long): List<DaySum> =
        txnDao.dailyExpenseTotals(start, end)

    suspend fun byDayOfWeek(start: Long, end: Long): List<DayOfWeekSum> =
        txnDao.expenseByDayOfWeek(start, end)

    suspend fun byHour(start: Long, end: Long): List<HourSum> =
        txnDao.expenseByHour(start, end)

    /** Net worth (§14): tracked account balances net of loans/credit-card dues. */
    suspend fun netWorth(): Long = accountRepository.totalTrackedBalance()
}
