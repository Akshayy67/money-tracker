package com.aimoneytracker.data.parser

import com.aimoneytracker.domain.model.ProcessingFlag
import com.aimoneytracker.domain.model.TransactionType

/**
 * A pluggable parser for one bank/UPI message format (§3). Adding a new bank = adding one
 * [RegexBankParser] instance (or a subclass) to [ParserRegistry] — nothing else changes.
 */
interface BankParser {
    val bankName: String

    /** Cheap check: does this parser claim the message based on sender / body markers? */
    fun claims(sender: String?, body: String): Boolean

    /** Full parse. Return null if this parser cannot extract a transaction from the message. */
    fun parse(sender: String?, body: String, receivedAt: Long): ParsedTransaction?
}

/**
 * Default regex-driven parser. Most Indian banks differ only in their sender IDs and minor wording,
 * so this base does the heavy extraction via [FieldExtractors]; subclasses or instances just declare
 * their sender tokens and optional body markers.
 *
 * @param bankName       Display name of the bank/wallet.
 * @param senderTokens   Uppercase substrings that identify this bank's sender IDs (e.g. "HDFCBK").
 * @param bodyMarkers    Optional lowercase substrings that strengthen a claim (e.g. "hdfc bank").
 */
open class RegexBankParser(
    override val bankName: String,
    private val senderTokens: List<String>,
    private val bodyMarkers: List<String> = emptyList(),
) : BankParser {

    override fun claims(sender: String?, body: String): Boolean {
        val s = sender?.uppercase()?.replace("-", "")?.replace(" ", "") ?: ""
        val senderHit = senderTokens.any { s.contains(it) }
        val bodyHit = bodyMarkers.any { body.lowercase().contains(it) }
        return senderHit || bodyHit
    }

    override fun parse(sender: String?, body: String, receivedAt: Long): ParsedTransaction? {
        if (!SenderFilter.isTransactionMessage(sender, body)) {
            return ParsedTransaction.notFinancial(body, sender)
        }

        val amount = FieldExtractors.extractAmount(body) ?: return ParsedTransaction.notFinancial(body, sender)
        val type = FieldExtractors.extractType(body) ?: TransactionType.DEBIT
        val flag = FieldExtractors.detectFlag(body)
        val merchantRaw = FieldExtractors.extractMerchantRaw(body)
        val vpa = FieldExtractors.extractVpa(body)
        val masked = FieldExtractors.extractMaskedAccount(body)
        val balance = FieldExtractors.extractBalance(body)
        val method = FieldExtractors.extractPaymentMethod(body)
        val ref = FieldExtractors.extractRef(body)
        val dateTime = FieldExtractors.extractDateTime(body, receivedAt)

        val confidence = scoreConfidence(
            hasAmount = true,
            hasType = FieldExtractors.extractType(body) != null,
            hasMerchant = merchantRaw != null || vpa != null,
            hasAccount = masked != null,
            hasBalance = balance != null,
            senderKnown = SenderFilter.isLikelyFinancialSender(sender),
        )

        return ParsedTransaction(
            isFinancial = true,
            amount = amount,
            type = type,
            currency = "INR",
            merchantRaw = merchantRaw ?: vpa,
            payeeHandle = vpa,
            accountMasked = masked,
            paymentMethod = method,
            availableBalance = balance,
            dateTime = dateTime,
            refNumber = ref,
            bankName = bankName,
            flag = flag,
            confidence = confidence,
            raw = body,
            sender = sender,
        )
    }

    /**
     * Confidence (0..1): a weighted sum of how many of the key fields matched cleanly.
     * Amount + a clear debit/credit verb dominate; merchant, account, balance and a known sender
     * add incremental confidence. This drives the "needs review" threshold (§7).
     */
    protected fun scoreConfidence(
        hasAmount: Boolean,
        hasType: Boolean,
        hasMerchant: Boolean,
        hasAccount: Boolean,
        hasBalance: Boolean,
        senderKnown: Boolean,
    ): Double {
        var score = 0.0
        if (hasAmount) score += 0.35
        if (hasType) score += 0.25
        if (hasMerchant) score += 0.20
        if (hasAccount) score += 0.08
        if (hasBalance) score += 0.07
        if (senderKnown) score += 0.05
        return score.coerceIn(0.0, 1.0)
    }
}
