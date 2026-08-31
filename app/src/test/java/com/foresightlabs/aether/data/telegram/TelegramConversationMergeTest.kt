package com.foresightlabs.aether.data.telegram

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for [TelegramClient.upsertConversation]'s merge path.
 *
 * [TelegramClient]'s constructor does no JNI/TDLib work -- [Client] isn't touched
 * until [TelegramClient.start] is called -- but it does read `Context` (a persisted
 * push-receiver id) and `Dispatchers.Main.immediate`, both of which need Robolectric
 * rather than a bare `Application()`. That's the only reason this runs under
 * [AndroidJUnit4]; the logic under test is otherwise pure in-memory merging.
 */
@RunWith(AndroidJUnit4::class)
class TelegramConversationMergeTest {

    private lateinit var client: TelegramClient

    @Before
    fun setUp() {
        client = TelegramClient(ApplicationProvider.getApplicationContext())
    }

    private fun message(id: Long, dateSeconds: Int, presentationKey: String? = null) = Message(
        id = id.toString(),
        chatId = "1",
        senderId = "1",
        senderName = "Test",
        text = "msg-$id",
        timestamp = "",
        dateSeconds = dateSeconds,
        isOutgoing = true,
        presentationKey = presentationKey
    )

    @Test
    fun appendedMessageStaysSortedByDateThenId() {
        client.upsertConversation(1L, listOf(message(1, 100), message(2, 200)), prepend = false)
        client.upsertConversation(1L, listOf(message(3, 150)), prepend = false)

        val ids = client.messagesFlow(1L).value.map { it.id }
        assertEquals(listOf("1", "3", "2"), ids)
    }

    @Test
    fun reinsertingAnExistingIdDeduplicatesRatherThanAppending() {
        client.upsertConversation(1L, listOf(message(1, 100)), prepend = false)
        client.upsertConversation(1L, listOf(message(1, 100)), prepend = false)

        assertEquals(1, client.messagesFlow(1L).value.size)
    }

    @Test
    fun presentationKeyCarriesOverWhenTheIncomingMessageHasNone() {
        client.upsertConversation(1L, listOf(message(1, 100, presentationKey = "temp-1")), prepend = false)

        // TDLib's own confirmation of the same id arrives without a presentationKey
        // (it's a UI-only bridge for the pre-confirmation send) -- the existing one
        // must survive the update, not be wiped back to null.
        client.upsertConversation(1L, listOf(message(1, 100, presentationKey = null)), prepend = false)

        assertEquals("temp-1", client.messagesFlow(1L).value.single().presentationKey)
    }

    @Test
    fun presentationKeyReconciliationAppliesRegardlessOfPrepend() {
        client.upsertConversation(1L, listOf(message(5, 500, presentationKey = "temp-5")), prepend = false)
        // A prepended page (older history loading upward) reconciling the same id
        // must follow the same "keep the existing key when the incoming one is
        // null" rule append does -- prepend only changes where new ids land before
        // the final sort, not how a colliding id is reconciled.
        client.upsertConversation(1L, listOf(message(5, 500, presentationKey = null)), prepend = true)

        assertEquals("temp-5", client.messagesFlow(1L).value.single().presentationKey)
    }

    @Test
    fun manySequentialSingleMessageUpsertsCompleteWithoutQuadraticBlowup() {
        val messageCount = 3000

        val elapsedMs = kotlin.system.measureTimeMillis {
            for (i in 0 until messageCount) {
                client.upsertConversation(1L, listOf(message(i.toLong(), i)), prepend = false)
            }
        }

        assertEquals(messageCount, client.messagesFlow(1L).value.size)
        // A quadratic re-scan per insert turns this into ~4.5 billion comparisons at
        // n=3000; the O(n) lookup this guards keeps it to a few thousand sorts of an
        // already-sorted list. Bound is generous on purpose -- this is a regression
        // trip-wire against reintroducing the O(n^2) scan, not a micro-benchmark.
        assertTrue(
            "manySequentialSingleMessageUpserts took ${elapsedMs}ms, " +
                "suspiciously slow for $messageCount inserts",
            elapsedMs < 15_000
        )
    }
}
