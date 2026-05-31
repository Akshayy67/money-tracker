package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.BudgetDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.BudgetEntity
import com.aimoneytracker.domain.model.BudgetPeriod
import com.aimoneytracker.domain.model.BudgetScope
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val txnDao: TransactionDao,
) {
    data class BudgetStatus(
        val budget: BudgetEntity,
        val spent: Long,
        val remaining: Long,
        val pctUsed: Double,
        val exceeded: Boolean,
        val nearingLimit: Boolean,
    )

    fun observeActive(): Flow<List<BudgetEntity>> = budgetDao.observeActive()
    suspend fun add(budget: BudgetEntity): Long = budgetDao.insert(budget.copy(createdAt = DateUtil.now()))
    suspend fun update(budget: BudgetEntity) = budgetDao.update(budget)
    suspend fun delete(budget: BudgetEntity) = budgetDao.delete(budget)

    suspend fun statuses(now: Long = DateUtil.now()): List<BudgetStatus> =
        budgetDao.getActive().map { status(it, now) }

    suspend fun status(budget: BudgetEntity, now: Long = DateUtil.now()): BudgetStatus {
        val (start, end) = periodBounds(budget, now)
        val spent = when (budget.scope) {
            BudgetScope.OVERALL -> txnDao.sumByType(TransactionType.DEBIT.name, start, end)
            BudgetScope.CATEGORY -> txnDao.sumByCategory(TransactionType.DEBIT.name, start, end)
                .firstOrNull { it.category == budget.categoryKey }?.total ?: 0L
            BudgetScope.PERSON -> txnDao.personSumByType(budget.personId ?: -1, TransactionType.DEBIT.name)
        }
        val remaining = budget.amount - spent
        val pct = if (budget.amount > 0) spent * 100.0 / budget.amount else 0.0
        return BudgetStatus(
            budget = budget,
            spent = spent,
            remaining = remaining,
            pctUsed = pct,
            exceeded = spent > budget.amount,
            nearingLimit = pct >= budget.alertThresholdPct && spent <= budget.amount,
        )
    }

    private fun periodBounds(budget: BudgetEntity, now: Long): Pair<Long, Long> = when (budget.period) {
        BudgetPeriod.WEEKLY -> DateUtil.startOfWeek(now) to DateUtil.endOfWeek(now)
        BudgetPeriod.MONTHLY -> DateUtil.startOfMonth(now) to DateUtil.endOfMonth(now)
        BudgetPeriod.ANNUAL -> DateUtil.startOfYear(now) to now
        BudgetPeriod.CUSTOM -> (budget.customStart ?: DateUtil.startOfMonth(now)) to
            (budget.customEnd ?: DateUtil.endOfMonth(now))
    }
}
