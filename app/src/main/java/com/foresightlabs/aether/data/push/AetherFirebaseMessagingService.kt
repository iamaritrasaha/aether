package com.foresightlabs.aether.data.push

import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.BuildConfig
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/**
 * The one place Aether talks to Firebase.
 *
 * Both callbacks are thin forwarders into [TelegramClient][com.foresightlabs.aether.data.telegram.TelegramClient]:
 * a token goes to `registerFcmToken`, a push payload goes to
 * `processPushNotification`. Neither builds an Android notification here --
 * TDLib decides what changed and the existing canonical pipeline
 * (`AetherNotificationManager`, off TDLib's own notification updates) is what
 * posts it. That split is deliberate: constructing a notification straight
 * from FCM fields would duplicate TDLib's notification semantics (grouping,
 * mute state, read state) and risk showing something TDLib itself would not.
 *
 * The system creates this Service (and therefore this process, running
 * [AetherApplication.onCreate] first) on a push even when no Activity is
 * running, which is exactly the "smallest safe application-level
 * initialization" the background path needs -- nothing further is started
 * here. Nothing on this path reads an Activity, a ViewModel, or any Compose
 * state, so it behaves identically whether or not the app has a UI alive.
 *
 * [onMessageReceived] blocks (via [runBlocking]) for the whole delivery, and
 * that is the point: returning from this callback tells Android the work is
 * done and the process may be frozen. The delivery is internally bounded --
 * see [PushDelivery] -- so this blocks for as long as the work genuinely takes
 * and never indefinitely. The one outcome that can outlast the callback's
 * execution allowance, TDLib's error 406 ("a live server connection is
 * required"), is not waited on here at all: it is handed to a bounded
 * WorkManager job and this returns.
 */
class AetherFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        if (!BuildConfig.HAS_FCM_CONFIG) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d(TAG, "FCM_DISABLED_MISSING_CONFIG")
            }
            return
        }
        (application as? AetherApplication)?.telegram?.registerFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        if (BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "FCM_ON_MESSAGE_RECEIVED elapsedRealtime=$startedAt")
        }
        if (!BuildConfig.HAS_FCM_CONFIG) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d(TAG, "FCM_DISABLED_MISSING_CONFIG")
            }
            return
        }
        val data = message.data
        if (data.isEmpty()) {
            // Nothing for TDLib to process. A notification-only message (or a
            // malformed one) is dropped rather than guessed at.
            if (BuildConfig.DEBUG) android.util.Log.d(TAG, "FCM_EMPTY_DATA_IGNORED")
            return
        }
        if (BuildConfig.DEBUG) {
            // The number of fields, never a field. Payload contents -- including
            // the encrypted blob, sender, and any message text -- are only ever
            // read by TDLib.
            android.util.Log.d(TAG, "FCM_DATA_RECEIVED fieldCount=${data.size}")
        }
        val payload = PushPayloads.buildProcessPushNotificationPayload(data, message.sentTime, message.notification?.sound)
        val telegram = (application as? AetherApplication)?.telegram
        if (telegram == null) {
            if (BuildConfig.DEBUG) android.util.Log.w(TAG, "FCM_HANDOFF_UNAVAILABLE")
            return
        }
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "FCM_HANDOFF_TO_TDLIB")
        try {
            val outcome = runBlocking { telegram.processPushNotification(payload) }
            if (BuildConfig.DEBUG) {
                android.util.Log.d(
                    TAG,
                    "FCM_TDLIB_HANDOFF_COMPLETE outcome=$outcome " +
                        "durationMs=${android.os.SystemClock.elapsedRealtime() - startedAt}"
                )
            }
        } catch (error: Throwable) {
            // A background callback is the worst place to let something escape:
            // there is no UI to notice and the process is about to be frozen
            // either way. The failure is recorded by class only -- no payload,
            // no message, no credential.
            if (BuildConfig.DEBUG) {
                android.util.Log.w(
                    TAG,
                    "FCM_TDLIB_HANDOFF_FAILED error=${error.javaClass.simpleName} " +
                        "durationMs=${android.os.SystemClock.elapsedRealtime() - startedAt}"
                )
            }
        }
    }

    private companion object {
        const val TAG = "AetherTd"
    }
}
