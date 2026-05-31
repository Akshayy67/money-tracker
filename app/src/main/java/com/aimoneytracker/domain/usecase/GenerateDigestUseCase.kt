package com.aimoneytracker.domain.usecase

import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.DigestRecordEntity
import com.aimoneytracker.data.repository.AnalyticsRepository
import com.aimoneytracker.data.repository.BudgetRepository
import com.aimoneytracker.data.repository.DigestRepository
import com.aimoneytracker.data.repository.GoalRepository
import com.aimoneytracker.data.repository.PersonRepository
import com.aimoneytracker.data.repository.SubscriptionRepository
import com.aimoneytracker.domain.ai.AiService
import com.aimoneytracker.domain.digest.DigestContent
import com.aimoneytracker.domain.digest.DigestEngine
import com.aimoneytracker.domain.digest.DigestInput
import com.aimoneytracker.domain.digest.GoalProgress
import com.aimoneytracker.domain.digest.NamedAmount
import com.aimoneytracker.domain.digest.UpcomingBill
import com.aimoneytracker.domain.model.DigestType
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil
import com.aimoneytracker.util.Money
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Builds a digest (§16): gathers deterministic figures, runs [DigestEngine], optionally adds an LLM
 * one-liner, and stores a [DigestRecordEntity]. Returns the content and the saved record id.
 */
class GenerateDigestUseCase @Inject constructor(
    private val txnDao: TransactionDao,
    private val analytics: AnalyticsRepository,
    private val personRepository: PersonRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val budgetRepository: BudgetRepository,
    private val goalRepository: GoalRepository,
    private val digestRepository: DigestRepository,
    private val aiService: AiService,
    private val engine: DigestEngine,
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class Output(val recordId: Long, val content: DigestContent)

    suspend operator fun invoke(type: DigestType, includeAiSummary: Boolean = true): Output {
        val now = DateUtil.now()
        val (start, end, prevStart, prevEnd) = bounds(type, now)

        val summary = analytics.summary(start, end)
        val prevSummary = analytics.summary(prevStart, prevEnd)

        val categories = analytics.categoryBreakdown(start, end)
        val prevCategories = analytics.categoryBreakdown(prevStart, prevEnd)
        val topCategoriesNamed = categories.map { NamedAmount(it.category, it.total, it.count) }
        val prevCategoryMap = prevCategories.associate { it.category to it.total }

        val topMerchant = analytics.topMerchants(start, end, 1).firstOrNull()
            ?.let { NamedAmount(it.merchantNormalized, it.total, it.count) }

        // Top person (by total transacted) with name resolved.
        val personSums = txnDao.sumByPerson(start, end)
        val topPerson = personSums.maxByOrNull { it.total }?.let { ps ->
            val name = ps.relatedPersonId?.let { personRepository.getById(it)?.name } ?: "Someone"
            NamedAmount(name, ps.total, ps.count)
        }

        val biggest = txnDao.topExpenses(start, end, 1).firstOrNull()
            ?.let { NamedAmount(it.merchantNormalized, it.amount) }

        val count = txnDao.countInRange(start, end)

        val budgetStatus = budgetRepository.statuses(now).let { statuses ->
            val exceeded = statuses.count { it.exceeded }
            val nearing = statuses.count { it.nearingLimit }
            when {
                statuses.isEmpty() -> null
                exceeded > 0 -> "$exceeded budget${if (exceeded > 1) "s" else ""} exceeded"
                nearing > 0 -> "$nearing budget${if (nearing > 1) "s" else ""} nearing the limit"
                else -> "All budgets on track"
            }
        }

        // Upcoming bills / subscriptions in the look-ahead window.
        val lookaheadEnd = if (type == DigestType.WEEKLY) DateUtil.daysAgo(-7, now) else DateUtil.endOfMonth(now)
        val upcoming = subscriptionRepository.dueBetween(now, lookaheadEnd).mapNotNull { sub ->
            sub.nextDueDate?.let { UpcomingBill(sub.name, sub.amount, it) }
        }

        val goals = goalRepository.getAll().filter { !it.isAchieved }
            .map { GoalProgress(it.name, it.savedAmount, it.targetAmount) }

        val avgSpend = trailingAverageSpend(type, now)

        val input = DigestInput(
            type = type,
            periodStart = start,
            periodEnd = end,
            totalSpent = summary.expense,
            totalIncome = summary.income,
            previousTotalSpent = prevSummary.expense,
            transactionCount = count,
            topCategories = topCategoriesNamed,
            previousCategoryTotals = prevCategoryMap,
            currentCategoryTotals = topCategoriesNamed,
            topMerchant = topMerchant,
            topPerson = topPerson,
            biggestExpense = biggest,
            budgetStatusText = budgetStatus,
            upcomingBills = upcoming,
            subscriptionsRenewing = upcoming,
            netWorthChange = 0,
            goalProgress = goals,
            averagePeriodSpend = avgSpend,
        )

        var content = engine.build(input)

        // Optional friendly natural-language summary from the deterministic numbers.
        var nl: String? = null
        if (includeAiSummary && aiService.isAvailable()) {
            nl = aiService.summarizeDigest(
                buildList {
                    add("Period: ${DateUtil.format(start)} - ${DateUtil.format(end)}")
                    add("Total spent: ${Money.format(content.totalSpent)} (${content.spendPctChange.toInt()}% vs previous)")
                    add("Income: ${Money.format(content.totalIncome)}, net: ${Money.format(content.netSavings)}")
                    content.topCategories.forEach { add("Top category ${it.name}: ${Money.format(it.amount)}") }
                    content.topMerchant?.let { add("Top merchant: ${it.name} ${Money.format(it.amount)}") }
                }
            )
        }

        val recordId = digestRepository.save(
            DigestRecordEntity(
                type = type,
                periodStart = start,
                periodEnd = end,
                createdAt = now,
                headline = content.headline,
                bodyJson = json.encodeToString(DigestContent.serializer(), content),
                naturalLanguageSummary = nl,
            )
        )
        return Output(recordId, content)
    }

    private data class Bounds(val start: Long, val end: Long, val prevStart: Long, val prevEnd: Long)

    private fun bounds(type: DigestType, now: Long): Bounds = if (type == DigestType.WEEKLY) {
        val start = DateUtil.startOfWeek(now)
        val end = DateUtil.endOfWeek(now)
        val prevStart = DateUtil.daysAgo(7, start)
        Bounds(start, end, prevStart, start - 1)
    } else {
        val start = DateUtil.startOfMonth(now)
        val end = DateUtil.endOfMonth(now)
        val prevRef = DateUtil.monthsAgo(1, now)
        Bounds(start, end, DateUtil.startOfMonth(prevRef), DateUtil.endOfMonth(prevRef))
    }

    private suspend fun trailingAverageSpend(type: DigestType, now: Long): Long {
        return if (type == DigestType.WEEKLY) {
            val start = DateUtil.daysAgo(56, now) // ~8 weeks
            val total = txnDao.sumByType(TransactionType.DEBIT.name, start, now)
            total / 8
        } else {
            val start = DateUtil.monthsAgo(6, now)
            val total = txnDao.sumByType(TransactionType.DEBIT.name, start, now)
            total / 6
        }
    }
}
