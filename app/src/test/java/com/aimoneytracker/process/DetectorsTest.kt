package com.aimoneytracker.process

import com.aimoneytracker.data.local.entity.AccountEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.domain.model.AccountType
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.domain.process.DuplicateDetector
import com.aimoneytracker.domain.process.TransferDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorsTest {

    private val base = 1_700_000_000_000L

    private fun txn(
        id: Long, amount: Long, type: TransactionType, time: Long,
        merchant: String = "Swiggy", account: Long? = 1,
    ) = TransactionEntity(
        id = id, amount = amount, type = type, merchantNormalized = merchant,
        merchantRaw = merchant, category = "other", dateTime = time, accountId = account,
    )

    @Test
    fun detectsDuplicateWithinWindow() {
        val detector = DuplicateDetector()
        val sms = txn(1, 50000, TransactionType.DEBIT, base)
        val notification = txn(2, 50000, TransactionType.DEBIT, base + 30_000) // 30s later
        val dup = detector.findDuplicate(notification, listOf(sms))
        assertNotNull(dup)
        assertEquals(1L, dup!!.id)
    }

    @Test
    fun doesNotDuplicateOutsideWindow() {
        val detector = DuplicateDetector()
        val a = txn(1, 50000, TransactionType.DEBIT, base)
        val b = txn(2, 50000, TransactionType.DEBIT, base + 10 * 60 * 1000) // 10 min later
        assertNull(detector.findDuplicate(b, listOf(a)))
    }

    @Test
    fun mergePrefersRicherRecord() {
        val detector = DuplicateDetector()
        val withBalance = txn(1, 50000, TransactionType.DEBIT, base).copy(availableBalance = 999, confidence = 0.9)
        val withoutBalance = txn(2, 50000, TransactionType.DEBIT, base).copy(confidence = 0.6)
        val merged = detector.merge(withBalance, withoutBalance)
        assertEquals(999L, merged.availableBalance)
    }

    @Test
    fun detectsSelfTransferPair() {
        val detector = TransferDetector()
        val out = txn(1, 100000, TransactionType.DEBIT, base, merchant = "Self", account = 1)
        val inc = txn(2, 100000, TransactionType.CREDIT, base + 60_000, merchant = "Self", account = 2)
        val pairs = detector.detectPairs(listOf(out, inc), setOf(1L, 2L))
        assertEquals(1, pairs.size)
        assertEquals(1L, pairs.first().outgoing.id)
        assertEquals(2L, pairs.first().incoming.id)
    }

    @Test
    fun transferCounterpartFound() {
        val detector = TransferDetector()
        val accounts = listOf(
            AccountEntity(id = 1, name = "A", type = AccountType.BANK, isOwnAccount = true),
            AccountEntity(id = 2, name = "B", type = AccountType.BANK, isOwnAccount = true),
        )
        val credit = txn(2, 100000, TransactionType.CREDIT, base + 60_000, account = 2)
        val debit = txn(1, 100000, TransactionType.DEBIT, base, account = 1)
        val counterpart = detector.findCounterpart(credit, listOf(debit), accounts)
        assertNotNull(counterpart)
        assertEquals(1L, counterpart!!.id)
    }
}
