package com.aimoneytracker.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aimoneytracker.ui.navigation.Routes

private data class MoreItem(val label: String, val route: String, val icon: ImageVector)

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val items = listOf(
        MoreItem("Analytics & insights", Routes.ANALYTICS, Icons.Filled.Analytics),
        MoreItem("Budgets", Routes.BUDGETS, Icons.Filled.PieChart),
        MoreItem("Goals", Routes.GOALS, Icons.Filled.Savings),
        MoreItem("Subscriptions", Routes.SUBSCRIPTIONS, Icons.Filled.Subscriptions),
        MoreItem("Split expenses", Routes.SPLITS, Icons.Filled.Groups),
        MoreItem("Accounts", Routes.ACCOUNTS, Icons.Filled.CreditCard),
        MoreItem("Digests", Routes.DIGESTS, Icons.Filled.Summarize),
        MoreItem("AI assistant", Routes.CHAT, Icons.AutoMirrored.Filled.Chat),
        MoreItem("Analyze past SMS", Routes.PAST_ANALYZER, Icons.Filled.History),
        MoreItem("Needs review", Routes.REVIEW, Icons.Filled.Receipt),
        MoreItem("Categorization rules", Routes.RULES, Icons.Filled.Rule),
        MoreItem("Net worth", Routes.ACCOUNTS, Icons.Filled.AccountBalanceWallet),
        MoreItem("Settings", Routes.SETTINGS, Icons.Filled.Settings),
    )
    LazyColumn(Modifier.fillMaxWidth()) {
        items(items) { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onNavigate(item.route) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(item.label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
