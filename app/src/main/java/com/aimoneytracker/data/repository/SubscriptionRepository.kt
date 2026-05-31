package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.SubscriptionDao
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.SubscriptionEntity
import com.aimoneytracker.domain.model.SubscriptionKind
import com.aimoneytracker.domain.process.RecurringDetector
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val txnDao: TransactionDao,
    private val recurringDetector: RecurringDetector,
) {
    fun observeActive(): Flow<List<SubscriptionEntity>> = subscriptionDao.observeActive()
    suspend fun getActive(): List<SubscriptionEntity> = subscriptionDao.getActive()
    suspend fun add(sub: SubscriptionEntity): Long = subscriptionDao.insert(sub.copy(createdAt = DateUtil.now()))
    suspend fun update(sub: SubscriptionEntity) = subscriptionDao.update(sub)
    suspend fun delete(sub: SubscriptionEntity) = subscriptionDao.delete(sub)
    suspend fun dueBetween(start: Long, end: Long): List<SubscriptionEntity> = subscriptionDao.dueBetween(start, end)

    /** Run recurring detection over the last ~8 months and upsert detected subscriptions/EMIs (§12). */
    suspend fun detectAndStore(): Int {
        val history = txnDao.getInRangeIncludingTransfers(DateUtil.monthsAgo(8), DateUtil.now())
        val detected = recurringDetector.detect(history).filter { !it.isIncome }
        var added = 0
        for (d in detected) {
            val existing = subscriptionDao.findByMerchant(d.merchantKey)
            val kind = guessKind(d.merchantName)
            if (existing == null) {
                subscriptionDao.insert(
                    SubscriptionEntity(
                        name = d.merchantName,
                        merchantKey = d.merchantKey,
                        kind = kind,
                        amount = d.typicalAmount,
                        cycle = d.cycle,
                        nextDueDate = d.nextDueDate,
                        dayOfCycle = d.dayOfCycle,
                        lastChargedDate = d.lastDate,
                        lastChargedAmount = d.typicalAmount,
                        detected = true,
                        createdAt = DateUtil.now(),
                    )
                )
                added++
            } else {
                subscriptionDao.update(existing.copy(
                    amount = d.typicalAmount, cycle = d.cycle, nextDueDate = d.nextDueDate,
                    lastChargedDate = d.lastDate, lastChargedAmount = d.typicalAmount,
                ))
            }
        }
        return added
    }

    private fun guessKind(name: String): SubscriptionKind {
        val l = name.lowercase()
        return when {
            "emi" in l || "loan" in l || "finance" in l -> SubscriptionKind.EMI
            "rent" in l -> SubscriptionKind.RENT
            "insurance" in l || "lic" in l || "premium" in l -> SubscriptionKind.INSURANCE
            "electricity" in l || "water" in l || "gas" in l || "broadband" in l -> SubscriptionKind.UTILITY
            else -> SubscriptionKind.SUBSCRIPTION
        }
    }
}
