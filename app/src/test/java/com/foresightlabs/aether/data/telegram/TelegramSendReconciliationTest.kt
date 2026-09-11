package com.foresightlabs.aether.data.telegram

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the pending-to-confirmed swap an outgoing message
 * goes through: [TdApi.UpdateNewMessage] inserts it under TDLib's temporary
 * local id, then [TdApi.UpdateMessageSendSucceeded] replaces that with the
 * server-confirmed id.
 *
 * The bug this guards against: the conversation's LazyColumn keys each row on
 * [com.foresightlabs.aether.domain.messages.ConversationEntry.Single.key],
 * which falls back to the message id whenever `presentationKey` is null. With
 * no `presentationKey` stamped on the pending message, that id swap changed
 * the row's key -- so Compose's `animateItem()` played an exit animation for
 * the old key and an enter animation for the new one, briefly rendering both
 * the outgoing bubble and the just-confirmed one at once. [TelegramClient]
 * now stamps a stable `presentationKey` on a message the moment it enters as
 * [TdApi.MessageSendingStatePending], so the swap in
 * [TelegramClient.replaceMessage] carries it forward unchanged.
 */
@RunWith(AndroidJUnit4::class)
class TelegramSendReconciliationTest {

    private lateinit var client: TelegramClient

    @Before
    fun setUp() {
        client = TelegramClient(ApplicationProvider.getApplicationContext())
    }

    private fun textMessage(id: Long, chatId: Long, pending: Boolean): TdApi.Message {
        return TdApi.Message().apply {
            this.id = id
            this.chatId = chatId
            senderId = TdApi.MessageSenderUser(1L)
            date = 100
            isOutgoing = true
            sendingState = if (pending) TdApi.MessageSendingStatePending() else null
            content = TdApi.MessageText(TdApi.FormattedText("hello", emptyArray()), null, null)
        }
    }

    @Test
    fun confirmedSendKeepsTheSamePresentationKeyAsThePendingRow() = runBlocking {
        val chatId = 1L
        val pendingId = 100L
        val confirmedId = 200L

        client.handleUpdate(TdApi.UpdateNewMessage(textMessage(pendingId, chatId, pending = true)))
        withTimeout(2000) {
            client.messagesFlow(chatId).first { it.size == 1 }
        }
        val pendingRow = client.messagesFlow(chatId).value.single()
        assertEquals(pendingId.toString(), pendingRow.id)
        assertNotNull(pendingRow.presentationKey)

        client.handleUpdate(
            TdApi.UpdateMessageSendSucceeded(textMessage(confirmedId, chatId, pending = false), pendingId)
        )
        withTimeout(2000) {
            client.messagesFlow(chatId).first { list -> list.any { it.id == confirmedId.toString() } }
        }

        // Exactly one row survives the swap -- not the pending one plus a
        // second, separately-keyed confirmed one.
        val list = client.messagesFlow(chatId).value
        assertEquals(1, list.size)
        val confirmedRow = list.single()
        assertEquals(confirmedId.toString(), confirmedRow.id)
        // The row's presentation key -- what the conversation list actually
        // keys its LazyColumn item on -- must be unchanged by the id swap.
        assertEquals(pendingRow.presentationKey, confirmedRow.presentationKey)
    }
}
