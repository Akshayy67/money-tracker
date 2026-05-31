package com.aimoneytracker.data.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.aimoneytracker.data.parser.SenderFilter
import com.aimoneytracker.data.processor.TransactionProcessor
import com.aimoneytracker.domain.model.TransactionSource
import com.aimoneytracker.util.Money
import com.aimoneytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Incoming-SMS capture (§2). Concatenates multipart messages, applies the noise filter, and routes
 * each candidate to the [TransactionProcessor]. Low-confidence results trigger the §7 review prompt.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var processor: TransactionProcessor
    @Inject lateinit var notifications: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val bySender = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((sender, parts) in bySender) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val ts = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    if (!SenderFilter.isTransactionMessage(sender, body)) continue
                    val outcome = processor.processRawMessage(sender, body, ts, TransactionSource.SMS)
                    if (outcome.needsReview && outcome.transactionId != null) {
                        val who = outcome.payeeHandle ?: outcome.merchant
                        val verb = if (outcome.isCredit) "received from" else "sent to"
                        notifications.postReview(
                            transactionId = outcome.transactionId,
                            title = "What was this?",
                            text = "${Money.format(outcome.amount)} $verb $who — tap to categorize.",
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
