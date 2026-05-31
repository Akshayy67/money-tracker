package com.aimoneytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aimoneytracker.data.local.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)

    @Query("SELECT * FROM rules ORDER BY priority DESC, strength DESC")
    suspend fun getAll(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY priority DESC, strength DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE matchField = :field AND matchValue = :value LIMIT 1")
    suspend fun findExact(field: String, value: String): RuleEntity?
}
