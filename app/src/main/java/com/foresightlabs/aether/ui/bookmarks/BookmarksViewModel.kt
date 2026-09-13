package com.foresightlabs.aether.ui.bookmarks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.data.local.BookmarkStore
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.ui.conversation.ReplyEditTargetOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One bookmark as the list shows it. The snippet is resolved on demand --
 * only the stable identity lives on disk -- and a message Telegram no longer
 * has is reported as such, never silently dropped.
 */
data class BookmarkRow(
    val chatId: Long,
    val messageId: Long,
    val savedAtSec: Int,
    /** Null while the chat title is still being resolved. */
    val chatTitle: String?,
    /** Null while resolving; a snippet or a type label once resolved. */
    val preview: String?,
    /** Telegram has no such message (deleted, or the chat is gone). */
    val isMissing: Boolean = false,
    val chatIsMissing: Boolean = false,
    /** The preview could not be fetched right now (typically offline); the row still opens. */
    val previewUnavailable: Boolean = false
)

/**
 * Backs the Bookmarks surface: every message this account bookmarked locally,
 * newest first, with just enough resolved context to recognize each one.
 *
 * The list itself is published straight from the store -- removing a row is
 * immediate -- while titles and previews resolve beside it, at most once per
 * identity. Nothing resolved here is written back to disk.
 */
class BookmarksViewModel(application: Application) : AndroidViewModel(application) {

    private val telegram = (application as AetherApplication).telegram
    private val bookmarkStore = (application as AetherApplication).bookmarkStore
    private val accountId = telegram.getMyUserId()

    private sealed interface Preview {
        data class Found(val text: String) : Preview
        data object Missing : Preview
        data object Unavailable : Preview
    }

    private val previews = MutableStateFlow<Map<String, Preview>>(emptyMap())

    /** Chat id to its title; a null value means TDLib does not know the chat. */
    private val chatTitles = MutableStateFlow<Map<Long, String?>>(emptyMap())

    // Main-thread only (viewModelScope), so plain sets suffice.
    private val previewsInFlight = HashSet<String>()
    private val titlesInFlight = HashSet<Long>()

    /** Null until the store has answered, so an empty state never flashes for a non-empty list. */
    val rows: StateFlow<List<BookmarkRow>?> = combine(
        bookmarkStore.bookmarksFor(accountId),
        previews,
        chatTitles
    ) { bookmarks, previewMap, titles ->
        bookmarks.sortedByDescending { it.savedAtSec }.map { bookmark ->
            val preview = previewMap[keyOf(bookmark.chatId, bookmark.messageId)]
            BookmarkRow(
                chatId = bookmark.chatId,
                messageId = bookmark.messageId,
                savedAtSec = bookmark.savedAtSec,
                chatTitle = titles[bookmark.chatId],
                preview = (preview as? Preview.Found)?.text,
                isMissing = preview == Preview.Missing,
                chatIsMissing = titles.containsKey(bookmark.chatId) && titles[bookmark.chatId] == null,
                previewUnavailable = preview == Preview.Unavailable
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            bookmarkStore.bookmarksFor(accountId).collect { bookmarks ->
                bookmarks.forEach { resolve(it) }
            }
        }
    }

    private fun resolve(bookmark: BookmarkStore.Bookmark) {
        val chatId = bookmark.chatId
        if (!chatTitles.value.containsKey(chatId) && titlesInFlight.add(chatId)) {
            viewModelScope.launch {
                val title = telegram.chat(chatId)?.title
                    ?: runCatching { telegram.ensureChatLoaded(chatId) }.getOrNull()?.title
                chatTitles.update { it + (chatId to title) }
                titlesInFlight.remove(chatId)
            }
        }

        val key = keyOf(chatId, bookmark.messageId)
        val known = previews.value[key]
        // Found and Missing are final; Unavailable is retried on the next list change.
        if (known is Preview.Found || known == Preview.Missing) return
        if (!previewsInFlight.add(key)) return
        viewModelScope.launch {
            val outcome = runCatching { telegram.resolveReplyEditTarget(chatId, bookmark.messageId) }.getOrNull()
            val preview = when (outcome) {
                is ReplyEditTargetOutcome.Resolved -> Preview.Found(previewFor(outcome.message))
                // Deleted (or never existed): the row renders its honest state
                // instead of spinning forever.
                is ReplyEditTargetOutcome.Missing -> Preview.Missing
                // Transient (typically network): the row still opens.
                is ReplyEditTargetOutcome.Unavailable, null -> Preview.Unavailable
            }
            previews.update { it + (key to preview) }
            previewsInFlight.remove(key)
        }
    }

    private fun keyOf(chatId: Long, messageId: Long) = "$chatId:$messageId"

    private fun previewFor(message: Message): String =
        when {
            message.text.isNotBlank() -> message.text.take(PREVIEW_MAX_CHARS)
            else -> when (message.type) {
                MessageType.IMAGE -> "Photo"
                MessageType.VIDEO -> "Video"
                MessageType.VOICE -> "Voice message"
                MessageType.AUDIO -> "Audio"
                MessageType.FILE -> message.fileName ?: "File"
                MessageType.STICKER -> "Sticker"
                MessageType.ANIMATION -> "GIF"
                MessageType.VIDEO_NOTE -> "Video message"
                MessageType.LOCATION -> "Location"
                MessageType.VENUE -> "Place"
                MessageType.CONTACT -> "Contact"
                MessageType.POLL -> "Poll"
                else -> "Message"
            }
        }

    /** Removes one bookmark, from the list itself. */
    fun remove(row: BookmarkRow) {
        viewModelScope.launch { bookmarkStore.remove(accountId, row.chatId, row.messageId) }
    }

    private companion object {
        /** Two lines of preview never need more than this; the rest stays in TDLib. */
        const val PREVIEW_MAX_CHARS = 240
    }
}
