package com.aimoneytracker.domain.process

import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.domain.model.ProcessingFlag
import com.aimoneytracker.domain.model.TransactionType
import javax.inject.Inject

/**
 * Refund & reversal linking (§4). A refund/reversal credit is matched back to the original purchase
 * (same merchant, same-or-smaller amount, within a reasonable window), so analytics can net it out
 * and the UI can show "refund of <purchase>".
 */
class RefundDetector @Inject constructor() {

    private val windowMillis = 45L * 24 * 60 * 60 * 1000 // refunds can take weeks

    /** Returns the original purchase a refund credit belongs to, or null. */
    fun findOriginalPurchase(
        refund: TransactionEntity,
        priorDebits: List<TransactionEntity>,
    ): TransactionEntity? {
        if (refund.type != TransactionType.CREDIT) return null
        val isRefundish = refund.processingFlag == ProcessingFlag.REFUND ||
            refund.processingFlag == ProcessingFlag.REVERSAL ||
            (refund.rawMessage?.lowercase()?.let { "refund" in it || "reversed" in it } == true)
        if (!isRefundish) return null

        val merchant = refund.merchantNormalized.lowercase().trim()
        return priorDebits
            .filter { it.type == TransactionType.DEBIT }
            .filter { refund.dateTime - it.dateTime in 0..windowMillis }
            .filter { it.merchantNormalized.lowercase().trim() == merchant || merchant.isBlank() }
            .filter { it.amount >= refund.amount } // refund <= original
            .minByOrNull { kotlin.math.abs(it.amount - refund.amount) }
    }
}
