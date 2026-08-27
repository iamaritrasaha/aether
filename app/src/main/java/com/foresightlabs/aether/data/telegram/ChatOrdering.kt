package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi

data class ChatListPosition(
    val order: Long,
    val isPinned: Boolean
)

object ChatOrdering {
    /**
     * The chat's position within a specific Telegram folder, if it is in one.
     *
     * Folder membership is an *additional* [TdApi.ChatPosition], not a replacement
     * for the main one, so a chat in a folder still appears in the main list — which
     * is why omitting folder support never hid a conversation.
     */
    fun folderPosition(
        positions: Array<TdApi.ChatPosition>?,
        folderId: Int
    ): ChatListPosition? {
        val match = positions?.firstOrNull { position ->
            (position.list as? TdApi.ChatListFolder)?.chatFolderId == folderId &&
                position.order != 0L
        } ?: return null
        return ChatListPosition(order = match.order, isPinned = match.isPinned)
    }

    /** Whether Telegram currently holds this chat in the archive list. */
    fun isArchived(positions: Array<TdApi.ChatPosition>?): Boolean =
        positions?.any { it.list is TdApi.ChatListArchive && it.order != 0L } == true

    fun mainPosition(positions: Array<TdApi.ChatPosition>?): ChatListPosition? {
        if (positions == null) return null
        var best: TdApi.ChatPosition? = null
        for (position in positions) {
            if (position.list is TdApi.ChatListMain) {
                if (best == null || unsignedGreater(position.order, best.order)) {
                    best = position
                }
            }
        }
        return best?.let { ChatListPosition(order = it.order, isPinned = it.isPinned) }
    }

    fun compare(leftOrder: Long, rightOrder: Long): Int {
        return java.lang.Long.compareUnsigned(rightOrder, leftOrder)
    }

    fun unsignedGreater(a: Long, b: Long): Boolean {
        return java.lang.Long.compareUnsigned(a, b) > 0
    }

    fun isInMainList(order: Long): Boolean = order != 0L
}
