package com.aimoneytracker.data.parser

import com.aimoneytracker.domain.model.PaymentMethod
import com.aimoneytracker.domain.model.ProcessingFlag
import com.aimoneytracker.domain.model.TransactionType

/**
 * The structured result of parsing a single SMS / notification. Fields that could not be extracted
 * are null. [confidence] (0..1) reflects how cleanly the required fields matched; low-confidence
 * results land in the "needs review" queue (§3, §7).
 */
data class ParsedTransaction(
    val isFinancial: Boolean,
    val amount: Long? = null,                 // minor units (paise)
    val type: TransactionType? = null,
    val currency: String = "INR",
    val merchantRaw: String? = null,
    val payeeHandle: String? = null,          // UPI VPA, e.g. "rahul@okaxis"
    val accountMasked: String? = null,        // e.g. "XX1234"
    val paymentMethod: PaymentMethod = PaymentMethod.UNKNOWN,
    val availableBalance: Long? = null,
    val dateTime: Long = 0L,
    val refNumber: String? = null,
    val bankName: String? = null,
    val flag: ProcessingFlag = ProcessingFlag.NONE,
    val confidence: Double = 0.0,
    val raw: String = "",
    val sender: String? = null,
) {
    companion object {
        fun notFinancial(raw: String, sender: String?) =
            ParsedTransaction(isFinancial = false, raw = raw, sender = sender, confidence = 0.0)
    }
}
