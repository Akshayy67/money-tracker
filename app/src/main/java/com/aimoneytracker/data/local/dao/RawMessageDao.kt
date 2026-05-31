package com.aimoneytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aimoneytracker.data.local.entity.RawMessageEntity

@Dao
interface RawMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: RawMessageEntity): Long

    @Query("SELECT * FROM raw_messages ORDER BY receivedAt DESC")
    suspend fun getAll(): List<RawMessageEntity>

    @Query("SELECT * FROM raw_messages WHERE parsed = 0 ORDER BY receivedAt ASC")
    suspend fun getUnparsed(): List<RawMessageEntity>

    @Query("SELECT * FROM raw_messages WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): RawMessageEntity?

    @Query("UPDATE raw_messages SET parsed = :parsed, producedTransactionId = :txnId WHERE id = :id")
    suspend fun markParsed(id: Long, parsed: Boolean, txnId: Long?)

    @Query("SELECT COUNT(*) FROM raw_messages")
    suspend fun count(): Int
}
