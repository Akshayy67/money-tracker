package com.aimoneytracker.ui.subscriptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.SubscriptionEntity
import com.aimoneytracker.data.repository.SubscriptionRepository
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.ui.components.SectionHeader
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
) : ViewModel() {
    val subs: StateFlow<List<SubscriptionEntity>> =
        repository.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun detect() { viewModelScope.launch { repository.detectAndStore() } }
}

@Composable
fun SubscriptionsScreen(viewModel: SubscriptionsViewModel = hiltViewModel()) {
    val subs by viewModel.subs.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { viewModel.detect() },
                icon = { Icon(Icons.Filled.Refresh, null) }, text = { Text("Detect") })
        },
    ) { padding ->
        if (subs.isEmpty()) {
            EmptyState("No subscriptions detected yet.\nTap Detect to scan your history.")
        } else {
            val monthlyTotal = subs.sumOf { it.amount }
            LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
                item {
                    Card(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Recurring spend", style = MaterialTheme.typography.labelSmall)
                            Text(Money.format(monthlyTotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("${subs.size} active subscriptions/bills", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                item { SectionHeader("Upcoming") }
                items(subs, key = { it.id }) { sub ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, fontWeight = FontWeight.Medium)
                            val due = sub.nextDueDate?.let { "Next ${DateUtil.format(it, "dd MMM")}" } ?: sub.cycle.name.lowercase()
                            Text("$due • ${sub.kind.name.lowercase()}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(Money.format(sub.amount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
