package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.DigestType

/** A sent digest, stored so the digest screen can show history (§16). */
@Entity(tableName = "digest_records", indices = [Index("createdAt")])
data class DigestRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: DigestType,
    val periodStart: Long,
    val periodEnd: Long,
    val createdAt: Long,
    val headline: String,
    val bodyJson: String,                // serialized DigestContent
    val naturalLanguageSummary: String? = null,
    val opened: Boolean = false,
)
