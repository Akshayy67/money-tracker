package com.aimoneytracker.domain.forecast

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Deterministic cash-flow forecasting engine (§15).
 *
 * IMPORTANT: every number here is computed in Kotlin. The LLM is only used elsewhere to *phrase*
 * this result — it never produces a figure.
 *
 * The forecast is layered for accuracy:
 *  1. Deterministic events — sum scheduled future outflows/inflows in the window (high confidence).
 *  2. Variable model — a per-day-of-week daily spend rate from trailing history, recency-weighted,
 *     with day-of-month seasonality, projected over the remaining days.
 *  3. Outlier handling — daily spend is winsorized (capped at a high percentile) so a single big
 *     purchase doesn't inflate the rate.
 *  4. MTD-vs-history blend — weight shifts toward month-to-date actuals as the month progresses.
 *  5. Projected end balance = current + scheduled inflows − scheduled outflows − projected variable.
 *  6. Confidence band — ±1 std dev of daily spend scaled over the remaining days.
 *  7. Safe-to-spend — what's left for discretionary per day after obligations, goals and a buffer.
 *  8. Run-low date — day-by-day simulation finds when the balance may dip below the threshold.
 *  9. Calibration — [ForecastCalibration] (learned from past errors) nudges the spend & blend.
 */
@Singleton
class ForecastEngine @Inject constructor() {

    // Recency weighting: a 30-day half-life. A sample 30 days old counts half as much as today's.
    private val recencyHalfLifeDays = 30.0
    private val recencyDecay = ln(2.0) / recencyHalfLifeDays

