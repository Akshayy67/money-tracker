package com.aimoneytracker.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Money helpers. Amounts are stored everywhere as [Long] minor units (paise for INR).
 * All formatting and parsing of currency goes through here so the rest of the app never
 * deals with floating-point currency directly.
 */
object Money {

    private val inrFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    /** Format minor units (paise) as a localized currency string, e.g. 50000L -> "₹500.00". */
    fun format(minor: Long, withDecimals: Boolean = true): String {
        val major = minor / 100.0
        return if (withDecimals) {
            inrFormat.format(major)
        } else {
            val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            fmt.maximumFractionDigits = 0
            fmt.format(major)
        }
    }

    /** Compact format for charts/cards, e.g. 1234500L -> "₹12.3K". */
    fun formatCompact(minor: Long): String {
        val major = abs(minor) / 100.0
        val sign = if (minor < 0) "-" else ""
        return when {
            major >= 1_00_00_000 -> "$sign₹%.2fCr".format(major / 1_00_00_000)
            major >= 1_00_000 -> "$sign₹%.2fL".format(major / 1_00_000)
            major >= 1_000 -> "$sign₹%.1fK".format(major / 1_000)
            else -> "$sign₹%.0f".format(major)
        }
    }

    /** Parse a user-entered rupee string ("500", "500.50", "₹1,200") into minor units. */
    fun parseToMinor(input: String): Long? {
        val cleaned = input.replace("[^0-9.\\-]".toRegex(), "")
        val value = cleaned.toDoubleOrNull() ?: return null
        return (value * 100).roundToLong()
    }

    /** Convert a Double rupee amount (e.g. from a parser) into minor units. */
    fun majorToMinor(major: Double): Long = (major * 100).roundToLong()

    fun minorToMajor(minor: Long): Double = minor / 100.0
}
