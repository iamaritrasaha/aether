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
 * here.
 *
 * [onMessageReceived] blocks (via [runBlocking]) only for the one round trip
 * `processPushNotification` itself makes to TDLib -- not an arbitrary wait
 * afterward. TDLib documents its Ok result as meaning every update the push
 * caused has already been sent, so a successful call needs no further
 * waiting and this returns promptly. The one case that can genuinely outlast
 * this callback's short OS-granted execution window -- error 406, "a live
 * server connection is required" -- is not waited on here at all; TelegramClient
 * hands it to a bounded WorkManager job instead. This callback stays short in
 * every outcome.
 */
class AetherFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        if (!BuildConfig.HAS_FCM_CONFIG) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("AetherTd", "FCM_DISABLED_MISSING_CONFIG")
            }
            return
        }
        (application as? AetherApplication)?.telegram?.registerFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("AetherTd", "FCM_MESSAGE_RECEIVED")
        }
        if (!BuildConfig.HAS_FCM_CONFIG) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("AetherTd", "FCM_DISABLED_MISSING_CONFIG")
            }
            return
        }
        val data = message.data
        if (data.isEmpty()) return
        val sound = message.notification?.sound
        val payload = PushPayloads.buildProcessPushNotificationPayload(data, message.sentTime, sound)
        val telegram = (application as? AetherApplication)?.telegram ?: return
        runBlocking { telegram.processPushNotification(payload) }
    }
}
