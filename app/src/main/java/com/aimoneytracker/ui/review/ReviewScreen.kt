package com.aimoneytracker.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.domain.model.RelationshipType
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money

/**
 * The "What was this?" review queue (§7). Each card offers one-tap branches:
 * recent payees · Person · Business/Shop · Rent · Skip — with optional category & note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(viewModel: ReviewViewModel = hiltViewModel()) {
    val items by viewModel.needsReview.collectAsState()
    val people by viewModel.people.collectAsState()

    if (items.isEmpty()) {
        EmptyState("All caught up — nothing to review!")
        return
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        items(items, key = { it.id }) { txn ->
            ReviewCard(
                txn = txn,
                peopleNames = people.map { it.id to it.name },
                onBusiness = { cat, note -> viewModel.tagBusiness(txn, cat, note) },
                onPerson = { pid, cat, note -> viewModel.tagPerson(txn, pid, cat, note) },
                onNewPerson = { name, rel, note -> viewModel.createPersonAndTag(txn, name, rel, null, note) },
                onRent = { note -> viewModel.tagRent(txn, note) },
                onSkip = { viewModel.skip(txn) },
                onIgnore = { viewModel.ignore(txn) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    txn: TransactionEntity,
    peopleNames: List<Pair<Long, String>>,
    onBusiness: (String, String?) -> Unit,
    onPerson: (Long, String?, String?) -> Unit,
    onNewPerson: (String, RelationshipType, String?) -> Unit,
    onRent: (String?) -> Unit,
    onSkip: () -> Unit,
    onIgnore: () -> Unit,
) {
    var branch by remember { mutableStateOf("") }  // "", "business", "person", "newperson"
    var note by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            val verb = if (txn.type.name == "CREDIT") "received from" else "sent to"
            Text("What was this?", style = MaterialTheme.typography.labelSmall)
            Text(
                "${Money.format(txn.amount)} $verb ${txn.merchantNormalized}",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Text(DateUtil.format(txn.dateTime, "dd MMM yyyy, HH:mm"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

            // Row 1: top-level branches.
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { branch = "person" }, label = { Text("Person") })
                AssistChip(onClick = { branch = "business" }, label = { Text("Business/Shop") })
                AssistChip(onClick = { onRent(note.ifBlank { null }) }, label = { Text("Rent") })
                AssistChip(onClick = onSkip, label = { Text("Skip") })
                AssistChip(onClick = onIgnore, label = { Text("Don't track") })
            }

            when (branch) {
                "business" -> {
                    Text("Pick a category", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categoryChoices().forEach { (key, name) ->
                            AssistChip(onClick = { onBusiness(key, note.ifBlank { null }) }, label = { Text(name) })
                        }
                    }
                }
                "person" -> {
                    Text("Who?", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        peopleNames.take(8).forEach { (id, name) ->
                            AssistChip(onClick = { onPerson(id, null, note.ifBlank { null }) }, label = { Text(name) })
                        }
                        AssistChip(onClick = { branch = "newperson" }, label = { Text("+ New") })
                    }
                }
                "newperson" -> {
                    OutlinedTextField(value = newName, onValueChange = { newName = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(RelationshipType.FRIEND, RelationshipType.FAMILY, RelationshipType.ROOMMATE, RelationshipType.OTHER).forEach { rel ->
                            AssistChip(
                                onClick = { if (newName.isNotBlank()) onNewPerson(newName, rel, note.ifBlank { null }) },
                                label = { Text(rel.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Add a note (optional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
            )
        }
    }
}

private fun categoryChoices(): List<Pair<String, String>> = listOf(
    CategoryCatalog.GROCERIES to "Groceries",
    CategoryCatalog.DINING to "Food",
    CategoryCatalog.TRANSPORT to "Transport",
    CategoryCatalog.SHOPPING to "Shopping",
    CategoryCatalog.HEALTHCARE to "Health",
    CategoryCatalog.UTILITIES to "Utilities",
    CategoryCatalog.ENTERTAINMENT to "Fun",
    CategoryCatalog.OTHER to "Other",
)
