package com.aimoneytracker.domain.process

import com.aimoneytracker.data.local.entity.TransactionEntity
import javax.inject.Inject
import kotlin.math.abs

/**
 * Duplicate detection & merge (§4). The same payment often arrives twice — once by SMS and once by
 * an app notification — within seconds. Two records are considered duplicates when they share the
 * same amount and type, target the same payee, and fall inside a short time window.
 */
class DuplicateDetector @Inject constructor() {

    /** Default window: an SMS and a notification for one payment usually land within ~3 minutes. */
    private val windowMillis = 3 * 60 * 1000L

    /** A coarse key for indexed pre-filtering: amount + normalized merchant + ~10-min bucket. */
    fun dedupKey(amount: Long, merchantNormalized: String, dateTime: Long): String {
        val bucket = dateTime / (10 * 60 * 1000L)
        return "$amount|${merchantNormalized.lowercase().trim()}|$bucket"
    }

    fun windowFor(dateTime: Long): Pair<Long, Long> =
        (dateTime - windowMillis) to (dateTime + windowMillis)

    /** Returns an existing transaction that the [candidate] duplicates, or null. */
    fun findDuplicate(candidate: TransactionEntity, existing: List<TransactionEntity>): TransactionEntity? {
        return existing.firstOrNull { other ->
            other.id != candidate.id &&
                other.amount == candidate.amount &&
                other.type == candidate.type &&
                abs(other.dateTime - candidate.dateTime) <= windowMillis &&
                payeeMatches(candidate, other)
        }
    }

    private fun payeeMatches(a: TransactionEntity, b: TransactionEntity): Boolean {
        val an = a.merchantNormalized.lowercase().trim()
        val bn = b.merchantNormalized.lowercase().trim()
        if (an == bn) return true
        if (an.isBlank() || bn.isBlank()) return true // amount+time alone is strong evidence
        // One contains the other (e.g. "Swiggy" vs "Swiggy Order").
        return an.contains(bn) || bn.contains(an)
    }

    /**
     * Merge two duplicates into one, preferring the richer record. SMS tends to carry the balance;
     * notifications sometimes carry a cleaner merchant. We keep the higher-confidence base and fill
     * any missing fields from the other.
     */
    fun merge(primary: TransactionEntity, secondary: TransactionEntity): TransactionEntity {
        val base = if (primary.confidence >= secondary.confidence) primary else secondary
        val other = if (base === primary) secondary else primary
        return base.copy(
            availableBalance = base.availableBalance ?: other.availableBalance,
            accountId = base.accountId ?: other.accountId,
            merchantNormalized = base.merchantNormalized.ifBlank { other.merchantNormalized },
            relatedPersonId = base.relatedPersonId ?: other.relatedPersonId,
            notes = base.notes ?: other.notes,
            rawMessage = base.rawMessage ?: other.rawMessage,
        )
    }
}
