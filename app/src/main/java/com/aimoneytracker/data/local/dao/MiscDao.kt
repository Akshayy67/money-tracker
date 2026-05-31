package com.aimoneytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aimoneytracker.data.local.entity.AuditLogEntity
import com.aimoneytracker.data.local.entity.DigestRecordEntity
import com.aimoneytracker.data.local.entity.ForecastSnapshotEntity
import com.aimoneytracker.data.local.entity.GroupEntity
import com.aimoneytracker.data.local.entity.IncomeSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity): Long

    @Update suspend fun update(group: GroupEntity)
    @Delete suspend fun delete(group: GroupEntity)

    @Query("SELECT * FROM groups ORDER BY name")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?
}

@Dao
interface IncomeSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: IncomeSourceEntity): Long

    @Update suspend fun update(source: IncomeSourceEntity)
    @Delete suspend fun delete(source: IncomeSourceEntity)

    @Query("SELECT * FROM income_sources WHERE active = 1")
    fun observeActive(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_sources WHERE active = 1")
    suspend fun getActive(): List<IncomeSourceEntity>

    @Query("SELECT * FROM income_sources WHERE merchantKey = :key LIMIT 1")
    suspend fun findByMerchant(key: String): IncomeSourceEntity?
}

@Dao
interface ForecastSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ForecastSnapshotEntity): Long

    @Update suspend fun update(snapshot: ForecastSnapshotEntity)

    @Query("SELECT * FROM forecast_snapshots ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): ForecastSnapshotEntity?

    @Query("SELECT * FROM forecast_snapshots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ForecastSnapshotEntity>>

    @Query("SELECT * FROM forecast_snapshots WHERE actualEndBalance IS NOT NULL ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentCalibrated(limit: Int): List<ForecastSnapshotEntity>

    @Query("SELECT * FROM forecast_snapshots WHERE forecastForMonth = :month AND actualEndBalance IS NULL")
    suspend fun openForMonth(month: String): List<ForecastSnapshotEntity>
}

@Dao
interface DigestRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DigestRecordEntity): Long

    @Query("SELECT * FROM digest_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DigestRecordEntity>>

    @Query("SELECT * FROM digest_records WHERE id = :id")
    suspend fun getById(id: Long): DigestRecordEntity?

    @Query("UPDATE digest_records SET opened = 1 WHERE id = :id")
    suspend fun markOpened(id: Long)
}

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log WHERE entityType = :type AND entityId = :id ORDER BY timestamp DESC")
    fun observeFor(type: String, id: Long): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>
}
