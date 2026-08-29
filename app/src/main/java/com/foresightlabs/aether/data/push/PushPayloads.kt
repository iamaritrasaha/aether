package com.foresightlabs.aether.data.push

import org.json.JSONObject

/**
 * Builds the JSON string [TdApi.ProcessPushNotification][org.drinkless.tdlib.TdApi.ProcessPushNotification]
 * expects, from the raw fields Telegram's server put on an FCM data message.
 *
 * This is deliberately dumb: every field TDLib/Telegram put in [data] --
 * including an encrypted payload's opaque `"p"` field -- is carried through
 * byte-for-byte, never inspected or transformed. The one field this adds is
 * `"google.sent_time"`, which TDLib's payload format expects and which only
 * FCM itself can supply (it isn't part of the data map). Kept as a pure
 * function, separate from the Service, so the "the payload TDLib receives
 * matches what Telegram sent" invariant is testable without an Android
 * environment.
 */
object PushPayloads {
    fun buildProcessPushNotificationPayload(
        data: Map<String, String>,
        sentTimeMillis: Long,
        sound: String? = null
    ): String {
        val json = JSONObject()
        for ((key, value) in data) {
            json.put(key, value)
        }
        json.put("google.sent_time", sentTimeMillis)
        if (!sound.isNullOrBlank() && !json.has("google.notification.sound")) {
            json.put("google.notification.sound", sound)
        }
        return json.toString()
    }
}
