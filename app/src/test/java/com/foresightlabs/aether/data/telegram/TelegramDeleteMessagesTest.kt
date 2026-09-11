package com.foresightlabs.aether.data.telegram

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Message
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for [TelegramClient.handleUpdate]'s
 * [TdApi.UpdateDeleteMessages] handling -- see [TelegramConversationMergeTest]'s
 * doc for why Robolectric is needed but no running TDLib [org.drinkless.tdlib.Client] is.
 *
 * The bug this guards against: a message actually deleted (by this client, a
 * remote client, or the server) never left [TelegramClient.messagesFlow]
 * because the handler unconditionally returned early whenever
 * `fromCache == true`, regardless of the update being genuinely permanent.
 */
@RunWith(AndroidJUnit4::class)
class TelegramDeleteMessagesTest {

    private lateinit var client: TelegramClient

    @Before
    fun setUp() {
        client = TelegramClient(ApplicationProvider.getApplicationContext())
    }

    private fun message(id: Long, chatId: Long = 1L) = Message(
        id = id.toString(),
        chatId = chatId.toString(),
        senderId = "1",
        senderName = "Test",
        text = "msg-$id",
        timestamp = "",
        dateSeconds = 100,
        isOutgoing = true
    )

    private suspend fun awaitRemoved(chatId: Long, id: Long) {
        withTimeout(2000) {
            client.messagesFlow(chatId).first { list -> list.none { it.id == id.toString() } }
        }
    }

    @Test
    fun permanentServerDeleteRemovesTheMessage() = runBlocking {
        client.upsertConversation(1L, listOf(message(1)), prepend = false)
        assertEquals(1, client.messagesFlow(1L).value.size)

        client.handleUpdate(TdApi.UpdateDeleteMessages(1L, longArrayOf(1L), true, false))

        awaitRemoved(1L, 1L)
        assertTrue(client.messagesFlow(1L).value.isEmpty())
    }

    @Test
    fun fromCacheDeleteStillRemovesTheMessage() = runBlocking {
        // The regression itself: a from-cache=true update (TDLib's own store no
        // longer has the message either way) must not be ignored -- see the class doc.
        client.upsertConversation(1L, listOf(message(2)), prepend = false)

        client.handleUpdate(TdApi.UpdateDeleteMessages(1L, longArrayOf(2L), false, true))

        awaitRemoved(1L, 2L)
        assertTrue(client.messagesFlow(1L).value.isEmpty())
    }

    @Test
    fun deletingOneMessageLeavesOthersInTheSameChatUntouched() = runBlocking {
        client.upsertConversation(1L, listOf(message(1), message(3)), prepend = false)

        client.handleUpdate(TdApi.UpdateDeleteMessages(1L, longArrayOf(1L), true, false))

        awaitRemoved(1L, 1L)
        val remaining = client.messagesFlow(1L).value.map { it.id }
        assertEquals(listOf("3"), remaining)
    }

    @Test
    fun deleteInOneChatDoesNotAffectAnotherChatsMessages() = runBlocking {
        client.upsertConversation(1L, listOf(message(1, chatId = 1L)), prepend = false)
        client.upsertConversation(2L, listOf(message(1, chatId = 2L)), prepend = false)

        client.handleUpdate(TdApi.UpdateDeleteMessages(1L, longArrayOf(1L), true, false))

        awaitRemoved(1L, 1L)
        assertEquals(1, client.messagesFlow(2L).value.size)
    }

    @Test
    fun multipleMessageIdsInOneUpdateAreAllRemoved() = runBlocking {
        client.upsertConversation(1L, listOf(message(1), message(2), message(3)), prepend = false)

        client.handleUpdate(TdApi.UpdateDeleteMessages(1L, longArrayOf(1L, 2L), true, false))

        withTimeout(2000) {
            client.messagesFlow(1L).first { list -> list.size == 1 }
        }
        assertEquals(listOf("3"), client.messagesFlow(1L).value.map { it.id })
    }
}
