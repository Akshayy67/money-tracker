package com.aimoneytracker.data.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aimoneytracker.data.processor.TransactionProcessor
import com.aimoneytracker.domain.model.TransactionSource
import com.aimoneytracker.util.Money
import com.aimoneytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads transaction notifications from bank/UPI apps (§2). The user grants notification access in
 * system settings. We only inspect known financial packages, extract the text, and route it to the
 * [TransactionProcessor] (which de-duplicates against any matching SMS).
 */
@AndroidEntryPoint
class TransactionNotificationListener : NotificationListenerService() {

    @Inject lateinit var processor: TransactionProcessor
    @Inject lateinit var notifications: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val financialPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",
        "net.one97.paytm",
        "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc",
        "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus",
        "com.axis.mobile",
        "com.msf.kbank.mobile", // Kotak
        "com.bankofbaroda.mconnect",
        "in.amazon.mShop.android.shopping", // Amazon Pay
        "com.mobikwik_new", "com.freecharge.android",
        "com.dreamplug.androidapp", // CRED
        "com.whatsapp", // WhatsApp Pay receipts
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in financialPackages) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = listOf(title, big.ifBlank { text }).filter { it.isNotBlank() }.joinToString(" — ")
        if (body.isBlank()) return

        scope.launch {
            val outcome = processor.processRawMessage(
                sender = pkg, body = body, receivedAt = sbn.postTime,
                source = TransactionSource.NOTIFICATION, packageName = pkg,
            )
            if (outcome.needsReview && outcome.transactionId != null && !outcome.merged) {
                val who = outcome.payeeHandle ?: outcome.merchant
                notifications.postReview(
                    transactionId = outcome.transactionId,
                    title = "What was this?",
                    text = "${Money.format(outcome.amount)} • $who — tap to categorize.",
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
