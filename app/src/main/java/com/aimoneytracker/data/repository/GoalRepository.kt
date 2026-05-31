package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.GoalDao
import com.aimoneytracker.data.local.entity.GoalContributionEntity
import com.aimoneytracker.data.local.entity.GoalEntity
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao,
) {
    fun observeAll(): Flow<List<GoalEntity>> = goalDao.observeAll()
    suspend fun getAll(): List<GoalEntity> = goalDao.getAll()
    suspend fun add(goal: GoalEntity): Long = goalDao.insert(goal.copy(createdAt = DateUtil.now()))
    suspend fun update(goal: GoalEntity) = goalDao.update(goal)
    suspend fun delete(goal: GoalEntity) = goalDao.delete(goal)
    fun observeContributions(goalId: Long): Flow<List<GoalContributionEntity>> =
        goalDao.observeContributions(goalId)

    suspend fun contribute(goalId: Long, amount: Long, note: String? = null) {
        val goal = goalDao.getById(goalId) ?: return
        goalDao.addContribution(GoalContributionEntity(goalId = goalId, amount = amount, date = DateUtil.now(), note = note))
        val newSaved = goal.savedAmount + amount
        goalDao.update(goal.copy(savedAmount = newSaved, isAchieved = newSaved >= goal.targetAmount))
    }

    /** Sum of monthly contributions needed across all active goals — feeds forecast goal reserve. */
    suspend fun monthlyGoalReserve(): Long =
        goalDao.getAll().filter { !it.isAchieved }.sumOf { it.monthlyContribution ?: 0L }
}
