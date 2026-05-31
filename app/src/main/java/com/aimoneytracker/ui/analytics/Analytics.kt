package com.aimoneytracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.result.CategorySum
import com.aimoneytracker.data.local.result.MerchantSum
import com.aimoneytracker.data.repository.AnalyticsRepository
import com.aimoneytracker.data.repository.SubscriptionRepository
import com.aimoneytracker.domain.ai.AiService
import com.aimoneytracker.domain.insights.Insight
import com.aimoneytracker.domain.insights.InsightsEngine
import com.aimoneytracker.util.DateUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsUiState(
    val income: Long = 0,
    val expense: Long = 0,
    val net: Long = 0,
    val categories: List<CategorySum> = emptyList(),
    val topMerchants: List<MerchantSum> = emptyList(),
    val dayOfWeek: List<Pair<String, Long>> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val insightsEngine: InsightsEngine,
    private val subscriptionRepository: SubscriptionRepository,
    private val aiService: AiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val now = DateUtil.now()
            val start = DateUtil.startOfMonth(now)
            val end = DateUtil.endOfMonth(now)
            val prevRef = DateUtil.monthsAgo(1, now)
            val prevStart = DateUtil.startOfMonth(prevRef)
            val prevEnd = DateUtil.endOfMonth(prevRef)

            val summary = analytics.summary(start, end)
            val categories = analytics.categoryBreakdown(start, end)
            val prevCategories = analytics.categoryBreakdown(prevStart, prevEnd)
            val merchants = analytics.topMerchants(start, end, 10)
            val dow = analytics.byDayOfWeek(start, end)
            val hours = analytics.byHour(start, end)

            val dowLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val dowPairs = (0..6).map { d -> dowLabels[d] to (dow.firstOrNull { it.dow == d }?.total ?: 0L) }

            val insights = insightsEngine.analyze(
                InsightsEngine.Input(
                    currentCategories = categories,
                    previousCategories = prevCategories,
                    byDayOfWeek = dow,
                    byHour = hours,
                    topMerchants = merchants,
                    totalSpent = summary.expense,
                    unusedSubscriptionNames = emptyList(),
                )
            )

            _state.value = AnalyticsUiState(
                income = summary.income, expense = summary.expense, net = summary.net,
                categories = categories, topMerchants = merchants, dayOfWeek = dowPairs,
                insights = insights, loading = false,
            )
        }
    }
}
