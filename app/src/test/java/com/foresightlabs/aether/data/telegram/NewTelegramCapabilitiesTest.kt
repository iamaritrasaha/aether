package com.foresightlabs.aether.data.telegram
import com.foresightlabs.aether.data.media.TgsDecompressor
import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageActionPolicy
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class NewTelegramCapabilitiesTest {

    @Test
    fun tgsDecompressorDecompressesGzippedJson() {
        val sampleJson = """{"v":"5.5.2","fr":60,"ip":0,"op":180,"w":512,"h":512}"""
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(sampleJson.toByteArray(Charsets.UTF_8)) }
        val compressedBytes = bos.toByteArray()

        val decompressed = TgsDecompressor.decompressBytes(compressedBytes)
        assertEquals(sampleJson, decompressed)
    }

    @Test
    fun tgsDecompressorReturnsNullForInvalidBytes() {
        val invalidBytes = byteArrayOf(1, 2, 3, 4, 5)
        val result = TgsDecompressor.decompressBytes(invalidBytes)
        assertNull(result)
    }

    @Test
    fun messageVideoNoteMapsProperly() {
        val localFile = TdApi.LocalFile("/path/to/videonote.mp4", true, true, false, true, 0, 0, 0)
        val file = TdApi.File(101, 1024, 1024, localFile, null)
        val videoNote = TdApi.VideoNote().apply {
            duration = 15
            length = 240
            waveform = byteArrayOf(1, 2, 3)
            video = file
        }
        val msgVideoNote = TdApi.MessageVideoNote().apply {
            this.videoNote = videoNote
        }

        val tdMsg = TdApi.Message().apply {
            id = 12345L
            chatId = 100L
            senderId = TdApi.MessageSenderUser(42L)
            date = 1600000000
            content = msgVideoNote
        }

        val mapped = TelegramMappers.mapMessage(
            message = tdMsg,
            users = emptyMap(),
            chats = emptyMap(),
            myUserId = 42L,
            lastReadOutboxMessageId = 0L
        )
        assertEquals(MessageType.VIDEO_NOTE, mapped.type)
        assertEquals(15, mapped.voiceDurationSec)
        assertEquals(1, mapped.mediaItems.size)
        assertEquals("/path/to/videonote.mp4", mapped.mediaItems.first().url)
    }

    @Test
    fun messageVenueMapsTitleAndAddress() {
        val location = TdApi.Location(37.7749, -122.4194, 0.0)
        val venue = TdApi.Venue(location, "Blue Bottle", "66 Mint St", "foursquare", "1234", "venue")
        val msgVenue = TdApi.MessageVenue(venue)

        val tdMsg = TdApi.Message().apply {
            id = 12346L
            chatId = 100L
            senderId = TdApi.MessageSenderUser(42L)
            date = 1600000000
            content = msgVenue
        }

        val mapped = TelegramMappers.mapMessage(
            message = tdMsg,
            users = emptyMap(),
            chats = emptyMap(),
            myUserId = 42L,
            lastReadOutboxMessageId = 0L
        )
        assertEquals(MessageType.VENUE, mapped.type)
        assertEquals("Blue Bottle", mapped.venueTitle)
        assertEquals("66 Mint St", mapped.venueAddress)
    }

    @Test
    fun messageLocationMapsLiveLocation() {
        val location = TdApi.Location(37.7749, -122.4194, 0.0)
        val msgLocation = TdApi.MessageLocation(location, 900, 600, 45, 0)

        val tdMsg = TdApi.Message().apply {
            id = 12347L
            chatId = 100L
            senderId = TdApi.MessageSenderUser(42L)
            date = 1600000000
            content = msgLocation
        }

        val mapped = TelegramMappers.mapMessage(
            message = tdMsg,
            users = emptyMap(),
            chats = emptyMap(),
            myUserId = 42L,
            lastReadOutboxMessageId = 0L
        )
        assertEquals(MessageType.LOCATION, mapped.type)
        assertTrue(mapped.isLiveLocation)
        assertEquals(600, mapped.liveLocationExpiresIn)
    }

    @Test
    fun messageStickerMapsFormats() {
        val localFile = TdApi.LocalFile("/path/to/sticker.tgs", true, true, false, true, 0, 0, 0)
        val file = TdApi.File(201, 2048, 2048, localFile, null)
        val sticker = TdApi.Sticker().apply {
            setId = 1L
            width = 512
            height = 512
            emoji = "😀"
            format = TdApi.StickerFormatTgs()
            this.sticker = file
        }
        val msgSticker = TdApi.MessageSticker().apply {
            this.sticker = sticker
        }

        val tdMsg = TdApi.Message().apply {
            id = 12348L
            chatId = 100L
            senderId = TdApi.MessageSenderUser(42L)
            date = 1600000000
            content = msgSticker
        }

        val mapped = TelegramMappers.mapMessage(
            message = tdMsg,
            users = emptyMap(),
            chats = emptyMap(),
            myUserId = 42L,
            lastReadOutboxMessageId = 0L
        )
        assertEquals(MessageType.STICKER, mapped.type)
        assertEquals("tgs", mapped.stickerFormat)
        assertEquals("😀", mapped.text)
    }

    @Test
    fun replaceMediaOfferedOnlyWhenAllowedByServerAndMediaType() {
        val imageMsg = Message(
            id = "1",
            chatId = "100",
            senderId = "42",
            senderName = "Me",
            text = "Photo caption",
            timestamp = "12:00",
            isOutgoing = true,
            type = MessageType.IMAGE
        )

        val allowedCaps = MessageCapabilities(
            canBeEdited = true,
            canEditMedia = true,
            canBeDeletedOnlyForSelf = true,
            canBeDeletedForAllUsers = true,
            canBeForwarded = true,
            canBeReplied = true,
            canBePinned = true,
            canBeCopied = true,
            canBeSaved = true,
            canGetLink = true,
            canGetReadDate = true,
            canGetViewers = true
        )

        val actions = MessageActionPolicy.actionsFor(imageMsg, allowedCaps)
        assertTrue(MessageAction.REPLACE_MEDIA in actions)

        val disallowedCaps = allowedCaps.copy(canEditMedia = false)
        val actionsDisallowed = MessageActionPolicy.actionsFor(imageMsg, disallowedCaps)
        assertFalse(MessageAction.REPLACE_MEDIA in actionsDisallowed)
    }

    @Test
    fun videoNoteIsRecognizedAsSaveableMedia() {
        val videoNoteMsg = Message(
            id = "2",
            chatId = "100",
            senderId = "42",
            senderName = "Me",
            text = "",
            timestamp = "12:00",
            isOutgoing = true,
            type = MessageType.VIDEO_NOTE
        )

        val caps = MessageCapabilities(
            canBeEdited = true,
            canEditMedia = true,
            canBeDeletedOnlyForSelf = true,
            canBeDeletedForAllUsers = true,
            canBeForwarded = true,
            canBeReplied = true,
            canBePinned = true,
            canBeCopied = true,
            canBeSaved = true,
            canGetLink = true,
            canGetReadDate = true,
            canGetViewers = true
        )

        val actions = MessageActionPolicy.actionsFor(videoNoteMsg, caps)
        assertTrue(MessageAction.SAVE in actions)
    }
}
