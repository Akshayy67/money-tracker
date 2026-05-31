package com.aimoneytracker.domain.insights

import com.aimoneytracker.data.local.result.CategorySum
import com.aimoneytracker.data.local.result.DayOfWeekSum
import com.aimoneytracker.data.local.result.HourSum
import com.aimoneytracker.data.local.result.MerchantSum
import com.aimoneytracker.util.Money
import javax.inject.Inject
import javax.inject.Singleton

enum class InsightSeverity { INFO, POSITIVE, WARNING }

data class Insight(val title: String, val detail: String, val severity: InsightSeverity)

/**
 * Deterministic insight detection (§14). Spots weekend/late-night patterns, category spikes, merchant
 * loyalty and saving opportunities from already-computed aggregates. The AI layer may later rephrase
 * a [Insight.detail], but the facts here are computed in Kotlin.
 */
@Singleton
class InsightsEngine @Inject constructor() {

    data class Input(
        val currentCategories: List<CategorySum>,
        val previousCategories: List<CategorySum>,
        val byDayOfWeek: List<DayOfWeekSum>,
        val byHour: List<HourSum>,
        val topMerchants: List<MerchantSum>,
        val totalSpent: Long,
        val unusedSubscriptionNames: List<String>,
    )

    fun analyze(input: Input): List<Insight> {
        val out = mutableListOf<Insight>()

        // Weekend vs weekday spending (dow: 0=Sun..6=Sat per strftime %w).
        val weekend = input.byDayOfWeek.filter { it.dow == 0 || it.dow == 6 }.sumOf { it.total }
        val weekday = input.byDayOfWeek.filter { it.dow in 1..5 }.sumOf { it.total }
        if (weekday > 0 && weekend > 0) {
            val weekendDaily = weekend / 2.0
            val weekdayDaily = weekday / 5.0
            if (weekendDaily > weekdayDaily * 1.4) {
                out += Insight(
                    "Weekends cost more",
                    "You spend about ${(weekendDaily / weekdayDaily).format1()}× more per day on weekends.",
                    InsightSeverity.INFO,
                )
            }
        }

        // Late-night spending (22:00–04:00).
        val lateNight = input.byHour.filter { it.hour >= 22 || it.hour <= 4 }.sumOf { it.total }
        if (input.totalSpent > 0 && lateNight > input.totalSpent * 0.15) {
            out += Insight(
                "Late-night spending",
                "${Money.format(lateNight)} (${(lateNight * 100.0 / input.totalSpent).toInt()}%) of spend happened late at night.",
                InsightSeverity.INFO,
            )
        }

        // Category spikes vs previous period.
        val prevMap = input.previousCategories.associate { it.category to it.total }
        input.currentCategories.forEach { cur ->
            val prev = prevMap[cur.category] ?: 0L
            if (prev > 0 && cur.total > prev * 1.6) {
                out += Insight(
                    "${cur.category.replaceFirstChar { it.uppercase() }} jumped",
                    "Up ${((cur.total - prev) * 100 / prev)}% to ${Money.format(cur.total)} vs last period.",
                    InsightSeverity.WARNING,
                )
            }
        }

        // Merchant loyalty.
        input.topMerchants.firstOrNull()?.let { top ->
            if (top.count >= 5) {
                out += Insight(
                    "Frequent at ${top.merchantNormalized}",
                    "${top.count} visits, ${Money.format(top.total)} total. Your most-used merchant.",
                    InsightSeverity.INFO,
                )
            }
        }

        // Unused subscriptions = saving opportunity.
        if (input.unusedSubscriptionNames.isNotEmpty()) {
            out += Insight(
                "Possible savings",
                "These look unused: ${input.unusedSubscriptionNames.take(3).joinToString(", ")}. Consider cancelling.",
                InsightSeverity.POSITIVE,
            )
        }

        return out
    }

    private fun Double.format1(): String = "%.1f".format(this)
}
