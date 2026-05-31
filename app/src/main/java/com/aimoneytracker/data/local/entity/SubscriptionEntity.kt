package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.SubscriptionCycle
import com.aimoneytracker.domain.model.SubscriptionKind

/**
 * A detected or user-created recurring obligation (subscription, EMI, rent, utility, insurance).
 * Feeds the [com.aimoneytracker.domain.forecast.ForecastEngine] with known future outflows.
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val merchantKey: String? = null,
    val kind: SubscriptionKind = SubscriptionKind.SUBSCRIPTION,
    val amount: Long,                    // minor units (typical/expected charge)
    val cycle: SubscriptionCycle = SubscriptionCycle.MONTHLY,
    val customCycleDays: Int? = null,
    val nextDueDate: Long? = null,
    val dayOfCycle: Int? = null,         // e.g. day-of-month
    val categoryKey: String? = null,
    val accountId: Long? = null,
    val lastChargedDate: Long? = null,
    val lastChargedAmount: Long? = null,
    val detected: Boolean = false,       // auto-detected vs user-added
    val active: Boolean = true,
    val reminderDaysBefore: Int = 2,
    val createdAt: Long = 0,
)
