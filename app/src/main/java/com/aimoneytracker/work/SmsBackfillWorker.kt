package com.aimoneytracker.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aimoneytracker.data.parser.SenderFilter
import com.aimoneytracker.data.preferences.SettingsRepository
import com.aimoneytracker.data.processor.TransactionProcessor
import com.aimoneytracker.domain.model.TransactionSource
import com.aimoneytracker.util.DateUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Past-SMS analyser (§2). On first run — or any time from Settings/More — reads the last [KEY_DAYS]
 * days of SMS, routes each candidate through the parsing engine, and populates the database so the
 * app is useful immediately.
 *
 * Progress is published via [setProgress] (scanned / total / found) so the onboarding screen can
 * show a live progress bar, and a summary is returned in the worker's output data.
 */
@HiltWorker
class SmsBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: TransactionProcessor,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val days = inputData.getInt(KEY_DAYS, DEFAULT_DAYS).coerceIn(1, 3650)
        val since = DateUtil.daysAgo(days)
        val resolver = applicationContext.contentResolver
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address", "body", "date")

        val cursor = resolver.query(uri, projection, "date >= ?", arrayOf(since.toString()), "date ASC")
        var scanned = 0
        var found = 0

        cursor?.use {
            val total = it.count.coerceAtLeast(1)
            val addrIdx = it.getColumnIndex("address")
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")

            setProgress(progressData(0, 0, total, false))

            while (it.moveToNext()) {
                scanned++
                val sender = if (addrIdx >= 0) it.getString(addrIdx) else null
                val body = if (bodyIdx >= 0) it.getString(bodyIdx) else null
                val date = if (dateIdx >= 0) it.getLong(dateIdx) else DateUtil.now()

                if (!body.isNullOrBlank() && SenderFilter.isTransactionMessage(sender, body)) {
                    val outcome = processor.processRawMessage(sender, body, date, TransactionSource.SMS)
                    if (outcome.transactionId != null && !outcome.merged) found++
                }

                // Throttle progress updates so we don't spam WorkManager for every row.
                if (scanned % 15 == 0 || scanned == total) {
                    setProgress(progressData(scanned, found, total, false))
                }
            }
            setProgress(progressData(scanned, found, total, true))
        }

        settings.setBackfillDone(true)
        Result.success(
            workDataOf(KEY_SCANNED to scanned, KEY_FOUND to found, KEY_DONE to true)
        )
    }.getOrElse { Result.failure(workDataOf(KEY_DONE to true, KEY_SCANNED to 0, KEY_FOUND to 0)) }

    private fun progressData(scanned: Int, found: Int, total: Int, done: Boolean) = workDataOf(
        KEY_SCANNED to scanned,
        KEY_FOUND to found,
        KEY_TOTAL to total,
        KEY_PROGRESS to (scanned * 100 / total.coerceAtLeast(1)),
        KEY_DONE to done,
    )

    companion object {
        const val KEY_DAYS = "days"
        const val KEY_SCANNED = "scanned"
        const val KEY_FOUND = "found"
        const val KEY_TOTAL = "total"
        const val KEY_PROGRESS = "progress"
        const val KEY_DONE = "done"
        const val DEFAULT_DAYS = 365
    }
}
