package com.foresightlabs.aether.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.foresightlabs.aether.AetherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AetherApplication ?: return
        val action = intent.action ?: return

        when (action) {
            AetherNotificationManager.ACTION_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val replyText = results?.getCharSequence(AetherNotificationManager.KEY_TEXT_REPLY)?.toString()?.trim()
                val chatId = intent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L)
                val replyToMessageId = intent.getLongExtra(AetherNotificationManager.EXTRA_REPLY_TO_MESSAGE_ID, 0L)
                val groupId = intent.getIntExtra(AetherNotificationManager.EXTRA_NOTIFICATION_GROUP_ID, 0)

                if (!replyText.isNullOrBlank() && chatId != 0L) {
                    val pendingResult = goAsync()
                    receiverScope.launch {
                        try {
                            app.telegram.send(
                                chatId = chatId,
                                text = replyText,
                                replyToMessageId = if (replyToMessageId != 0L) replyToMessageId else null
                            )
                            // Mark previously received messages in this group as read
                            val messageIds = intent.getLongArrayExtra(AetherNotificationManager.EXTRA_MESSAGE_IDS)
                            if (messageIds != null && messageIds.isNotEmpty()) {
                                app.telegram.viewMessages(chatId, messageIds, forceRead = true)
                            }
                            app.notificationManager.onNotificationDismissed(groupId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            AetherNotificationManager.ACTION_MARK_READ -> {
                val chatId = intent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L)
                val messageIds = intent.getLongArrayExtra(AetherNotificationManager.EXTRA_MESSAGE_IDS) ?: longArrayOf()
                val groupId = intent.getIntExtra(AetherNotificationManager.EXTRA_NOTIFICATION_GROUP_ID, 0)

                if (chatId != 0L && messageIds.isNotEmpty()) {
                    val pendingResult = goAsync()
                    receiverScope.launch {
                        try {
                            app.telegram.viewMessages(chatId, messageIds, forceRead = true)
                            app.notificationManager.onNotificationDismissed(groupId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else if (groupId != 0) {
                    app.notificationManager.onNotificationDismissed(groupId)
                }
            }

            AetherNotificationManager.ACTION_DISMISS -> {
                val groupId = intent.getIntExtra(AetherNotificationManager.EXTRA_NOTIFICATION_GROUP_ID, 0)
                val maxNotificationId = intent.getIntExtra(AetherNotificationManager.EXTRA_MAX_NOTIFICATION_ID, 0)

                if (groupId != 0) {
                    val pendingResult = goAsync()
                    receiverScope.launch {
                        try {
                            if (maxNotificationId > 0) {
                                app.telegram.removeNotificationGroup(groupId, maxNotificationId)
                            }
                            app.notificationManager.onNotificationDismissed(groupId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
