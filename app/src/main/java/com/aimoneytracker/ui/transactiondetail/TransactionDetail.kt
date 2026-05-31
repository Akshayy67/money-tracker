package com.aimoneytracker.ui.transactiondetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
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
import com.aimoneytracker.data.local.entity.PersonEntity
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.repository.PersonRepository
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.domain.model.RelationshipType
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
    private val personRepository: PersonRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1

    val transaction: StateFlow<TransactionEntity?> =
        repository.observeById(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val people: StateFlow<List<PersonEntity>> =
        personRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCategory(category: String) { viewModelScope.launch { repository.applyCorrection(id, category = category) } }

    fun setNote(note: String) {
        viewModelScope.launch { repository.getById(id)?.let { repository.update(it.copy(notes = note)) } }
    }

    /** Assign this transaction to an existing person (and learn the handle→person link). */
    fun assignPerson(personId: Long) {
        viewModelScope.launch {
            val txn = repository.getById(id) ?: return@launch
            repository.applyCorrection(id, personId = personId, handle = handleOf(txn))
        }
    }

    /** Create a new person (e.g. a Friend) and assign this transaction to them. */
    fun createPersonAndAssign(name: String, relationship: RelationshipType) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            val newId = personRepository.add(PersonEntity(name = name.trim(), relationship = relationship))
            val txn = repository.getById(id) ?: return@launch
            repository.applyCorrection(id, personId = newId, handle = handleOf(txn))
        }
    }

    /** Remove the person link from this transaction. */
    fun clearPerson() {
        viewModelScope.launch {
            repository.getById(id)?.let { repository.update(it.copy(relatedPersonId = null)) }
        }
    }

    fun delete(onDone: () -> Unit) { viewModelScope.launch { repository.delete(id); onDone() } }
    fun toggleIgnore(ignored: Boolean) { viewModelScope.launch { repository.setIgnored(id, ignored) } }

    private fun handleOf(txn: TransactionEntity): String? =
        Regex("""([a-zA-Z0-9._\-]{2,})@([a-zA-Z]{2,})""").find(txn.rawMessage ?: "")?.value?.lowercase()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailScreen(onDeleted: () -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val txn by viewModel.transaction.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val t = txn ?: run { Text("Loading…", Modifier.padding(16.dp)); return }
    var note by remember(t.id) { mutableStateOf(t.notes ?: "") }
    var addingFriend by remember(t.id) { mutableStateOf(false) }
    var newName by remember(t.id) { mutableStateOf("") }

    val assignedPerson = people.firstOrNull { it.id == t.relatedPersonId }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(Money.format(t.amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${t.type} • ${t.paymentMethod}", color = MaterialTheme.colorScheme.outline)
        Text(t.merchantNormalized, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
        Text(DateUtil.format(t.dateTime, "dd MMM yyyy, HH:mm"), color = MaterialTheme.colorScheme.outline)
        t.availableBalance?.let { Text("Reported balance: ${Money.format(it)}", Modifier.padding(top = 4.dp)) }

        // ---- Category ----
        Text("Category: ${CategoryVisuals.name(t.category)}", Modifier.padding(top = 16.dp), fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
            CategoryCatalog.defaults.filter { it.parentKey == null }.forEach { cat ->
                FilterChip(t.category == cat.key, { viewModel.setCategory(cat.key) }, { Text(cat.displayName) }, Modifier.padding(end = 6.dp))
            }
        }

        // ---- Person / Friend assignment ----
        Text(
            if (assignedPerson != null) "Person: ${assignedPerson.name}" else "Assign to a person",
            Modifier.padding(top = 20.dp), fontWeight = FontWeight.Medium,
        )
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            people.forEach { person ->
                FilterChip(
                    selected = person.id == t.relatedPersonId,
                    onClick = { viewModel.assignPerson(person.id) },
                    label = { Text(person.name) },
                )
            }
            AssistChip(onClick = { addingFriend = true }, label = { Text("+ New friend") })
            if (assignedPerson != null) {
                AssistChip(onClick = { viewModel.clearPerson() }, label = { Text("Clear") })
            }
        }

        if (addingFriend) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it },
                label = { Text("New person's name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text("Relationship", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    RelationshipType.FRIEND, RelationshipType.FAMILY, RelationshipType.PARTNER,
                    RelationshipType.ROOMMATE, RelationshipType.COLLEAGUE, RelationshipType.OTHER,
                ).forEach { rel ->
                    AssistChip(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.createPersonAndAssign(newName, rel)
                                addingFriend = false; newName = ""
                            }
                        },
                        label = { Text(rel.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }

        // ---- Note ----
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
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
        Row(Modifier.padding(24.dp)) {}
    }
}
