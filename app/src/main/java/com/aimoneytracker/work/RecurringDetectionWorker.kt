package com.aimoneytracker.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aimoneytracker.data.repository.IncomeRepository
import com.aimoneytracker.data.repository.SubscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodically (re)detects subscriptions/EMIs and recurring income from history (§11, §12). */
@HiltWorker
class RecurringDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
    private val incomeRepository: IncomeRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        subscriptionRepository.detectAndStore()
        incomeRepository.detectRecurringIncome()
        Result.success()
    }.getOrElse { Result.retry() }
}
