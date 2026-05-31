package com.aimoneytracker.ui.transactiondetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.ui.components.CategoryVisuals
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val repository: TransactionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1

    val transaction: StateFlow<TransactionEntity?> =
        repository.observeById(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setCategory(category: String) { viewModelScope.launch { repository.applyCorrection(id, category = category) } }
    fun setNote(note: String) {
        viewModelScope.launch { repository.getById(id)?.let { repository.update(it.copy(notes = note)) } }
    }
    fun delete(onDone: () -> Unit) { viewModelScope.launch { repository.delete(id); onDone() } }
    fun toggleIgnore(ignored: Boolean) { viewModelScope.launch { repository.setIgnored(id, ignored) } }
}

@Composable
fun TransactionDetailScreen(onDeleted: () -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val txn by viewModel.transaction.collectAsStateWithLifecycle()
    val t = txn ?: run { Text("Loading…", Modifier.padding(16.dp)); return }
    var note by remember(t.id) { mutableStateOf(t.notes ?: "") }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(Money.format(t.amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${t.type} • ${t.paymentMethod}", color = MaterialTheme.colorScheme.outline)
        Text(t.merchantNormalized, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
        Text(DateUtil.format(t.dateTime, "dd MMM yyyy, HH:mm"), color = MaterialTheme.colorScheme.outline)
        t.availableBalance?.let { Text("Reported balance: ${Money.format(it)}", Modifier.padding(top = 4.dp)) }

        Text("Category: ${CategoryVisuals.name(t.category)}", Modifier.padding(top = 16.dp), fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
            CategoryCatalog.defaults.filter { it.parentKey == null }.forEach { cat ->
                FilterChip(t.category == cat.key, { viewModel.setCategory(cat.key) }, { Text(cat.displayName) }, Modifier.padding(end = 6.dp))
            }
        }

        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Button(onClick = { viewModel.setNote(note) }, modifier = Modifier.padding(top = 8.dp)) { Text("Save note") }

        t.rawMessage?.let {
            Text("Original message", Modifier.padding(top = 16.dp), fontWeight = FontWeight.Medium)
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            OutlinedButton(onClick = { viewModel.toggleIgnore(!t.isIgnored) }, modifier = Modifier.weight(1f)) {
                Text(if (t.isIgnored) "Un-ignore" else "Ignore")
            }
            Button(onClick = { viewModel.delete(onDeleted) }, modifier = Modifier.weight(1f).padding(start = 8.dp)) { Text("Delete") }
        }
    }
}
