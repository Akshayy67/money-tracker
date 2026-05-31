package com.aimoneytracker.data.parser

/**
 * Noise filter (§3). Only messages that (a) come from a known financial sender ID pattern AND
 * (b) contain both an amount and a transaction keyword are treated as candidate transactions.
 * OTP-only and promotional SMS are rejected here before they ever reach a parser.
 */
object SenderFilter {

    // Indian alphanumeric sender IDs look like "VK-HDFCBK", "AD-SBIINB", "JD-ICICIT", "VM-PAYTM".
    // We match on the bank/UPI token after the optional 2-char prefix.
    private val FINANCIAL_SENDER_TOKENS = listOf(
        "HDFC", "SBI", "SBIINB", "SBIUPI", "ICICI", "ICICIB", "AXIS", "AXISBK", "KOTAK", "PNB",
        "BOB", "CANBNK", "UNION", "IDBI", "YESBNK", "INDUS", "FEDERAL", "RBL", "IDFC", "AUBANK",
        "PAYTM", "PHONEPE", "GPAY", "BHIM", "UPI", "AMAZONP", "MOBIKWIK", "FREECHARGE", "CRED",
        "SLICE", "JUPITER", "FAMPAY", "NPCI", "BANK", "PYTM", "CITI", "HSBC", "SCB", "DBS",
    )

    private val TRANSACTION_KEYWORDS = listOf(
        "debited", "credited", "spent", "received", "withdrawn", "deposited", "paid", "payment",
        "txn", "transaction", "transferred", "purchase", "debit", "credit", "sent", "refund",
        "a/c", "acct", "upi", "imps", "neft", "rtgs", "balance", "bal",
    )

    private val PROMO_KEYWORDS = listOf(
        "offer", "cashback up to", "win ", "discount", "sale", "lowest price", "buy now",
        "limited time", "click here", "apply now", "loan offer", "pre-approved", "congratulations you",
        "download", "refer", "% off", "voucher",
    )

    private val OTP_KEYWORDS = listOf("otp", "one time password", "verification code", "do not share")

    fun isLikelyFinancialSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val s = sender.uppercase().replace("-", "").replace(" ", "")
        return FINANCIAL_SENDER_TOKENS.any { s.contains(it) }
    }

    /** True if the message body looks like a real transaction (has amount + keyword, not OTP/promo). */
    fun isTransactionMessage(sender: String?, body: String): Boolean {
        val lower = body.lowercase()

        // Reject pure OTP messages (these never carry a debit/credit verb).
        val hasOtp = OTP_KEYWORDS.any { lower.contains(it) }
        val hasTxnVerb = TRANSACTION_KEYWORDS.any { lower.contains(it) }
        if (hasOtp && !hasTxnVerb) return false

        // Reject promotional unless it also clearly reports a real txn.
        val promoHits = PROMO_KEYWORDS.count { lower.contains(it) }
        val hasAmount = FieldExtractors.extractAmount(body) != null
        if (promoHits >= 2 && !hasAmount) return false

        val financialSender = isLikelyFinancialSender(sender)
        // Accept if from a financial sender with an amount, OR strong keyword evidence with amount.
        return hasAmount && (financialSender || hasTxnVerb)
    }
}
