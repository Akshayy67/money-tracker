package com.aimoneytracker.data.parser

import com.aimoneytracker.domain.model.PaymentMethod
import com.aimoneytracker.domain.model.ProcessingFlag
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared, bank-agnostic field extraction. Indian bank/UPI SMS share a remarkably consistent
 * vocabulary ("debited", "credited", "A/c", "UPI", "Avl Bal"), so a strong generic extractor handles
 * the bulk; individual [BankParser]s reuse these helpers and only override what differs.
 *
 * Every regex is intentionally tolerant: extra whitespace, optional currency symbols, and Indian
 * digit grouping (1,23,456.78) are all accepted.
 */
object FieldExtractors {

    // A money number: a run of digits and Indian-style commas, with an optional decimal part.
    // [0-9][0-9,]* captures the WHOLE number ("1775", "1,775", "12,34,567") — the old
    // [0-9]{1,3}(?:,[0-9]{2,3})* matched only the first 3 digits of un-grouped amounts (1775 -> 177).
    private const val NUM = """[0-9][0-9,]*(?:\.[0-9]{1,2})?"""

    // ₹ / Rs / Rs. / INR  followed by an amount.
    private val AMOUNT_REGEX = Regex("""(?:rs\.?|inr|₹)\s*($NUM)""", RegexOption.IGNORE_CASE)

