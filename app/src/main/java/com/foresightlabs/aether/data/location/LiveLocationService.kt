package com.foresightlabs.aether.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.MainActivity

/**
 * Foreground service to maintain continuous live location updates when the app is backgrounded,
 * as required by Android 10+ location privacy rules.
 *
 * Privacy Invariant:
 * This service runs ONLY while an active, user-initiated live location session exists.
 * It immediately terminates when the user taps "Stop Sharing" or when the live period expires.
 * Uses START_NOT_STICKY so that process death never creates a zombie notification without active tracking.
 */
class LiveLocationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val coordinator = (applicationContext as? AetherApplication)?.liveLocationCoordinator

        if (intent?.action == ACTION_STOP) {
            val chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1L)
            val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)

            if (chatId != -1L && messageId != -1L) {
                coordinator?.stopLiveSharing(chatId, messageId)
            } else {
                coordinator?.stopAll()
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // If there are no active sessions and this is a restart, terminate immediately
        if (coordinator != null && coordinator.activeSessions.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val coordinator = (applicationContext as? AetherApplication)?.liveLocationCoordinator
        if (coordinator != null && coordinator.activeSessions.isNotEmpty()) {
            coordinator.stopAll()
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sharing Live Location")
            .setContentText("Aether is sharing your live location in chat")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingLaunch)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Location Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification displayed while live location is actively shared"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "aether_live_location_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.foresightlabs.aether.action.STOP_LIVE_LOCATION"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_MESSAGE_ID = "extra_message_id"

        fun start(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java)
            context.stopService(intent)
        }
    }
}
