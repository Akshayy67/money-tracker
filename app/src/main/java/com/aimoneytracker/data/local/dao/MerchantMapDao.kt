package com.aimoneytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aimoneytracker.data.local.entity.MerchantMapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantMapDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MerchantMapEntity)

    @Query("SELECT * FROM merchant_map WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): MerchantMapEntity?

    @Query("SELECT * FROM merchant_map")
    suspend fun getAll(): List<MerchantMapEntity>

    @Query("SELECT * FROM merchant_map ORDER BY hitCount DESC")
    fun observeAll(): Flow<List<MerchantMapEntity>>

    @Query("UPDATE merchant_map SET hitCount = hitCount + 1 WHERE `key` = :key")
    suspend fun incrementHit(key: String)

    @Query("DELETE FROM merchant_map WHERE `key` = :key")
    suspend fun delete(key: String)
}
