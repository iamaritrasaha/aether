package com.foresightlabs.aether.domain.search

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.Message

/** A message found by global search, with the conversation it belongs to. */
@Immutable
data class GlobalMessageHit(
    val message: Message,
    val chat: Chat?
) {
    val chatTitle: String get() = chat?.title ?: "Unknown chat"
}

/**
 * Results of a global search, kept in distinct categories.
 *
 * Telegram answers three different questions here — which conversations match, which
 * people match, and which *messages* match — from three different endpoints. Merging
 * them into one list would lose the distinction and, worse, invite filling the gaps
 * locally when one endpoint has not answered yet.
 */
@Immutable
data class GlobalSearchState(
    val query: String = "",
    val chats: List<Chat> = emptyList(),
    val contacts: List<Chat> = emptyList(),
    val messages: List<GlobalMessageHit> = emptyList(),
    val isLoadingChats: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val messagesCursor: String = "",
    val hasMoreMessages: Boolean = false,
    val messagesTotal: Int = 0,
    val error: String? = null
) {
    val isLoading: Boolean get() = isLoadingChats || isLoadingMessages

    val hasAnyResult: Boolean
        get() = chats.isNotEmpty() || contacts.isNotEmpty() || messages.isNotEmpty()

    /**
     * True only when every source has answered and none of them found anything.
     *
     * Reporting "no results" while a request is still outstanding is the single
     * easiest way to make a working search look broken.
     */
    val isEmptyResult: Boolean
        get() = query.isNotBlank() && !isLoading && !hasAnyResult && error == null

    companion object {
        val Idle = GlobalSearchState()
    }
}
