package com.foresightlabs.aether.data.calls

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.MainActivity
import com.foresightlabs.aether.calls.media.CallDiagnostics
import com.foresightlabs.aether.calls.media.CallStage
import com.foresightlabs.aether.domain.calls.CallPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        // Notification actions for an INCOMING call. The action carries the
        // call id it was created for; a mismatch with the live call (stale
        // notification, call already resolved) is ignored rather than acting
        // on the wrong session.
        if (action == ACTION_ACCEPT_CALL || action == ACTION_DECLINE_CALL) {
            val app = application as? AetherApplication
            val repo = app?.callsRepository
            if (repo != null) {
                val actionCallId = intent?.getIntExtra(EXTRA_CALL_ID, -1) ?: -1
                val liveCallId = repo.activeCallState.value?.callId
                val incoming = IncomingCallNotifier(this)
                incoming.cancel()
                val actionBackend = runCatching {
                    com.foresightlabs.aether.domain.calls.CallBackend.valueOf(
                        intent?.getStringExtra(EXTRA_BACKEND)
                            ?: com.foresightlabs.aether.domain.calls.CallBackend.TELEGRAM_BETA.name
                    )
                }.getOrDefault(com.foresightlabs.aether.domain.calls.CallBackend.TELEGRAM_BETA)

                if (actionCallId == liveCallId && liveCallId != null) {
                    if (action == ACTION_ACCEPT_CALL && ContextCompat.checkSelfPermission(this, CallPermissions.MICROPHONE) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                        // Accepting without the microphone is not an option:
                        // Android kills the process when the microphone-typed
                        // foreground service starts ungranted (see
                        // grantedServiceType). Declining on the user's behalf
                        // would be equally wrong -- hand them the call screen,
                        // whose Answer control runs the normal permission flow.
                        CallDiagnostics.stage(
                            intent?.getLongExtra(EXTRA_GENERATION, 0L) ?: 0L,
                            CallStage.TDLIB_READY,
                            "accept_from_notification blocked=no_record_audio callId=$liveCallId"
                        )
                        openMainActivity()
                    } else {
                        CoroutineScope(Dispatchers.Default).launch {
                            when (actionBackend) {
                                com.foresightlabs.aether.domain.calls.CallBackend.AETHER -> {
                                    val aether = (application as? AetherApplication)?.aetherCallsRepository
                                    if (action == ACTION_ACCEPT_CALL) {
                                        aether?.acceptCall()
                                    } else {
                                        aether?.declineCall()
                                    }
                                }
                                com.foresightlabs.aether.domain.calls.CallBackend.TELEGRAM_BETA -> {
                                    if (action == ACTION_ACCEPT_CALL) {
                                        repo.acceptCall(liveCallId)
                                    } else {
                                        repo.discardCall(liveCallId)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    CallDiagnostics.stage(0L, CallStage.TEARDOWN, "stale_call_action action=$action actionCallId=$actionCallId liveCallId=$liveCallId")
                }
            }
            stopForegroundService()
            return START_NOT_STICKY
        }

        val name = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "Telegram Call"
        val isConnected = intent?.getBooleanExtra(EXTRA_IS_CONNECTED, false) ?: false
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false
        val generation = intent?.getLongExtra(EXTRA_GENERATION, 0L) ?: 0L

        val type = grantedServiceType(this, isVideo)
        if (type == 0) {
            // Android refuses -- and from Android 14 kills the process for --
            // a microphone/camera foreground service started without the
            // matching runtime permission. There is nothing to promote here,
            // so stop instead of asking the system for something it will
            // answer with a SecurityException.
            CallDiagnostics.stage(generation, CallStage.SERVICE_STOPPED, "reason=no_media_permission")
            stopForegroundService()
            return START_NOT_STICKY
        }

        val notification = buildNotification(name, isConnected, isVideo)
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            CallDiagnostics.stage(generation, CallStage.SERVICE_STARTED, "video=$isVideo connected=$isConnected")
        } catch (t: Throwable) {
            // A foreground service is a convenience for a call that continues
            // in the background -- never a reason to take the process down
            // while the user is on a call.
            CallDiagnostics.failure(generation, CallStage.SERVICE_STARTED, t)
            stopForegroundService()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
        // START_NOT_STICKY, deliberately: a call is a live session, not
        // restartable state. If the system kills the process mid-call, the
        // native session and TDLib call state die with it; a sticky restart
        // would re-post an active-call notification for a call that no longer
        // exists. The repository re-promotes the service explicitly (fresh
        // intent, fresh extras) whenever a call is actually live.
    }

    /** Opens Aether's main surface -- used when a notification action must be
     * completed in-app (e.g. an Answer that first needs a runtime grant). */
    private fun openMainActivity() {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            startActivity(open)
        } catch (t: Throwable) {
            CallDiagnostics.failure(0L, CallStage.TEARDOWN, t)
        }
    }

    private fun buildNotification(callerName: String, isConnected: Boolean, isVideo: Boolean): Notification {
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

        // Telegram calls carry their Beta label everywhere the user sees
        // them; the active-call screen itself stays warning-free.
        val statusText = when {
            isConnected && isVideo -> "Video Call Active · Telegram (Beta)"
            isConnected -> "Voice Call Active · Telegram (Beta)"
            else -> "Connecting call… · Telegram (Beta)"
        }

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
        const val ACTION_ACCEPT_CALL = "com.foresightlabs.aether.action.ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.foresightlabs.aether.action.DECLINE_CALL"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_IS_CONNECTED = "extra_is_connected"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_GENERATION = "extra_generation"
        const val EXTRA_BACKEND = "extra_backend"

        /**
         * The foreground service type this call may actually claim, given what
         * the user has actually granted.
         *
         * Android matches each declared service type against its own runtime
         * permission and, from Android 14, throws `SecurityException` -- fatal
         * from `onStartCommand` -- when one is missing. So the type is derived
         * from the grants rather than from what the call wishes it had: a call
         * with no microphone grant claims nothing at all (`0`).
         *
         * Microphone-typed only, deliberately: the manifest's `CallService`
         * declaration no longer lists `FOREGROUND_SERVICE_TYPE_CAMERA` (see
         * its comment there) because Aether explicitly pauses local camera
         * capture when the app backgrounds during a video call
         * (`DefaultCallsRepository`'s foreground/background handling) rather
         * than holding the camera open off-screen. Requesting a type the
         * manifest does not declare throws
         * `MissingForegroundServiceTypeException`, so `isVideo` is
         * intentionally not consulted here -- adding it back would require
         * re-declaring the camera type in the manifest first.
         */
        // FOREGROUND_SERVICE_TYPE_* are API 30 constants used below minSdk (24)
        // purely as compile-time int flags for ServiceCompat, which applies a
        // type only on API levels that understand one.
        @SuppressLint("InlinedApi")
        internal fun grantedServiceType(context: Context, isVideo: Boolean): Int {
            fun granted(permission: String) =
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

            if (!granted(CallPermissions.MICROPHONE)) return 0
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            return type
        }

        fun startService(
            context: Context,
            callerName: String,
            isConnected: Boolean,
            isVideo: Boolean = false,
            generation: Long = 0L
        ) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_IS_CONNECTED, isConnected)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_GENERATION, generation)
            }
            // startForegroundService() promises a startForeground() call within
            // a few seconds and crashes the process if one never arrives. When
            // nothing can legally be promoted, never make that promise.
            if (grantedServiceType(context, isVideo) == 0) {
                CallDiagnostics.stage(generation, CallStage.SERVICE_STOPPED, "reason=no_media_permission")
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                // Background-start restrictions and OEM policies can refuse the
                // start outright. The call itself is unaffected.
                CallDiagnostics.failure(generation, CallStage.SERVICE_STARTED, t)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, CallService::class.java)
                context.stopService(intent)
            } catch (_: Throwable) {
            }
        }
    }
}
