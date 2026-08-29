package com.foresightlabs.aether.data.push
import com.foresightlabs.aether.data.push.PushPayloads
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TdApi.ProcessPushNotification expects the exact fields Telegram's server
 * put on the FCM data message, byte-for-byte -- never a debug representation
 * like RemoteMessage.toString(), and never a payload this code has
 * reinterpreted. These tests pin that the built JSON carries every incoming
 * key untouched (including an opaque encrypted "p" field) and adds only the
 * one field FCM itself supplies outside the data map.
 *
 * Runs under Robolectric because org.json.JSONObject -- like the rest of the
 * Android SDK surface -- throws when exercised under the plain JVM unit test
 * stub jar; Robolectric provides the real implementation.
 */
@RunWith(RobolectricTestRunner::class)
class PushPayloadsTest {

    @Test
    fun everyDataFieldSurvivesIntoTheJsonPayloadUnchanged() {
        val data = mapOf(
            "p" to "AbCdEf123==-encrypted-opaque-blob",
            "google.notification.sound" to "default"
        )

        val payload = PushPayloads.buildProcessPushNotificationPayload(data, sentTimeMillis = 1_700_000_000_000L)
        val json = JSONObject(payload)

        assertEquals("AbCdEf123==-encrypted-opaque-blob", json.getString("p"))
        assertEquals("default", json.getString("google.notification.sound"))
    }

    @Test
    fun googleSentTimeIsAddedFromFcmMetadataNotFromTheDataMap() {
        val payload = PushPayloads.buildProcessPushNotificationPayload(
            data = mapOf("loc_key" to "MESSAGE_TEXT"),
            sentTimeMillis = 42L
        )
        val json = JSONObject(payload)

        assertEquals(42L, json.getLong("google.sent_time"))
        assertEquals("MESSAGE_TEXT", json.getString("loc_key"))
    }

    @Test
    fun soundParameterIsAddedWhenProvided() {
        val payload = PushPayloads.buildProcessPushNotificationPayload(
            data = mapOf("p" to "x"),
            sentTimeMillis = 100L,
            sound = "custom_ring.mp3"
        )
        val json = JSONObject(payload)

        assertEquals("custom_ring.mp3", json.getString("google.notification.sound"))
        assertEquals("x", json.getString("p"))
        assertEquals(100L, json.getLong("google.sent_time"))
    }

    @Test
    fun soundParameterDoesNotOverwriteExistingDataSound() {
        val payload = PushPayloads.buildProcessPushNotificationPayload(
            data = mapOf("p" to "x", "google.notification.sound" to "data_sound.mp3"),
            sentTimeMillis = 100L,
            sound = "notification_sound.mp3"
        )
        val json = JSONObject(payload)

        assertEquals("data_sound.mp3", json.getString("google.notification.sound"))
    }

    @Test
    fun anEmptyDataMapStillProducesAValidPayloadWithJustSentTime() {
        val payload = PushPayloads.buildProcessPushNotificationPayload(emptyMap(), sentTimeMillis = 7L)
        val json = JSONObject(payload)

        assertEquals(1, json.length())
        assertEquals(7L, json.getLong("google.sent_time"))
    }

    @Test
    fun theResultIsNeverARemoteMessageDebugRepresentation() {
        // The regression this guards: passing message.toString() (or any
        // "RemoteMessage{...}" style debug dump) instead of a real JSON
        // object -- TDLib would fail to parse either, but silently, so this
        // asserts the shape directly rather than trusting a passing call.
        val payload = PushPayloads.buildProcessPushNotificationPayload(
            data = mapOf("p" to "x"),
            sentTimeMillis = 1L
        )
        assertTrue(payload.trim().startsWith("{"))
        assertTrue(payload.trim().endsWith("}"))
        assertTrue(!payload.contains("RemoteMessage"))
    }
}
