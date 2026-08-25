package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi

data class ChatListPosition(
    val order: Long,
    val isPinned: Boolean
)

object ChatOrdering {
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
