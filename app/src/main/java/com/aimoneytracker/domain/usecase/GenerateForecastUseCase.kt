package com.aimoneytracker.domain.usecase

import com.aimoneytracker.data.local.dao.TransactionDao
import com.aimoneytracker.data.local.entity.ForecastSnapshotEntity
import com.aimoneytracker.data.preferences.SettingsRepository
import com.aimoneytracker.data.repository.AccountRepository
import com.aimoneytracker.data.repository.ForecastSnapshotRepository
import com.aimoneytracker.data.repository.GoalRepository
import com.aimoneytracker.data.repository.IncomeRepository
import com.aimoneytracker.data.repository.SubscriptionRepository
import com.aimoneytracker.domain.categorize.CategoryCatalog
import com.aimoneytracker.domain.forecast.DaySpend
import com.aimoneytracker.domain.forecast.ForecastEngine
import com.aimoneytracker.domain.forecast.ForecastInput
import com.aimoneytracker.domain.forecast.ForecastResult
import com.aimoneytracker.domain.forecast.ForecastCalibrator
import com.aimoneytracker.domain.forecast.ScheduledEvent
import com.aimoneytracker.domain.model.TransactionType
import com.aimoneytracker.util.DateUtil
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Assembles a [ForecastInput] from the database and runs the deterministic [ForecastEngine] (§15).
 * Optionally persists a [ForecastSnapshotEntity] for self-calibration. No LLM is involved.
 */
class GenerateForecastUseCase @Inject constructor(
    private val txnDao: TransactionDao,
    private val accountRepository: AccountRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val incomeRepository: IncomeRepository,
    private val goalRepository: GoalRepository,
    private val snapshotRepository: ForecastSnapshotRepository,
    private val settings: SettingsRepository,
    private val engine: ForecastEngine,
    private val calibrator: ForecastCalibrator,
) {
    suspend operator fun invoke(persist: Boolean = false): ForecastResult {
        val now = DateUtil.now()
        val today = DateUtil.toLocalDate(now)
        val monthStartMs = DateUtil.startOfMonth(now)
        val monthEndMs = DateUtil.endOfMonth(now)
        val monthStart = DateUtil.toLocalDate(monthStartMs)
        val monthEnd = DateUtil.toLocalDate(monthEndMs)

        val currentBalance = accountRepository.totalTrackedBalance()
        val prefs = settings.settings.first()

        // ---- MTD discretionary spend (excludes committed/fixed categories) ----
        val monthDebits = txnDao.getInRange(monthStartMs, now)
            .filter { it.type == TransactionType.DEBIT && !CategoryCatalog.isCommitted(it.category) }
        val mtdDiscretionary = monthDebits.sumOf { it.amount }

        // ---- Trailing daily discretionary spend (last ~4 months) for the variable model ----
        val histStart = DateUtil.monthsAgo(4, now)
        val trailing = txnDao.expensesForHistory(histStart, monthStartMs - 1)
            .filter { !CategoryCatalog.isCommitted(it.category) }
            .groupBy { DateUtil.toLocalDate(it.dateTime) }
            .map { (date, txns) -> DaySpend(date, txns.sumOf { it.amount }) }

        // ---- Scheduled future outflows: subscriptions/EMIs/rent due before month end ----
        val subs = subscriptionRepository.dueBetween(now, monthEndMs)
        val scheduledOutflows = subs.mapNotNull { sub ->
            sub.nextDueDate?.let {
                ScheduledEvent(sub.name, sub.amount, DateUtil.toLocalDate(it), isInflow = false, categoryKey = sub.categoryKey)
            }
        }

        // ---- Scheduled inflows: recurring salary/income expected in the window ----
        val scheduledInflows = incomeRepository.getActive().mapNotNull { src ->
            val day = src.typicalDayOfMonth ?: return@mapNotNull null
            val due = DateUtil.toLocalDate(DateUtil.nextDayOfMonth(day, now))
            if (!due.isBefore(today) && !due.isAfter(monthEnd))
                ScheduledEvent(src.name, src.typicalAmount, due, isInflow = true)
            else null
        }

        val calibration = calibrator.calibrate(snapshotRepository.recentCalibrated(12))

        val input = ForecastInput(
            today = today,
            monthStart = monthStart,
            monthEnd = monthEnd,
            currentBalance = currentBalance,
            mtdDiscretionarySpend = mtdDiscretionary,
            trailingDailySpend = trailing,
            scheduledEvents = scheduledOutflows + scheduledInflows,
            knownOneOffs = emptyList(),
            lowBalanceThreshold = prefs.lowBalanceThreshold,
            goalReserve = goalRepository.monthlyGoalReserve(),
            monthlyBudget = null,
            calibration = calibration,
        )

        val result = engine.forecast(input)

        if (persist) {
            snapshotRepository.save(
                ForecastSnapshotEntity(
                    createdAt = now,
                    forecastForMonth = DateUtil.monthKey(now),
                    currentBalance = result.currentBalance,
                    projectedEndBalanceExpected = result.projectedEndBalanceExpected,
                    projectedEndBalanceLow = result.projectedEndBalanceLow,
                    projectedEndBalanceHigh = result.projectedEndBalanceHigh,
                    projectedSpendExpected = result.projectedVariableSpend,
                    scheduledOutflows = result.scheduledOutflowTotal,
                    scheduledInflows = result.scheduledInflowTotal,
                    dailySafeToSpend = result.dailySafeToSpend,
                    runLowDate = result.runLowDate?.let { DateUtil.startOfDay(it) },
                    blendWeightMtd = result.mtdWeightUsed,
                )
            )
        }
        return result
    }

    /** Close out last month's open snapshots by filling in actuals (drives calibration, §15.9). */
    suspend fun calibratePastSnapshots() {
        val lastMonthRef = DateUtil.monthsAgo(1)
        val monthKey = DateUtil.monthKey(lastMonthRef)
        val open = snapshotRepository.openForMonth(monthKey)
        if (open.isEmpty()) return
        val start = DateUtil.startOfMonth(lastMonthRef)
        val end = DateUtil.endOfMonth(lastMonthRef)
        val actualSpend = txnDao.getInRange(start, end)
            .filter { it.type == TransactionType.DEBIT && !CategoryCatalog.isCommitted(it.category) }
            .sumOf { it.amount }
        val actualEndBalance = accountRepository.totalTrackedBalance()
        open.forEach { snap ->
            snapshotRepository.update(
                snap.copy(
                    actualSpend = actualSpend,
                    actualEndBalance = actualEndBalance,
                    errorAbs = kotlin.math.abs(snap.projectedSpendExpected - actualSpend),
                )
            )
        }
    }
}
