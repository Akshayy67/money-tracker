package com.aimoneytracker.domain.categorize

import com.aimoneytracker.data.local.entity.CategoryEntity

/**
 * The default category catalog (§6). Seeded into the categories table on first run. Keys are stable
 * identifiers used throughout the app; display names are localizable in the UI layer.
 */
object CategoryCatalog {

    const val FOOD = "food"
    const val GROCERIES = "groceries"
    const val DINING = "dining"
    const val TRANSPORT = "transport"
    const val FUEL = "fuel"
    const val SHOPPING = "shopping"
    const val RENT = "rent"
    const val UTILITIES = "utilities"
    const val MOBILE_RECHARGE = "mobile_recharge"
    const val INTERNET = "internet"
    const val HEALTHCARE = "healthcare"
    const val EDUCATION = "education"
    const val TRAVEL = "travel"
    const val ENTERTAINMENT = "entertainment"
    const val INVESTMENTS = "investments"
    const val INSURANCE = "insurance"
    const val TAXES = "taxes"
    const val SALARY = "salary"
    const val FREELANCE = "freelance"
    const val GIFTS = "gifts"
    const val DONATIONS = "donations"
    const val TRANSFERS = "transfers"
    const val LOAN_REPAYMENT = "loan_repayment"
    const val EMI = "emi"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SAVINGS = "savings"
    const val CASH = "cash"
    const val OTHER = "other"
    const val UNCATEGORIZED = "uncategorized"

    val defaults: List<CategoryEntity> = listOf(
        cat(FOOD, "Food & Dining", icon = "Restaurant", color = "#FF7043", order = 1),
        cat(GROCERIES, "Groceries", parent = FOOD, icon = "ShoppingCart", color = "#66BB6A", order = 2),
        cat(DINING, "Dining out", parent = FOOD, icon = "LocalDining", color = "#FF8A65", order = 3),
        cat(TRANSPORT, "Transport", icon = "DirectionsBus", color = "#42A5F5", order = 4),
        cat(FUEL, "Fuel", parent = TRANSPORT, icon = "LocalGasStation", color = "#5C6BC0", order = 5),
        cat(SHOPPING, "Shopping", icon = "ShoppingBag", color = "#AB47BC", order = 6),
        cat(RENT, "Rent", icon = "Home", color = "#8D6E63", order = 7),
        cat(UTILITIES, "Utilities", icon = "Bolt", color = "#FFA726", order = 8),
        cat(MOBILE_RECHARGE, "Mobile recharge", parent = UTILITIES, icon = "PhoneAndroid", color = "#26C6DA", order = 9),
        cat(INTERNET, "Internet", parent = UTILITIES, icon = "Wifi", color = "#29B6F6", order = 10),
        cat(HEALTHCARE, "Healthcare", icon = "LocalHospital", color = "#EF5350", order = 11),
        cat(EDUCATION, "Education", icon = "School", color = "#7E57C2", order = 12),
        cat(TRAVEL, "Travel", icon = "Flight", color = "#26A69A", order = 13),
        cat(ENTERTAINMENT, "Entertainment", icon = "Movie", color = "#EC407A", order = 14),
        cat(INVESTMENTS, "Investments", icon = "TrendingUp", color = "#43A047", order = 15),
        cat(INSURANCE, "Insurance", icon = "Shield", color = "#5E35B1", order = 16),
        cat(TAXES, "Taxes", icon = "AccountBalance", color = "#6D4C41", order = 17),
        cat(SALARY, "Salary", icon = "Payments", color = "#2E7D32", income = true, order = 18),
        cat(FREELANCE, "Freelance income", icon = "Work", color = "#388E3C", income = true, order = 19),
        cat(GIFTS, "Gifts", icon = "CardGiftcard", color = "#D81B60", order = 20),
        cat(DONATIONS, "Donations", icon = "VolunteerActivism", color = "#C2185B", order = 21),
        cat(TRANSFERS, "Transfers", icon = "SwapHoriz", color = "#90A4AE", transfer = true, order = 22),
        cat(LOAN_REPAYMENT, "Loan repayment", icon = "AccountBalanceWallet", color = "#795548", order = 23),
        cat(EMI, "EMI payments", icon = "CreditCard", color = "#6A1B9A", order = 24),
        cat(SUBSCRIPTIONS, "Subscriptions", icon = "Subscriptions", color = "#F4511E", order = 25),
        cat(SAVINGS, "Savings", icon = "Savings", color = "#00897B", order = 26),
        cat(CASH, "Cash", icon = "Money", color = "#9E9E9E", order = 27),
        cat(OTHER, "Other", icon = "Category", color = "#78909C", order = 98),
        cat(UNCATEGORIZED, "Uncategorized", icon = "HelpOutline", color = "#BDBDBD", order = 99),
    )

    private fun cat(
        key: String,
        name: String,
        parent: String? = null,
        icon: String,
        color: String,
        income: Boolean = false,
        transfer: Boolean = false,
        order: Int,
    ) = CategoryEntity(
        key = key,
        displayName = name,
        parentKey = parent,
        iconName = icon,
        colorHex = color,
        isIncome = income,
        isTransfer = transfer,
        isSystem = true,
        sortOrder = order,
    )

    fun isIncome(key: String): Boolean = defaults.firstOrNull { it.key == key }?.isIncome ?: false

    /**
     * "Committed" categories represent predictable, scheduled outflows. The forecast engine models
     * these via deterministic scheduled events and EXCLUDES them from the variable/discretionary
     * daily-rate model so a fixed bill doesn't inflate the everyday spend rate.
     */
    val committedCategories: Set<String> = setOf(
        RENT, EMI, LOAN_REPAYMENT, INSURANCE, SUBSCRIPTIONS, TAXES,
    )

    fun isCommitted(key: String): Boolean = key in committedCategories

    private val byKey by lazy { defaults.associateBy { it.key } }

    fun displayNameOf(key: String): String =
        byKey[key]?.displayName ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun colorHexOf(key: String): String = byKey[key]?.colorHex ?: "#78909C"
}
