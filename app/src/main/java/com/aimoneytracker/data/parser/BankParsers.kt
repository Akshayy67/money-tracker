package com.aimoneytracker.data.parser

/**
 * The shipped bank/UPI parser instances (§3). Each is a one-object rule set — add a bank by adding
 * one entry to [allBankParsers]. The generic [RegexBankParser] handles extraction; here we only
 * declare each format's identifying sender tokens and body markers.
 *
 * Covered: SBI, HDFC, ICICI, Axis, Kotak, PNB + generic UPI/GPay/PhonePe/Paytm. The trailing
 * [GenericUpiParser] / [GenericBankParser] catch anything from a financial sender we didn't name.
 */

val SbiParser = RegexBankParser(
    bankName = "SBI",
    senderTokens = listOf("SBI", "SBIINB", "SBIUPI", "SBIBNK", "ATMSBI"),
    bodyMarkers = listOf("sbi", "state bank"),
)

val HdfcParser = RegexBankParser(
    bankName = "HDFC Bank",
    senderTokens = listOf("HDFC", "HDFCBK", "HDFCBANK"),
    bodyMarkers = listOf("hdfc"),
)

val IciciParser = RegexBankParser(
    bankName = "ICICI Bank",
    senderTokens = listOf("ICICI", "ICICIB", "ICICIT", "ICICIBANK"),
    bodyMarkers = listOf("icici"),
)

val AxisParser = RegexBankParser(
    bankName = "Axis Bank",
    senderTokens = listOf("AXIS", "AXISBK", "AXISBANK"),
    bodyMarkers = listOf("axis bank"),
)

val KotakParser = RegexBankParser(
    bankName = "Kotak",
    senderTokens = listOf("KOTAK", "KOTAKB", "KMBL"),
    bodyMarkers = listOf("kotak"),
)

val PnbParser = RegexBankParser(
    bankName = "PNB",
    senderTokens = listOf("PNB", "PNBSMS", "PNBBNK"),
    bodyMarkers = listOf("punjab national", "pnb"),
)

/** UPI wallets/apps that send their own transaction notifications. */
val GenericUpiParser = RegexBankParser(
    bankName = "UPI",
    senderTokens = listOf("PAYTM", "PYTM", "PHONEPE", "GPAY", "BHIM", "UPI", "NPCI", "CRED", "AMAZONP"),
    bodyMarkers = listOf("upi", "vpa", "@ok", "@ybl", "@paytm"),
)

/** Last-resort parser for any other financial-looking sender. */
val GenericBankParser = RegexBankParser(
    bankName = "Bank",
    senderTokens = listOf("BANK", "BNK"),
    bodyMarkers = listOf("debited", "credited", "a/c", "available bal", "avbl bal"),
)

/**
 * Registration order matters: specific banks are tried first, generic fallbacks last.
 * To add a bank, insert its parser above the generic ones.
 */
val allBankParsers: List<BankParser> = listOf(
    SbiParser,
    HdfcParser,
    IciciParser,
    AxisParser,
    KotakParser,
    PnbParser,
    GenericUpiParser,
    GenericBankParser,
)
