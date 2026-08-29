package com.foresightlabs.aether

import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.model.MessageType
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPreviewMappingTest {
    private val users = mapOf(
        42L to TdApi.User().apply {
            id = 42L
            firstName = "Urmila"
            lastName = "Sarkar"
        }
    )

    private fun reply(content: TdApi.MessageContent? = null) = TdApi.MessageReplyToMessage().apply {
        chatId = 7L
        messageId = 100L
        this.content = content
    }

    private fun target(content: TdApi.MessageContent, outgoing: Boolean = false) = TdApi.Message().apply {
        id = 100L
        chatId = 7L
        senderId = TdApi.MessageSenderUser(42L)
        isOutgoing = outgoing
        this.content = content
    }

    private fun map(reply: TdApi.MessageReplyToMessage, target: TdApi.Message? = null) =
        TelegramMappers.mapReplyPreview(reply, target, users, emptyMap(), 42L)

    @Test
    fun textReplyUsesTheActualSenderAndText() {
        val preview = TelegramMappers.mapReplyPreview(
            reply(),
            target(TdApi.MessageText(TdApi.FormattedText("Aktu time er gap a", emptyArray()), null, null)),
            users,
            emptyMap(),
            99L
        )

        assertEquals("Urmila Sarkar", preview.senderName)
        assertEquals("Aktu time er gap a", preview.text)
        assertEquals(100L, preview.messageId)
        assertEquals(7L, preview.chatId)
        assertTrue(preview.isAvailable)
    }

    @Test
    fun ownMessageUsesTheExistingYouNamingPolicy() {
        val preview = map(reply(), target(TdApi.MessageText(TdApi.FormattedText("My earlier message", emptyArray()), null, null), outgoing = true))

        assertEquals("You", preview.senderName)
        assertEquals("My earlier message", preview.text)
    }

    @Test
    fun quoteTakesPrecedenceOverTheWholeOriginalText() {
        val preview = map(
            reply().apply {
                quote = TdApi.TextQuote(TdApi.FormattedText("the quoted span", emptyArray()), 4, true)
            },
            target(TdApi.MessageText(TdApi.FormattedText("the complete original message", emptyArray()), null, null))
        )

        assertEquals("the quoted span", preview.text)
        assertTrue(preview.isQuotedExcerpt)
    }

    @Test
    fun mediaRepliesUseSemanticDescriptions() {
        val photo = map(reply(TdApi.MessagePhoto()))
        val voice = map(reply(TdApi.MessageVoiceNote()))
        val file = map(reply(TdApi.MessageDocument().apply { document = TdApi.Document().apply { fileName = "brief.pdf" } }))

        assertEquals("Photo", photo.text)
        assertEquals(MessageType.IMAGE, photo.type)
        assertEquals("Voice message", voice.text)
        assertEquals(MessageType.VOICE, voice.type)
        assertEquals("brief.pdf", file.text)
        assertEquals(MessageType.FILE, file.type)
    }

    @Test
    fun missingTargetIsTruthfulAndNotClickable() {
        val preview = map(reply(), target = null)

        assertFalse(preview.isAvailable)
        assertEquals("Original message unavailable", preview.text)
        assertEquals(100L, preview.messageId)
    }
}
