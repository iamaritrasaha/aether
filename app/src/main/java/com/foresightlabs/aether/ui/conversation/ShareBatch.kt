package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.domain.sharing.SharedAttachmentKind

/** One item of a mixed share, ready for the sequential batch sender. */
data class ShareBatchItem(val path: String, val kind: SharedAttachmentKind)

/**
 * Sends a mixed share's items one at a time, in the order the user attached
 * them, under a single in-flight guard (see ConversationViewModel.sendSharedBatch
 * for why the per-item senders' guard cannot do this).
 *
 * Failure semantics follow the existing send UX: a failed item surfaces its
 * error through [onError] exactly like a failed single send, and the REST
 * STILL SEND -- the same independence a photo album's members have and the
 * same convention the previous per-item share path intended. Nothing is
 * retried here and nothing is sent twice: each item is attempted exactly
 * once, in order.
 *
 * [caption] (and [replyToMessageId]) attach to the FIRST item only --
 * Telegram captions a group from its first member; a lone item keeps the
 * caption either way.
 */
internal suspend fun sendShareBatch(
    items: List<ShareBatchItem>,
    caption: String,
    replyToMessageId: Long?,
    send: suspend (item: ShareBatchItem, caption: String, replyToMessageId: Long?) -> Result<*>,
    onError: (String) -> Unit
) {
    items.forEachIndexed { index, item ->
        val isFirst = index == 0
        val itemCaption = if (isFirst) caption else ""
        val itemReply = replyToMessageId.takeIf { isFirst }
        val result = send(item, itemCaption, itemReply)
        result.exceptionOrNull()?.message?.let(onError)
    }
}
