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
