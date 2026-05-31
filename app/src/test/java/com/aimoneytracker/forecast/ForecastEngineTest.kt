package com.aimoneytracker.forecast

import com.aimoneytracker.domain.forecast.DaySpend
import com.aimoneytracker.domain.forecast.ForecastEngine
import com.aimoneytracker.domain.forecast.ForecastInput
import com.aimoneytracker.domain.forecast.ScheduledEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForecastEngineTest {

    private val engine = ForecastEngine()

    private fun input(
        currentBalance: Long,
        trailing: List<DaySpend>,
        scheduled: List<ScheduledEvent> = emptyList(),
        today: LocalDate = LocalDate.of(2024, 6, 10),
    ) = ForecastInput(
        today = today,
        monthStart = LocalDate.of(2024, 6, 1),
        monthEnd = LocalDate.of(2024, 6, 30),
        currentBalance = currentBalance,
        mtdDiscretionarySpend = 0,
        trailingDailySpend = trailing,
        scheduledEvents = scheduled,
        knownOneOffs = emptyList(),
        lowBalanceThreshold = 0,
    )

    private fun uniformTrailing(perDay: Long, days: Int, endBefore: LocalDate): List<DaySpend> =
        (1..days).map { DaySpend(endBefore.minusDays(it.toLong()), perDay) }

    @Test
    fun projectedEndBalanceObeysAccountingIdentity() {
        val trailing = uniformTrailing(10_000, 60, LocalDate.of(2024, 6, 1))
        val scheduled = listOf(
            ScheduledEvent("Rent", 5_000_000, LocalDate.of(2024, 6, 15), isInflow = false),
            ScheduledEvent("Salary", 8_000_000, LocalDate.of(2024, 6, 28), isInflow = true),
        )
        val result = engine.forecast(input(currentBalance = 10_000_000, trailing = trailing, scheduled = scheduled))

        assertEquals(5_000_000, result.scheduledOutflowTotal)
        assertEquals(8_000_000, result.scheduledInflowTotal)
        // current + inflows - outflows - projected variable == expected end balance.
        val expected = 10_000_000 + result.scheduledInflowTotal - result.scheduledOutflowTotal - result.projectedVariableSpend
        assertEquals(expected, result.projectedEndBalanceExpected)
    }

    @Test
    fun confidenceBandIsOrdered() {
        val trailing = uniformTrailing(20_000, 60, LocalDate.of(2024, 6, 1))
        val result = engine.forecast(input(currentBalance = 5_000_000, trailing = trailing))
        assertTrue(result.projectedEndBalanceLow <= result.projectedEndBalanceExpected)
        assertTrue(result.projectedEndBalanceExpected <= result.projectedEndBalanceHigh)
    }

    @Test
    fun runLowDateFlaggedWhenBalanceInsufficient() {
        // Tiny balance, heavy daily spend → should run low before month end.
        val trailing = uniformTrailing(200_000, 60, LocalDate.of(2024, 6, 1))
        val result = engine.forecast(
            input(currentBalance = 100_000, trailing = trailing).copy(lowBalanceThreshold = 0)
        )
        assertTrue(result.projectedVariableSpend > 0)
        // With far more projected spend than balance, run-low should be set.
        assertTrue(result.runLowDate != null)
    }

    @Test
    fun emptyHistoryGivesZeroVariableSpend() {
        val result = engine.forecast(input(currentBalance = 1_000_000, trailing = emptyList()))
        assertEquals(0L, result.projectedVariableSpend)
        assertEquals(1_000_000L, result.projectedEndBalanceExpected)
    }

    @Test
    fun safeToSpendNonNegative() {
        val trailing = uniformTrailing(10_000, 30, LocalDate.of(2024, 6, 1))
        val result = engine.forecast(input(currentBalance = 2_000_000, trailing = trailing))
        assertTrue(result.dailySafeToSpend >= 0)
        assertTrue(result.totalSafeToSpend >= 0)
    }
}
