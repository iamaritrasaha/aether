package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestFileRegistryTest {
    private fun file(id: Int, path: String, complete: Boolean) = TdApi.File().apply {
        this.id = id
        local = TdApi.LocalFile().apply {
            this.path = path
            isDownloadingCompleted = complete
        }
    }

    @Test
    fun anUpdateFileReplacesTheSnapshotUsedByLaterMessageMapping() {
        val registry = LatestFileRegistry()
        val embedded = file(42, "/partial/42.part", complete = false)
        val completed = file(42, "/final/42.ogg", complete = true)

        registry.seed(embedded)
        registry.update(completed)

        assertSame(completed, registry.resolve(embedded))
        assertEquals("/final/42.ogg", registry.resolve(embedded)?.local?.path)
    }

    @Test
    fun aLateOldMessageSnapshotCannotOverwriteAnUpdateFile() {
        val registry = LatestFileRegistry()
        val completed = file(7, "/final/7.jpg", complete = true)
        val stale = file(7, "/partial/7.part", complete = false)

        registry.update(completed)
        registry.seed(stale)

        assertSame(completed, registry.get(7))
    }

    @Test
    fun anUpdateFileMakesAnAlreadyMappedVoiceMessagePlayableWithoutResendingMessage() {
        val finalFile = java.io.File.createTempFile("voice-final", ".ogg")
        finalFile.writeBytes(ByteArray(256) { 1 })
        try {
            val registry = LatestFileRegistry()
            val stale = file(99, "/partial/99.part", complete = false).apply {
                size = 256
                expectedSize = 256
            }
            val completed = file(99, finalFile.absolutePath, complete = true).apply {
                size = 256
                expectedSize = 256
            }
            val voice = TdApi.MessageVoiceNote().apply {
                voiceNote = TdApi.VoiceNote(2, byteArrayOf(), "audio/ogg", null, stale)
            }
            registry.update(completed)

            val mapped = TelegramMappers.mapPresentation(
                voice,
                123L,
                resolvePath = { TelegramMappers.localPath(registry.resolve(it)) },
                resolveContentPath = { TelegramMappers.localPath(registry.resolve(it)) }
            )

            assertTrue(mapped.mediaItems.single().hasLocalFile)
            assertEquals(finalFile.absolutePath, mapped.mediaItems.single().url)
        } finally {
            finalFile.delete()
        }
    }
}
