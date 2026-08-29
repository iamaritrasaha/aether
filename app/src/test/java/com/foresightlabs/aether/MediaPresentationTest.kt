package com.foresightlabs.aether

import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.model.MessageType
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Media messages must be presented as media.
 *
 * Every photo, video, document and voice note previously mapped to
 * `MessageType.UNSUPPORTED` and rendered as a text bubble reading "Photo" or
 * "Voice message" — the content was received but never shown.
 */
class MediaPresentationTest {

    private fun downloadedFile(id: Int, path: String, size: Long = 1_048_576L) =
        TdApi.File().apply {
            this.id = id
            this.size = size
            expectedSize = size
            local = TdApi.LocalFile().apply {
                this.path = path
                isDownloadingCompleted = true
            }
            remote = TdApi.RemoteFile()
        }

    private fun pendingFile(id: Int) = TdApi.File().apply {
        this.id = id
        local = TdApi.LocalFile().apply {
            path = ""
            isDownloadingCompleted = false
            canBeDownloaded = true
        }
        remote = TdApi.RemoteFile()
    }

    private fun resolve(file: TdApi.File?): String? = TelegramMappers.localPath(file)

    @Test
    fun aPhotoIsPresentedAsAnImageWithItsLargestDownloadedSize() {
        val small = TdApi.PhotoSize("s", downloadedFile(1, "/data/small.jpg"), 90, 90, intArrayOf())
        val large = TdApi.PhotoSize("y", downloadedFile(2, "/data/large.jpg"), 1280, 720, intArrayOf())
        val content = TdApi.MessagePhoto().apply {
            photo = TdApi.Photo(false, null, arrayOf(small, large))
            caption = TdApi.FormattedText("Sunset", emptyArray())
        }

        val presentation = TelegramMappers.mapPresentation(content, 55L, ::resolve)

        assertEquals(MessageType.IMAGE, presentation.type)
        assertEquals("Sunset", presentation.text)
        assertEquals(1, presentation.mediaItems.size)
        assertEquals("/data/large.jpg", presentation.mediaItems.single().url)
        assertEquals(1280, presentation.mediaItems.single().width)
    }

