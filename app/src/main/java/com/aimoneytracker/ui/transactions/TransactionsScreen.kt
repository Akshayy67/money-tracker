package com.aimoneytracker.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.ui.components.EmptyState
import com.aimoneytracker.ui.components.TransactionRow

@Composable
fun TransactionsScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val items = viewModel.transactions.collectAsLazyPagingItems()
    var search by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it; viewModel.setSearch(it) },
            label = { Text("Search merchant, notes, tags") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { typeFilter = null; viewModel.setType(null) },
                label = { Text("All") },
                modifier = Modifier.padding(end = 6.dp),
            )
            FilterChip(
                selected = typeFilter == TransactionType.DEBIT,
                onClick = { typeFilter = TransactionType.DEBIT; viewModel.setType(TransactionType.DEBIT) },
                label = { Text("Expense") },
                modifier = Modifier.padding(end = 6.dp),
            )
            FilterChip(
                selected = typeFilter == TransactionType.CREDIT,
                onClick = { typeFilter = TransactionType.CREDIT; viewModel.setType(TransactionType.CREDIT) },
                label = { Text("Income") },
            )
        }

        if (items.itemCount == 0) {
            EmptyState("No transactions yet.\nAuto-capture from SMS will fill this in.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    items[index]?.let { txn -> TransactionRow(txn, onClick = { onTransactionClick(txn.id) }) }
                }
            }
        }
    }
}
