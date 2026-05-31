package com.aimoneytracker.domain.categorize

import com.aimoneytracker.data.local.entity.MerchantMapEntity
import com.aimoneytracker.data.local.entity.RuleEntity
import com.aimoneytracker.domain.model.CategorizationSource
import com.aimoneytracker.domain.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

data class CategorizationInput(
    val merchantNormalized: String,
    val merchantKey: String,
    val rawMessage: String?,
    val vpa: String?,
    val type: TransactionType,
    val note: String? = null,
)

data class CategorizationResult(
    val category: String,
    val subcategory: String? = null,
    val source: CategorizationSource,
    val confidence: Double,
    val assignPersonId: Long? = null,
    val ignore: Boolean = false,
    val needsAi: Boolean = false,
)

/**
 * The non-AI part of the categorization pipeline (§6): rule-based → merchant-map → keyword
 * heuristics. If nothing matches with confidence, [CategorizationResult.needsAi] is set so the
 * processor can optionally ask the LLM (unless Local-only mode is on). User corrections feed back
 * as [RuleEntity]s, which take top priority here.
 */
@Singleton
class CategorizationEngine @Inject constructor() {

    fun categorize(
        input: CategorizationInput,
        rules: List<RuleEntity>,
        merchantEntry: MerchantMapEntity?,
    ): CategorizationResult {
        // 1) Rules (highest priority/strength first). Created from user corrections & hand-written.
        matchRule(input, rules)?.let { return it }

        // 2) Learned merchant dictionary (handles + merchants the user has tagged before).
        if (merchantEntry != null) {
            return CategorizationResult(
                category = merchantEntry.category,
                subcategory = merchantEntry.subcategory,
                source = CategorizationSource.MERCHANT_MAP,
                confidence = 0.95,
            )
        }

        // 3) Built-in keyword heuristics for common merchants/contexts.
        keywordGuess(input)?.let { return it }

        // 4) Income types are easy to bucket even when the source is unknown.
        if (input.type == TransactionType.CREDIT) {
            return CategorizationResult(
                category = CategoryCatalog.OTHER,
                source = CategorizationSource.DEFAULT,
                confidence = 0.3,
                needsAi = true,
            )
        }

        // Nothing matched → uncategorized, flag for AI / user review.
        return CategorizationResult(
            category = CategoryCatalog.UNCATEGORIZED,
            source = CategorizationSource.DEFAULT,
            confidence = 0.0,
            needsAi = true,
        )
    }

    private fun matchRule(input: CategorizationInput, rules: List<RuleEntity>): CategorizationResult? {
        for (rule in rules.sortedWith(compareByDescending<RuleEntity> { it.priority }.thenByDescending { it.strength })) {
            val haystack = when (rule.matchField) {
                "MERCHANT" -> input.merchantNormalized
                "RAW" -> input.rawMessage ?: ""
                "HANDLE" -> input.vpa ?: ""
                "NOTE" -> input.note ?: ""
                else -> input.merchantNormalized
            }.lowercase()
            val needle = rule.matchValue.lowercase()
            val matched = when (rule.matchType) {
                "EQUALS" -> haystack == needle
                "REGEX" -> runCatching { Regex(rule.matchValue, RegexOption.IGNORE_CASE).containsMatchIn(haystack) }.getOrDefault(false)
                "HANDLE" -> input.vpa?.lowercase() == needle
                else -> haystack.contains(needle) // CONTAINS
            }
            if (matched) {
                return CategorizationResult(
                    category = rule.assignCategory ?: CategoryCatalog.OTHER,
                    subcategory = rule.assignSubcategory,
                    source = CategorizationSource.RULE,
                    confidence = 0.97,
                    assignPersonId = rule.assignPersonId,
                    ignore = rule.markIgnore,
                )
            }
        }
        return null
    }

    // Keyword → category. Ordered; first hit wins. Confidence is moderate (heuristic, not learned).
    private val keywordMap: List<Pair<List<String>, String>> = listOf(
        listOf("swiggy", "zomato", "restaurant", "cafe", "dominos", "kfc", "mcdonald", "pizza", "eat", "food") to CategoryCatalog.DINING,
        listOf("bigbasket", "grofers", "blinkit", "zepto", "dmart", "grocery", "supermarket", "kirana", "more retail") to CategoryCatalog.GROCERIES,
        listOf("uber", "ola", "rapido", "metro", "irctc", "redbus", "cab", "auto", "bus", "train") to CategoryCatalog.TRANSPORT,
        listOf("petrol", "fuel", "indian oil", "hpcl", "bharat petroleum", "shell", "gas station") to CategoryCatalog.FUEL,
        listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "shopping", "mall", "store", "croma", "reliance digital") to CategoryCatalog.SHOPPING,
        listOf("netflix", "spotify", "hotstar", "prime", "youtube", "subscription", "membership") to CategoryCatalog.SUBSCRIPTIONS,
        listOf("electricity", "water bill", "gas bill", "utility", "bescom", "broadband", "wifi") to CategoryCatalog.UTILITIES,
        listOf("recharge", "airtel", "jio", "vodafone", "vi ", "bsnl", "mobile") to CategoryCatalog.MOBILE_RECHARGE,
        listOf("hospital", "pharmacy", "apollo", "pharmeasy", "1mg", "clinic", "medical", "doctor", "lab") to CategoryCatalog.HEALTHCARE,
        listOf("school", "college", "tuition", "course", "udemy", "coursera", "exam", "university") to CategoryCatalog.EDUCATION,
        listOf("flight", "hotel", "makemytrip", "goibibo", "oyo", "airbnb", "travel", "trip") to CategoryCatalog.TRAVEL,
        listOf("movie", "bookmyshow", "pvr", "inox", "game", "entertainment") to CategoryCatalog.ENTERTAINMENT,
        listOf("zerodha", "groww", "upstox", "mutual fund", "sip", "invest", "stock", "demat") to CategoryCatalog.INVESTMENTS,
        listOf("insurance", "lic", "policy", "premium") to CategoryCatalog.INSURANCE,
        listOf("rent", "landlord") to CategoryCatalog.RENT,
        listOf("emi", "loan", "finance ltd") to CategoryCatalog.EMI,
        listOf("salary", "payroll", "stipend") to CategoryCatalog.SALARY,
        listOf("atm", "cash withdrawal") to CategoryCatalog.CASH,
    )

    private fun keywordGuess(input: CategorizationInput): CategorizationResult? {
        val hay = (input.merchantNormalized + " " + (input.rawMessage ?: "")).lowercase()
        for ((keywords, category) in keywordMap) {
            if (keywords.any { hay.contains(it) }) {
                val isIncomeCat = CategoryCatalog.isIncome(category)
                // Don't assign an income category to a debit, or vice-versa.
                if (isIncomeCat && input.type != TransactionType.CREDIT) continue
                return CategorizationResult(
                    category = category,
                    source = CategorizationSource.MERCHANT_MAP,
                    confidence = 0.7,
                )
            }
        }
        return null
    }
}
