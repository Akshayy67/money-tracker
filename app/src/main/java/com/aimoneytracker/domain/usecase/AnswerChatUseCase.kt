package com.aimoneytracker.domain.usecase

import com.aimoneytracker.data.repository.AccountRepository
import com.aimoneytracker.data.repository.AnalyticsRepository
import com.aimoneytracker.data.repository.PersonRepository
import com.aimoneytracker.data.repository.SplitRepository
import com.aimoneytracker.data.repository.SubscriptionRepository
import com.aimoneytracker.data.repository.TransactionFilter
import com.aimoneytracker.data.repository.TransactionRepository
import com.aimoneytracker.domain.ai.AiService
import com.aimoneytracker.domain.ai.ChatTurn
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money
import javax.inject.Inject

/**
 * The AI assistant (§22). Intent is detected and ALL figures are computed deterministically from the
 * DB; the LLM is then asked only to phrase the answer over those exact numbers (and never to compute).
 * Works fully offline — without AI it returns the deterministic answer directly.
 *
 * The returned [ChatAnswer.filter] deep-links the UI to the matching filtered transaction view.
 */
class AnswerChatUseCase @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val txnRepository: TransactionRepository,
    private val personRepository: PersonRepository,
    private val splitRepository: SplitRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountRepository: AccountRepository,
    private val aiService: AiService,
) {
    data class ChatAnswer(val text: String, val data: String, val filter: TransactionFilter?)

    suspend operator fun invoke(question: String, history: List<ChatTurn> = emptyList()): ChatAnswer {
        val q = question.lowercase()
        val now = DateUtil.now()
        val (start, end, periodLabel) = parsePeriod(q, now)

        // ---- Deterministic intent routing ----
        // Only treat a question as a "spend" query when it actually looks like one; otherwise we'd
        // answer EVERY unrecognized question with "You spent ₹X this month". Unmatched questions go
        // to a richer financial-overview answer that the LLM can phrase against real numbers.
        val deterministic: ChatAnswer = when {
            "owe" in q && ("me" in q || "who" in q) -> answerWhoOwes()
            "subscription" in q || "cancel" in q -> answerSubscriptions()
            "balance" in q || "how much money" in q -> answerBalance()
            ("save" in q || "saved" in q) && "year" in q -> answerSavings(DateUtil.startOfYear(now), now, "this year")
            ("save" in q || "saved" in q || "savings" in q) -> answerSavings(start, end, periodLabel)
            "income" in q || "earn" in q || "salary" in q -> answerIncome(start, end, periodLabel)
            "most" in q && ("friend" in q || "person" in q || "with" in q) -> answerTopPerson(start, end)
            "with" in q && extractPersonName(q) != null -> answerSpendWithPerson(extractPersonName(q)!!, start, end)
            isSpendQuestion(q) -> answerSpend(q, start, end, periodLabel)
            else -> answerOverview(start, end, periodLabel, question)
        }

        // ---- Optional AI phrasing over the deterministic data ----
        val phrased = aiService.answerChat(question, deterministic.data, history)
        return deterministic.copy(text = phrased ?: deterministic.text)
    }

    /** Heuristic: does the question actually ask about spending/expenses? */
    private fun isSpendQuestion(q: String): Boolean {
        val spendWords = listOf("spent", "spend", "spending", "expense", "expenses", "cost", "paid", "pay")
        return spendWords.any { it in q } || matchCategory(q) != null || extractAmount(q) != null
    }

    /**
     * Fallback for questions we don't have a specific handler for. We hand the LLM a compact financial
     * snapshot (income, expense, net, top categories) so it can answer over REAL numbers instead of
     * defaulting to a misleading "you spent X this month". With AI off, we return the snapshot directly.
     */
    private suspend fun answerOverview(start: Long, end: Long, period: String, question: String): ChatAnswer {
        val s = analytics.summary(start, end)
        val cats = analytics.categoryBreakdown(start, end).take(5)
        val catText = cats.joinToString(", ") { "${prettyCat(it.category)} ${Money.format(it.total)}" }
        val text = buildString {
            append("Here's your $period snapshot — income ${Money.format(s.income)}, ")
            append("expenses ${Money.format(s.expense)}, net ${Money.format(s.net)}.")
            if (catText.isNotBlank()) append(" Top categories: $catText.")
        }
        val data = buildString {
            append("metric=overview; period=$period; ")
            append("income_minor=${s.income}; expense_minor=${s.expense}; net_minor=${s.net}; ")
            append("top_categories=[")
            append(cats.joinToString("; ") { "${it.category}:${it.total}" })
            append("]; note=If the question can't be answered from these figures, say so briefly.")
        }
        return ChatAnswer(text, data, TransactionFilter(startDate = start, endDate = end))
    }

    private suspend fun answerIncome(start: Long, end: Long, period: String): ChatAnswer {
        val s = analytics.summary(start, end)
        val text = "Your income $period was ${Money.format(s.income)}."
        val data = "metric=income; period=$period; income_minor=${s.income}"
        return ChatAnswer(text, data, TransactionFilter(startDate = start, endDate = end, type = TransactionType.CREDIT))
    }

    private suspend fun answerSpend(q: String, start: Long, end: Long, period: String): ChatAnswer {
        val categoryKey = matchCategory(q)
        val minAmount = extractAmount(q)
        val weekendOnly = "weekend" in q

        var filter = TransactionFilter(
            startDate = start, endDate = end, type = TransactionType.DEBIT,
            categories = categoryKey?.let { listOf(it) } ?: emptyList(),
            amountMin = minAmount,
        )

        val txns = txnRepository.query(filter).let {
            if (weekendOnly) it.filter { t -> DateUtil.isWeekend(t.dateTime) } else it
        }
        val total = txns.sumOf { it.amount }
        val catWord = categoryKey?.let { " on ${prettyCat(it)}" } ?: ""
        val amtWord = minAmount?.let { " above ${Money.format(it)}" } ?: ""
        val weekendWord = if (weekendOnly) " on weekends" else ""
        val text = "You spent ${Money.format(total)}$catWord$amtWord$weekendWord $period (${txns.size} transactions)."
        val data = "metric=spend; period=$period; total_minor=$total; count=${txns.size};" +
            " category=${categoryKey ?: "all"}; min_amount_minor=${minAmount ?: 0}; weekend_only=$weekendOnly"
        return ChatAnswer(text, data, filter)
    }

    private suspend fun answerSpendWithPerson(name: String, start: Long, end: Long): ChatAnswer {
        val person = personRepository.getAll().firstOrNull { it.name.contains(name, ignoreCase = true) }
            ?: return ChatAnswer("I couldn't find anyone called \"$name\".", "metric=person; found=false", null)
        val bal = personRepository.balanceWith(person.id)
        val filter = TransactionFilter(personId = person.id, startDate = start, endDate = end)
        val text = "With ${person.name}: you sent ${Money.format(bal.sent)} and received ${Money.format(bal.received)} " +
            "(net ${Money.format(bal.sent - bal.received)})."
        val data = "metric=person_net; person=${person.name}; sent_minor=${bal.sent}; received_minor=${bal.received}"
        return ChatAnswer(text, data, filter)
    }

    private suspend fun answerTopPerson(start: Long, end: Long): ChatAnswer {
        val people = personRepository.getAll()
        val ranked = people.map { it to personRepository.balanceWith(it.id) }
            .sortedByDescending { it.second.sent }
        val top = ranked.firstOrNull()
            ?: return ChatAnswer("No people tracked yet.", "metric=top_person; found=false", null)
        val text = "You spend the most with ${top.first.name}: ${Money.format(top.second.sent)} sent."
        val data = "metric=top_person; name=${top.first.name}; sent_minor=${top.second.sent}"
        return ChatAnswer(text, data, TransactionFilter(personId = top.first.id))
    }

    private suspend fun answerWhoOwes(): ChatAnswer {
        val owing = splitRepository.outstandingByPerson()
        if (owing.isEmpty()) return ChatAnswer("Nobody owes you right now — all settled up!", "metric=owes; count=0", null)
        // Resolve names first (getById is suspend; can't call it inside the non-suspend joinToString lambda).
        val topOwing = owing.entries.sortedByDescending { it.value }.take(10)
        val resolved = topOwing.map { (pid, amt) -> (personRepository.getById(pid)?.name ?: "Person $pid") to amt }
        val lines = resolved.joinToString("\n") { (name, amt) -> "• $name owes ${Money.format(amt)}" }
        val total = owing.values.sum()
        return ChatAnswer("You're owed ${Money.format(total)} in total:\n$lines",
            "metric=owes; total_minor=$total; people=${owing.size}", null)
    }

    private suspend fun answerSubscriptions(): ChatAnswer {
        val subs = subscriptionRepository.getActive()
        if (subs.isEmpty()) return ChatAnswer("No subscriptions detected.", "metric=subscriptions; count=0", null)
        val monthly = subs.sumOf { it.amount }
        val list = subs.sortedByDescending { it.amount }.take(10)
            .joinToString("\n") { "• ${it.name}: ${Money.format(it.amount)}/${it.cycle.name.lowercase()}" }
        val text = "You have ${subs.size} subscriptions (~${Money.format(monthly)}/cycle):\n$list"
        return ChatAnswer(text, "metric=subscriptions; count=${subs.size}; total_minor=$monthly", null)
    }

    private suspend fun answerBalance(): ChatAnswer {
        val balance = accountRepository.totalTrackedBalance()
        return ChatAnswer("Your total balance across tracked accounts is ${Money.format(balance)}.",
            "metric=balance; total_minor=$balance", null)
    }

    private suspend fun answerSavings(start: Long, end: Long, period: String): ChatAnswer {
        val s = analytics.summary(start, end)
        val verb = if (s.net >= 0) "saved" else "overspent by"
        val text = "You $verb ${Money.format(kotlin.math.abs(s.net))} $period " +
            "(income ${Money.format(s.income)}, expenses ${Money.format(s.expense)})."
        val data = "metric=savings; period=$period; income_minor=${s.income}; expense_minor=${s.expense}; net_minor=${s.net}"
        return ChatAnswer(text, data, null)
    }

    // ---- lightweight NLU helpers ----
    private fun parsePeriod(q: String, now: Long): Triple<Long, Long, String> = when {
        "last month" in q -> {
            val ref = DateUtil.monthsAgo(1, now)
            Triple(DateUtil.startOfMonth(ref), DateUtil.endOfMonth(ref), "last month")
        }
        "this week" in q || "week" in q -> Triple(DateUtil.startOfWeek(now), DateUtil.endOfWeek(now), "this week")
        "this year" in q || "year" in q -> Triple(DateUtil.startOfYear(now), now, "this year")
        "today" in q -> Triple(DateUtil.startOfDay(DateUtil.toLocalDate(now)), now, "today")
        else -> Triple(DateUtil.startOfMonth(now), DateUtil.endOfMonth(now), "this month")
    }

    private fun matchCategory(q: String): String? {
        val map = mapOf(
            "food" to CategoryCatalog.FOOD, "dining" to CategoryCatalog.DINING,
            "grocer" to CategoryCatalog.GROCERIES, "transport" to CategoryCatalog.TRANSPORT,
            "fuel" to CategoryCatalog.FUEL, "shop" to CategoryCatalog.SHOPPING,
            "rent" to CategoryCatalog.RENT, "entertainment" to CategoryCatalog.ENTERTAINMENT,
            "travel" to CategoryCatalog.TRAVEL, "health" to CategoryCatalog.HEALTHCARE,
            "subscription" to CategoryCatalog.SUBSCRIPTIONS, "invest" to CategoryCatalog.INVESTMENTS,
        )
        return map.entries.firstOrNull { q.contains(it.key) }?.value
    }

    private fun prettyCat(key: String) = key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun extractAmount(q: String): Long? {
        val m = Regex("""(?:above|over|more than|>)\s*(?:rs\.?|inr|₹)?\s*([0-9,]+)""").find(q) ?: return null
        return Money.parseToMinor(m.groupValues[1])
    }

    private fun extractPersonName(q: String): String? {
        val m = Regex("""with\s+([a-z]+)""").find(q) ?: return null
        val name = m.groupValues[1]
        return if (name in setOf("the", "my", "a", "weekends", "cash", "card")) null else name
    }
}
