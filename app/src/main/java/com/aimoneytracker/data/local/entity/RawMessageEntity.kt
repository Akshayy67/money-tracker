package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.TransactionSource

/**
 * Every captured source message is stored verbatim, whether or not it parsed into a transaction.
 * This enables reprocessing: when parser rules improve, [com.aimoneytracker.data.parser] re-runs
 * over all rows here.
 */
@Entity(
    tableName = "raw_messages",
    indices = [Index("receivedAt"), Index("contentHash", unique = true)]
)
data class RawMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String?,
    val body: String,
    val receivedAt: Long,
    val source: TransactionSource,
    val packageName: String? = null,        // for notification source
    val contentHash: String,                // dedupe identical captures
    val parsed: Boolean = false,
    val producedTransactionId: Long? = null,
)
