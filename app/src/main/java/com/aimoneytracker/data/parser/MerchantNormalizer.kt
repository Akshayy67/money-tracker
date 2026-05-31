package com.aimoneytracker.data.parser

/**
 * Merchant name normalization (§3). Collapses the many raw variants of the same merchant
 * (`SWIGGY*ORDER`, `SWIGGYLTD`, `swiggy@ybl`) into one clean display name ("Swiggy"), so
 * categorization and analytics stay consistent.
 *
 * Strategy: (1) strip payment-rail noise and suffixes, (2) look up a curated alias map of common
 * Indian merchants, (3) fall back to a title-cased cleaned token.
 */
object MerchantNormalizer {

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
        "bharatpetroleum" to "Bharat Petroleum",
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
        "upi", "razorpay", "rzp", "billdesk", "ccavenue", "payu", "pg",
    )

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown"
        val lower = raw.lowercase().trim()

        // 1) Curated alias map.
        ALIAS_MAP.firstOrNull { lower.contains(it.first) }?.let { return it.second }

        // 2) Strip a VPA's domain (name@bank -> name) and rail noise.
        var token = lower.substringBefore("@")
        // Replace common separators with spaces.
        token = token.replace(Regex("[*_/.\\-]+"), " ")
        token = token.replace(Regex("\\d{4,}"), " ") // drop long numeric ids

        val words = token.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it !in NOISE_SUFFIXES }
            .filter { it !in PAYMENT_RAILS }
            .filter { !it.all { c -> c.isDigit() } }

        if (words.isEmpty()) return raw.take(24).trim().ifBlank { "Unknown" }

        // 3) Title-case the cleaned token(s), keep it short.
        return words.take(3).joinToString(" ") { w ->
            w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /** A stable lowercase key used for the merchant map / rule lookups. */
    fun key(raw: String?): String = normalize(raw).lowercase().trim()
}
