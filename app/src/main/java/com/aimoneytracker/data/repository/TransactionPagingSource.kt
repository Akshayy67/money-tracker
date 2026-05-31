package com.aimoneytracker.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.TransactionEntity

/**
 * A simple offset-based [PagingSource] for dynamically-filtered transaction lists.
 *
 * Room's `@RawQuery` cannot return a `PagingSource`, so we page manually: each load runs the filter's
 * SQL with `LIMIT/OFFSET` via [TransactionDao.queryRaw]. Pages are keyed by row offset.
 */
class TransactionPagingSource(
    private val dao: TransactionDao,
    private val filter: TransactionFilter,
) : PagingSource<Int, TransactionEntity>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransactionEntity> {
        val offset = params.key ?: 0
        val limit = params.loadSize
        return try {
            val rows = dao.queryRaw(filter.toPagedQuery(limit, offset))
            LoadResult.Page(
                data = rows,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (rows.size < limit) null else offset + limit,
            )
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, TransactionEntity>): Int? {
        val anchor = state.anchorPosition ?: return null
        return state.closestPageToPosition(anchor)?.prevKey?.plus(state.config.pageSize)
            ?: state.closestPageToPosition(anchor)?.nextKey?.minus(state.config.pageSize)
    }
}
