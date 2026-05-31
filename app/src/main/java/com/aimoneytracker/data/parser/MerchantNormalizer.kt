package com.aimoneytracker.data.parser

/**
 * Merchant name normalization (§3). Collapses the many raw variants of the same merchant
 * (`SWIGGY*ORDER`, `SWIGGYLTD`, `swiggy@ybl`) into one clean display name ("Swiggy"), so
 * categorization and analytics stay consistent.
 *
 * Strategy: (1) if the raw value is a junk UPI/QR handle (e.g. `paytmqr281...@paytm`,
 * `9876543210@ybl`), don't invent a fake name — return "UPI Payment" so it lands in the §7 review
 * queue instead of polluting analytics; (2) curated alias map of common Indian merchants;
 * (3) strip payment-rail noise/suffixes and title-case the cleaned token.
 */
object MerchantNormalizer {

    /** Display name used when we genuinely can't derive a real merchant from the text. */
    const val UNRESOLVED = "UPI Payment"

    // Substrings (lowercased) -> canonical display name. First match wins.
    private val ALIAS_MAP: List<Pair<String, String>> = listOf(
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "zepto" to "Zepto",
        "blinkit" to "Blinkit",
        "grofers" to "Blinkit",
        "bigbasket" to "BigBasket",
        "dunzo" to "Dunzo",
        "amazon" to "Amazon",
        "amzn" to "Amazon",
        "flipkart" to "Flipkart",
        "myntra" to "Myntra",
        "ajio" to "Ajio",
        "uber" to "Uber",
        "ola" to "Ola",
        "rapido" to "Rapido",
        "irctc" to "IRCTC",
        "makemytrip" to "MakeMyTrip",
        "goibibo" to "Goibibo",
        "redbus" to "RedBus",
        "netflix" to "Netflix",
        "spotify" to "Spotify",
        "hotstar" to "Disney+ Hotstar",
        "disney" to "Disney+ Hotstar",
        "primevideo" to "Amazon Prime",
        "youtube" to "YouTube",
        "swiggyinstamart" to "Swiggy Instamart",
        "jiomart" to "JioMart",
        "jio" to "Jio",
        "airtel" to "Airtel",
        "vodafone" to "Vi",
        "vi " to "Vi",
        "bsnl" to "BSNL",
        "dmart" to "DMart",
        "reliance" to "Reliance",
        "more " to "More Retail",
        "starbucks" to "Starbucks",
        "mcdonald" to "McDonald's",
        "kfc" to "KFC",
        "dominos" to "Domino's",
        "pizzahut" to "Pizza Hut",
        "decathlon" to "Decathlon",
        "croma" to "Croma",
        "apollo" to "Apollo Pharmacy",
        "pharmeasy" to "PharmEasy",
        "1mg" to "Tata 1mg",
        "indianoil" to "Indian Oil",
        "ioc" to "Indian Oil",
        "bharatpetroleum" to "Bharat Petroleum",
        "bpcl" to "Bharat Petroleum",
        "hpcl" to "HP Petrol",
        "google" to "Google",
        "googleplay" to "Google Play",
        "phonepe" to "PhonePe",
        "paytm" to "Paytm",
        "groww" to "Groww",
        "zerodha" to "Zerodha",
        "upstox" to "Upstox",
        "lic" to "LIC",
    )

    // Noise tokens to strip from raw merchant strings.
    private val NOISE_SUFFIXES = listOf(
        "ltd", "limited", "pvt", "private", "llp", "inc", "order", "payment", "payments",
        "india", "in", "online", "store", "retail", "services", "tech", "technologies",
    )

    private val PAYMENT_RAILS = listOf(
        "ybl", "okaxis", "okhdfcbank", "oksbi", "okicici", "paytm", "apl", "ibl", "axl", "hdfcbank",
        "upi", "razorpay", "rzp", "billdesk", "ccavenue", "payu", "pg", "ptys", "ptsbi", "yapl",
        "waaxis", "wahdfcbank", "wasbi", "jupiteraxis", "fbl", "icici", "axisb", "kbl",
    )

    // PSP/aggregator/QR prefixes whose local-part is a machine ID, never a real merchant name.
    private val JUNK_HANDLE_PREFIXES = listOf(
        "paytmqr", "bharatpe", "merchant", "mab", "q", "razorpay.", "rzp.", "rzpy", "yespay",
        "okbizaxis", "gpay-", "bpalmtm", "fkrt",
    )

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return UNRESOLVED
        val lower = raw.lowercase().trim()

        // 1) Curated alias map (matches even inside a noisy string, e.g. "swiggy*order").
        ALIAS_MAP.firstOrNull { lower.contains(it.first) }?.let { return it.second }

        // 2) If it's a UPI handle, judge its local-part. Junk machine handles -> UNRESOLVED.
        val localPart = lower.substringBefore("@")
        if (looksLikeJunkHandle(localPart)) return UNRESOLVED

        // 3) Strip rail noise and clean.
        var token = localPart.replace(Regex("[*_/.\\-]+"), " ")
        token = token.replace(Regex("\\d{4,}"), " ") // drop long numeric ids

        val words = token.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it !in NOISE_SUFFIXES }
            .filter { it !in PAYMENT_RAILS }
            .filter { !it.all { c -> c.isDigit() } }
            .filter { it.length >= 2 }                 // drop 1-char fragments

        if (words.isEmpty()) return UNRESOLVED

        // A single long alphanumeric blob (e.g. "qcwf6kg7e9x") is a machine id, not a name.
        if (words.size == 1 && isAlphanumericBlob(words[0])) return UNRESOLVED

        // 4) Title-case the cleaned token(s), keep it short.
        return words.take(3).joinToString(" ") { w ->
            w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /** A stable lowercase key used for the merchant map / rule lookups. */
    fun key(raw: String?): String = normalize(raw).lowercase().trim()

    /**
     * True when the value is clearly a real, nameable merchant (so the processor can auto-trust it).
     * False for [UNRESOLVED], which should go to the review queue.
     */
    fun isResolved(name: String): Boolean = name != UNRESOLVED

    private fun looksLikeJunkHandle(localPart: String): Boolean {
        if (localPart.isBlank()) return true
        // Pure phone-number / numeric handle, e.g. "9876543210@ybl".
        if (localPart.all { it.isDigit() }) return true
        if (JUNK_HANDLE_PREFIXES.any { localPart.startsWith(it) }) return true
        // Mostly digits with some letters (typical QR/merchant IDs): >=6 digits and digit-heavy.
        val digits = localPart.count { it.isDigit() }
        if (digits >= 6 && digits >= localPart.length / 2) return true
        return false
    }

    private fun isAlphanumericBlob(word: String): Boolean {
        if (word.length < 10) return false
        val hasLetter = word.any { it.isLetter() }
        val hasDigit = word.any { it.isDigit() }
        return hasLetter && hasDigit
    }
}
