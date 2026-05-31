package com.aimoneytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aimoneytracker.data.local.entity.SplitEntity
import com.aimoneytracker.data.local.entity.SplitParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplit(split: SplitEntity): Long

    @Update
    suspend fun updateSplit(split: SplitEntity)

    @Delete
    suspend fun deleteSplit(split: SplitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<SplitParticipantEntity>)

    @Update
    suspend fun updateParticipant(participant: SplitParticipantEntity)

    @Query("SELECT * FROM splits ORDER BY date DESC")
    fun observeAll(): Flow<List<SplitEntity>>

    @Query("SELECT * FROM splits WHERE id = :id")
    suspend fun getSplit(id: Long): SplitEntity?

    @Query("SELECT * FROM split_participants WHERE splitId = :splitId")
    suspend fun participantsOf(splitId: Long): List<SplitParticipantEntity>

    @Query("SELECT * FROM split_participants WHERE splitId = :splitId")
    fun observeParticipants(splitId: Long): Flow<List<SplitParticipantEntity>>

    @Query("SELECT * FROM split_participants WHERE personId = :personId AND settled = 0")
    suspend fun outstandingFor(personId: Long): List<SplitParticipantEntity>

    @Query("SELECT * FROM split_participants WHERE settled = 0")
    suspend fun allOutstanding(): List<SplitParticipantEntity>
}
