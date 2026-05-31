package com.aimoneytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aimoneytracker.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE active = 1 ORDER BY nextDueDate")
    fun observeActive(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE active = 1")
    suspend fun getActive(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE active = 1 AND nextDueDate BETWEEN :start AND :end ORDER BY nextDueDate")
    suspend fun dueBetween(start: Long, end: Long): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE merchantKey = :key LIMIT 1")
    suspend fun findByMerchant(key: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: Long): SubscriptionEntity?
}
