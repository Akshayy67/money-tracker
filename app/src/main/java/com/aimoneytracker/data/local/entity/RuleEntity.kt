package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user/learned categorization rule. Matching is performed against a transaction's merchant,
 * raw message, or UPI handle. Higher [priority] and [strength] win. Created automatically from
 * user corrections (§6 learning) or written by hand in the rules editor
 * (e.g. "landlord@oksbi -> Rent").
 */
@Entity(tableName = "rules", indices = [Index("matchValue")])
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchType: String,           // CONTAINS | EQUALS | REGEX | HANDLE
    val matchField: String,          // MERCHANT | RAW | HANDLE | NOTE
    val matchValue: String,
    val assignCategory: String? = null,
    val assignSubcategory: String? = null,
    val assignPersonId: Long? = null,
    val assignType: String? = null,  // optional force DEBIT/CREDIT/TRANSFER
    val markIgnore: Boolean = false,
    val priority: Int = 100,
    val strength: Int = 1,           // incremented each time the rule is reinforced by a correction
    val isUserCreated: Boolean = true,
    val createdAt: Long = 0,
)
