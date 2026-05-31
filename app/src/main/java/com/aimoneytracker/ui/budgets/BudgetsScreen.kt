package com.aimoneytracker.ui.budgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimoneytracker.domain.model.BudgetScope
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.ui.theme.NegativeRed
import com.aimoneytracker.ui.theme.WarningAmber
import com.aimoneytracker.util.Money

@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = hiltViewModel()) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Budget") })
        },
    ) { padding ->
        if (statuses.isEmpty()) {
            EmptyState("No budgets yet. Add one to track your limits.")
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
                items(statuses, key = { it.budget.id }) { s ->
                    Card(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(s.budget.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Text("${Money.format(s.spent)} / ${Money.format(s.budget.amount)}")
                            }
                            LinearProgressIndicator(
                                progress = { (s.pctUsed / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                color = when {
                                    s.exceeded -> NegativeRed
                                    s.nearingLimit -> WarningAmber
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            )
                            val msg = when {
                                s.exceeded -> "Exceeded by ${Money.format(-s.remaining)}"
                                s.nearingLimit -> "Nearing limit • ${Money.format(s.remaining)} left"
                                else -> "${Money.format(s.remaining)} left"
                            }
                            Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New monthly budget") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") })
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val minor = Money.parseToMinor(amount)
                    if (name.isNotBlank() && minor != null) {
                        viewModel.addBudget(name, minor, BudgetScope.OVERALL, null)
                        showAdd = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
