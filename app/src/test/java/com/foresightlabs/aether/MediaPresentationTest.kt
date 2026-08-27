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
    fun aPhotoStillDownloadingCarriesNoMediaItemRatherThanAPlaceholder() {
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
        assertTrue(presentation.mediaItems.isEmpty())
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
}
