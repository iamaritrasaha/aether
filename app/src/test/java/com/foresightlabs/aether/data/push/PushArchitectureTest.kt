package com.foresightlabs.aether.data.push

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Structural guarantees about the background push path.
 *
 * The invariant worth protecting by test rather than by review is that there is
 * exactly ONE place an Android notification is produced. A push path that built
 * its own notification from FCM payload text would duplicate TDLib's semantics
 * (grouping, mute state, read state) and could show something TDLib itself would
 * not -- and it would do so only in the background, where it is hardest to notice.
 */
@RunWith(RobolectricTestRunner::class)
class PushArchitectureTest {

    private fun source(relative: String): String {
        // Unit tests run from the module directory; fall back to the repo root so
        // this works under either working directory.
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Source not found for $relative (looked in ${candidates.map { it.absolutePath }})")
        return file.readText()
    }

    @Test
    fun the_firebase_service_never_builds_a_notification_itself() {
        val service = source("com/foresightlabs/aether/data/push/AetherFirebaseMessagingService.kt")
        for (forbidden in listOf("NotificationCompat", "NotificationManagerCompat", ".notify(")) {
            assertFalse(
                "The push entry point must hand off to TDLib, not construct notifications ($forbidden)",
                service.contains(forbidden)
            )
        }
        assertTrue(
            "A push must reach TDLib's own processing",
            service.contains("processPushNotification")
        )
    }

    @Test
    fun only_the_notification_manager_posts_android_notifications() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("NotificationManagerCompat.from(") }
            .map { it.name }
            .toList()
        assertEquals(
            "Exactly one canonical notification-output path is allowed",
            listOf("AetherNotificationManager.kt"),
            offenders
        )
    }

    @Test
    fun the_new_message_update_does_not_open_a_second_notification_path() {
        val client = source("com/foresightlabs/aether/data/telegram/TelegramClient.kt")
        val newMessageBranch = client.substringAfter("is TdApi.UpdateNewMessage ->")
            .substringBefore("is TdApi.UpdateMessageContent ->")
        assertFalse(
            "Notifications come from TDLib's notification updates, never from UpdateNewMessage",
            newMessageBranch.contains("notificationManager")
        )
    }

    @Test
    fun the_406_continuation_is_bounded_and_never_a_foreground_service() {
        val client = source("com/foresightlabs/aether/data/telegram/TelegramClient.kt")
        assertTrue("Error 406 must hand off to the bounded worker", client.contains("code == 406"))
        assertFalse(
            "The push continuation must not become an expedited/foreground job",
            // Leading dot: the class comment documents the absence of
            // setExpedited() in prose, which is not a call site.
            client.contains(".setExpedited(")
        )
        assertFalse(
            "No persistent foreground service for message delivery",
            client.contains("startForegroundService(")
        )
        assertTrue(
            "Retries are bounded rather than looping forever",
            source("com/foresightlabs/aether/data/push/PushFetchWorker.kt").contains("MAX_ATTEMPTS")
        )
    }

    @Test
    fun both_firebase_callbacks_are_gated_on_configuration_being_present() {
        val service = source("com/foresightlabs/aether/data/push/AetherFirebaseMessagingService.kt")
        // Asserted per callback rather than by counting occurrences: a token
        // arriving in an unconfigured build is as much a startup hazard as a
        // message, and a count would pass even if both guards sat in one method.
        for (callback in listOf("onNewToken", "onMessageReceived")) {
            val body = service.substringAfter("override fun $callback").substringBefore("\n    override fun ")
            assertTrue(
                "$callback must not touch Firebase without configuration",
                body.contains("HAS_FCM_CONFIG")
            )
        }
        val application = source("com/foresightlabs/aether/AetherApplication.kt")
        assertTrue(
            "The eager token fetch must not run without configuration",
            application.contains("if (!BuildConfig.HAS_FCM_CONFIG)")
        )
    }

    @Test
    fun every_firebase_process_entry_point_in_the_manifest_is_gated() {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.exists() }.readText()
        // A ContentProvider initialises before Application.onCreate, so a
        // BuildConfig check alone would run too late to prevent it. Checked by
        // naming each component and reading its own android:enabled, rather than
        // counting placeholders -- a count says nothing about which ones are gated.
        val components = Regex("""<(?:provider|receiver|service)\b[^>]*>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest)
            .map { it.value }
            .filter { it.contains("com.google.firebase") || it.contains("AetherFirebaseMessagingService") }
            .toList()
        assertTrue("Expected the Firebase components to be declared", components.size >= 4)
        for (component in components) {
            assertTrue(
                "An ungated Firebase entry point would initialise in an unconfigured build: $component",
                component.contains("android:enabled=\"\${aetherFcmEnabled}\"")
            )
        }
    }

    // --- malformed input ------------------------------------------------------

    @Test
    fun a_malformed_or_hostile_push_payload_still_produces_valid_json() {
        // Telegram's server is the only legitimate source, but the FCM data map is
        // attacker-influencable in principle and must never be able to produce a
        // payload that breaks TDLib's parser by string concatenation.
        val data = mapOf(
            "p" to """{"broken":"quote}""",
            "weird\"key" to "value\\with\\backslashes",
            "empty" to ""
        )
        val payload = PushPayloads.buildProcessPushNotificationPayload(data, 1L)
        val parsed = JSONObject(payload)
        assertEquals("""{"broken":"quote}""", parsed.getString("p"))
        assertEquals("value\\with\\backslashes", parsed.getString("weird\"key"))
        assertEquals("", parsed.getString("empty"))
        assertEquals(1L, parsed.getLong("google.sent_time"))
    }
}
