package com.foresightlabs.aether.data.telegram

import com.foresightlabs.aether.domain.model.MessageType
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What a voice note's bubble is told about its file. Playback trusts
 * `hasLocalFile`/`url` completely, so they must describe ALL of the voice
 * file's bytes -- never the partial part TDLib reports while a download runs,
 * and never a snapshot's stale transfer flags.
 */
class VoicePlaybackMappingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun voiceNote(file: TdApi.File) = TdApi.MessageVoiceNote().apply {
        voiceNote = TdApi.VoiceNote().apply {
            duration = 7
            waveform = ByteArray(0)
            mimeType = "audio/ogg"
            voice = file
        }
        caption = TdApi.FormattedText("", emptyArray())
    }

    private fun file(
        id: Int,
        path: String,
        size: Long,
        completed: Boolean,
        active: Boolean
    ) = TdApi.File().apply {
        this.id = id
        this.size = size
        expectedSize = size
        local = TdApi.LocalFile().apply {
            this.path = path
            isDownloadingCompleted = completed
            isDownloadingActive = active
            canBeDownloaded = true
            downloadedSize = if (path.isBlank()) 0L else java.io.File(path).length()
        }
        remote = TdApi.RemoteFile().apply { isUploadingCompleted = true }
    }

    private fun present(
        content: TdApi.MessageContent,
        isDownloadActive: (Int) -> Boolean? = { null }
    ) = TelegramMappers.mapPresentation(
        content = content,
        messageId = 501L,
        resolvePath = TelegramMappers::localPath,
        resolveContentPath = TelegramMappers::localPath,
        isDownloadActive = isDownloadActive
    )

    @Test
    fun aVoiceNoteMidDownloadIsNotPlayableEvenThoughItsPartIsOnDisk() {
        val part = tmp.newFile("part_voice").apply { writeBytes(ByteArray(10) { 7 }) }
        val presentation = present(voiceNote(file(42, part.absolutePath, size = 4_000L, completed = false, active = true)))

        val item = presentation.mediaItems.single()
        assertEquals(MessageType.VOICE, presentation.type)
        assertFalse("a partial part must never reach the player", item.hasLocalFile)
        assertEquals("", item.url)
        assertEquals(42, item.fileId)
    }

    @Test
    fun aDownloadThatStoppedShortIsStillNotPlayable() {
        val part = tmp.newFile("stopped_voice").apply { writeBytes(ByteArray(10) { 7 }) }
        val item = present(voiceNote(file(42, part.absolutePath, size = 4_000L, completed = false, active = false)))
            .mediaItems.single()
        assertFalse(item.hasLocalFile)
    }

    @Test
    fun aCompletedVoiceNoteIsPlayableFromItsOwnVoiceFile() {
        val oga = tmp.newFile("voice_42.oga").apply { writeBytes(ByteArray(4_000) { 1 }) }
        val item = present(voiceNote(file(42, oga.absolutePath, size = 4_000L, completed = true, active = false)))
            .mediaItems.single()

        assertTrue(item.hasLocalFile)
        assertEquals(oga.absolutePath, item.url)
        assertEquals("the item is keyed by the voice file itself", "501:42", item.id)
    }

    @Test
    fun theClientsLiveDownloadStateBeatsTheStaleSnapshot() {
        val pending = voiceNote(file(42, "", size = 4_000L, completed = false, active = true))
        assertFalse(
            "TDLib finished (or never started) even though the snapshot still says active",
            present(pending) { false }.mediaItems.single().isDownloading
        )

        val idleSnapshot = voiceNote(file(43, "", size = 4_000L, completed = false, active = false))
        assertTrue(present(idleSnapshot) { true }.mediaItems.single().isDownloading)
        assertFalse("no live view: the snapshot is all there is", present(idleSnapshot).mediaItems.single().isDownloading)
    }

    @Test
    fun aFullSizedLocalFileCountsWithoutTdlibsCompletionFlag() {
        val picked = tmp.newFile("picked.jpg").apply { writeBytes(ByteArray(2_048) { 3 }) }
        val outgoing = file(9, picked.absolutePath, size = 2_048L, completed = false, active = false)
        assertTrue("a file picked on this device, before upload, is whole", TelegramMappers.isFullyLocal(outgoing))
        assertEquals(picked.absolutePath, TelegramMappers.localPath(outgoing))
    }
}
