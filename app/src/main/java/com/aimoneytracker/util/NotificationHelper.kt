package com.aimoneytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aimoneytracker.MainActivity
import com.aimoneytracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Centralizes notification channels and posting (§7 review prompts, §19 alerts, §16 digests). */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_TXN, context.getString(R.string.channel_transactions), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_REVIEW, context.getString(R.string.channel_review), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_ALERT, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_DIGEST, context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { nm.createNotificationChannel(it) }
    }

    fun postReview(transactionId: Long, title: String, text: String) {
        post(CH_REVIEW, transactionId.toInt(), title, text, deepLink = "review/$transactionId")
    }

    fun postAlert(id: Int, title: String, text: String) {
        post(CH_ALERT, id, title, text, deepLink = "dashboard")
    }

    fun postDigest(recordId: Long, title: String, text: String) {
        post(CH_DIGEST, 10000 + recordId.toInt(), title, text, deepLink = "digest/$recordId")
    }

    private fun post(channel: String, id: Int, title: String, text: String, deepLink: String) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled().not()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_LINK, deepLink)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notif) }
    }

    companion object {
        const val CH_TXN = "transactions"
        const val CH_REVIEW = "review"
        const val CH_ALERT = "alerts"
        const val CH_DIGEST = "digest"
        const val EXTRA_DEEP_LINK = "deep_link"
    }
}
