package com.foresightlabs.aether.domain.presence

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Presence

/**
 * A real person Aether can show in the presence strip, always tied to an existing
 * conversation so tapping the avatar has somewhere truthful to go.
 */
@Immutable
data class ActivePerson(
    val chat: Chat,
    val presence: Presence,
    val hasUnseenPulse: Boolean = chat.hasUnseenPulse
) {
    val id: String get() = chat.id
    val name: String get() = chat.directUser?.name ?: chat.title
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name }
}

/**
 * What Aether can honestly say about who is around.
 *
 * Telegram privacy settings frequently withhold exact presence, so there are two
 * distinct truthful shapes plus an empty one. The UI must label them differently:
 * approximate activity is never called "Online".
 */
@Immutable
sealed interface ActiveNowState {

    /** Telegram reports these people as genuinely online right now. */
    data class Online(override val people: List<ActivePerson>) : ActiveNowState

    /**
     * Nobody is reported as exactly online, but these contacts were active recently
     * according to Telegram's own approximate status.
     */
    data class RecentlyActive(override val people: List<ActivePerson>) : ActiveNowState

    /** Nothing meaningful to show. The strip is hidden rather than filled. */
    data object Empty : ActiveNowState {
        override val people: List<ActivePerson> = emptyList()
    }

    val people: List<ActivePerson>

    /** Accurate row label for the current shape. */
    val label: String
        get() = when (this) {
            is Online -> "Active now"
            is RecentlyActive -> "Recently active"
            Empty -> ""
        }
}

object ActiveNow {

    const val MAX_PEOPLE = 12

    /**
     * Derives the presence strip from real chat state only.
     *
     * Included: individual people you have a direct conversation with.
     * Excluded: groups, channels, bots, deleted accounts and Saved Messages —
     * none of those are a person who can be present.
     */
    fun from(chats: List<Chat>, limit: Int = MAX_PEOPLE): ActiveNowState {
        val candidates = chats.filter { chat ->
            val user = chat.directUser
            chat.type == ChatType.DIRECT &&
                user != null &&
                !user.isBot &&
                !user.isDeleted
        }
        if (candidates.isEmpty()) return ActiveNowState.Empty

        val online = candidates
            .filter { it.directUser?.presence == Presence.ONLINE }
            .sortedWith(presenceOrder)
            .take(limit)
            .map { ActivePerson(it, Presence.ONLINE) }

        if (online.isNotEmpty()) return ActiveNowState.Online(online)

        // Fall back to Telegram's own approximate "recently" status, clearly labelled
        // as such. Restricted to contacts so the row stays people-first.
        val recent = candidates
            .filter { it.directUser?.presence == Presence.RECENTLY && it.directUser?.isContact == true }
            .sortedWith(presenceOrder)
            .take(limit)
            .map { ActivePerson(it, Presence.RECENTLY) }

        if (recent.isNotEmpty()) return ActiveNowState.RecentlyActive(recent)

        return ActiveNowState.Empty
    }

    /** Contacts first, then Telegram's own chat ordering. Deterministic. */
    private val presenceOrder = compareByDescending<Chat> { it.directUser?.isContact == true }
        .thenComparator { a, b -> java.lang.Long.compareUnsigned(b.order, a.order) }
        .thenBy { it.id }
}
