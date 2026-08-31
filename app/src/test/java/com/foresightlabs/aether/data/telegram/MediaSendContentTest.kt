package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the actual TdApi request built for a photo/video send, not just UI
 * state: a view-once send must produce
 * `selfDestructType = TdApi.MessageSelfDestructTypeImmediately`, the real
 * TDLib/Telegram construct for "opens once, then self-destructs" -- and a
 * normal send must leave `selfDestructType` null, i.e. completely unchanged
 * from before view-once existed.
 */
class MediaSendContentTest {

    @Test
    fun `normal photo has no self-destruct type`() {
        val content = MediaSendContent.photo("/tmp/photo.jpg", "caption", viewOnce = false)
        assertNull(content.selfDestructType)
        assertEquals("caption", content.caption?.text)
    }

    @Test
    fun `view-once photo carries MessageSelfDestructTypeImmediately`() {
        val content = MediaSendContent.photo("/tmp/photo.jpg", "", viewOnce = true)
        assertTrue(content.selfDestructType is TdApi.MessageSelfDestructTypeImmediately)
    }

    @Test
    fun `normal video has no self-destruct type`() {
        val content = MediaSendContent.video("/tmp/video.mp4", "caption", duration = 5, width = 100, height = 200, viewOnce = false)
        assertNull(content.selfDestructType)
        assertEquals(5, content.duration)
    }

    @Test
    fun `view-once video carries MessageSelfDestructTypeImmediately`() {
        val content = MediaSendContent.video("/tmp/video.mp4", "", duration = 0, width = 0, height = 0, viewOnce = true)
        assertTrue(content.selfDestructType is TdApi.MessageSelfDestructTypeImmediately)
    }

    /**
     * TDLib documents `selfDestructType` as "private chats only" and exposes it on
     * the single-media input types; there is no album equivalent. Aether cannot
     * send a malformed combination because the two paths do not meet: the album
     * send takes no view-once flag at all, and the view-once review surface holds
     * exactly one item. Asserted structurally, because the guarantee is the
     * absence of a parameter -- there is no runtime state that could express the
     * illegal combination for a test to exercise.
     */
    @Test
    fun `the album send path cannot carry a view-once flag`() {
        val client = listOf(
            File("src/main/java/com/foresightlabs/aether/data/telegram/TelegramClient.kt"),
            File("app/src/main/java/com/foresightlabs/aether/data/telegram/TelegramClient.kt")
        ).first { it.exists() }.readText()
        val albumSignature = client.substringAfter("suspend fun sendPhotoAlbum(").substringBefore("): Result")
        assertFalse(
            "sendPhotoAlbum must not accept a view-once flag",
            albumSignature.contains("viewOnce")
        )
    }

    @Test
    fun `the media review surface holds exactly one item`() {
        val curtain = listOf(
            File("src/main/java/com/foresightlabs/aether/ui/conversation/ConversationCurtain.kt"),
            File("app/src/main/java/com/foresightlabs/aether/ui/conversation/ConversationCurtain.kt")
        ).first { it.exists() }.readText()
        val pendingMedia = curtain.substringAfter("data class PendingMedia(").substringBefore(")")
        // One path, one isVideo, one viewOnce -- no collection to multi-select into.
        assertFalse("PendingMedia must not hold a list", pendingMedia.contains("List<"))
        assertTrue(pendingMedia.contains("val path: String"))
    }

    @Test
    fun `view-once toggle does not affect any other field`() {
        val normal = MediaSendContent.photo("/tmp/photo.jpg", "hello", viewOnce = false)
        val viewOnce = MediaSendContent.photo("/tmp/photo.jpg", "hello", viewOnce = true)
        assertEquals((normal.photo as TdApi.InputFileLocal).path, (viewOnce.photo as TdApi.InputFileLocal).path)
        assertEquals(normal.caption?.text, viewOnce.caption?.text)
        assertEquals(normal.hasSpoiler, viewOnce.hasSpoiler)
        assertEquals(normal.showCaptionAboveMedia, viewOnce.showCaptionAboveMedia)
    }
}
