package com.foresightlabs.aether.data.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.messaging.RemoteMessage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The push entry point, driven the way Android drives it: a Service created in
 * a process with no Activity, no ViewModel and no Compose state anywhere.
 *
 * The point is not that a notification appears -- there is no real TDLib here
 * to produce one -- but that the callback runs to completion and returns. A
 * background callback that throws, or that never returns, is the failure this
 * path cannot afford: nothing is watching, and the process is about to be
 * frozen either way.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseEntryPointTest {

    private fun service(): AetherFirebaseMessagingService =
        Robolectric.buildService(AetherFirebaseMessagingService::class.java).create().get()

    private fun dataMessage(vararg fields: Pair<String, String>): RemoteMessage =
        RemoteMessage.Builder("aether@test")
            .apply { fields.forEach { (key, value) -> addData(key, value) } }
            .build()

    @Test
    fun a_data_push_is_handled_without_any_activity_or_ui_present() {
        // A Telegram-shaped encrypted push: one opaque field, which only TDLib
        // ever reads.
        service().onMessageReceived(dataMessage("p" to "AAAAAAAAAAAAAAAAAAAAAA"))
    }

    @Test
    fun a_push_with_no_data_is_ignored_rather_than_guessed_at() {
        service().onMessageReceived(dataMessage())
    }

    @Test
    fun a_malformed_payload_does_not_escape_the_callback() {
        service().onMessageReceived(
            dataMessage(
                "p" to """{"broken":"quote}""",
                "weird\"key" to "value\\with\\backslashes"
            )
        )
    }

    @Test
    fun a_token_callback_is_safe_without_a_session() {
        service().onNewToken("test-token-value")
    }

    @Test
    fun the_callback_returns_rather_than_blocking_the_process_indefinitely() {
        val startedAt = System.nanoTime()
        service().onMessageReceived(dataMessage("p" to "AAAA"))
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        // A TDLib that cannot start must resolve readiness rather than leave
        // it permanently incomplete: the callback should end in milliseconds,
        // not sit out the readiness timeout and then the delivery budget.
        assertTrue(
            "The push callback must return promptly, not hold the process (took ${elapsedMs}ms)",
            elapsedMs < READINESS_TIMEOUT_MS
        )
    }

    private companion object {
        /** TelegramClient's own bound on waiting for TDLib parameters. */
        const val READINESS_TIMEOUT_MS = 5_000L
    }
}
