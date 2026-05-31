package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.DigestRecordDao
import com.aimoneytracker.data.local.dao.ForecastSnapshotDao
import com.aimoneytracker.data.local.dao.GroupDao
import com.aimoneytracker.data.local.dao.IncomeSourceDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.DigestRecordEntity
import com.aimoneytracker.data.local.entity.ForecastSnapshotEntity
import com.aimoneytracker.data.local.entity.GroupEntity
import com.aimoneytracker.data.local.entity.IncomeSourceEntity
import com.aimoneytracker.domain.model.IncomeKind
import com.aimoneytracker.domain.process.RecurringDetector
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(private val groupDao: GroupDao) {
    fun observeAll(): Flow<List<GroupEntity>> = groupDao.observeAll()
    suspend fun getById(id: Long): GroupEntity? = groupDao.getById(id)
    suspend fun add(group: GroupEntity): Long = groupDao.insert(group.copy(createdAt = DateUtil.now()))
    suspend fun update(group: GroupEntity) = groupDao.update(group)
    suspend fun delete(group: GroupEntity) = groupDao.delete(group)
}

@Singleton
class IncomeRepository @Inject constructor(
    private val incomeSourceDao: IncomeSourceDao,
    private val txnDao: TransactionDao,
    private val recurringDetector: RecurringDetector,
) {
    fun observeActive(): Flow<List<IncomeSourceEntity>> = incomeSourceDao.observeActive()
    suspend fun getActive(): List<IncomeSourceEntity> = incomeSourceDao.getActive()
    suspend fun add(source: IncomeSourceEntity): Long = incomeSourceDao.insert(source.copy(createdAt = DateUtil.now()))
    suspend fun update(source: IncomeSourceEntity) = incomeSourceDao.update(source)
    suspend fun delete(source: IncomeSourceEntity) = incomeSourceDao.delete(source)

    /** Detect the recurring salary credit (date + amount) for forecasting (§11). */
    suspend fun detectRecurringIncome(): Int {
        val history = txnDao.getInRangeIncludingTransfers(DateUtil.monthsAgo(8), DateUtil.now())
        val detected = recurringDetector.detect(history).filter { it.isIncome }
        var added = 0
        for (d in detected) {
            if (incomeSourceDao.findByMerchant(d.merchantKey) != null) continue
            val kind = if (d.merchantName.lowercase().let { "salary" in it || "payroll" in it })
                IncomeKind.SALARY else IncomeKind.OTHER
            incomeSourceDao.insert(
                IncomeSourceEntity(
                    name = d.merchantName, kind = kind, typicalAmount = d.typicalAmount,
                    typicalDayOfMonth = d.dayOfCycle, merchantKey = d.merchantKey,
                    lastReceivedDate = d.lastDate, lastReceivedAmount = d.typicalAmount,
                    recurring = true, active = true, createdAt = DateUtil.now(),
                )
            )
            added++
        }
        return added
    }
}

@Singleton
class ForecastSnapshotRepository @Inject constructor(
    private val dao: ForecastSnapshotDao,
) {
    fun observeAll(): Flow<List<ForecastSnapshotEntity>> = dao.observeAll()
    suspend fun latest(): ForecastSnapshotEntity? = dao.latest()
    suspend fun save(snapshot: ForecastSnapshotEntity): Long = dao.insert(snapshot)
    suspend fun update(snapshot: ForecastSnapshotEntity) = dao.update(snapshot)
    suspend fun recentCalibrated(limit: Int = 12): List<ForecastSnapshotEntity> = dao.recentCalibrated(limit)
    suspend fun openForMonth(month: String): List<ForecastSnapshotEntity> = dao.openForMonth(month)
}

@Singleton
class DigestRepository @Inject constructor(
    private val dao: DigestRecordDao,
) {
    fun observeAll(): Flow<List<DigestRecordEntity>> = dao.observeAll()
    suspend fun getById(id: Long): DigestRecordEntity? = dao.getById(id)
    suspend fun save(record: DigestRecordEntity): Long = dao.insert(record)
    suspend fun markOpened(id: Long) = dao.markOpened(id)
}
