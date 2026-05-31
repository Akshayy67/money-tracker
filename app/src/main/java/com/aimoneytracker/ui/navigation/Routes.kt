package com.aimoneytracker.ui.navigation

/** Central route table. Detail routes take a trailing id argument. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val FORECAST = "forecast"
    const val PEOPLE = "people"
    const val MORE = "more"

    const val TRANSACTION_DETAIL = "transaction" // transaction/{id}
    const val REVIEW = "review"                   // review/{id}
    const val PERSON_DETAIL = "person"            // person/{id}
    const val ADD_TRANSACTION = "add_transaction"
    const val SEARCH = "search"
    const val ACCOUNTS = "accounts"
    const val BUDGETS = "budgets"
    const val GOALS = "goals"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SPLITS = "splits"
    const val ANALYTICS = "analytics"
    const val DIGESTS = "digests"
    const val DIGEST_DETAIL = "digest"            // digest/{id}
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val RULES = "rules"
    const val PAST_ANALYZER = "past_analyzer"

    fun transactionDetail(id: Long) = "$TRANSACTION_DETAIL/$id"
    fun review(id: Long) = "$REVIEW/$id"
    fun personDetail(id: Long) = "$PERSON_DETAIL/$id"
    fun digestDetail(id: Long) = "$DIGEST_DETAIL/$id"
}
