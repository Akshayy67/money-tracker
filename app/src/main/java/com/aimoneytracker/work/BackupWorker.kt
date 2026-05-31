package com.aimoneytracker.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aimoneytracker.data.backup.BackupManager
import com.aimoneytracker.data.preferences.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Scheduled encrypted backup (§20, §21). */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        if (!settings.settings.first().autoBackup) return Result.success()
        backupManager.createBackup(encrypt = true)
        Result.success()
    }.getOrElse { Result.retry() }
}
