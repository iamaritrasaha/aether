package com.foresightlabs.aether.data.notifications

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/** Debug-only timing bridge for the TDLib notification pipeline. */
internal object NotificationTiming {
    private val newMessageAt = ConcurrentHashMap<Long, Long>()
    private val notificationGroupAt = ConcurrentHashMap<Long, Long>()

    fun markNewMessage(messageId: Long) {
        if (!com.foresightlabs.aether.BuildConfig.DEBUG) return
        newMessageAt[messageId] = SystemClock.elapsedRealtime()
    }

    fun markNotificationGroup(messageIds: Iterable<Long>) {
        if (!com.foresightlabs.aether.BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        messageIds.forEach { messageId ->
            val started = newMessageAt.remove(messageId) ?: return@forEach
            notificationGroupAt[messageId] = now
            android.util.Log.d(
                "AetherTd",
                "TIMING_NEW_MESSAGE_TO_NOTIFICATION_GROUP messageId=$messageId elapsedMs=${now - started} elapsedRealtime=$now"
            )
        }
    }

    fun markPosted(messageIds: Iterable<Long>, groupId: Int) {
        if (!com.foresightlabs.aether.BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        messageIds.forEach { messageId ->
            val groupedAt = notificationGroupAt.remove(messageId) ?: return@forEach
            android.util.Log.d(
                "AetherTd",
                "TIMING_NOTIFICATION_GROUP_TO_ANDROID_POSTED groupId=$groupId messageId=$messageId elapsedMs=${now - groupedAt} elapsedRealtime=$now"
            )
        }
    }
}
