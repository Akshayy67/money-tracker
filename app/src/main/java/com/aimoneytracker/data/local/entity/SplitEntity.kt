package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.SplitStatus
import com.aimoneytracker.domain.model.SplitType

@Entity(tableName = "splits")
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val totalAmount: Long,               // minor units
    val type: SplitType = SplitType.EQUAL,
    val payerPersonId: Long? = null,     // null = the app user paid
    val groupId: Long? = null,
    val transactionId: Long? = null,     // source transaction, if any
    val date: Long,
    val status: SplitStatus = SplitStatus.OUTSTANDING,
    val createdAt: Long = 0,
)

@Entity(
    tableName = "split_participants",
    indices = [Index("splitId"), Index("personId")]
)
data class SplitParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val personId: Long? = null,          // null = the app user
    val shareAmount: Long,               // owed by this participant, minor units
    val weight: Double? = null,          // for WEIGHTED
    val percentage: Double? = null,      // for PERCENTAGE
    val paidAmount: Long = 0,
    val settled: Boolean = false,
)
