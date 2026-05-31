package com.aimoneytracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.local.result.CategorySum
import com.aimoneytracker.data.repository.AnalyticsRepository
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.domain.forecast.ForecastResult
import com.aimoneytracker.domain.usecase.GenerateForecastUseCase
import com.aimoneytracker.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val income: Long = 0,
    val expense: Long = 0,
    val net: Long = 0,
    val savingsRatePct: Double = 0.0,
    val categories: List<CategorySum> = emptyList(),
    val recent: List<TransactionEntity> = emptyList(),
    val needsReviewCount: Int = 0,
    val forecast: ForecastResult? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val transactionRepository: TransactionRepository,
    private val generateForecast: GenerateForecastUseCase,
) : ViewModel() {

    private val forecastState = MutableStateFlow<ForecastResult?>(null)

    val state: StateFlow<DashboardUiState> = combine(
        analytics.observeMonthSummary(),
        analytics.observeCategoryBreakdown(DateUtil.startOfMonth(), DateUtil.endOfMonth()),
        transactionRepository.observeAll(),
        transactionRepository.observeNeedsReviewCount(),
        forecastState,
    ) { summary, categories, all, reviewCount, forecast ->
        DashboardUiState(
            income = summary.income,
            expense = summary.expense,
            net = summary.net,
            savingsRatePct = summary.savingsRatePct,
            categories = categories,
            recent = all.take(8),
            needsReviewCount = reviewCount,
            forecast = forecast,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init { refreshForecast() }

    fun refreshForecast() {
        viewModelScope.launch {
            runCatching { generateForecast(persist = false) }.getOrNull()?.let { forecastState.value = it }
        }
    }
}
