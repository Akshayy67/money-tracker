package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.CategorizationSource
import com.aimoneytracker.domain.model.PaymentMethod
import com.aimoneytracker.domain.model.ProcessingFlag
import com.aimoneytracker.domain.model.TransactionSource
import com.aimoneytracker.domain.model.TransactionType

/**
 * The central record. One row per (possibly merged) financial event.
 *
 * Money fields are minor units (paise). [dateTime] is epoch millis (UTC). The original captured text
 * is preserved in [rawMessage] / linked [RawMessageEntity] so transactions can be reprocessed when
 * parser rules improve.
 *
 * Indices cover the hot filter paths: date (timeline), account, category, person.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("dateTime"),
        Index("accountId"),
        Index("category"),
        Index("relatedPersonId"),
        Index("type"),
        Index("isReviewed"),
        Index("dedupKey"),
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val amount: Long,                       // minor units, always positive; direction is [type]
    val type: TransactionType,
    val currency: String = "INR",

    val merchantNormalized: String,         // cleaned, e.g. "Swiggy"
    val merchantRaw: String,                // as seen in the message, e.g. "SWIGGY*ORDER"

    val category: String,                   // category key (see CategoryCatalog)
    val subcategory: String? = null,
    val categorizationSource: CategorizationSource = CategorizationSource.DEFAULT,
    val categoryConfidence: Double = 0.0,

    val dateTime: Long,                     // epoch millis
    val accountId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.UNKNOWN,

    val location: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val attachments: List<String> = emptyList(),   // file uris
    val receiptImage: String? = null,

    val relatedPersonId: Long? = null,
    val relatedSplitId: Long? = null,
    val relatedTransactionId: Long? = null,        // refund<->purchase, transfer pair, etc.

    val availableBalance: Long? = null,            // bank-reported balance if present in the message

    val confidence: Double = 1.0,                  // parse confidence 0..1
    val processingFlag: ProcessingFlag = ProcessingFlag.NONE,

    val rawMessage: String? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
    val senderId: String? = null,                  // e.g. "VK-HDFCBK"
    val dedupKey: String? = null,                  // amount|payee|window bucket for duplicate detection

    val isReimbursable: Boolean = false,
    val isReviewed: Boolean = true,
    val isIgnored: Boolean = false,
    val isArchived: Boolean = false,

    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
