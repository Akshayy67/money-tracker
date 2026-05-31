package com.aimoneytracker.domain.forecast

import com.aimoneytracker.data.local.entity.ForecastSnapshotEntity
import javax.inject.Inject
import kotlin.math.abs

/**
 * Self-calibration (§15.9). Looks at past forecast snapshots whose month has since closed (so the
 * actuals are known) and derives gentle correction factors:
 *  - [ForecastCalibration.spendMultiplier]: if we systematically under/over-predicted spend.
 *  - [ForecastCalibration.mtdWeightBias]: nudges trust between MTD actuals and history.
 *
 * Corrections are deliberately conservative and clamped so a couple of noisy months can't swing the
 * model wildly.
 */
class ForecastCalibrator @Inject constructor() {

    fun calibrate(calibratedSnapshots: List<ForecastSnapshotEntity>): ForecastCalibration {
        val withActuals = calibratedSnapshots.filter { it.actualSpend != null }
        if (withActuals.size < 2) return ForecastCalibration.NEUTRAL

        // Average ratio of actual spend to predicted spend.
        val ratios = withActuals.mapNotNull { snap ->
            val predicted = snap.projectedSpendExpected
            val actual = snap.actualSpend ?: return@mapNotNull null
            if (predicted <= 0) null else actual.toDouble() / predicted.toDouble()
        }
        if (ratios.isEmpty()) return ForecastCalibration.NEUTRAL

        val meanRatio = ratios.average()
        // Damp toward 1.0 (only apply ~50% of the observed bias) and clamp to a safe band.
        val spendMultiplier = (1.0 + (meanRatio - 1.0) * 0.5).coerceIn(0.75, 1.35)

        // If errors are large, lean a touch more on history (negative bias) for stability.
        val meanAbsError = withActuals.mapNotNull { it.errorAbs }.map { abs(it) }.average()
        val mtdWeightBias = if (meanAbsError > 0) (-0.05).coerceAtLeast(-0.1) else 0.0

        return ForecastCalibration(spendMultiplier = spendMultiplier, mtdWeightBias = mtdWeightBias)
    }
}
