package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.AccountType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val bankName: String? = null,
    val maskedNumber: String? = null,        // e.g. "XXXX1234"
    val openingBalance: Long = 0,            // minor units
    val currentBalanceCached: Long = 0,      // last computed balance (refreshed by repo)
    val reportedBalance: Long? = null,       // most recent bank-reported available balance (SMS)
    val reportedBalanceAt: Long? = null,
    val isTracked: Boolean = true,
    val isOwnAccount: Boolean = true,        // used by transfer detection
    val colorHex: String? = null,
    // Credit-card lifecycle
    val creditLimit: Long? = null,
    val statementDay: Int? = null,           // day of month statement generates
    val dueDay: Int? = null,                 // day of month payment is due
    val currentStatementDue: Long? = null,
    val minimumDue: Long? = null,
    val createdAt: Long = 0,
)
