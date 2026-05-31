package com.aimoneytracker.digest

import com.aimoneytracker.domain.digest.DigestEngine
import com.aimoneytracker.domain.digest.DigestInput
import com.aimoneytracker.domain.digest.NamedAmount
import com.aimoneytracker.domain.model.DigestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestEngineTest {

    private val engine = DigestEngine()

    private fun input(spent: Long, income: Long, prevSpent: Long) = DigestInput(
        type = DigestType.MONTHLY,
        periodStart = 0,
        periodEnd = 100,
        totalSpent = spent,
        totalIncome = income,
        previousTotalSpent = prevSpent,
        transactionCount = 42,
        topCategories = listOf(NamedAmount("food", 300000, 10)),
        previousCategoryTotals = mapOf("food" to 100000),
        currentCategoryTotals = listOf(NamedAmount("food", 300000, 10)),
        topMerchant = NamedAmount("Swiggy", 150000, 5),
        topPerson = null,
        biggestExpense = NamedAmount("Rent", 5000000),
        budgetStatusText = "All budgets on track",
        upcomingBills = emptyList(),
        subscriptionsRenewing = emptyList(),
        netWorthChange = 0,
        goalProgress = emptyList(),
        averagePeriodSpend = 1200000,
    )

    @Test
    fun computesNetSavingsAndRate() {
        val c = engine.build(input(spent = 1_000_000, income = 1_500_000, prevSpent = 800_000))
        assertEquals(500_000L, c.netSavings)
        assertEquals(33, c.savingsRatePct.toInt())
    }

    @Test
    fun computesSpendPercentChange() {
        val c = engine.build(input(spent = 1_200_000, income = 1_500_000, prevSpent = 1_000_000))
        assertEquals(20, c.spendPctChange.toInt())
    }

    @Test
    fun flagsCategorySpike() {
        // food went 100000 -> 300000 (+200%) which exceeds the 60% anomaly threshold.
        val c = engine.build(input(spent = 1_000_000, income = 1_500_000, prevSpent = 800_000))
        assertTrue(c.unusualFlags.any { it.contains("Food", ignoreCase = true) })
    }

    @Test
    fun headlineMentionsSavedWhenPositiveNet() {
        val c = engine.build(input(spent = 1_000_000, income = 1_500_000, prevSpent = 1_000_000))
        assertTrue(c.headline.contains("saved", ignoreCase = true))
    }

    @Test
    fun headlineMentionsOverspentWhenNegativeNet() {
        val c = engine.build(input(spent = 2_000_000, income = 1_000_000, prevSpent = 1_000_000))
        assertTrue(c.headline.contains("overspent", ignoreCase = true))
    }
}
