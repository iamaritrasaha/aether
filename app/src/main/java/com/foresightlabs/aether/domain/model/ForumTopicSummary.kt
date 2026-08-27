package com.foresightlabs.aether.domain.model

import androidx.compose.runtime.Immutable

/**
 * One topic of a forum supergroup, as Telegram reports it.
 *
 * A forum's topics are separate conversations that happen to share a chat id. Every
 * field here is server state — ordering, unread counts and the draft all belong to
 * the topic, not to the chat, which is exactly why routing them through the chat is
 * wrong.
 */
@Immutable
data class ForumTopicSummary(
    val chatId: Long,
    val topicId: Int,
    val name: String,
    /** The forum's built-in "General" topic, which cannot be deleted. */
    val isGeneral: Boolean = false,
    val isClosed: Boolean = false,
    val isHidden: Boolean = false,
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val unreadMentionCount: Int = 0,
    val order: Long = 0L,
    val lastMessagePreview: String = "",
    val draftText: String? = null,
    val isMuted: Boolean = false
) {
    val hasUnread: Boolean get() = unreadCount > 0
}
