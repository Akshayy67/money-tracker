package com.aimoneytracker.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimoneytracker.ui.components.BarChart
import com.aimoneytracker.ui.components.CategoryVisuals
import com.aimoneytracker.ui.components.DonutChart
import com.aimoneytracker.ui.components.DonutSlice
import com.aimoneytracker.ui.components.LoadingState
import com.aimoneytracker.ui.components.SectionHeader
import com.aimoneytracker.ui.components.StatCard
import com.aimoneytracker.ui.theme.PositiveGreen
import com.aimoneytracker.ui.theme.WarningAmber
import com.aimoneytracker.util.Money

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) { LoadingState(); return }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                StatCard("Income", state.income, Modifier.weight(1f))
                StatCard("Expenses", state.expense, Modifier.weight(1f))
                StatCard("Net", state.net, Modifier.weight(1f), signed = true)
            }
        }

        if (state.insights.isNotEmpty()) {
            item { SectionHeader("Insights") }
            items(state.insights) { insight ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(insight.title, fontWeight = FontWeight.SemiBold,
                            color = when (insight.severity.name) {
                                "WARNING" -> WarningAmber
                                "POSITIVE" -> PositiveGreen
                                else -> MaterialTheme.colorScheme.onSurface
                            })
                        Text(insight.detail, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            item { SectionHeader("By category") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        DonutChart(state.categories.take(6).map {
                            DonutSlice(CategoryVisuals.name(it.category), it.total, CategoryVisuals.color(it.category))
                        })
                    }
                }
            }
        }

        item { SectionHeader("Spending by day of week") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(12.dp)) { BarChart(state.dayOfWeek) }
            }
        }

        if (state.topMerchants.isNotEmpty()) {
            item { SectionHeader("Top merchants") }
            items(state.topMerchants) { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(m.merchantNormalized, Modifier.weight(1f))
                    Text("${Money.format(m.total)}  (${m.count})", fontWeight = FontWeight.Medium)
                }
            }
        }
        item { Row(Modifier.padding(24.dp)) {} }
    }
}
