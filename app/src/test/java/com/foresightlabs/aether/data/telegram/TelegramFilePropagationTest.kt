package com.foresightlabs.aether.data.telegram

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Models TDLib's real ordering: the message arrives once with a stale File,
 * then UpdateFile supplies the completed local snapshot independently.
 */
@RunWith(AndroidJUnit4::class)
class TelegramFilePropagationTest {
    @Test
    fun updateFileRemapsAnAlreadyVisibleVoiceMessageWithoutResendingTheMessage() = runBlocking {
        val finalFile = File.createTempFile("telegram-voice-final", ".ogg")
        finalFile.writeBytes(ByteArray(256) { 1 })
        try {
            val staleFile = TdApi.File().apply {
                id = 701
                size = 256
                expectedSize = 256
                local = TdApi.LocalFile().apply {
                    path = "/partial/701.ogg.part"
                    isDownloadingCompleted = false
                    canBeDownloaded = true
                }
            }
            val message = TdApi.Message().apply {
                id = 9001L
                chatId = 42L
                date = 1
                senderId = TdApi.MessageSenderUser(7L)
                content = TdApi.MessageVoiceNote().apply {
                    voiceNote = TdApi.VoiceNote(2, byteArrayOf(), "audio/ogg", null, staleFile)
                }
            }
            val completedFile = TdApi.File().apply {
                id = 701
                size = 256
                expectedSize = 256
                local = TdApi.LocalFile().apply {
                    path = finalFile.absolutePath
                    isDownloadingCompleted = true
                    downloadedSize = 256
                    downloadedPrefixSize = 256
                }
            }
            val client = TelegramClient(ApplicationProvider.getApplicationContext())

            client.handleUpdate(TdApi.UpdateNewMessage(message))
            val before = client.messagesFlow(42L).value.single().mediaItems.single()
            assertEquals(701, before.fileId)
            assertFalse(before.hasLocalFile)

            client.handleUpdate(TdApi.UpdateFile(completedFile))
            val after = client.messagesFlow(42L).value.single().mediaItems.single()
            assertTrue(after.hasLocalFile)
            assertEquals(finalFile.absolutePath, after.url)
        } finally {
            finalFile.delete()
        }
    }
}
