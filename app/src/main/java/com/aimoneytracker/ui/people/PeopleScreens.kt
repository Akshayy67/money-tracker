package com.aimoneytracker.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.ui.components.SectionHeader
import com.aimoneytracker.ui.components.StatCard
import com.aimoneytracker.ui.components.TransactionRow
import com.aimoneytracker.ui.theme.NegativeRed
import com.aimoneytracker.ui.theme.PositiveGreen
import com.aimoneytracker.util.Money

@Composable
fun PeopleScreen(onPersonClick: (Long) -> Unit, viewModel: PeopleViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    if (items.isEmpty()) {
        EmptyState("No people yet.\nPeople are auto-created when you tag UPI payments.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        items(items, key = { it.person.id }) { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onPersonClick(item.person.id) }.padding(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.person.name, fontWeight = FontWeight.Medium)
                    Text(item.person.relationship.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                val owed = item.net // positive => you sent more (they owe / you spent on them)
                Text(
                    (if (owed >= 0) "net " else "net +") + Money.format(kotlin.math.abs(owed)),
                    color = if (owed >= 0) NegativeRed else PositiveGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun PersonDetailScreen(onTransactionClick: (Long) -> Unit, viewModel: PersonDetailViewModel = hiltViewModel()) {
    val person by viewModel.person.collectAsStateWithLifecycle()
    val txns by viewModel.transactions.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(person?.name ?: "Person", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                person?.relationship?.let {
                    Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                StatCard("Sent", balance.sent, Modifier.weight(1f))
                StatCard("Received", balance.received, Modifier.weight(1f))
                StatCard("Net", balance.sent - balance.received, Modifier.weight(1f), signed = true)
            }
        }
        item { SectionHeader("History") }
        items(txns, key = { it.id }) { txn -> TransactionRow(txn, onClick = { onTransactionClick(txn.id) }) }
    }
}
