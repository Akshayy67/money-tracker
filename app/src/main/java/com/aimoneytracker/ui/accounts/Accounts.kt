package com.aimoneytracker.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import com.aimoneytracker.data.local.entity.AccountEntity
import com.aimoneytracker.data.repository.AccountRepository
import com.aimoneytracker.domain.model.AccountType
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

data class AccountRow(val account: AccountEntity, val balance: Long, val drift: Long?)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<AccountRow>>(emptyList())
    val rows: StateFlow<List<AccountRow>> = _rows.asStateFlow()

    private val _netWorth = MutableStateFlow(0L)
    val netWorthState: StateFlow<Long> = _netWorth.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { accounts ->
                _rows.value = accounts.map { acc ->
                    AccountRow(acc, repository.computedBalance(acc.id), repository.balanceDrift(acc.id))
                }
                _netWorth.value = repository.totalTrackedBalance()
            }
        }
    }

    fun add(name: String, type: AccountType, opening: Long) {
        viewModelScope.launch {
            repository.add(AccountEntity(name = name, type = type, openingBalance = opening, isTracked = true, isOwnAccount = true))
        }
    }
}

@Composable
fun AccountsScreen(viewModel: AccountsViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val netWorth by viewModel.netWorthState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Account") })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
            item {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Net worth", style = MaterialTheme.typography.labelSmall)
                        Text(Money.format(netWorth), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { SectionHeader("Accounts") }
            items(rows, key = { it.account.id }) { row ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(row.account.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(Money.format(row.balance), fontWeight = FontWeight.SemiBold)
                        }
                        Text(row.account.type.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        row.drift?.let {
                            if (it != 0L) Text("⚠ Drift vs bank-reported: ${Money.format(it)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var opening by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New account") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") })
                    OutlinedTextField(opening, { opening = it }, label = { Text("Opening balance (₹)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.add(name, AccountType.BANK, Money.parseToMinor(opening) ?: 0)
                        showAdd = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
