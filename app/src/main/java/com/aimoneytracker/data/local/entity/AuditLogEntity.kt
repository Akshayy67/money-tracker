package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Edit/audit history of changes to transactions and other records (§21). */
@Entity(tableName = "audit_log", indices = [Index("entityType"), Index("entityId"), Index("timestamp")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,              // "transaction", "rule", ...
    val entityId: Long,
    val action: String,                  // CREATE | UPDATE | DELETE | MERGE | CATEGORIZE
    val field: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val timestamp: Long,
)
