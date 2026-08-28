package com.foresightlabs.aether.data.calls

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.annotation.SuppressLint
import androidx.core.app.NotificationCompat
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("ForegroundServiceType")
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_CALL) {
            val app = application as? AetherApplication
            app?.callsRepository?.let { repo ->
                val callId = repo.activeCallState.value?.callId
                if (callId != null) {
                    CoroutineScope(Dispatchers.Default).launch {
                        repo.discardCall(callId)
                    }
                }
            }
            stopForegroundService()
            return START_NOT_STICKY
        }

        val name = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "Telegram Call"
        val isConnected = intent?.getBooleanExtra(EXTRA_IS_CONNECTED, false) ?: false

        val notification = buildNotification(name, isConnected)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun buildNotification(callerName: String, isConnected: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_STOP_CALL
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isConnected) "Voice Call Active" else "Connecting call…"

        return NotificationCompat.Builder(this, AetherApplication.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(callerName)
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        const val ACTION_START_CALL = "com.foresightlabs.aether.action.START_CALL"
        const val ACTION_STOP_CALL = "com.foresightlabs.aether.action.STOP_CALL"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_IS_CONNECTED = "extra_is_connected"

        fun startService(context: Context, callerName: String, isConnected: Boolean) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_IS_CONNECTED, isConnected)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallService::class.java)
            context.stopService(intent)
        }
    }
}