    fun forecast(input: ForecastInput): ForecastResult {
        val today = input.today
        val monthEnd = input.monthEnd
        val daysInMonth = ChronoUnit.DAYS.between(input.monthStart, monthEnd).toInt() + 1
        val daysElapsed = (ChronoUnit.DAYS.between(input.monthStart, today).toInt() + 1)
            .coerceIn(1, daysInMonth)
        // Remaining days, counting today as still spendable.
        val remainingDays = (daysInMonth - daysElapsed).coerceAtLeast(0)

        // ---- Layer 2/3: build the recency-weighted, winsorized daily-rate model ----
        val model = buildVariableModel(input.trailingDailySpend, today)

        // ---- Layer 4: MTD vs historical blend ----
        // weight toward actuals grows with how far into the month we are.
        val baseMtdWeight = daysElapsed.toDouble() / daysInMonth.toDouble()
        val mtdWeight = (baseMtdWeight + input.calibration.mtdWeightBias).coerceIn(0.0, 1.0)

        val mtdDailyRate = if (daysElapsed > 0) input.mtdDiscretionarySpend.toDouble() / daysElapsed else 0.0

        // Project remaining days from the historical model (seasonally adjusted per day).
        val remainingDates = (1..remainingDays).map { today.plusDays(it.toLong()) }
        val historicalProjectionByDay = remainingDates.map { model.expectedFor(it) }
        val historicalProjection = historicalProjectionByDay.sum()

        // Project remaining days from the MTD run-rate (seasonally shaped so totals stay comparable).
        val mtdProjectionByDay = remainingDates.map { date ->
            val seasonal = model.seasonalShape(date)
            mtdDailyRate * seasonal
        }
        val mtdProjection = mtdProjectionByDay.sum()

        val blendedByDay = remainingDates.indices.map { i ->
            (mtdWeight * mtdProjectionByDay[i] + (1 - mtdWeight) * historicalProjectionByDay[i]) *
                input.calibration.spendMultiplier
        }
        val projectedVariable = blendedByDay.sum().roundToLong().coerceAtLeast(0)

        // ---- Layer 1: scheduled events in (today, monthEnd] ----
        val futureEvents = (input.scheduledEvents + input.knownOneOffs)
            .filter { !it.dueDate.isBefore(today) && !it.dueDate.isAfter(monthEnd) }
        val scheduledOut = futureEvents.filter { !it.isInflow }.sumOf { it.amount }
        val scheduledIn = futureEvents.filter { it.isInflow }.sumOf { it.amount }
        val upcomingObligations = futureEvents.filter { !it.isInflow }.sortedBy { it.dueDate }

        // ---- Layer 5: projected end balance ----
        val projectedEnd = input.currentBalance + scheduledIn - scheduledOut - projectedVariable

        // ---- Layer 6: confidence band (±1 std of total variable spend) ----
        val dailyStd = model.dailyStdDev
        val totalVarStd = dailyStd * sqrt(remainingDays.toDouble())
        val band = (totalVarStd * input.calibration.spendMultiplier).roundToLong()
        // More spend => lower balance, so the pessimistic (low) end subtracts the band.
        val endLow = projectedEnd - band
        val endHigh = projectedEnd + band

        // ---- Layer 7: safe-to-spend ----
        val futureInflow = scheduledIn
        val balanceCeiling = input.currentBalance + futureInflow - scheduledOut -
            input.goalReserve - input.lowBalanceThreshold
        val balanceBasedSafe = max(0L, balanceCeiling)
        // If a budget exists, also bound by remaining budget minus committed + projected discretionary.
        val budgetBasedSafe = input.monthlyBudget?.let { budget ->
            max(0L, budget - input.mtdDiscretionarySpend - scheduledOut)
        }
        val totalSafe = listOfNotNull(balanceBasedSafe, budgetBasedSafe).min()
        val dailySafe = if (remainingDays > 0) totalSafe / remainingDays else totalSafe

        // ---- Layer 8: run-low date via day-by-day simulation (also builds the chart series) ----
        val (projection, runLowDate) = simulate(
            startBalance = input.currentBalance,
            today = today,
            remainingDates = remainingDates,
            variableByDay = blendedByDay,
            events = futureEvents,
            band = band,
            remainingDays = remainingDays,
            threshold = input.lowBalanceThreshold,
        )

        // Confidence: high when little spend is left to estimate (late month / mostly fixed),
        // lower when most of the month and its variance is still ahead.
        val variableShare = if (projectedEnd != 0L)
            projectedVariable.toDouble() / max(1.0, (projectedVariable + scheduledOut).toDouble()) else 0.5
        val progress = daysElapsed.toDouble() / daysInMonth
        val confidence = (0.45 + 0.4 * progress + 0.15 * (1 - variableShare)).coerceIn(0.0, 0.99)

        return ForecastResult(
            currentBalance = input.currentBalance,
            projectedEndBalanceExpected = projectedEnd,
            projectedEndBalanceLow = endLow,
            projectedEndBalanceHigh = endHigh,
            projectedVariableSpend = projectedVariable,
            scheduledOutflowTotal = scheduledOut,
            scheduledInflowTotal = scheduledIn,
            dailySafeToSpend = dailySafe,
            totalSafeToSpend = totalSafe,
            runLowDate = runLowDate,
            mtdWeightUsed = mtdWeight,
            dailyRateExpected = if (remainingDays > 0) (projectedVariable / remainingDays) else 0L,
            upcomingObligations = upcomingObligations,
            projection = projection,
            confidence = confidence,
        )
    }

    /** Day-by-day balance walk producing the projection series and the run-low date. */
    private fun simulate(
        startBalance: Long,
        today: LocalDate,
        remainingDates: List<LocalDate>,
        variableByDay: List<Double>,
        events: List<ScheduledEvent>,
        band: Long,
        remainingDays: Int,
        threshold: Long,
    ): Pair<List<ProjectionPoint>, LocalDate?> {
        val points = mutableListOf<ProjectionPoint>()
        var balance = startBalance
        var cumulativeStd = 0.0
        var runLow: LocalDate? = null

        // Anchor today's point at the starting balance.
        points += ProjectionPoint(today, balance, balance, balance)

        val perDayBand = if (remainingDays > 0) band.toDouble() / sqrt(remainingDays.toDouble()) else 0.0

        remainingDates.forEachIndexed { i, date ->
            val dayEvents = events.filter { it.dueDate == date }
            val inflow = dayEvents.filter { it.isInflow }.sumOf { it.amount }
            val outflow = dayEvents.filter { !it.isInflow }.sumOf { it.amount }
            val variable = variableByDay.getOrElse(i) { 0.0 }.roundToLong()

            balance += inflow - outflow - variable
            // The band grows with sqrt(days) as uncertainty accumulates.
            cumulativeStd = sqrt(cumulativeStd * cumulativeStd + perDayBand * perDayBand)
            val spread = cumulativeStd.roundToLong()

            if (runLow == null && balance < threshold) runLow = date
            points += ProjectionPoint(
                date = date,
                expectedBalance = balance,
                lowBalance = balance - spread,
                highBalance = balance + spread,
            )
        }
        return points to runLow
    }