    // Amount that appears *before* the currency word, e.g. "500 INR" or "1775.00 INR".
    private val AMOUNT_SUFFIX_REGEX = Regex("""($NUM)\s*(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)

    private val BALANCE_REGEX = Regex(
        """(?:avl|avbl|available|avlbl|a\/c|clear|total)?\s*(?:bal|balance)[:\s.]*\s*(?:rs\.?|inr|₹)?\s*($NUM)""",
        RegexOption.IGNORE_CASE
    )

    private val MASKED_ACCOUNT_REGEX = Regex(
        """(?:a\/c|acct|account|ac)\s*(?:no\.?|number|ending|x+)?\s*[:\-]?\s*(?:x+|\*+)?([0-9]{3,6})""",
        RegexOption.IGNORE_CASE
    )

    private val CARD_REGEX = Regex(
        """card\s*(?:no\.?|ending|x+)?\s*[:\-]?\s*(?:x+|\*+)?([0-9]{3,6})""",
        RegexOption.IGNORE_CASE
    )

    private val VPA_REGEX = Regex("""([a-zA-Z0-9._\-]{2,})@([a-zA-Z]{2,})""")

    private val REF_REGEX = Regex(
        """(?:ref(?:erence)?(?:\s*no\.?)?|txn\s*(?:id|no)?|upi\s*ref|rrn)\s*[:\-]?\s*([a-zA-Z0-9]{6,})""",
        RegexOption.IGNORE_CASE
    )

    // "to JOHN DOE", "at SWIGGY", "trf to ...", "VPA name", "to/from <merchant>"
    private val PAYEE_REGEX = Regex(
        """(?:\bto\b|\bat\b|\bfrom\b|\btrf\s*to\b|towards|\bpaid\s*to\b|\bvpa\b)\s+([A-Za-z0-9][A-Za-z0-9 &.@'\-_*]{1,40})""",
        RegexOption.IGNORE_CASE
    )

    private val DEBIT_WORDS = listOf(
        "debited", "debit", "spent", "withdrawn", "paid", "sent", "deducted", "purchase",
        "transferred to", "trf to", "dr ", " dr.", "payment of"
    )
    private val CREDIT_WORDS = listOf(
        "credited", "credit", "received", "deposited", "added", "refund", "reversed",
        "cr ", " cr.", "has been credited"
    )

    // Date patterns: 12-05-2024 / 12/05/24 / 12 May 2024 / 12-May-24
    private val DATE_NUMERIC = Regex("""\b(\d{1,2})[\-/](\d{1,2})[\-/](\d{2,4})\b""")
    private val DATE_TEXT = Regex(
        """\b(\d{1,2})[\-\s]([A-Za-z]{3})[\-\s'](\d{2,4})\b"""
    )
    private val TIME_REGEX = Regex("""\b(\d{1,2}):(\d{2})(?::\d{2})?\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)

    fun extractAmount(body: String): Long? {
        AMOUNT_REGEX.find(body)?.let { return toMinor(it.groupValues[1]) }
        AMOUNT_SUFFIX_REGEX.find(body)?.let { return toMinor(it.groupValues[1]) }
        return null
    }

    fun extractBalance(body: String): Long? {
        // Prefer a balance phrase that isn't the same token as the transaction amount.
        val match = BALANCE_REGEX.find(body) ?: return null
        return toMinor(match.groupValues[1])
    }

    fun extractType(body: String): TransactionType? {
        val lower = " ${body.lowercase()} "
        val credit = CREDIT_WORDS.any { lower.contains(it) }
        val debit = DEBIT_WORDS.any { lower.contains(it) }
        return when {
            credit && !debit -> TransactionType.CREDIT
            debit && !credit -> TransactionType.DEBIT
            // If both present (e.g. "debited ... credited to payee"), bias to the first occurring verb.
            credit && debit -> {
                val firstCredit = CREDIT_WORDS.minOf { idxOrMax(lower, it) }
                val firstDebit = DEBIT_WORDS.minOf { idxOrMax(lower, it) }
                if (firstDebit <= firstCredit) TransactionType.DEBIT else TransactionType.CREDIT
            }
            else -> null
        }
    }

    fun extractMaskedAccount(body: String): String? {
        CARD_REGEX.find(body)?.let { return "XX${it.groupValues[1]}" }
        MASKED_ACCOUNT_REGEX.find(body)?.let { return "XX${it.groupValues[1]}" }
        return null
    }

    fun extractVpa(body: String): String? = VPA_REGEX.find(body)?.value?.lowercase()

    fun extractRef(body: String): String? = REF_REGEX.find(body)?.groupValues?.get(1)

    fun extractPaymentMethod(body: String): PaymentMethod {
        val l = body.lowercase()
        return when {
            "upi" in l || VPA_REGEX.containsMatchIn(body) -> PaymentMethod.UPI
            "atm" in l || "withdrawn" in l && "card" in l -> PaymentMethod.ATM
            "imps" in l -> PaymentMethod.IMPS
            "neft" in l -> PaymentMethod.NEFT
            "rtgs" in l -> PaymentMethod.RTGS
            "card" in l || "pos" in l -> PaymentMethod.CARD
            "auto" in l && "debit" in l -> PaymentMethod.AUTO_DEBIT
            "net banking" in l || "netbanking" in l -> PaymentMethod.NETBANKING
            "cheque" in l || "chq" in l -> PaymentMethod.CHEQUE
            "cash" in l -> PaymentMethod.CASH
            else -> PaymentMethod.UNKNOWN
        }
    }

    // Words that follow "to/at/from" but are NOT a merchant (avoids "to your account" etc.).
    private val NON_MERCHANT_PAYEE = setOf(
        "your", "you", "a", "ac", "account", "the", "my", "self", "wallet", "bank", "card", "vpa",
    )

    /**
     * Extract the merchant/payee phrase. Collects both a "to/at/from <name>" capture and the VPA's
     * local-part, then prefers the cleaner one: a real alphabetic payee name beats a junky/numeric VPA
     * handle (e.g. for "Rs 500 to RAVI KUMAR via UPI 98765@ybl" we want "RAVI KUMAR", not "98765").
     * Returns the raw text; [MerchantNormalizer] cleans/validates it afterward.
     */
    fun extractMerchantRaw(body: String): String? {
        val payeeName = PAYEE_REGEX.find(body)?.let { m ->
            val candidate = m.groupValues[1].trim().trimEnd('.', ',', '-')
            // Trim trailing narration like "... on 12-05", "... Ref 123", "... via UPI".
            val cleaned = candidate
                .substringBefore(" on ").substringBefore(" Ref").substringBefore(" ref")
                .substringBefore(" via ").substringBefore(" UPI").substringBefore(" Avl").trim()
            cleaned.takeIf { it.length >= 2 && it.lowercase() !in NON_MERCHANT_PAYEE }
        }

        val vpaName = VPA_REGEX.find(body)?.let { m ->
            m.groupValues[1].takeIf { it.length in 2..40 && !it.all { c -> c.isDigit() } }
        }

        // A payee name with at least one alphabetic word is the most reliable signal.
        val payeeHasLetters = payeeName?.any { it.isLetter() } == true
        return when {
            payeeHasLetters -> payeeName
            vpaName != null -> vpaName
            payeeName != null -> payeeName
            else -> null
        }
    }

    /**
     * Extract the transaction date/time from the SMS body, falling back to [fallback] (the SMS
     * received timestamp) when the body has no usable date or the parsed date is implausible.
     *
     * Key correctness rules (this is the "wrong dates after scrape" fix):
     *  - The real day is used as-is (1..31), validated against the month — NOT clamped to 28, which
     *    previously turned the 29th/30th/31st into the 28th.
     *  - Indian bank SMS are DD-MM-YY, but we disambiguate: if the first number is >12 it's the day,
     *    if the second is >12 it's the day; otherwise assume DD-MM (Indian convention).
     *  - A parsed date is rejected (→ fallback) if it's invalid or in the future, so a garbled body
     *    can never override the reliable SMS timestamp with a nonsense date.
     */
    fun extractDateTime(body: String, fallback: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        var date: LocalDate? = null

        DATE_NUMERIC.find(body)?.let { m ->
            val a = m.groupValues[1].toInt()   // first number
            val b = m.groupValues[2].toInt()   // second number
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            // Disambiguate day vs month.
            val (day, month) = when {
                a > 12 && b <= 12 -> a to b          // DD-MM
                b > 12 && a <= 12 -> b to a          // MM-DD
                else -> a to b                        // ambiguous → Indian DD-MM
            }
            if (month in 1..12 && day in 1..daysInMonth(y, month)) {
                runCatching { date = LocalDate.of(y, month, day) }
            }
        }

        if (date == null) {
            DATE_TEXT.find(body)?.let { m ->
                val day = m.groupValues[1].toInt()
                val month = monthFromText(m.groupValues[2])
                var y = m.groupValues[3].toInt()
                if (y < 100) y += 2000
                if (month != null && day in 1..daysInMonth(y, month)) {
                    runCatching { date = LocalDate.of(y, month, day) }
                }
            }
        }

        // Reject a body date that is invalid or in the future — trust the SMS timestamp instead.
        val resolved = date
        if (resolved == null || resolved.isAfter(today)) return fallback

        var time = LocalTime.of(12, 0)
        TIME_REGEX.find(body)?.let {
            var h = it.groupValues[1].toIntOrNull() ?: 12
            val mi = it.groupValues[2].toIntOrNull() ?: 0
            val ampm = it.groupValues[3].lowercase()
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            if (h in 0..23 && mi in 0..59) runCatching { time = LocalTime.of(h, mi) }
        }
        return LocalDateTime.of(resolved, time).atZone(zone).toInstant().toEpochMilli()
    }

    private fun daysInMonth(year: Int, month: Int): Int =
        runCatching { java.time.YearMonth.of(year, month).lengthOfMonth() }.getOrDefault(31)

    /** Parse-time flags for special transaction states (§4). */
    fun detectFlag(body: String): ProcessingFlag {
        val l = body.lowercase()
        return when {
            "fail" in l || "declined" in l || "unsuccessful" in l || "could not be" in l -> ProcessingFlag.FAILED
            "refund" in l -> ProcessingFlag.REFUND
            "reversed" in l || "reversal" in l -> ProcessingFlag.REVERSAL
            "atm" in l && ("withdrawn" in l || "withdrawal" in l) -> ProcessingFlag.ATM_WITHDRAWAL
            "cash deposit" in l || "deposited at" in l -> ProcessingFlag.CASH_DEPOSIT
            else -> ProcessingFlag.NONE
        }
    }

    private fun toMinor(group: String): Long? {
        val normalized = group.replace(",", "")
        return Money.parseToMinor(normalized)
    }

    private fun idxOrMax(haystack: String, needle: String): Int {
        val i = haystack.indexOf(needle)
        return if (i < 0) Int.MAX_VALUE else i
    }

    private fun monthFromText(s: String): Int? {
        return runCatching {
            LocalDate.parse("01 ${s.lowercase().replaceFirstChar { it.uppercase() }} 2000",
                DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)).monthValue
        }.getOrNull()
    }
}
