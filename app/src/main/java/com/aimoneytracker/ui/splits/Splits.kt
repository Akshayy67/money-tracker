package com.aimoneytracker.ui.splits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.SplitEntity
import com.aimoneytracker.data.repository.PersonRepository
import com.aimoneytracker.data.repository.SplitRepository
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.ui.components.SectionHeader
import com.aimoneytracker.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplitsViewModel @Inject constructor(
    private val splitRepository: SplitRepository,
    private val personRepository: PersonRepository,
) : ViewModel() {
    val splits: StateFlow<List<SplitEntity>> =
        splitRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _owes = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    val owes: StateFlow<List<Pair<String, Long>>> = _owes.asStateFlow()

    init { refreshOwes() }

    fun refreshOwes() {
        viewModelScope.launch {
            _owes.value = splitRepository.outstandingByPerson().entries.map { (pid, amt) ->
                (personRepository.getById(pid)?.name ?: "Person $pid") to amt
            }.sortedByDescending { it.second }
        }
    }
}

@Composable
fun SplitsScreen(viewModel: SplitsViewModel = hiltViewModel()) {
    val splits by viewModel.splits.collectAsStateWithLifecycle()
    val owes by viewModel.owes.collectAsStateWithLifecycle()

    if (splits.isEmpty() && owes.isEmpty()) {
        EmptyState("No split expenses yet.")
        return
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        if (owes.isNotEmpty()) {
            item { SectionHeader("Who owes you") }
            items(owes) { (name, amt) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(name, Modifier.weight(1f))
                    Text(Money.format(amt), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { SectionHeader("Splits") }
        items(splits, key = { it.id }) { s ->
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(s.description, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(Money.format(s.totalAmount))
                    }
                    Text("${s.type.name.lowercase()} • ${s.status.name.lowercase().replace('_', ' ')}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