    /** The learned variable-spend model: per-day-of-week rates + day-of-month shape + volatility. */
    private class VariableModel(
        val dowRate: Map<DayOfWeek, Double>,
        val overallRate: Double,
        val domBucketMultiplier: Map<Int, Double>, // bucket 0=days1-10,1=11-20,2=21-31
        val dailyStdDev: Double,
    ) {
        fun expectedFor(date: LocalDate): Double {
            val base = dowRate[date.dayOfWeek] ?: overallRate
            return base * (domBucketMultiplier[bucketOf(date)] ?: 1.0)
        }

        /** A unit-mean seasonal multiplier used to shape the flat MTD rate across remaining days. */
        fun seasonalShape(date: LocalDate): Double {
            if (overallRate <= 0.0) return 1.0
            return (expectedFor(date) / overallRate).coerceIn(0.3, 3.0)
        }

        companion object {
            fun bucketOf(date: LocalDate): Int = when (date.dayOfMonth) {
                in 1..10 -> 0
                in 11..20 -> 1
                else -> 2
            }
        }
    }

    private fun buildVariableModel(samples: List<DaySpend>, today: LocalDate): VariableModel {
        if (samples.isEmpty()) {
            return VariableModel(emptyMap(), 0.0, emptyMap(), 0.0)
        }

        // Outlier handling: winsorize daily amounts at the 95th percentile so a single huge day
        // doesn't dominate the rate.
        val sortedAmts = samples.map { it.amount }.sorted()
        val p95 = percentile(sortedAmts, 0.95).coerceAtLeast(1)
        val winsorized = samples.map { it.copy(amount = it.amount.coerceAtMost(p95)) }

        // Recency weight for each sample.
        fun weight(d: LocalDate): Double {
            val age = ChronoUnit.DAYS.between(d, today).toDouble().coerceAtLeast(0.0)
            return exp(-recencyDecay * age)
        }

        // Weighted overall mean daily spend.
        val totalW = winsorized.sumOf { weight(it.date) }
        val overall = if (totalW > 0) winsorized.sumOf { weight(it.date) * it.amount } / totalW else 0.0

        // Per day-of-week weighted mean (fallback to overall when sparse).
        val dowRate = DayOfWeek.values().associateWith { dow ->
            val rows = winsorized.filter { it.date.dayOfWeek == dow }
            val w = rows.sumOf { weight(it.date) }
            if (w > 0) rows.sumOf { weight(it.date) * it.amount } / w else overall
        }

        // Day-of-month bucket multipliers (start-of-month tends to be higher).
        val domBucket = (0..2).associateWith { bucket ->
            val rows = winsorized.filter { VariableModel.bucketOf(it.date) == bucket }
            val w = rows.sumOf { weight(it.date) }
            val mean = if (w > 0) rows.sumOf { weight(it.date) * it.amount } / w else overall
            if (overall > 0) (mean / overall).coerceIn(0.5, 2.0) else 1.0
        }

        // Weighted std-dev of daily spend for the confidence band.
        val variance = if (totalW > 0)
            winsorized.sumOf { weight(it.date) * (it.amount - overall) * (it.amount - overall) } / totalW
        else 0.0
        val std = sqrt(variance.coerceAtLeast(0.0))

        return VariableModel(dowRate, overall, domBucket, std)
    }

    private fun percentile(sortedAsc: List<Long>, p: Double): Long {
        if (sortedAsc.isEmpty()) return 0
        val idx = (p * (sortedAsc.size - 1)).roundToLong().toInt().coerceIn(0, sortedAsc.size - 1)
        return sortedAsc[idx]
    }
}
