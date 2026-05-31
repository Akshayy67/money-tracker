package com.aimoneytracker.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.BudgetEntity
import com.aimoneytracker.data.repository.BudgetRepository
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.domain.model.BudgetPeriod
import com.aimoneytracker.domain.model.BudgetScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val statuses: StateFlow<List<BudgetRepository.BudgetStatus>> =
        combine(budgetRepository.observeActive(), transactionRepository.observeAll()) { b, _ -> b }
            .mapLatest { budgetRepository.statuses() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addBudget(name: String, amountMinor: Long, scope: BudgetScope, categoryKey: String?) {
        viewModelScope.launch {
            budgetRepository.add(
                BudgetEntity(name = name, amount = amountMinor, period = BudgetPeriod.MONTHLY,
                    scope = scope, categoryKey = categoryKey)
            )
        }
    }

    fun delete(budget: BudgetEntity) { viewModelScope.launch { budgetRepository.delete(budget) } }
}
