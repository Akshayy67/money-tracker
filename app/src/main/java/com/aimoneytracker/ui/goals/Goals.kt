package com.aimoneytracker.ui.goals

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.GoalEntity
import com.aimoneytracker.data.repository.GoalRepository
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalRepository,
) : ViewModel() {
    val goals: StateFlow<List<GoalEntity>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, target: Long, monthly: Long?) {
        viewModelScope.launch { repository.add(GoalEntity(name = name, targetAmount = target, monthlyContribution = monthly)) }
    }

    fun contribute(goalId: Long, amount: Long) { viewModelScope.launch { repository.contribute(goalId, amount) } }
}

@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Goal") })
        },
    ) { padding ->
        if (goals.isEmpty()) {
            EmptyState("No goals yet. Set a savings goal to track progress.")
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
                items(goals, key = { it.id }) { g ->
                    Card(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Text("${Money.format(g.savedAmount)} / ${Money.format(g.targetAmount)}")
                            }
                            val pct = if (g.targetAmount > 0) (g.savedAmount.toFloat() / g.targetAmount).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Text("${(pct * 100).toInt()}% saved", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var monthly by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New goal") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") })
                    OutlinedTextField(target, { target = it }, label = { Text("Target (₹)") })
                    OutlinedTextField(monthly, { monthly = it }, label = { Text("Monthly contribution (₹, optional)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = Money.parseToMinor(target)
                    if (name.isNotBlank() && t != null) {
                        viewModel.add(name, t, Money.parseToMinor(monthly))
                        showAdd = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
