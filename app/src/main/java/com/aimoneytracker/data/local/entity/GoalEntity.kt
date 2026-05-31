package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.GoalType

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GoalType = GoalType.CUSTOM,
    val targetAmount: Long,              // minor units
    val savedAmount: Long = 0,
    val targetDate: Long? = null,
    val monthlyContribution: Long? = null,
    val linkedAccountId: Long? = null,
    val iconName: String? = null,
    val isAchieved: Boolean = false,
    val createdAt: Long = 0,
)

/** A single contribution toward a goal. */
@Entity(tableName = "goal_contributions")
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amount: Long,
    val date: Long,
    val note: String? = null,
)
