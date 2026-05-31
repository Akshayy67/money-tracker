package com.aimoneytracker.ui.digest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimoneytracker.domain.model.DigestType
import com.aimoneytracker.ui.components.CategoryVisuals
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.ui.components.SectionHeader
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money

@Composable
fun DigestListScreen(onDigestClick: (Long) -> Unit, viewModel: DigestListViewModel = hiltViewModel()) {
    val digests by viewModel.digests.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.generateNow(DigestType.WEEKLY) }, modifier = Modifier.weight(1f)) { Text("Weekly now") }
                OutlinedButton(onClick = { viewModel.generateNow(DigestType.MONTHLY) }, modifier = Modifier.weight(1f)) { Text("Monthly now") }
            }
        }
        if (digests.isEmpty()) {
            item { EmptyState("No digests yet. Generate one above or wait for the scheduled recap.") }
        } else {
            items(digests, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().padding(12.dp).clickable { onDigestClick(d.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${d.type.name.lowercase().replaceFirstChar { it.uppercase() }} • ${DateUtil.format(d.createdAt, "dd MMM")}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(d.headline, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun DigestDetailScreen(viewModel: DigestDetailViewModel = hiltViewModel()) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val c = content ?: run { EmptyState("Digest not found."); return }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(c.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                summary?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column { Text("Spent", style = MaterialTheme.typography.labelSmall); Text(Money.format(c.totalSpent), fontWeight = FontWeight.Bold) }
                Column { Text("Income", style = MaterialTheme.typography.labelSmall); Text(Money.format(c.totalIncome), fontWeight = FontWeight.Bold) }
                Column { Text("Net", style = MaterialTheme.typography.labelSmall); Text(Money.format(c.netSavings), fontWeight = FontWeight.Bold) }
            }
        }
        if (c.topCategories.isNotEmpty()) {
            item { SectionHeader("Top categories") }
            items(c.topCategories) { cat ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(CategoryVisuals.name(cat.name), Modifier.weight(1f))
                    Text(Money.format(cat.amount))
                }
            }
        }
        if (c.unusualFlags.isNotEmpty()) {
            item { SectionHeader("Worth noting") }
            items(c.unusualFlags) { flag ->
                Text("• $flag", Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
        }
        if (c.upcomingBills.isNotEmpty()) {
            item { SectionHeader("Upcoming bills") }
            items(c.upcomingBills) { bill ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("${bill.name} • ${DateUtil.format(bill.dueDateMillis, "dd MMM")}", Modifier.weight(1f))
                    Text(Money.format(bill.amount))
                }
            }
        }
        item { Row(Modifier.padding(24.dp)) {} }
    }
}
