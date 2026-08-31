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
 * Telegram privacy settings frequently withhold exact presence, so live status is
 * never fabricated: a person's dot only lights up from their own exact status, per
 * [ActivePerson.presence] — never inferred from the row as a whole. The row itself
 * always tries to show *someone* rather than sit empty: people who are live (online,
 * or failing that recently active) come first, and the remaining slots are filled
 * with the contacts you talk to most, whatever their current status.
 */
@Immutable
sealed interface ActiveNowState {

    /** At least one person here is reported as genuinely online right now. */
    data class Online(override val people: List<ActivePerson>) : ActiveNowState

    /** Nobody is exactly online, but at least one person was active recently. */
    data class RecentlyActive(override val people: List<ActivePerson>) : ActiveNowState

    /** Nobody is live at all -- every person shown is here on usage alone. */
    data class MostUsed(override val people: List<ActivePerson>) : ActiveNowState

    /** No personal chats to show anyone from. The strip is hidden rather than filled. */
    data object Empty : ActiveNowState {
        override val people: List<ActivePerson> = emptyList()
    }

    val people: List<ActivePerson>

    /** Accurate row label for the current shape. */
    val label: String
        get() = when (this) {
            is Online -> "Active now"
            is RecentlyActive -> "Recently active"
            is MostUsed -> "Your people"
            Empty -> ""
        }
}

object ActiveNow {

    const val MAX_PEOPLE = 12

    /**
     * Derives the presence strip from real chat state only.
     *
     * Included: individual people you have a direct conversation with. Excluded:
     * groups, channels, bots, deleted accounts and Saved Messages -- none of those
     * are a person who can be present.
     *
     * Live people (online, then recently-active) fill the row first; the remaining
     * slots, up to [limit], backfill with the rest of your contacts ranked by
     * [presenceOrder] -- Telegram's own chat-list order, itself recency/usage
     * weighted -- so someone who talks to you often but happens to be offline right
     * now still shows up rather than leaving the row sparse or empty.
     */
    fun from(chats: List<Chat>, limit: Int = MAX_PEOPLE): ActiveNowState {
        val candidates = chats.filter { chat ->
            chat.isPersonalChat && chat.directUser != null
        }
        if (candidates.isEmpty()) return ActiveNowState.Empty

        val ranked = candidates.sortedWith(presenceOrder)

        val online = ranked
            .filter { it.directUser?.presence == Presence.ONLINE }
            .map { ActivePerson(it, Presence.ONLINE) }

        // Restricted to contacts, same as the most-used backfill below, so the row
        // stays people-first rather than surfacing someone from a one-off chat.
        val recentlyActive = ranked
            .filter { it.directUser?.presence == Presence.RECENTLY && it.directUser?.isContact == true }
            .map { ActivePerson(it, Presence.RECENTLY) }

        val live = if (online.isNotEmpty()) online else recentlyActive
        val liveIds = live.mapTo(HashSet()) { it.id }

        val mostUsed = ranked
            .asSequence()
            .filter { it.id !in liveIds && it.directUser?.isContact == true }
            .map { ActivePerson(it, it.directUser?.presence ?: Presence.UNKNOWN) }
            .take((limit - live.size).coerceAtLeast(0))
            .toList()

        val people = (live + mostUsed).take(limit)

        return when {
            online.isNotEmpty() -> ActiveNowState.Online(people)
            recentlyActive.isNotEmpty() -> ActiveNowState.RecentlyActive(people)
            people.isNotEmpty() -> ActiveNowState.MostUsed(people)
            else -> ActiveNowState.Empty
        }
    }

    /** Contacts first, then Telegram's own chat ordering. Deterministic. */
    private val presenceOrder = compareByDescending<Chat> { it.directUser?.isContact == true }
        .thenComparator { a, b -> java.lang.Long.compareUnsigned(b.order, a.order) }
        .thenBy { it.id }
}
