package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The receive-side half of view-once: [TelegramMappers.mapMessage] must read
 * TDLib's own `selfDestructType` from the real [TdApi.Message], not infer it
 * from anything client-side -- see [MediaSendContentTest] for the send side.
 *
 * This is the fix for the actual reported failure: sending genuinely worked
 * (confirmed by TDLib's own SendMessage response carrying
 * MessageSelfDestructTypeImmediately back), but Aether never read that field
 * on any message, sent or received, so nothing in the app ever looked
 * different from an ordinary photo.
 */
class ViewOnceMappingTest {

    private fun photoMessage(selfDestructType: TdApi.MessageSelfDestructType?): TdApi.Message {
        val localFile = TdApi.LocalFile("/path/to/photo.jpg", true, true, false, true, 0, 0, 0)
        val file = TdApi.File(101, 1024, 1024, localFile, null)
        val size = TdApi.PhotoSize("x", file, 800, 600, intArrayOf())
        val photo = TdApi.Photo(false, null, arrayOf(size))
        return TdApi.Message().apply {
            id = 1L
            chatId = 100L
            senderId = TdApi.MessageSenderUser(42L)
            date = 1600000000
            content = TdApi.MessagePhoto(photo, null, TdApi.FormattedText("", emptyArray()), false, false, false)
            this.selfDestructType = selfDestructType
        }
    }

    @Test
    fun viewOnceSelfDestructTypeMapsToIsViewOnceTrue() {
        val tdMsg = photoMessage(TdApi.MessageSelfDestructTypeImmediately())
        val mapped = TelegramMappers.mapMessage(
            message = tdMsg, users = emptyMap(), chats = emptyMap(), myUserId = 42L, lastReadOutboxMessageId = 0L
        )
        assertTrue(mapped.isViewOnce)
    }

    @Test
    fun timerSelfDestructTypeDoesNotMapToViewOnce() {
        // A timed self-destruct (delete N seconds after opening) is a real,
        // different TDLib concept from "opens once" -- must not be conflated.
        val tdMsg = photoMessage(TdApi.MessageSelfDestructTypeTimer(30))
        val mapped = TelegramMappers.mapMessage(
            message = tdMsg, users = emptyMap(), chats = emptyMap(), myUserId = 42L, lastReadOutboxMessageId = 0L
        )
        assertFalse(mapped.isViewOnce)
    }

    @Test
    fun noSelfDestructTypeMapsToIsViewOnceFalse() {
        val tdMsg = photoMessage(null)
        val mapped = TelegramMappers.mapMessage(
            message = tdMsg, users = emptyMap(), chats = emptyMap(), myUserId = 42L, lastReadOutboxMessageId = 0L
        )
        assertFalse(mapped.isViewOnce)
    }
}
