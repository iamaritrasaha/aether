package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardingOrderTest {
    @Test
    fun selectionUsesConversationOrderInsteadOfSetOrder() {
        val messages = listOf(message("10"), message("11"), message("12"))
        val selected = linkedSetOf("12", "10")

        assertEquals(listOf("10", "12"), orderedMessagesForForwarding(messages, selected).map { it.id })
    }

    @Test
    fun albumMembersRemainInConversationOrder() {
        val messages = listOf(message("21"), message("22"), message("23"))
        assertEquals(
            listOf("21", "22", "23"),
            orderedMessagesForForwarding(messages, setOf("23", "21", "22")).map { it.id }
        )
    }

    private fun message(id: String) = Message(
        id = id,
        chatId = "1",
        senderId = "2",
        senderName = "Person",
        text = id,
        timestamp = "",
        isOutgoing = false,
        type = MessageType.TEXT
    )
}
