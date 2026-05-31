package com.aimoneytracker.domain.digest

import kotlinx.serialization.Serializable

@Serializable
data class NamedAmount(val name: String, val amount: Long, val count: Int = 0)

@Serializable
data class CategoryChange(val category: String, val current: Long, val previous: Long) {
    val delta: Long get() = current - previous
    val pctChange: Double get() = if (previous == 0L) (if (current == 0L) 0.0 else 100.0)
        else (current - previous) * 100.0 / previous
}

@Serializable
data class UpcomingBill(val name: String, val amount: Long, val dueDateMillis: Long)

@Serializable
data class GoalProgress(val name: String, val saved: Long, val target: Long) {
    val pct: Double get() = if (target == 0L) 0.0 else (saved * 100.0 / target).coerceIn(0.0, 100.0)
}

/**
 * The deterministic content of a digest (§16). Computed entirely in Kotlin; an optional natural
 * language summary is generated from these numbers by the LLM but never replaces them.
 */
@Serializable
data class DigestContent(
    val type: String,                 // DigestType.name ("WEEKLY"/"MONTHLY"); kept as String so this
                                      // @Serializable model doesn't pull the enum into serialization.
    val periodStart: Long,
    val periodEnd: Long,

    val totalSpent: Long,
    val totalIncome: Long = 0,
    val netSavings: Long = 0,
    val savingsRatePct: Double = 0.0,

    val previousTotalSpent: Long = 0,
    val spendPctChange: Double = 0.0,

    val transactionCount: Int = 0,
    val topCategories: List<NamedAmount> = emptyList(),
    val topMerchant: NamedAmount? = null,
    val topPerson: NamedAmount? = null,
    val biggestExpense: NamedAmount? = null,

    val categoryBreakdown: List<NamedAmount> = emptyList(),
    val biggestChanges: List<CategoryChange> = emptyList(),

    val budgetStatusText: String? = null,
    val unusualFlags: List<String> = emptyList(),

    val upcomingBills: List<UpcomingBill> = emptyList(),
    val subscriptionsRenewing: List<UpcomingBill> = emptyList(),

    val netWorthChange: Long = 0,
    val goalProgress: List<GoalProgress> = emptyList(),
    val savedVsAverageText: String? = null,

    val headline: String = "",
)
