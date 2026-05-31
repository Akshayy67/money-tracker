package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stored forecast result, used for self-calibration: past forecasts are compared to actuals to
 * track forecast error and adjust the MTD-vs-history blend weights over time (§15.9).
 */
@Entity(tableName = "forecast_snapshots", indices = [Index("createdAt")])
data class ForecastSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val forecastForMonth: String,        // "yyyy-MM"
    val currentBalance: Long,
    val projectedEndBalanceExpected: Long,
    val projectedEndBalanceLow: Long,
    val projectedEndBalanceHigh: Long,
    val projectedSpendExpected: Long,
    val scheduledOutflows: Long,
    val scheduledInflows: Long,
    val dailySafeToSpend: Long,
    val runLowDate: Long? = null,
    val blendWeightMtd: Double,          // weight applied to MTD run-rate at time of forecast
    // Filled in later when the month closes, for calibration:
    val actualEndBalance: Long? = null,
    val actualSpend: Long? = null,
    val errorAbs: Long? = null,
)
