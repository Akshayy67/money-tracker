package com.aimoneytracker.domain.process

import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.domain.model.SubscriptionCycle
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Detects recurring obligations (subscriptions, EMIs, rent, utilities) and recurring income (salary)
 * from transaction history (§11, §12). These feed the forecasting engine's deterministic event layer.
 *
 * A series is "recurring" when the same merchant is charged at a roughly fixed interval (weekly /
 * monthly / quarterly / yearly) with a stable amount across at least three occurrences.
 */
class RecurringDetector @Inject constructor() {

    data class DetectedRecurring(
        val merchantKey: String,
        val merchantName: String,
        val typicalAmount: Long,
        val cycle: SubscriptionCycle,
        val dayOfCycle: Int,
        val lastDate: Long,
        val nextDueDate: Long,
        val occurrences: Int,
        val isIncome: Boolean,
    )

    fun detect(transactions: List<TransactionEntity>): List<DetectedRecurring> {
        val out = mutableListOf<DetectedRecurring>()
        out += detectFor(transactions.filter { it.type == TransactionType.DEBIT }, isIncome = false)
        out += detectFor(transactions.filter { it.type == TransactionType.CREDIT }, isIncome = true)
        return out
    }

    private fun detectFor(txns: List<TransactionEntity>, isIncome: Boolean): List<DetectedRecurring> {
        return txns.groupBy { it.merchantNormalized.lowercase().trim() }
            .filterKeys { it.isNotBlank() }
            .mapNotNull { (key, group) ->
                if (group.size < 3) return@mapNotNull null
                val sorted = group.sortedBy { it.dateTime }
                val gaps = sorted.zipWithNext { a, b ->
                    ChronoUnit.DAYS.between(DateUtil.toLocalDate(a.dateTime), DateUtil.toLocalDate(b.dateTime))
                }
                if (gaps.isEmpty()) return@mapNotNull null
                val avgGap = gaps.average()
                val cycle = classifyCycle(avgGap) ?: return@mapNotNull null

                // Amounts should be reasonably stable (coefficient of variation < ~25%).
                val amounts = sorted.map { it.amount }
                val meanAmt = amounts.average()
                if (meanAmt <= 0) return@mapNotNull null
                val sd = kotlin.math.sqrt(amounts.sumOf { (it - meanAmt) * (it - meanAmt) } / amounts.size)
                if (sd / meanAmt > 0.30 && !isIncome) return@mapNotNull null

                val last = sorted.last()
                val typical = amounts.sorted()[amounts.size / 2] // median
                val dom = DateUtil.toLocalDate(last.dateTime).dayOfMonth
                val next = nextDue(last.dateTime, cycle)

                DetectedRecurring(
                    merchantKey = key,
                    merchantName = last.merchantNormalized,
                    typicalAmount = typical,
                    cycle = cycle,
                    dayOfCycle = dom,
                    lastDate = last.dateTime,
                    nextDueDate = next,
                    occurrences = sorted.size,
                    isIncome = isIncome,
                )
            }
    }

    private fun classifyCycle(avgGapDays: Double): SubscriptionCycle? = when {
        avgGapDays in 5.0..9.0 -> SubscriptionCycle.WEEKLY
        avgGapDays in 26.0..34.0 -> SubscriptionCycle.MONTHLY
        avgGapDays in 85.0..95.0 -> SubscriptionCycle.QUARTERLY
        avgGapDays in 175.0..190.0 -> SubscriptionCycle.HALF_YEARLY
        avgGapDays in 355.0..375.0 -> SubscriptionCycle.YEARLY
        else -> null
    }

    private fun nextDue(lastDate: Long, cycle: SubscriptionCycle): Long {
        val d = DateUtil.toLocalDate(lastDate)
        val next = when (cycle) {
            SubscriptionCycle.WEEKLY -> d.plusWeeks(1)
            SubscriptionCycle.MONTHLY -> d.plusMonths(1)
            SubscriptionCycle.QUARTERLY -> d.plusMonths(3)
            SubscriptionCycle.HALF_YEARLY -> d.plusMonths(6)
            SubscriptionCycle.YEARLY -> d.plusYears(1)
            SubscriptionCycle.CUSTOM -> d.plusMonths(1)
        }
        return DateUtil.startOfDay(next)
    }
}
