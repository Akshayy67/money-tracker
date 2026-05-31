package com.aimoneytracker.domain.digest

import com.aimoneytracker.domain.model.DigestType
import com.aimoneytracker.util.Money
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Pre-fetched, name-resolved inputs for the digest. The generator gathers these from repositories. */
data class DigestInput(
    val type: DigestType,
    val periodStart: Long,
    val periodEnd: Long,
    val totalSpent: Long,
    val totalIncome: Long,
    val previousTotalSpent: Long,
    val transactionCount: Int,
    val topCategories: List<NamedAmount>,
    val previousCategoryTotals: Map<String, Long>,
    val currentCategoryTotals: List<NamedAmount>,
    val topMerchant: NamedAmount?,
    val topPerson: NamedAmount?,
    val biggestExpense: NamedAmount?,
    val budgetStatusText: String?,
    val upcomingBills: List<UpcomingBill>,
    val subscriptionsRenewing: List<UpcomingBill>,
    val netWorthChange: Long,
    val goalProgress: List<GoalProgress>,
    val averagePeriodSpend: Long, // trailing average spend for this period type
)

/**
 * Deterministic digest builder (§16). Turns pre-fetched figures into the structured [DigestContent].
 * All math (percentage changes, savings rate, anomaly flags, biggest movers) happens here in Kotlin.
 */
@Singleton
class DigestEngine @Inject constructor() {

    fun build(input: DigestInput): DigestContent {
        val net = input.totalIncome - input.totalSpent
        val savingsRate = if (input.totalIncome > 0)
            (net.toDouble() / input.totalIncome * 100).coerceIn(-1000.0, 100.0) else 0.0

        val spendPct = pctChange(input.totalSpent, input.previousTotalSpent)

        // Biggest category movers vs the previous comparable period.
        val changes = input.currentCategoryTotals.map { cur ->
            CategoryChange(
                category = cur.name,
                current = cur.amount,
                previous = input.previousCategoryTotals[cur.name] ?: 0L,
            )
        }.sortedByDescending { abs(it.delta) }.take(5)

        // Simple anomaly flags: a category more than 60% above its previous period, and large spikes.
        val flags = buildList {
            changes.filter { it.previous > 0 && it.pctChange >= 60 }.take(3).forEach {
                add("${it.category.replaceFirstChar { c -> c.uppercase() }} up ${it.pctChange.toInt()}% vs last ${periodWord(input.type)}")
            }
            input.biggestExpense?.let {
                if (input.totalSpent > 0 && it.amount > input.totalSpent / 3) {
                    add("One large expense (${Money.format(it.amount)}) at ${it.name}")
                }
            }
        }

        val savedVsAvg = if (input.averagePeriodSpend > 0) {
            val diff = input.averagePeriodSpend - input.totalSpent
            if (diff >= 0) "You spent ${Money.format(diff)} less than your average ${periodWord(input.type)}"
            else "You spent ${Money.format(-diff)} more than your average ${periodWord(input.type)}"
        } else null

        val headline = buildHeadline(input.type, input.totalSpent, spendPct, net)

        return DigestContent(
            type = input.type.name,
            periodStart = input.periodStart,
            periodEnd = input.periodEnd,
            totalSpent = input.totalSpent,
            totalIncome = input.totalIncome,
            netSavings = net,
            savingsRatePct = savingsRate,
            previousTotalSpent = input.previousTotalSpent,
            spendPctChange = spendPct,
            transactionCount = input.transactionCount,
            topCategories = input.topCategories.take(3),
            topMerchant = input.topMerchant,
            topPerson = input.topPerson,
            biggestExpense = input.biggestExpense,
            categoryBreakdown = input.currentCategoryTotals,
            biggestChanges = changes,
            budgetStatusText = input.budgetStatusText,
            unusualFlags = flags,
            upcomingBills = input.upcomingBills,
            subscriptionsRenewing = input.subscriptionsRenewing,
            netWorthChange = input.netWorthChange,
            goalProgress = input.goalProgress,
            savedVsAverageText = savedVsAvg,
            headline = headline,
        )
    }

    private fun buildHeadline(type: DigestType, spent: Long, pct: Double, net: Long): String {
        val period = if (type == DigestType.WEEKLY) "week" else "month"
        val dir = when {
            pct > 2 -> "up ${pct.toInt()}%"
            pct < -2 -> "down ${abs(pct).toInt()}%"
            else -> "about the same"
        }
        return if (type == DigestType.MONTHLY) {
            val savedWord = if (net >= 0) "saved ${Money.format(net)}" else "overspent ${Money.format(-net)}"
            "This $period you spent ${Money.format(spent)} ($dir) and $savedWord."
        } else {
            "This $period you spent ${Money.format(spent)} — $dir vs last $period."
        }
    }

    private fun pctChange(current: Long, previous: Long): Double =
        if (previous == 0L) (if (current == 0L) 0.0 else 100.0)
        else (current - previous) * 100.0 / previous

    private fun periodWord(type: DigestType) = if (type == DigestType.WEEKLY) "week" else "month"
}
