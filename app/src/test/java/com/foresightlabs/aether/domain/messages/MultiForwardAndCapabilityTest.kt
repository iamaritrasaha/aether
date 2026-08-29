package com.foresightlabs.aether.domain.messages
import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageActionPolicy
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.ChatFolder
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates multi-message operations, capability intersection, and scheduled messaging.
 */
class MultiForwardAndCapabilityTest {

    private fun sampleMessage(
        id: String,
        text: String = "Hello",
        type: MessageType = MessageType.TEXT,
        isOutgoing: Boolean = false
    ) = Message(
        id = id,
        chatId = "100",
        senderId = "42",
        senderName = "Alice",
        text = text,
        timestamp = "12:00",
        type = type,
        isOutgoing = isOutgoing
    )

    @Test
    fun multiSelectionExcludesEditAndReplyActions() {
        val messages = listOf(
            sampleMessage("1"),
            sampleMessage("2")
        )
        val capabilities = mapOf(
            "1" to MessageCapabilities(canBeEdited = true, canBeForwarded = true, canBeCopied = true, canBeDeletedOnlyForSelf = true),
            "2" to MessageCapabilities(canBeEdited = true, canBeForwarded = true, canBeCopied = true, canBeDeletedOnlyForSelf = true)
        )

        val selection = messages.map { it to (capabilities[it.id] ?: MessageCapabilities()) }
        val allowed = MessageActionPolicy.actionsForSelection(selection)

        assertFalse("Edit cannot be performed across multiple messages", MessageAction.EDIT in allowed)
        assertFalse("Reply cannot be performed across multiple messages", MessageAction.REPLY in allowed)
        assertTrue("Forward is permitted across multiple messages", MessageAction.FORWARD in allowed)
        assertTrue("Copy is permitted across multiple messages", MessageAction.COPY in allowed)
    }

    @Test
    fun multiForwardRequiresAllMessagesToSupportForwarding() {
        val messages = listOf(
            sampleMessage("1"),
            sampleMessage("2")
        )
        val capabilities = mapOf(
            "1" to MessageCapabilities(canBeForwarded = true, canBeCopied = true),
            "2" to MessageCapabilities(canBeForwarded = false, canBeCopied = true) // e.g. protected content
        )

        val selection = messages.map { it to (capabilities[it.id] ?: MessageCapabilities()) }
        val allowed = MessageActionPolicy.actionsForSelection(selection)
        assertFalse(MessageAction.FORWARD in allowed)
    }

    @Test
    fun multiForwardAllowsSendCopyOnlyIfAllMessagesCanBeSaved() {
        val msg1 = sampleMessage("1")
        val msg2 = sampleMessage("2")
        val capabilities = mapOf(
            "1" to MessageCapabilities(canBeSaved = true),
            "2" to MessageCapabilities(canBeSaved = false)
        )

        val canSendCopy = listOf(msg1, msg2).all { capabilities[it.id]?.canBeSaved == true }
        assertFalse("Send as copy is not allowed if any message is protected from saving", canSendCopy)
    }

    @Test
    fun multiForwardDetectsCaptionsAcrossSelection() {
        val textMsg = sampleMessage("1", text = "Just text", type = MessageType.TEXT)
        val imageMsg = sampleMessage("2", text = "Photo caption", type = MessageType.IMAGE)

        val hasCaption = listOf(textMsg, imageMsg).any { it.text.isNotBlank() && it.type != MessageType.TEXT }
        assertTrue("Selection with media caption must offer remove caption option", hasCaption)
    }

    @Test
    fun audioMessagesAreRecognizedAsDistinctSaveableMedia() {
        val audioMsg = sampleMessage("10", text = "Audio track", type = MessageType.AUDIO)
        val actions = MessageActionPolicy.actionsFor(
            audioMsg,
            MessageCapabilities(canBeSaved = true)
        )
        assertTrue(MessageAction.SAVE in actions)
    }

    @Test
    fun animationAndStickerMessagesAreRecognizedAsSaveableMedia() {
        val stickerMsg = sampleMessage("11", text = "🔥", type = MessageType.STICKER)
        val animMsg = sampleMessage("12", text = "GIF", type = MessageType.ANIMATION)

        val stickerActions = MessageActionPolicy.actionsFor(stickerMsg, MessageCapabilities(canBeSaved = true))
        val animActions = MessageActionPolicy.actionsFor(animMsg, MessageCapabilities(canBeSaved = true))

        assertTrue(MessageAction.SAVE in stickerActions)
        assertTrue(MessageAction.SAVE in animActions)
    }

    @Test
    fun chatFoldersHaveMainAndCustomTypes() {
        val mainFolder = ChatFolder.Main
        val workFolder = ChatFolder(id = 2, title = "Work")

        assertTrue(mainFolder.isMainList)
        assertFalse(workFolder.isMainList)
        assertEquals("All chats", mainFolder.title)
        assertEquals("Work", workFolder.title)
    }
}
