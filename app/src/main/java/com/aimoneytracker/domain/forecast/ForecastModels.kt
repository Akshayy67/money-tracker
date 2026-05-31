package com.aimoneytracker.domain.forecast

import java.time.LocalDate

/** A single past day's total discretionary spend (minor units), used to learn the daily rate. */
data class DaySpend(val date: LocalDate, val amount: Long)

/** A known future-dated fixed obligation or income event inside the forecast window. */
data class ScheduledEvent(
    val name: String,
    val amount: Long,            // minor units, positive
    val dueDate: LocalDate,
    val isInflow: Boolean,
    val categoryKey: String? = null,
)

/**
 * Calibration knobs learned from comparing past forecasts to actuals (§15.9).
 * [spendMultiplier] nudges the projected variable spend (e.g. 1.05 if we historically under-predict);
 * [mtdWeightBias] shifts how aggressively we trust month-to-date actuals vs history.
 */
data class ForecastCalibration(
    val spendMultiplier: Double = 1.0,
    val mtdWeightBias: Double = 0.0,
) {
    companion object { val NEUTRAL = ForecastCalibration() }
}

/** Everything the [ForecastEngine] needs. Pure data — no Android, no DB. */
data class ForecastInput(
    val today: LocalDate,
    val monthStart: LocalDate,
    val monthEnd: LocalDate,
    val currentBalance: Long,                 // minor units, across tracked accounts
    val mtdDiscretionarySpend: Long,          // actual variable spend so far this month
    val trailingDailySpend: List<DaySpend>,   // per-day discretionary totals over trailing window
    val scheduledEvents: List<ScheduledEvent>,// future fixed outflows + expected inflows
    val knownOneOffs: List<ScheduledEvent>,   // user-added upcoming one-offs (treated like scheduled)
    val lowBalanceThreshold: Long = 0,
    val goalReserve: Long = 0,                 // amount to keep aside for goals this month
    val monthlyBudget: Long? = null,          // optional overall budget for safe-to-spend
    val calibration: ForecastCalibration = ForecastCalibration.NEUTRAL,
)

/** A point on the projected-balance chart (forecast portion). */
data class ProjectionPoint(
    val date: LocalDate,
    val expectedBalance: Long,
    val lowBalance: Long,    // pessimistic (more spend)
    val highBalance: Long,   // optimistic (less spend)
)

data class ForecastResult(
    val currentBalance: Long,
    val projectedEndBalanceExpected: Long,
    val projectedEndBalanceLow: Long,         // pessimistic
    val projectedEndBalanceHigh: Long,        // optimistic
    val projectedVariableSpend: Long,
    val scheduledOutflowTotal: Long,
    val scheduledInflowTotal: Long,
    val dailySafeToSpend: Long,
    val totalSafeToSpend: Long,
    val runLowDate: LocalDate?,
    val mtdWeightUsed: Double,
    val dailyRateExpected: Long,
    val upcomingObligations: List<ScheduledEvent>,
    val projection: List<ProjectionPoint>,
    val confidence: Double,                   // 0..1, narrows as the month progresses
)
