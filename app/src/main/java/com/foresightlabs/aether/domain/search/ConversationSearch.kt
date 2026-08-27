package com.foresightlabs.aether.domain.search

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.domain.model.Message

/**
 * State of a search running inside one conversation.
 *
 * Results are whole messages returned by the server, held newest-first exactly as
 * TDLib returned them. [cursor] is TDLib's own continuation token; Aether never
 * derives its own, because the server's ordering is the only one that stays correct
 * as the chat changes underneath the search.
 */
@Immutable
data class ConversationSearchState(
    val query: String = "",
    val isActive: Boolean = false,
    val isLoading: Boolean = false,
    val results: List<Message> = emptyList(),
    /** Index into [results], or -1 when nothing is selected. */
    val selectedIndex: Int = -1,
    /** Total the server reports, which may exceed what has been paged in. */
    val totalCount: Int = 0,
    val cursor: Long = 0L,
    val hasMore: Boolean = false,
    val error: String? = null
) {
    val currentResult: Message? get() = results.getOrNull(selectedIndex)

    val hasResults: Boolean get() = results.isNotEmpty()

    /** True only once a search has genuinely run and come back with nothing. */
    val isEmptyResult: Boolean
        get() = isActive && query.isNotBlank() && !isLoading && results.isEmpty() && error == null

    /**
     * Human-readable position, e.g. "3 of 128".
     *
     * Uses the server's total rather than the number paged in so far, so the count
     * does not appear to grow as the user steps through.
     */
    val positionLabel: String?
        get() {
            if (!hasResults || selectedIndex < 0) return null
            val total = if (totalCount > 0) totalCount else results.size
            return "${selectedIndex + 1} of $total"
        }

    /** Whether stepping back to an older result is possible right now. */
    val canGoOlder: Boolean get() = hasResults && (selectedIndex < results.lastIndex || hasMore)

    /** Whether stepping forward to a newer result is possible right now. */
    val canGoNewer: Boolean get() = hasResults && selectedIndex > 0

    companion object {
        val Idle = ConversationSearchState()
    }
}
