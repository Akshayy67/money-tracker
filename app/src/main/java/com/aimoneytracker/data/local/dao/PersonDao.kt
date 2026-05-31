package com.aimoneytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aimoneytracker.data.local.entity.PersonEntity
import com.aimoneytracker.data.local.entity.PersonHandleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("SELECT * FROM people ORDER BY name")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: Long): PersonEntity?

    @Query("SELECT * FROM people WHERE id = :id")
    fun observeById(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): PersonEntity?

    @Query("SELECT * FROM people")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE relationship = :rel ORDER BY name")
    fun observeByRelationship(rel: String): Flow<List<PersonEntity>>

    // ---- Handles ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandle(handle: PersonHandleEntity): Long

    @Update
    suspend fun updateHandle(handle: PersonHandleEntity)

    @Query("SELECT * FROM person_handles WHERE handle = :handle LIMIT 1")
    suspend fun findHandle(handle: String): PersonHandleEntity?

    @Query("SELECT * FROM person_handles WHERE personId = :personId")
    suspend fun handlesFor(personId: Long): List<PersonHandleEntity>

    @Query("SELECT * FROM person_handles")
    suspend fun allHandles(): List<PersonHandleEntity>

    @Query("UPDATE person_handles SET personId = :targetId WHERE personId = :sourceId")
    suspend fun reassignHandles(sourceId: Long, targetId: Long)
}
