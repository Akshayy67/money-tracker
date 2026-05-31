package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.IncomeKind

/**
 * A recurring income source (most importantly, the detected salary credit). The salary's typical
 * day-of-month and amount feed the forecasting engine's deterministic inflow layer.
 */
@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: IncomeKind = IncomeKind.SALARY,
    val typicalAmount: Long,             // minor units
    val typicalDayOfMonth: Int? = null,
    val accountId: Long? = null,
    val merchantKey: String? = null,
    val lastReceivedDate: Long? = null,
    val lastReceivedAmount: Long? = null,
    val recurring: Boolean = true,
    val active: Boolean = true,
    val createdAt: Long = 0,
)
