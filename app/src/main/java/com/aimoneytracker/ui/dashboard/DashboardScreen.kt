package com.aimoneytracker.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimoneytracker.ui.components.CategoryVisuals
import com.aimoneytracker.ui.components.DonutChart
import com.aimoneytracker.ui.components.DonutSlice
import com.aimoneytracker.ui.components.SectionHeader
import com.aimoneytracker.ui.components.StatCard
import com.aimoneytracker.ui.components.TransactionRow
import com.aimoneytracker.util.Money

@Composable
fun DashboardScreen(
    onTransactionClick: (Long) -> Unit,
    onReviewClick: () -> Unit,
    onForecastClick: () -> Unit,
    onSeeAll: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxWidth()) {
        if (state.needsReviewCount > 0) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp).clickable { onReviewClick() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, null)
                        Text(
                            "  ${state.needsReviewCount} transaction(s) need review — tap to tag them.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                StatCard("Income", state.income, Modifier.weight(1f))
                StatCard("Expenses", state.expense, Modifier.weight(1f))
                StatCard("Net", state.net, Modifier.weight(1f), signed = true)
            }
        }

        state.forecast?.let { f ->
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp).clickable { onForecastClick() },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Safe to spend", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${Money.format(f.dailySafeToSpend)}/day",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Projected month-end balance ~${Money.format(f.projectedEndBalanceExpected)} " +
                                "(${Money.format(f.projectedEndBalanceLow)} – ${Money.format(f.projectedEndBalanceHigh)})",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        f.runLowDate?.let {
                            Text("⚠ Balance may run low around $it",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            item { SectionHeader("Spending by category") }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        DonutChart(
                            state.categories.take(6).map {
                                DonutSlice(CategoryVisuals.name(it.category), it.total, CategoryVisuals.color(it.category))
                            }
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("Recent") {
                Text("See all", Modifier.clickable { onSeeAll() }, color = MaterialTheme.colorScheme.primary)
            }
        }
        items(state.recent, key = { it.id }) { txn ->
            TransactionRow(txn, onClick = { onTransactionClick(txn.id) })
        }
        item { Row(Modifier.padding(24.dp)) {} }
    }
}
