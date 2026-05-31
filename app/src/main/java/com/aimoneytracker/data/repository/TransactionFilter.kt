package com.aimoneytracker.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.aimoneytracker.domain.model.TransactionType

/** A combinable filter spec for search & filtering (§17). Translates to a parameterized SQL query. */
data class TransactionFilter(
    val text: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val categories: List<String> = emptyList(),
    val merchant: String? = null,
    val personId: Long? = null,
    val accountId: Long? = null,
    val amountMin: Long? = null,
    val amountMax: Long? = null,
    val paymentMethod: String? = null,
    val type: TransactionType? = null,
    val reviewedOnly: Boolean? = null,   // true=reviewed, false=unreviewed, null=both
    val splitOnly: Boolean = false,
    val reimbursableOnly: Boolean = false,
    val includeIgnored: Boolean = false,
    val includeArchived: Boolean = false,
    val tag: String? = null,
) {
    fun toQuery(): SupportSQLiteQuery {
        val where = StringBuilder("1=1")
        val args = mutableListOf<Any>()

        if (!includeArchived) where.append(" AND isArchived = 0")
        if (!includeIgnored) where.append(" AND isIgnored = 0")
        text?.takeIf { it.isNotBlank() }?.let {
            where.append(" AND (merchantNormalized LIKE ? OR merchantRaw LIKE ? OR notes LIKE ? OR tags LIKE ?)")
            val like = "%$it%"
            args.add(like); args.add(like); args.add(like); args.add(like)
        }
        startDate?.let { where.append(" AND dateTime >= ?"); args.add(it) }
        endDate?.let { where.append(" AND dateTime <= ?"); args.add(it) }
        if (categories.isNotEmpty()) {
            where.append(" AND category IN (${categories.joinToString(",") { "?" }})")
            args.addAll(categories)
        }
        merchant?.let { where.append(" AND merchantNormalized = ?"); args.add(it) }
        personId?.let { where.append(" AND relatedPersonId = ?"); args.add(it) }
        accountId?.let { where.append(" AND accountId = ?"); args.add(it) }
        amountMin?.let { where.append(" AND amount >= ?"); args.add(it) }
        amountMax?.let { where.append(" AND amount <= ?"); args.add(it) }
        paymentMethod?.let { where.append(" AND paymentMethod = ?"); args.add(it) }
        type?.let { where.append(" AND type = ?"); args.add(it.name) }
        reviewedOnly?.let { where.append(" AND isReviewed = ?"); args.add(if (it) 1 else 0) }
        if (splitOnly) where.append(" AND relatedSplitId IS NOT NULL")
        if (reimbursableOnly) where.append(" AND isReimbursable = 1")
        tag?.let { where.append(" AND tags LIKE ?"); args.add("%\"$it\"%") }

        val sql = "SELECT * FROM transactions WHERE $where ORDER BY dateTime DESC"
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    /**
     * Same filter as [toQuery] but with LIMIT/OFFSET appended, used by the custom paging source.
     * (Room's @RawQuery cannot return a PagingSource, so we page manually over [TransactionDao.queryRaw].)
     */
    fun toPagedQuery(limit: Int, offset: Int): SupportSQLiteQuery {
        val base = toQuery()
        val pagedSql = base.sql + " LIMIT ? OFFSET ?"
        val args = ArrayList<Any?>()
        // Re-bind the original args, then the paging args, by re-running the builder logic via toQuery
        // is not directly possible (args aren't exposed), so we rebuild here in lockstep with toQuery.
        return SimpleSQLiteQuery(pagedSql, collectArgs().apply { add(limit); add(offset) }.toTypedArray())
    }

    /** Rebuilds the bound-arg list in the exact order [toQuery] appends them. */
    private fun collectArgs(): MutableList<Any?> {
        val args = mutableListOf<Any?>()
        text?.takeIf { it.isNotBlank() }?.let {
            val like = "%$it%"; args.add(like); args.add(like); args.add(like); args.add(like)
        }
        startDate?.let { args.add(it) }
        endDate?.let { args.add(it) }
        if (categories.isNotEmpty()) args.addAll(categories)
        merchant?.let { args.add(it) }
        personId?.let { args.add(it) }
        accountId?.let { args.add(it) }
        amountMin?.let { args.add(it) }
        amountMax?.let { args.add(it) }
        paymentMethod?.let { args.add(it) }
        type?.let { args.add(it.name) }
        reviewedOnly?.let { args.add(if (it) 1 else 0) }
        tag?.let { args.add("%\"$it\"%") }
        return args
    }
}
