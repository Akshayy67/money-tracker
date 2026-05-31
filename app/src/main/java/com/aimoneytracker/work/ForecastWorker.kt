package com.aimoneytracker.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aimoneytracker.data.preferences.SettingsRepository
import com.aimoneytracker.data.repository.AccountRepository
import com.aimoneytracker.domain.usecase.GenerateForecastUseCase
import com.aimoneytracker.util.Money
import com.aimoneytracker.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Daily forecast recompute (§15.10). Persists a snapshot, calibrates past forecasts, refreshes cached
 * account balances, and raises a low-balance / run-low alert when warranted.
 */
@HiltWorker
class ForecastWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val generateForecast: GenerateForecastUseCase,
    private val accountRepository: AccountRepository,
    private val settings: SettingsRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        accountRepository.recomputeBalances()
        generateForecast.calibratePastSnapshots()
        val result = generateForecast(persist = true)

        val threshold = settings.settings.first().lowBalanceThreshold
        if (result.runLowDate != null) {
            notifications.postAlert(
                id = 5001,
                title = "Heads up: balance running low",
                text = "At your current pace your balance may dip below ${Money.format(threshold)} around " +
                    "${result.runLowDate}. Daily safe-to-spend: ${Money.format(result.dailySafeToSpend)}.",
            )
        }
        Result.success()
    }.getOrElse { Result.retry() }
}
