package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi

/**
 * Builds the `TdApi.InputMessagePhoto` / `TdApi.InputMessageVideo` content
 * [TelegramClient.sendPhoto] and [TelegramClient.sendVideo] send.
 *
 * Split into pure functions so the one thing view-once sending depends on --
 * `selfDestructType` becoming [TdApi.MessageSelfDestructTypeImmediately] when
 * requested and `null` otherwise, per TDLib's pinned revision (private chats
 * only; "can be opened only once and will be self-destructed once closed") --
 * is testable without a live TDLib client.
 */
internal object MediaSendContent {
    fun photo(photoPath: String, caption: String, viewOnce: Boolean): TdApi.InputMessagePhoto =
        TdApi.InputMessagePhoto(
            TdApi.InputFileLocal(photoPath),
            null,
            null,
            intArrayOf(),
            0,
            0,
            TdApi.FormattedText(caption, emptyArray()),
            false,
            if (viewOnce) TdApi.MessageSelfDestructTypeImmediately() else null,
            false
        )

    fun video(
        videoPath: String,
        caption: String,
        duration: Int,
        width: Int,
        height: Int,
        viewOnce: Boolean
    ): TdApi.InputMessageVideo =
        TdApi.InputMessageVideo(
            TdApi.InputFileLocal(videoPath),
            null,
            null,
            0,
            intArrayOf(),
            duration,
            width,
            height,
            true,
            TdApi.FormattedText(caption, emptyArray()),
            false,
            if (viewOnce) TdApi.MessageSelfDestructTypeImmediately() else null,
            false
        )
}
