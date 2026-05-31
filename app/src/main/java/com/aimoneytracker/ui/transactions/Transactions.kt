package com.aimoneytracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.repository.TransactionFilter
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionFilter())
    val filter = _filter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: Flow<PagingData<TransactionEntity>> =
        _filter.flatMapLatest { repository.paging(it) }.cachedIn(viewModelScope)

    fun setSearch(text: String) { _filter.value = _filter.value.copy(text = text.ifBlank { null }) }
    fun setType(type: TransactionType?) { _filter.value = _filter.value.copy(type = type) }
    fun setCategory(category: String?) {
        _filter.value = _filter.value.copy(categories = category?.let { listOf(it) } ?: emptyList())
    }
    fun applyFilter(filter: TransactionFilter) { _filter.value = filter }
    fun clear() { _filter.value = TransactionFilter() }
}
