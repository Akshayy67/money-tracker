package com.aimoneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimoneytracker.data.local.entity.TransactionEntity
import com.aimoneytracker.domain.model.ProcessingFlag
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil

@Composable
fun TransactionRow(txn: TransactionEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(CategoryVisuals.color(txn.category).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!txn.isReviewed) {
                Icon(Icons.Filled.HelpOutline, null, tint = CategoryVisuals.color(txn.category))
            } else {
                Text(
                    txn.merchantNormalized.take(1).uppercase(),
                    color = CategoryVisuals.color(txn.category),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(txn.merchantNormalized, fontWeight = FontWeight.Medium, maxLines = 1)
            val sub = buildString {
                append(CategoryVisuals.name(txn.category))
                append(" • ")
                append(DateUtil.format(txn.dateTime, "dd MMM, HH:mm"))
                if (txn.processingFlag == ProcessingFlag.TRANSFER) append(" • Transfer")
                if (txn.processingFlag == ProcessingFlag.REFUND) append(" • Refund")
            }
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
        }
        val isCredit = txn.type == TransactionType.CREDIT
        AmountText(txn.amount, isCredit && txn.processingFlag != ProcessingFlag.TRANSFER)
    }
}
