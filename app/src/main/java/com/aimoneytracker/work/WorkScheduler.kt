package com.aimoneytracker.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aimoneytracker.data.preferences.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules all background jobs (§15.10 daily forecast, §16 digests, §12 recurring, §20 backup). */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val wm get() = WorkManager.getInstance(context)

    suspend fun scheduleAll() {
        scheduleDailyForecast()
        scheduleRecurringDetection()
        scheduleDigests()
        scheduleBackup()
    }

    fun scheduleDailyForecast() {
        val request = PeriodicWorkRequestBuilder<ForecastWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(2, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork("daily_forecast", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleRecurringDetection() {
        val request = PeriodicWorkRequestBuilder<RecurringDetectionWorker>(2, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork("recurring_detection", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    suspend fun scheduleDigests() {
        val s = settings.settings.first()
        if (s.weeklyDigest) {
            val delay = initialDelayToWeekly(s.weeklyDigestDow, s.digestHour)
            val req = PeriodicWorkRequestBuilder<DigestWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(DigestWorker.KEY_TYPE, "WEEKLY").build())
                .build()
            wm.enqueueUniquePeriodicWork("weekly_digest", ExistingPeriodicWorkPolicy.UPDATE, req)
        } else wm.cancelUniqueWork("weekly_digest")

        if (s.monthlyDigest) {
            val delay = initialDelayToMonthly(s.digestHour)
            val req = PeriodicWorkRequestBuilder<DigestWorker>(30, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(DigestWorker.KEY_TYPE, "MONTHLY").build())
                .build()
            wm.enqueueUniquePeriodicWork("monthly_digest", ExistingPeriodicWorkPolicy.UPDATE, req)
        } else wm.cancelUniqueWork("monthly_digest")
    }

    fun scheduleBackup() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(3, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork("auto_backup", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Run the past-SMS analyser over the last [days] days (§2). REPLACE lets the user re-run it with
     * a different range.
     */
    fun runBackfill(days: Int = SmsBackfillWorker.DEFAULT_DAYS) {
        val request = OneTimeWorkRequestBuilder<SmsBackfillWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .setInputData(Data.Builder().putInt(SmsBackfillWorker.KEY_DAYS, days).build())
            .build()
        wm.enqueueUniqueWork(BACKFILL_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Live state of the past-SMS analyser, for the onboarding/progress screen. */
    fun observeBackfill(): Flow<List<WorkInfo>> = wm.getWorkInfosForUniqueWorkFlow(BACKFILL_WORK)

    fun runDigestNow(type: String) {
        val req = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInputData(Data.Builder().putString(DigestWorker.KEY_TYPE, type).build())
            .build()
        wm.enqueue(req)
    }

    private fun initialDelayToWeekly(dow: Int, hour: Int): Duration {
        val now = LocalDateTime.now()
        val targetDow = java.time.DayOfWeek.of(dow.coerceIn(1, 7))
        var target = now.toLocalDate().with(TemporalAdjusters.nextOrSame(targetDow)).atTime(LocalTime.of(hour, 0))
        if (target.isBefore(now)) target = target.plusWeeks(1)
        return Duration.between(now, target)
    }

    private fun initialDelayToMonthly(hour: Int): Duration {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth()).atTime(LocalTime.of(hour, 0))
        // If we're on the 1st before the hour, fire today.
        val firstThisMonth = now.toLocalDate().withDayOfMonth(1).atTime(LocalTime.of(hour, 0))
        if (now.isBefore(firstThisMonth)) target = firstThisMonth
        return Duration.between(now, target)
    }

    @Suppress("unused")
    private fun zoneNow(): LocalDate = LocalDate.now(ZoneId.systemDefault())

    companion object {
        const val BACKFILL_WORK = "sms_backfill"
    }
}
