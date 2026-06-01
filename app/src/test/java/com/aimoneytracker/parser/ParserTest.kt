package com.aimoneytracker.parser

import com.aimoneytracker.data.parser.ParserRegistry
import com.aimoneytracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    private val registry = ParserRegistry()
    private val now = 1_700_000_000_000L

    @Test
    fun extractsDayCorrectlyWithoutClampingTo28() {
        // Regression: the 31st must stay the 31st (old code clamped day to 28).
        val zone = java.time.ZoneId.systemDefault()
        val dt = com.aimoneytracker.data.parser.FieldExtractors
            .extractDateTime("Rs.500 debited on 31-01-2024 to SWIGGY", now)
        val ld = java.time.Instant.ofEpochMilli(dt).atZone(zone).toLocalDate()
        assertEquals(31, ld.dayOfMonth)
        assertEquals(1, ld.monthValue)
        assertEquals(2024, ld.year)

        // DD-MM disambiguation: 30-04 is 30 April, not clamped/swapped.
        val apr = com.aimoneytracker.data.parser.FieldExtractors
            .extractDateTime("Rs.500 debited on 30/04/24 to SWIGGY", now)
        val aprLd = java.time.Instant.ofEpochMilli(apr).atZone(zone).toLocalDate()
        assertEquals(30, aprLd.dayOfMonth)
        assertEquals(4, aprLd.monthValue)

        // An impossible date (32-13) falls back to the provided timestamp, not a garbled date.
        val bad = com.aimoneytracker.data.parser.FieldExtractors
            .extractDateTime("Rs.500 debited on 32-13-2024 to SWIGGY", now)
        assertEquals(now, bad)
    }

    @Test
    fun parsesUngroupedFourDigitAmountFully() {
        // Regression: "Rs1775" must be 1775.00 (177500 paise), not 177 (the old 3-digit truncation bug).
        val p = registry.parse("VK-HDFCBK", "Rs 1775 debited from A/c XX1234 to SWIGGY", now)
        assertTrue(p.isFinancial)
        assertEquals(177500L, p.amount)

        val p2 = registry.parse("VK-HDFCBK", "INR 1775.00 spent at AMAZON", now)
        assertEquals(177500L, p2.amount)

        val grouped = registry.parse("VK-HDFCBK", "Rs 1,775.50 debited to SWIGGY", now)
        assertEquals(177550L, grouped.amount)

        val large = registry.parse("VK-HDFCBK", "Rs 1,23,456 debited to SWIGGY", now)
        assertEquals(12345600L, large.amount)
    }

    @Test
    fun parsesSbiDebitWithBalance() {
        val body = "Dear Customer, Rs.500.00 debited from A/c XX1234 on 12-05-24 to SWIGGY UPI Ref 123456789. Avl Bal Rs.10,000.00 -SBI"
        val p = registry.parse("VK-SBIINB", body, now)
        assertTrue(p.isFinancial)
        assertEquals(50000L, p.amount)
        assertEquals(TransactionType.DEBIT, p.type)
        assertEquals(1000000L, p.availableBalance)
        assertEquals("XX1234", p.accountMasked)
    }

    @Test
    fun parsesHdfcCredit() {
        val body = "HDFC Bank: Rs 1,200.50 credited to a/c XX5678 on 01-06-2024. Avl bal Rs 25,300.00"
        val p = registry.parse("VM-HDFCBK", body, now)
        assertTrue(p.isFinancial)
        assertEquals(120050L, p.amount)
        assertEquals(TransactionType.CREDIT, p.type)
    }

    @Test
    fun parsesUpiHandleAsPayee() {
        val body = "INR 250 sent to rahul@okaxis via UPI from A/c XX0001. Ref 998877. -ICICI"
        val p = registry.parse("AD-ICICIB", body, now)
        assertTrue(p.isFinancial)
        assertEquals(25000L, p.amount)
        assertEquals(TransactionType.DEBIT, p.type)
        assertEquals("rahul@okaxis", p.payeeHandle)
    }

    @Test
    fun ignoresOtpMessage() {
        val body = "123456 is your OTP for login. Do not share it with anyone."
        val p = registry.parse("VK-HDFCBK", body, now)
        assertFalse(p.isFinancial)
    }

    @Test
    fun ignoresPromotionalMessage() {
        val body = "Get a pre-approved loan offer up to Rs 5,00,000! Apply now and win exciting rewards. Click here."
        val p = registry.parse("AD-PROMO", body, now)
        assertFalse(p.isFinancial)
    }

    @Test
    fun confidenceHigherWhenMoreFieldsMatch() {
        val rich = registry.parse("VK-SBIINB",
            "Rs.500.00 debited from A/c XX1234 to SWIGGY. Avl Bal Rs.10,000.00", now)
        val sparse = registry.parse("VK-SBIINB", "Rs.500 debited", now)
        assertTrue(rich.confidence > sparse.confidence)
    }
}
