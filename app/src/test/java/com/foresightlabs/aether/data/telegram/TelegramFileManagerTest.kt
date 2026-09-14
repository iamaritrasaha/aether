package com.foresightlabs.aether.data.telegram

import androidx.media3.datasource.DataSpec
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelegramFileManagerTest {
    @Test
    fun partialExistingBytesAreNeverComplete() {
        val partial = File.createTempFile("tdlib-partial", ".part").apply { writeBytes(ByteArray(1_200) { 1 }) }
        try {
            val manager = manager { TdApi.Error(400, "unused") }
            manager.seed(file(123, partial, completed = false, active = false, downloadedSize = 1_200))

            val state = manager.get(123)
            assertTrue(state is TelegramFileManager.State.NotDownloaded)
            assertFalse(state is TelegramFileManager.State.Downloaded)
        } finally {
            partial.delete()
        }
    }

    @Test
    fun media3OpensTheFinalUpdatePathInsteadOfThePartialMessagePath() {
        val partial = File.createTempFile("tdlib-partial", ".part").apply { writeBytes(byteArrayOf(1, 1, 1)) }
        val final = File.createTempFile("tdlib-final", ".ogg").apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }
        lateinit var manager: TelegramFileManager
        val initial = file(123, partial, completed = false, active = false, downloadedSize = partial.length())
        val completed = file(123, final, completed = true, active = false, downloadedSize = final.length())
        manager = manager { request ->
            when (request) {
                is TdApi.GetFile -> initial
                is TdApi.DownloadFile -> {
                    manager.update(completed) // independent UpdateFile, no message update
                    completed
                }
                else -> TdApi.Error(400, "unexpected")
            }
        }
        manager.seed(initial)

        val source = TelegramDataSource(manager)
        val opened = source.open(DataSpec.Builder().setUri(TelegramDataSource.uri(123)).build())
        val bytes = ByteArray(4)
        val count = source.read(bytes, 0, bytes.size)
        source.close()

        assertEquals(4L, opened)
        assertEquals(4, count)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), bytes)
        assertEquals(final.absolutePath, (manager.get(123) as TelegramFileManager.State.Downloaded).path)
        partial.delete()
        final.delete()
    }

    @Test
    fun photoVideoDocumentAudioAndVoiceObserveIndependentCompletion() {
        val manager = manager { TdApi.Error(400, "unused") }
        val mediaKinds = listOf("photo", "video", "document", "audio", "voice")
        val files = mediaKinds.mapIndexed { index, kind ->
            File.createTempFile("tdlib-$kind", ".bin").apply { writeBytes(byteArrayOf(index.toByte(), 1)) }
        }
        try {
            mediaKinds.indices.forEach { index ->
                val id = 200 + index
                manager.seed(file(id, files[index], completed = false, active = false, downloadedSize = 2))
                assertFalse(manager.get(id) is TelegramFileManager.State.Downloaded)

                manager.update(file(id, files[index], completed = true, active = false, downloadedSize = 2))
                assertTrue("${mediaKinds[index]} did not observe UpdateFile", manager.get(id) is TelegramFileManager.State.Downloaded)
            }
        } finally {
            files.forEach(File::delete)
        }
    }

    private fun manager(request: suspend (TdApi.Function<*>) -> TdApi.Object) =
        TelegramFileManager(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), request)

    private fun file(
        id: Int,
        path: File,
        completed: Boolean,
        active: Boolean,
        downloadedSize: Long
    ) = TdApi.File().apply {
        this.id = id
        size = path.length()
        expectedSize = path.length()
        local = TdApi.LocalFile().apply {
            this.path = path.absolutePath
            isDownloadingCompleted = completed
            isDownloadingActive = active
            canBeDownloaded = true
            this.downloadedSize = downloadedSize
            downloadedPrefixSize = downloadedSize
        }
    }
}
