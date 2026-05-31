package com.aimoneytracker.domain.process

import com.aimoneytracker.data.local.entity.AccountEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.domain.model.TransactionType
import javax.inject.Inject
import kotlin.math.abs

/**
 * Transfer detection (§4). Money moved between the user's *own* accounts must NOT count as income or
 * expense. We match an outgoing debit on one own-account to an incoming credit of the same amount on
 * another own-account within a short window, and mark both as TRANSFER (excluded from spend/income).
 */
class TransferDetector @Inject constructor() {

    private val windowMillis = 30 * 60 * 1000L // self-transfers can lag a few minutes to ~30 min

    data class TransferPair(val outgoing: TransactionEntity, val incoming: TransactionEntity)

    /**
     * Find transfer pairs within [transactions], restricted to accounts in [ownAccountIds].
     * A pair = a DEBIT and a CREDIT of equal amount, on two different own accounts, close in time.
     */
    fun detectPairs(
        transactions: List<TransactionEntity>,
        ownAccountIds: Set<Long>,
    ): List<TransferPair> {
        val debits = transactions.filter {
            it.type == TransactionType.DEBIT && it.accountId in ownAccountIds
        }
        val credits = transactions.filter {
            it.type == TransactionType.CREDIT && it.accountId in ownAccountIds
        }.toMutableList()

        val pairs = mutableListOf<TransferPair>()
        for (debit in debits.sortedBy { it.dateTime }) {
            val match = credits.firstOrNull { credit ->
                credit.amount == debit.amount &&
                    credit.accountId != debit.accountId &&
                    abs(credit.dateTime - debit.dateTime) <= windowMillis
            }
            if (match != null) {
                pairs += TransferPair(debit, match)
                credits.remove(match)
            }
        }
        return pairs
    }

    /**
     * Single-transaction check used at insert time: does [candidate] (a credit) look like the other
     * half of a recent own-account debit (or vice-versa)?
     */
    fun findCounterpart(
        candidate: TransactionEntity,
        recent: List<TransactionEntity>,
        ownAccounts: List<AccountEntity>,
    ): TransactionEntity? {
        val ownIds = ownAccounts.filter { it.isOwnAccount }.map { it.id }.toSet()
        if (candidate.accountId !in ownIds) return null
        val opposite = if (candidate.type == TransactionType.DEBIT)
            TransactionType.CREDIT else TransactionType.DEBIT
        return recent.firstOrNull { other ->
            other.id != candidate.id &&
                other.type == opposite &&
                other.amount == candidate.amount &&
                other.accountId in ownIds &&
                other.accountId != candidate.accountId &&
                abs(other.dateTime - candidate.dateTime) <= windowMillis
        }
    }
}
