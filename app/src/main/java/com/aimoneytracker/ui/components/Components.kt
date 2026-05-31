package com.aimoneytracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aimoneytracker.ui.theme.NegativeRed
import com.aimoneytracker.ui.theme.PositiveGreen
import com.aimoneytracker.util.Money

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        action?.invoke()
    }
}

@Composable
fun StatCard(label: String, amountMinor: Long, modifier: Modifier = Modifier, signed: Boolean = false) {
    Card(modifier.padding(4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            val color = when {
                !signed -> MaterialTheme.colorScheme.onSurface
                amountMinor >= 0 -> PositiveGreen
                else -> NegativeRed
            }
            Text(
                Money.format(amountMinor),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
fun AmountText(amountMinor: Long, isCredit: Boolean, modifier: Modifier = Modifier) {
    val sign = if (isCredit) "+" else "-"
    Text(
        "$sign${Money.format(amountMinor)}",
        modifier = modifier,
        color = if (isCredit) PositiveGreen else NegativeRed,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
