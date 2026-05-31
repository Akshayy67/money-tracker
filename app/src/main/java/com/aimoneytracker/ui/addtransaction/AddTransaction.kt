package com.aimoneytracker.ui.addtransaction

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.domain.model.PaymentMethod
import com.aimoneytracker.domain.model.TransactionSource
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repository: TransactionRepository,
) : ViewModel() {
    fun save(amountMinor: Long, type: TransactionType, merchant: String, category: String, note: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.addManual(
                TransactionEntity(
                    amount = amountMinor, type = type, merchantNormalized = merchant.ifBlank { "Cash" },
                    merchantRaw = merchant, category = category, dateTime = DateUtil.now(),
                    paymentMethod = PaymentMethod.CASH, source = TransactionSource.MANUAL,
                    notes = note, confidence = 1.0, isReviewed = true,
                )
            )
            onDone()
        }
    }
}

@Composable
fun AddTransactionScreen(onDone: () -> Unit, viewModel: AddTransactionViewModel = hiltViewModel()) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.DEBIT) }
    var category by remember { mutableStateOf(CategoryCatalog.OTHER) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Quick add", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / description") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            FilterChip(type == TransactionType.DEBIT, { type = TransactionType.DEBIT }, { Text("Expense") }, Modifier.padding(end = 8.dp))
            FilterChip(type == TransactionType.CREDIT, { type = TransactionType.CREDIT }, { Text("Income") })
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
            CategoryCatalog.defaults.filter { it.parentKey == null }.forEach { cat ->
                FilterChip(category == cat.key, { category = cat.key }, { Text(cat.displayName) }, Modifier.padding(end = 6.dp))
            }
        }

        Button(
            onClick = {
                val minor = Money.parseToMinor(amount) ?: return@Button
                viewModel.save(minor, type, merchant, category, note.ifBlank { null }, onDone)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text("Save") }
    }
}