    @Test
    fun anOutgoingLocalPhotoBeforeUploadIsImmediatelyAvailable() {
        val tempFile = java.io.File.createTempFile("outgoing_test_", ".jpg")
        tempFile.writeText("photo bytes")
        try {
            val localFile = TdApi.File().apply {
                id = 10
                size = tempFile.length()
                local = TdApi.LocalFile().apply {
                    path = tempFile.absolutePath
                    isDownloadingCompleted = false
                    canBeDownloaded = false
                }
            }
            val size = TdApi.PhotoSize("y", localFile, 1024, 768, intArrayOf())
            val content = TdApi.MessagePhoto().apply {
                photo = TdApi.Photo(false, null, arrayOf(size))
                caption = TdApi.FormattedText("", emptyArray())
            }

            val presentation = TelegramMappers.mapPresentation(content, 99L, ::resolve)

            assertEquals(MessageType.IMAGE, presentation.type)
            val media = presentation.mediaItems.single()
            assertTrue(media.hasLocalFile)
            assertEquals(tempFile.absolutePath, media.url)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * A message's media EXISTS the instant TDLib reports the message --
     * independent of whether its file has finished downloading. A photo
     * message whose file has not arrived yet must still carry a MediaItem
     * (so the Conversation shows a bubble immediately); what the pending
     * state changes is only that [hasLocalFile] is false and [isDownloading]
     * reflects TDLib's real transfer state, never that the item vanishes.
     */
    @Test
    fun aPhotoStillDownloadingCarriesAMediaItemMarkedAsNotYetLocal() {
        val content = TdApi.MessagePhoto().apply {
            photo = TdApi.Photo(
                false,
                null,
                arrayOf(TdApi.PhotoSize("y", pendingFile(3), 800, 600, intArrayOf()))
            )
            caption = TdApi.FormattedText("", emptyArray())
        }

        val presentation = TelegramMappers.mapPresentation(content, 56L, ::resolve)

        assertEquals(MessageType.IMAGE, presentation.type)
        assertEquals(1, presentation.mediaItems.size)
        val media = presentation.mediaItems.single()
        assertTrue(!media.hasLocalFile)
        assertEquals("", media.url)
        assertEquals(3, media.fileId)
        assertEquals(800, media.width)
    }

    @Test
    fun aDownloadTdlibStoppedWithoutFinishingIsReportedAsFailed() {
        val content = TdApi.MessagePhoto().apply {
            photo = TdApi.Photo(
                false,
                null,
                arrayOf(TdApi.PhotoSize("y", pendingFile(9), 800, 600, intArrayOf()))
            )
            caption = TdApi.FormattedText("", emptyArray())
        }

        val presentation = TelegramMappers.mapPresentation(
            content,
            56L,
            resolvePath = ::resolve,
            isDownloadFailed = { it == 9 }
        )

        val media = presentation.mediaItems.single()
        assertTrue(media.downloadFailed)
        assertTrue(!media.hasLocalFile)
    }

    @Test
    fun aVoiceNoteIsPresentedAsVoiceWithItsRealDuration() {
        val content = TdApi.MessageVoiceNote().apply {
            voiceNote = TdApi.VoiceNote(7, byteArrayOf(), "audio/ogg", null, downloadedFile(4, "/data/v.ogg"))
            caption = TdApi.FormattedText("", emptyArray())
        }

        val presentation = TelegramMappers.mapPresentation(content, 57L, ::resolve)

        assertEquals(MessageType.VOICE, presentation.type)
        assertEquals(7, presentation.voiceDurationSec)
    }

    @Test
    fun aDocumentCarriesItsRealNameSizeAndExtension() {
        val content = TdApi.MessageDocument().apply {
            document = TdApi.Document(
                "quarterly-report.pdf",
                "application/pdf",
                null,
                null,
                downloadedFile(5, "/data/report.pdf", size = 2_097_152L)
            )
            caption = TdApi.FormattedText("", emptyArray())
        }

        val presentation = TelegramMappers.mapPresentation(content, 58L, ::resolve)

        assertEquals(MessageType.FILE, presentation.type)
        assertEquals("quarterly-report.pdf", presentation.fileName)
        assertEquals("PDF", presentation.fileExtension)
        assertEquals("2.0 MB", presentation.fileSize)
    }

    @Test
    fun aStickerIsPresentedAsAStickerCarryingItsEmoji() {
        val content = TdApi.MessageSticker().apply {
            sticker = TdApi.Sticker().apply { emoji = "🔥" }
        }
        val presentation = TelegramMappers.mapPresentation(content, 59L, ::resolve)
        assertEquals(MessageType.STICKER, presentation.type)
        assertEquals("🔥", presentation.text)
    }

    // --- waveform ------------------------------------------------------------

    @Test
    fun anAbsentWaveformProducesNoSamplesRatherThanInventedOnes() {
        assertTrue(TelegramMappers.decodeWaveform(null).isEmpty())
        assertTrue(TelegramMappers.decodeWaveform(byteArrayOf()).isEmpty())
    }

    @Test
    fun theWaveformIsUnpackedAsFiveBitSamples() {
        // Two bytes hold three whole 5-bit samples.
        // 11111 00000 11111 0 -> 0xF8, 0x3E
        val samples = TelegramMappers.decodeWaveform(byteArrayOf(0xF8.toByte(), 0x3E.toByte()))

        assertEquals(3, samples.size)
        assertEquals(1f, samples[0], 0.0001f)
        assertEquals(0f, samples[1], 0.0001f)
        assertEquals(1f, samples[2], 0.0001f)
    }

    @Test
    fun everyDecodedSampleIsANormalisedAmplitude() {
        val packed = ByteArray(20) { (it * 37).toByte() }
        val samples = TelegramMappers.decodeWaveform(packed)
        assertEquals(32, samples.size)
        assertTrue(samples.all { it in 0f..1f })
    }

    @Test
    fun aVoiceNoteWaveformSurvivesTheRoundTripIntoTheMessage() {
        val content = TdApi.MessageVoiceNote().apply {
            voiceNote = TdApi.VoiceNote(
                3,
                byteArrayOf(0xF8.toByte(), 0x3E.toByte()),
                "audio/ogg",
                null,
                downloadedFile(6, "/data/v2.ogg")
            )
            caption = TdApi.FormattedText("", emptyArray())
        }
        val presentation = TelegramMappers.mapPresentation(content, 60L, ::resolve)
        assertNotNull(presentation.voiceWaveform)
        assertEquals(3, presentation.voiceWaveform.size)
    }

    @Test
    fun anAudioMessageIsPresentedAsAudioWithPerformerTitleAndDuration() {
        val content = TdApi.MessageAudio().apply {
            audio = TdApi.Audio().apply {
                duration = 180
                performer = "The Performer"
                title = "Song Title"
                fileName = "song.mp3"
                mimeType = "audio/mpeg"
                audio = downloadedFile(10, "/data/song.mp3", size = 4_194_304L)
            }
            caption = TdApi.FormattedText("Listen to this", emptyArray())
        }

        val presentation = TelegramMappers.mapPresentation(content, 61L, ::resolve)

        assertEquals(MessageType.AUDIO, presentation.type)
        assertEquals("Listen to this", presentation.text)
        assertEquals("The Performer — Song Title", presentation.fileName)
        assertEquals(180, presentation.voiceDurationSec)
        assertEquals("4.0 MB", presentation.fileSize)
    }

    @Test
    fun anAnimationMessageIsPresentedAsAnimation() {
        val content = TdApi.MessageAnimation().apply {
            animation = TdApi.Animation().apply {
                duration = 5
                width = 320
                height = 240
                fileName = "cat.gif"
                mimeType = "image/gif"
                animation = downloadedFile(11, "/data/cat.gif")
            }
            caption = TdApi.FormattedText("funny cat", emptyArray())
        }

        val presentation = TelegramMappers.mapPresentation(content, 62L, ::resolve)

        assertEquals(MessageType.ANIMATION, presentation.type)
        assertEquals("funny cat", presentation.text)
    }

    @Test
    fun aContactMessageIsPresentedAsContact() {
        val content = TdApi.MessageContact().apply {
            contact = TdApi.Contact("+1234567890", "Jane", "Doe", "", 0)
        }

        val presentation = TelegramMappers.mapPresentation(content, 63L, ::resolve)

        assertEquals(MessageType.CONTACT, presentation.type)
        assertEquals("Jane Doe", presentation.text)
    }

    @Test
    fun aLocationMessageIsPresentedAsLocation() {
        val content = TdApi.MessageLocation().apply {
            location = TdApi.Location(37.7749, -122.4194, 0.0)
        }

        val presentation = TelegramMappers.mapPresentation(content, 64L, ::resolve)

        assertEquals(MessageType.LOCATION, presentation.type)
        assertEquals("Location", presentation.text)
    }

    @Test
    fun autoDeleteInAndSelfDestructInArePassedThroughToDomainMessage() {
        val tdMessage = TdApi.Message().apply {
            id = 12345L
            chatId = 67890L
            senderId = TdApi.MessageSenderUser(1L)
            date = 1700000000
            content = TdApi.MessageText(TdApi.FormattedText("Secret", emptyArray()), null, null)
            autoDeleteIn = 3600.0
            selfDestructIn = 30.0
        }

        val message = TelegramMappers.mapMessage(
            message = tdMessage,
            users = emptyMap(),
            chats = emptyMap(),
            myUserId = 1L,
            lastReadOutboxMessageId = 0L,
            resolvePath = { null }
        )

        assertEquals(3600.0, message.autoDeleteIn, 0.001)
        assertEquals(30.0, message.selfDestructIn, 0.001)
    }

    @Test
    fun messageInfoSheetShowsAutoDeleteAndDurationRows() {
        val message = com.foresightlabs.aether.domain.model.Message(
            id = "1",
            chatId = "100",
            senderId = "1",
            senderName = "Alice",
            text = "Track",
            timestamp = "12:00",
            type = MessageType.AUDIO,
            voiceDurationSec = 125,
            autoDeleteIn = 300.0,
            isOutgoing = true
        )

        val rows = com.foresightlabs.aether.ui.components.infoRows(
            message = message,
            capabilities = com.foresightlabs.aether.domain.messages.MessageCapabilities.Unknown
        )

        assertTrue(rows.any { it.first == "Duration" && it.second == "2:05" })
        assertTrue(rows.any { it.first == "Auto-delete in" && it.second == "5:00" })
    }
}
