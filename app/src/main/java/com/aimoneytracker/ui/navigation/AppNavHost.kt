package com.aimoneytracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.aimoneytracker.ui.accounts.AccountsScreen
import com.aimoneytracker.ui.addtransaction.AddTransactionScreen
import com.aimoneytracker.ui.analytics.AnalyticsScreen
import com.aimoneytracker.ui.budgets.BudgetsScreen
import com.aimoneytracker.ui.chat.ChatScreen
import com.aimoneytracker.ui.dashboard.DashboardScreen
import com.aimoneytracker.ui.digest.DigestDetailScreen
import com.aimoneytracker.ui.digest.DigestListScreen
import com.aimoneytracker.ui.forecast.ForecastScreen
import com.aimoneytracker.ui.goals.GoalsScreen
import com.aimoneytracker.ui.more.MoreScreen
import com.aimoneytracker.ui.onboarding.PastAnalyzerScreen
import com.aimoneytracker.ui.people.PeopleScreen
import com.aimoneytracker.ui.people.PersonDetailScreen
import com.aimoneytracker.ui.review.ReviewScreen
import com.aimoneytracker.ui.rules.RulesScreen
import com.aimoneytracker.ui.settings.SettingsScreen
import com.aimoneytracker.ui.splits.SplitsScreen
import com.aimoneytracker.ui.subscriptions.SubscriptionsScreen
import com.aimoneytracker.ui.transactiondetail.TransactionDetailScreen
import com.aimoneytracker.ui.transactions.TransactionsScreen

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.DASHBOARD, "Home", Icons.Filled.Dashboard),
    TabItem(Routes.TRANSACTIONS, "Activity", Icons.AutoMirrored.Filled.ListAlt),
    TabItem(Routes.FORECAST, "Forecast", Icons.Filled.TrendingUp),
    TabItem(Routes.PEOPLE, "People", Icons.Filled.Group),
    TabItem(Routes.MORE, "More", Icons.Filled.MoreHoriz),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(navController: NavHostController, startDeepLink: String?) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = tabs.any { it.route == currentRoute }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(currentRoute)) },
                navigationIcon = {
                    if (!isTopLevel && currentRoute != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.DASHBOARD || currentRoute == Routes.TRANSACTIONS) {
                FloatingActionButton(onClick = { navController.navigate(Routes.ADD_TRANSACTION) }) {
                    Icon(Icons.Filled.Add, "Add")
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onTransactionClick = { navController.navigate(Routes.transactionDetail(it)) },
                    onReviewClick = { navController.navigate(Routes.REVIEW) },
                    onForecastClick = { navController.navigate(Routes.FORECAST) },
                    onSeeAll = { navController.navigate(Routes.TRANSACTIONS) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(onTransactionClick = { navController.navigate(Routes.transactionDetail(it)) })
            }
            composable(Routes.FORECAST) { ForecastScreen() }
            composable(Routes.PEOPLE) {
                PeopleScreen(onPersonClick = { navController.navigate(Routes.personDetail(it)) })
            }
            composable(Routes.MORE) { MoreScreen(onNavigate = { navController.navigate(it) }) }

            composable(Routes.ADD_TRANSACTION) { AddTransactionScreen(onDone = { navController.popBackStack() }) }
            composable(Routes.REVIEW) { ReviewScreen() }
            composable(
                "${Routes.REVIEW}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { ReviewScreen() }

            composable(
                "${Routes.TRANSACTION_DETAIL}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { TransactionDetailScreen(onDeleted = { navController.popBackStack() }) }

            composable(
                "${Routes.PERSON_DETAIL}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { PersonDetailScreen(onTransactionClick = { navController.navigate(Routes.transactionDetail(it)) }) }

            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.GOALS) { GoalsScreen() }
            composable(Routes.SUBSCRIPTIONS) { SubscriptionsScreen() }
            composable(Routes.SPLITS) { SplitsScreen() }
            composable(Routes.ANALYTICS) { AnalyticsScreen() }
            composable(Routes.ACCOUNTS) { AccountsScreen() }
            composable(Routes.CHAT) { ChatScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.RULES) { RulesScreen() }
            composable(Routes.PAST_ANALYZER) {
                PastAnalyzerScreen(onComplete = { navController.popBackStack() })
            }
            composable(Routes.DIGESTS) {
                DigestListScreen(onDigestClick = { navController.navigate(Routes.digestDetail(it)) })
            }
            composable(
                "${Routes.DIGEST_DETAIL}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { DigestDetailScreen() }
        }
    }

    // Handle a notification deep link once.
    androidx.compose.runtime.LaunchedEffect(startDeepLink) {
        startDeepLink?.let { link -> runCatching { navController.navigate(link) } }
    }
}

private fun titleFor(route: String?): String = when (route?.substringBefore("/")) {
    Routes.DASHBOARD -> "AI Money Tracker"
    Routes.TRANSACTIONS -> "Transactions"
    Routes.FORECAST -> "Forecast"
    Routes.PEOPLE -> "People"
    Routes.MORE -> "More"
    Routes.REVIEW -> "What was this?"
    Routes.TRANSACTION_DETAIL -> "Transaction"
    Routes.PERSON_DETAIL -> "Person"
    Routes.BUDGETS -> "Budgets"
    Routes.GOALS -> "Goals"
    Routes.SUBSCRIPTIONS -> "Subscriptions"
    Routes.SPLITS -> "Split expenses"
    Routes.ANALYTICS -> "Analytics"
    Routes.ACCOUNTS -> "Accounts"
    Routes.CHAT -> "Assistant"
    Routes.SETTINGS -> "Settings"
    Routes.RULES -> "Rules"
    Routes.DIGESTS -> "Digests"
    Routes.DIGEST_DETAIL -> "Digest"
    Routes.PAST_ANALYZER -> "Analyze past SMS"
    Routes.ADD_TRANSACTION -> "Add transaction"
    else -> "AI Money Tracker"
}
