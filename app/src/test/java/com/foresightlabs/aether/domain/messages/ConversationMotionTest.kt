package com.foresightlabs.aether.domain.messages
import com.foresightlabs.aether.domain.messages.ConversationMotion
import com.foresightlabs.aether.domain.messages.ConversationEntry
import com.foresightlabs.aether.domain.messages.MessageMotionEventType
import com.foresightlabs.aether.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMotionTest {

    @Test
    fun newMessageDirectionsAreSmallAndDistinct() {
        val outgoing = ConversationMotion.entrance(MessageMotionEventType.NEW_OUTGOING, reducedMotion = false)
        val incoming = ConversationMotion.entrance(MessageMotionEventType.NEW_INCOMING, reducedMotion = false)

        assertNotNull(outgoing)
        assertNotNull(incoming)
        assertTrue(outgoing!!.translationX > 0f)
        assertTrue(outgoing.translationY > 0f)
        assertTrue(incoming!!.translationX < 0f)
        assertTrue(incoming.translationY > 0f)
        assertTrue(outgoing.durationMs in 220..300)
        assertTrue(incoming.durationMs in 220..300)
    }

    @Test
    fun reducedMotionRemovesDirectionalAndScaleEntrance() {
        val entrance = ConversationMotion.entrance(MessageMotionEventType.NEW_INCOMING, reducedMotion = true)

        assertEquals(0f, entrance!!.translationX, 0f)
        assertEquals(0f, entrance.translationY, 0f)
        assertEquals(1f, entrance.scale, 0f)
        assertEquals(ConversationMotion.FAST_MS, entrance.durationMs)
    }

    @Test
    fun onlyChangingContentUsesShortChangeTransition() {
        assertTrue(ConversationMotion.usesShortChange(MessageMotionEventType.EDITED))
        assertTrue(ConversationMotion.usesShortChange(MessageMotionEventType.MEDIA_UPDATED))
        assertTrue(ConversationMotion.usesShortChange(MessageMotionEventType.REACTION_UPDATED))
        assertTrue(ConversationMotion.usesShortChange(MessageMotionEventType.SEND_CONFIRMED))
        assertNull(ConversationMotion.entrance(MessageMotionEventType.PAGINATION_HISTORY, false))
        assertNull(ConversationMotion.entrance(MessageMotionEventType.INITIAL_HISTORY, false))
    }

    @Test
    fun presentationKeyBridgesTemporaryAndFinalServerIds() {
        val temporary = Message(
            id = "-100",
            chatId = "7",
            senderId = "1",
            senderName = "You",
            text = "Sending",
            timestamp = "10:00",
            isOutgoing = true,
            presentationKey = "send:-100"
        )
        val confirmed = temporary.copy(id = "9001")

        assertEquals(
            ConversationEntry.Single(temporary).key,
            ConversationEntry.Single(confirmed).key
        )
    }
}
