package com.aimoneytracker.ui.forecast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimoneytracker.domain.forecast.ForecastResult
import com.aimoneytracker.ui.components.ChartPoint
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.ui.components.LoadingState
import com.aimoneytracker.ui.components.ProjectionLineChart
import com.aimoneytracker.ui.components.SectionHeader
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money

@Composable
fun ForecastScreen(viewModel: ForecastViewModel = hiltViewModel()) {
    val forecast by viewModel.state.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    when {
        loading && forecast == null -> LoadingState()
        forecast == null -> EmptyState("Not enough data yet to forecast.\nKeep capturing transactions.")
        else -> ForecastContent(forecast!!)
    }
}

@Composable
private fun ForecastContent(f: ForecastResult) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "At your current pace you'll spend ~${Money.format(f.projectedVariableSpend + f.scheduledOutflowTotal)} " +
                        "this month and have ~${Money.format(f.projectedEndBalanceExpected)} left.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Range: ${Money.format(f.projectedEndBalanceLow)} – ${Money.format(f.projectedEndBalanceHigh)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                )
                Text("Forecast confidence", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { f.confidence.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        }

        SectionHeader("Balance projection")
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val pts = f.projection.mapIndexed { i, p -> i to p }
            val expected = pts.map { (i, p) -> ChartPoint(i.toFloat(), Money.minorToMajor(p.expectedBalance).toFloat()) }
            val low = pts.map { (i, p) -> ChartPoint(i.toFloat(), Money.minorToMajor(p.lowBalance).toFloat()) }
            val high = pts.map { (i, p) -> ChartPoint(i.toFloat(), Money.minorToMajor(p.highBalance).toFloat()) }
            val actual = expected.take(1) // today's anchor (solid)
            ProjectionLineChart(actual = actual, forecast = expected, bandLow = low, bandHigh = high)
        }

        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Safe to spend", style = MaterialTheme.typography.labelSmall)
                Text("${Money.format(f.dailySafeToSpend)}/day",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("≈ ${Money.format(f.totalSafeToSpend)} for the rest of the month to stay on track.",
                    style = MaterialTheme.typography.bodyLarge)
                f.runLowDate?.let {
                    Text("⚠ Balance may dip below your threshold around $it.",
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        if (f.upcomingObligations.isNotEmpty()) {
            SectionHeader("Upcoming fixed obligations")
            f.upcomingObligations.forEach { ob ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(ob.name, fontWeight = FontWeight.Medium)
                        Text("Due ${ob.dueDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(Money.format(ob.amount), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.padding(24.dp)) {}
    }
}
