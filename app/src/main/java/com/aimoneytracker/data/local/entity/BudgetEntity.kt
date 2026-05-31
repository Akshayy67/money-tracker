package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.BudgetPeriod
import com.aimoneytracker.domain.model.BudgetScope

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,                    // minor units, the limit
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val scope: BudgetScope = BudgetScope.OVERALL,
    val categoryKey: String? = null,     // when scope == CATEGORY
    val personId: Long? = null,          // when scope == PERSON
    val customStart: Long? = null,       // when period == CUSTOM
    val customEnd: Long? = null,
    val alertThresholdPct: Int = 80,     // notify when spend crosses this % of limit
    val isActive: Boolean = true,
    val createdAt: Long = 0,
)
