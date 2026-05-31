package com.aimoneytracker.data.repository

import com.aimoneytracker.data.local.dao.SplitDao
import com.aimoneytracker.data.local.entity.SplitEntity
import com.aimoneytracker.data.local.entity.SplitParticipantEntity
import com.aimoneytracker.domain.model.SplitStatus
import com.aimoneytracker.domain.model.SplitType
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SplitRepository @Inject constructor(
    private val splitDao: SplitDao,
) {
    data class ParticipantShare(val personId: Long?, val weight: Double = 1.0, val percentage: Double? = null, val customAmount: Long? = null)

    fun observeAll(): Flow<List<SplitEntity>> = splitDao.observeAll()
    fun observeParticipants(splitId: Long): Flow<List<SplitParticipantEntity>> = splitDao.observeParticipants(splitId)
    suspend fun getSplit(id: Long): SplitEntity? = splitDao.getSplit(id)
    suspend fun participantsOf(id: Long): List<SplitParticipantEntity> = splitDao.participantsOf(id)

    /**
     * Create a split, computing each participant's share deterministically by [SplitType]
     * (equal / percentage / custom / weighted). Penny-rounding remainder goes to the first share.
     */
    suspend fun createSplit(
        description: String,
        totalAmount: Long,
        type: SplitType,
        participants: List<ParticipantShare>,
        payerPersonId: Long? = null,
        groupId: Long? = null,
        transactionId: Long? = null,
        date: Long = DateUtil.now(),
    ): Long {
        val splitId = splitDao.insertSplit(
            SplitEntity(description = description, totalAmount = totalAmount, type = type,
                payerPersonId = payerPersonId, groupId = groupId, transactionId = transactionId,
                date = date, status = SplitStatus.OUTSTANDING, createdAt = DateUtil.now())
        )
        val shares = computeShares(totalAmount, type, participants)
        splitDao.insertParticipants(
            participants.mapIndexed { i, p ->
                SplitParticipantEntity(
                    splitId = splitId, personId = p.personId, shareAmount = shares[i],
                    weight = p.weight, percentage = p.percentage,
                    paidAmount = if (p.personId == payerPersonId) shares[i] else 0,
                    settled = p.personId == payerPersonId,
                )
            }
        )
        return splitId
    }

    private fun computeShares(total: Long, type: SplitType, participants: List<ParticipantShare>): List<Long> {
        val n = participants.size
        if (n == 0) return emptyList()
        val shares = when (type) {
            SplitType.EQUAL -> LongArray(n) { total / n }.also { it[0] += total - it.sum() }.toList()
            SplitType.PERCENTAGE -> participants.map { ((it.percentage ?: (100.0 / n)) / 100.0 * total).toLong() }
                .toMutableList().also { it[0] += total - it.sum() }
            SplitType.CUSTOM -> participants.map { it.customAmount ?: 0L }
            SplitType.WEIGHTED -> {
                val totalWeight = participants.sumOf { it.weight }.takeIf { it > 0 } ?: 1.0
                participants.map { (it.weight / totalWeight * total).toLong() }
                    .toMutableList().also { it[0] += total - it.sum() }
            }
        }
        return shares
    }

    suspend fun markParticipantPaid(participant: SplitParticipantEntity, amount: Long) {
        val newPaid = participant.paidAmount + amount
        splitDao.updateParticipant(participant.copy(paidAmount = newPaid, settled = newPaid >= participant.shareAmount))
        recomputeStatus(participant.splitId)
    }

    private suspend fun recomputeStatus(splitId: Long) {
        val parts = splitDao.participantsOf(splitId)
        val split = splitDao.getSplit(splitId) ?: return
        val status = when {
            parts.all { it.settled } -> SplitStatus.SETTLED
            parts.any { it.paidAmount > 0 } -> SplitStatus.PARTIALLY_PAID
            else -> SplitStatus.OUTSTANDING
        }
        splitDao.updateSplit(split.copy(status = status))
    }

    /** Who owes whom across all splits (§9): net outstanding per person. */
    suspend fun outstandingByPerson(): Map<Long, Long> {
        val outstanding = splitDao.allOutstanding()
        return outstanding.filter { it.personId != null }
            .groupBy { it.personId!! }
            .mapValues { (_, list) -> list.sumOf { it.shareAmount - it.paidAmount } }
            .filterValues { it > 0 }
    }
}
