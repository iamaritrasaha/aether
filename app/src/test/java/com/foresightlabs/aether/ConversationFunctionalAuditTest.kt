package com.foresightlabs.aether

import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageActionPolicy
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.AetherText
import com.foresightlabs.aether.domain.text.ComposerFormatting
import com.foresightlabs.aether.domain.text.ComposerStyle
import com.foresightlabs.aether.domain.text.ReplyQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationFunctionalAuditTest {

    private fun createTestMessage(
        id: String = "1",
        text: String = "Test message",
        isOutgoing: Boolean = false,
        type: MessageType = MessageType.TEXT
    ): Message = Message(
        id = id,
        chatId = "100",
        senderId = "200",
        senderName = "Alice",
        text = text,
        timestamp = "10:00 AM",
        isOutgoing = isOutgoing,
        type = type
    )

    @Test
    fun testMessageActionPolicySaveOnlyForMedia() {
        val textMessage = createTestMessage(id = "1", text = "Hello world", isOutgoing = false, type = MessageType.TEXT)
        val imageMessage = createTestMessage(id = "2", text = "", isOutgoing = false, type = MessageType.IMAGE)
        val capabilities = MessageCapabilities(
            canBeReplied = true,
            canBeCopied = true,
            canBeForwarded = true,
            canBeSaved = true,
            canGetLink = true
        )

        val textActions = MessageActionPolicy.actionsFor(textMessage, capabilities, allowSelect = true)
        val imageActions = MessageActionPolicy.actionsFor(imageMessage, capabilities, allowSelect = true)

        // Text message should NOT have SAVE
        assertFalse(textActions.contains(MessageAction.SAVE))
        // Image message SHOULD have SAVE
        assertTrue(imageActions.contains(MessageAction.SAVE))
        // Real supported actions must be present
        assertTrue(textActions.contains(MessageAction.REPLY))
        assertTrue(textActions.contains(MessageAction.COPY))
        assertTrue(textActions.contains(MessageAction.FORWARD))
        assertTrue(textActions.contains(MessageAction.COPY_LINK))
        assertTrue(textActions.contains(MessageAction.SELECT))
    }

    @Test
    fun testCopyLinkHonorsCapability() {
        val message = createTestMessage(id = "2", text = "Check this", isOutgoing = false, type = MessageType.TEXT)

        val withLink = MessageCapabilities(canGetLink = true)
        val withoutLink = MessageCapabilities(canGetLink = false)

        assertTrue(MessageActionPolicy.actionsFor(message, withLink).contains(MessageAction.COPY_LINK))
        assertFalse(MessageActionPolicy.actionsFor(message, withoutLink).contains(MessageAction.COPY_LINK))
    }

    @Test
    fun testMultiSelectIntersection() {
        val msg1 = createTestMessage(id = "1", text = "Msg 1", isOutgoing = false, type = MessageType.TEXT)
        val msg2 = createTestMessage(id = "2", text = "Msg 2", isOutgoing = true, type = MessageType.TEXT)

        val cap1 = MessageCapabilities(canBeCopied = true, canBeForwarded = true, canBeDeletedOnlyForSelf = true)
        val cap2 = MessageCapabilities(canBeCopied = true, canBeForwarded = false, canBeDeletedOnlyForSelf = true)

        val actions = MessageActionPolicy.actionsForSelection(listOf(msg1 to cap1, msg2 to cap2))

        // Forward is only on msg1, so intersection should NOT have Forward
        assertFalse(actions.contains(MessageAction.FORWARD))
        // Copy and DeleteForMe are on both, so they must be included
        assertTrue(actions.contains(MessageAction.COPY))
        assertTrue(actions.contains(MessageAction.DELETE_FOR_ME))
        // Single-message-only actions must never appear in multi-select
        assertFalse(actions.contains(MessageAction.REPLY))
        assertFalse(actions.contains(MessageAction.EDIT))
        assertFalse(actions.contains(MessageAction.PIN))
    }

    @Test
    fun testQuoteReplyRequiresNonBlankText() {
        val textMsg = createTestMessage(id = "1", text = "Actual text", isOutgoing = false, type = MessageType.TEXT)
        val emptyTextMsg = createTestMessage(id = "2", text = "", isOutgoing = false, type = MessageType.IMAGE)

        val caps = MessageCapabilities(canBeReplied = true)

        assertTrue(MessageActionPolicy.actionsFor(textMsg, caps).contains(MessageAction.QUOTE_REPLY))
        assertFalse(MessageActionPolicy.actionsFor(emptyTextMsg, caps).contains(MessageAction.QUOTE_REPLY))
    }

    @Test
    fun testDeleteModesDistinction() {
        val message = createTestMessage(id = "1", text = "Delete me", isOutgoing = true, type = MessageType.TEXT)

        val selfOnly = MessageCapabilities(canBeDeletedOnlyForSelf = true, canBeDeletedForAllUsers = false)
        val everyoneOnly = MessageCapabilities(canBeDeletedOnlyForSelf = false, canBeDeletedForAllUsers = true)

        val selfActions = MessageActionPolicy.actionsFor(message, selfOnly)
        assertTrue(selfActions.contains(MessageAction.DELETE_FOR_ME))
        assertFalse(selfActions.contains(MessageAction.DELETE_FOR_EVERYONE))

        val everyoneActions = MessageActionPolicy.actionsFor(message, everyoneOnly)
        assertFalse(everyoneActions.contains(MessageAction.DELETE_FOR_ME))
        assertTrue(everyoneActions.contains(MessageAction.DELETE_FOR_EVERYONE))
    }

    @Test
    fun testComposerFormattingToggleAndSanitize() {
        val formatting = listOf(
            AetherEntity.Bold(offset = 0, length = 5),
            AetherEntity.Italic(offset = 6, length = 5)
        )

        val active = ComposerFormatting.activeStyles(formatting, 1, 3)
        assertTrue(active.contains(ComposerStyle.BOLD))
        assertFalse(active.contains(ComposerStyle.ITALIC))

        // Sanitizing ensures entities don't exceed text bounds
        val sanitized = ComposerFormatting.sanitise(formatting, 8)
        assertEquals(2, sanitized.size)
        assertEquals(5, sanitized[0].length)
        assertEquals(2, sanitized[1].length) // clamped to 8
    }

    @Test
    fun testReplyQuoteFromText() {
        val aetherText = AetherText("The quick brown fox jumps over the lazy dog")
        val quote = ReplyQuote.from(aetherText, 4, 15)
        assertNotNull(quote)
        assertEquals("quick brown", quote!!.text)
        assertEquals(4, quote.position)
    }

    @Test
    fun testAllMessageTypesRepresented() {
        val types = MessageType.entries
        assertTrue(types.contains(MessageType.TEXT))
        assertTrue(types.contains(MessageType.IMAGE))
        assertTrue(types.contains(MessageType.ALBUM))
        assertTrue(types.contains(MessageType.VOICE))
        assertTrue(types.contains(MessageType.AUDIO))
        assertTrue(types.contains(MessageType.VIDEO_NOTE))
        assertTrue(types.contains(MessageType.FILE))
        assertTrue(types.contains(MessageType.STICKER))
        assertTrue(types.contains(MessageType.ANIMATION))
        assertTrue(types.contains(MessageType.POLL))
        assertTrue(types.contains(MessageType.CONTACT))
        assertTrue(types.contains(MessageType.LOCATION))
        assertTrue(types.contains(MessageType.VENUE))
        assertTrue(types.contains(MessageType.SERVICE))
        assertTrue(types.contains(MessageType.CALL))
        assertTrue(types.contains(MessageType.UNSUPPORTED))
    }
}
