package com.foresightlabs.aether.data.calls

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.MainActivity
import com.foresightlabs.aether.data.notifications.AetherNotificationManager
import com.foresightlabs.aether.calls.media.CallDiagnostics
import com.foresightlabs.aether.calls.media.CallStage

/**
 * Ringing notification for INCOMING calls while they are pending.
 *
 * Scope is deliberately conservative (Android 14+ full-screen-intent policy):
 * this is a MAX-importance CATEGORY_CALL notification with Answer/Decline
 * actions, deliberately NOT a full-screen intent -- launching an activity
 * over the lock screen requires the USE_FULL_SCREEN_INTENT permission, which
 * Aether has not declared, and setting one without it is policy-unsafe (the
 * system would silently degrade it anyway on Android 14+). The
 * IMPORTANCE_HIGH channel gives the heads-up presentation + sound; when the
 * app is in the foreground the call screen is the primary surface.
 *
 * Posted when a non-outgoing call sits in PENDING and cancelled on any other
 * state (see [DefaultCallsRepository]'s activeCallState collector).
 */
class IncomingCallNotifier(private val context: Context) {

    private val notificationManager = AetherNotificationManager(
        context,
        getChat = { null },
        getUser = { null }
    )

    fun showIncoming(callId: Int, callerName: String, isVideo: Boolean, generation: Long) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            callId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_ACCEPT_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
            putExtra(CallService.EXTRA_GENERATION, generation)
        }
        val pendingAccept = PendingIntent.getService(
            context,
            callId,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_DECLINE_CALL
            putExtra(CallService.EXTRA_CALL_ID, callId)
            putExtra(CallService.EXTRA_GENERATION, generation)
        }
        val pendingDecline = PendingIntent.getService(
            context,
            callId,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(context, AetherApplication.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(callerName)
            .setContentText((if (isVideo) "Incoming video call" else "Incoming voice call") + " · Telegram Call (Beta)")
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(android.R.drawable.ic_menu_call, "Answer", pendingAccept)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", pendingDecline)
            .build()

        // Android 13+ gates notifications behind POST_NOTIFICATIONS; a denial
        // is a legitimate state (user opted out of call ringing), not an
        // error -- skip posting and record that the ringer was suppressed.
        val notificationsAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!notificationsAllowed) {
            CallDiagnostics.stage(generation, CallStage.TDLIB_READY, "incoming_notification suppressed=no_post_notifications callId=$callId")
            return
        }
        try {
            // Posts through the one canonical notification-output path
            // (AetherNotificationManager) per the architecture invariant.
            notificationManager.postCallNotification(NOTIFICATION_ID, notification)
            CallDiagnostics.stage(generation, CallStage.TDLIB_READY, "incoming_notification callId=$callId")
        } catch (t: Throwable) {
            CallDiagnostics.failure(generation, CallStage.TDLIB_READY, t)
        }
    }

    fun cancel() {
        try {
            notificationManager.cancelNotification(NOTIFICATION_ID)
        } catch (_: Throwable) {}
    }

    companion object {
        const val NOTIFICATION_ID = 1003
    }
}
