package com.aimoneytracker.ui.rules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.RuleEntity
import com.aimoneytracker.data.repository.CategoryRepository
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.ui.components.CategoryVisuals
import com.aimoneytracker.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {
    val rules: StateFlow<List<RuleEntity>> =
        repository.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(matchValue: String, category: String) {
        viewModelScope.launch {
            val isHandle = matchValue.contains("@")
            repository.addRule(
                RuleEntity(
                    matchType = if (isHandle) "HANDLE" else "CONTAINS",
                    matchField = if (isHandle) "HANDLE" else "MERCHANT",
                    matchValue = matchValue,
                    assignCategory = category,
                    priority = 250,
                    isUserCreated = true,
                )
            )
        }
    }

    fun delete(rule: RuleEntity) { viewModelScope.launch { repository.deleteRule(rule) } }
}

@Composable
fun RulesScreen(viewModel: RulesViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Rule") })
        },
    ) { padding ->
        if (rules.isEmpty()) {
            EmptyState("No rules yet.\nRules are learned from your corrections, or add one like\n\"landlord@oksbi → Rent\".")
        } else {
            LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
                items(rules, key = { it.id }) { rule ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${rule.matchValue} → ${CategoryVisuals.name(rule.assignCategory ?: "other")}")
                            Text("${rule.matchField} • strength ${rule.strength}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(onClick = { viewModel.delete(rule) }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var value by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(CategoryCatalog.RENT) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New rule") },
            text = {
                Column {
                    OutlinedTextField(value, { value = it }, label = { Text("Merchant or handle (e.g. landlord@oksbi)") })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                        CategoryCatalog.defaults.filter { it.parentKey == null }.forEach { cat ->
                            FilterChip(category == cat.key, { category = cat.key }, { Text(cat.displayName) }, Modifier.padding(end = 6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (value.isNotBlank()) { viewModel.add(value, category); showAdd = false }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
