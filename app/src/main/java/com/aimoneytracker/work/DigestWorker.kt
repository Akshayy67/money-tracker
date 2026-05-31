package com.aimoneytracker.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aimoneytracker.domain.model.DigestType
import com.aimoneytracker.domain.usecase.GenerateDigestUseCase
import com.aimoneytracker.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Generates a weekly/monthly digest and posts a tappable rich notification (§16). */
@HiltWorker
class DigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val generateDigest: GenerateDigestUseCase,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val type = when (inputData.getString(KEY_TYPE)) {
            "MONTHLY" -> DigestType.MONTHLY
            else -> DigestType.WEEKLY
        }
        val output = generateDigest(type)
        val title = if (type == DigestType.WEEKLY) "Your weekly recap" else "Your monthly recap"
        val text = output.content.naturalLanguageSummary ?: output.content.headline
        notifications.postDigest(output.recordId, title, text)
        Result.success()
    }.getOrElse { Result.failure() }

    companion object { const val KEY_TYPE = "digest_type" }
}
