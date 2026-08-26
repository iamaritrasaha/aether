package com.foresightlabs.aether

import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatFilterCategory
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.presence.ActiveNow
import com.foresightlabs.aether.domain.presence.ActiveNowState
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceAndFilterTest {

    private fun user(
        id: String,
        presence: Presence,
        isBot: Boolean = false,
        isContact: Boolean = true,
        isDeleted: Boolean = false
    ) = User(
        id = id,
        name = "Person $id",
        username = "",
        avatarInitials = "P",
        avatarGradient = listOf(Color.Red, Color.Blue),
        presence = presence,
        isBot = isBot,
        isContact = isContact,
        isDeleted = isDeleted
    )

    private fun chat(
        id: String,
        type: ChatType,
        directUser: User? = null,
        unread: Int = 0,
        order: Long = 100L
    ) = Chat(
        id = id,
        title = "Chat $id",
        type = type,
        lastMessageText = "",
        lastMessageTime = "",
        unreadCount = unread,
        avatarInitials = "C",
        avatarGradient = listOf(Color.Red, Color.Blue),
        directUser = directUser,
        order = order
    )

    // --- presence mapping stays truthful -------------------------------------

    @Test
    fun tdlibStatusMapsWithoutPromotion() {
        assertEquals(Presence.ONLINE, TelegramMappers.mapPresence(TdApi.UserStatusOnline(0)))
        assertEquals(Presence.OFFLINE, TelegramMappers.mapPresence(TdApi.UserStatusOffline(0)))
        assertEquals(Presence.RECENTLY, TelegramMappers.mapPresence(TdApi.UserStatusRecently(false)))
        assertEquals(Presence.WITHIN_WEEK, TelegramMappers.mapPresence(TdApi.UserStatusLastWeek(false)))
        assertEquals(Presence.WITHIN_MONTH, TelegramMappers.mapPresence(TdApi.UserStatusLastMonth(false)))
        assertEquals(Presence.UNKNOWN, TelegramMappers.mapPresence(TdApi.UserStatusEmpty()))
        assertEquals(Presence.UNKNOWN, TelegramMappers.mapPresence(null))
    }

    @Test
    fun onlyExactOnlineCountsAsOnline() {
        assertTrue(user("1", Presence.ONLINE).isOnline)
        assertFalse(user("2", Presence.RECENTLY).isOnline)
        assertFalse(user("3", Presence.WITHIN_WEEK).isOnline)
        assertFalse(user("4", Presence.UNKNOWN).isOnline)
        assertTrue(Presence.RECENTLY.isApproximate)
        assertFalse(Presence.RECENTLY.isExact)
    }

    // --- Active Now derivation ------------------------------------------------

    @Test
    fun activeNowShowsOnlyGenuinelyOnlinePeople() {
        val state = ActiveNow.from(
            listOf(
                chat("1", ChatType.DIRECT, user("1", Presence.ONLINE)),
                chat("2", ChatType.DIRECT, user("2", Presence.OFFLINE)),
                chat("3", ChatType.DIRECT, user("3", Presence.RECENTLY))
            )
        )
        assertTrue(state is ActiveNowState.Online)
        assertEquals(listOf("1"), state.people.map { it.id })
        assertEquals("Active now", state.label)
    }

    @Test
    fun groupsChannelsBotsAndDeletedAccountsAreExcluded() {
        val state = ActiveNow.from(
            listOf(
                chat("g", ChatType.GROUP),
                chat("c", ChatType.CHANNEL),
                chat("s", ChatType.SAVED_MESSAGES),
                chat("b", ChatType.DIRECT, user("b", Presence.ONLINE, isBot = true)),
                chat("d", ChatType.DIRECT, user("d", Presence.ONLINE, isDeleted = true))
            )
        )
        assertEquals(ActiveNowState.Empty, state)
    }

    @Test
    fun approximateActivityIsLabelledSeparatelyNeverAsOnline() {
        val state = ActiveNow.from(
            listOf(chat("1", ChatType.DIRECT, user("1", Presence.RECENTLY, isContact = true)))
        )
        assertTrue(state is ActiveNowState.RecentlyActive)
        assertEquals("Recently active", state.label)
        assertFalse(state.label.contains("Online", ignoreCase = true))
    }

    @Test
    fun recentlyActiveRequiresAContact() {
        val state = ActiveNow.from(
            listOf(chat("1", ChatType.DIRECT, user("1", Presence.RECENTLY, isContact = false)))
        )
        assertEquals(ActiveNowState.Empty, state)
    }

    @Test
    fun activeNowIsEmptyWhenNothingIsKnown() {
        val state = ActiveNow.from(
            listOf(chat("1", ChatType.DIRECT, user("1", Presence.UNKNOWN)))
        )
        assertEquals(ActiveNowState.Empty, state)
        assertTrue(state.people.isEmpty())
    }

    @Test
    fun activeNowPrefersContactsThenServerOrder() {
        val state = ActiveNow.from(
            listOf(
                chat("stranger", ChatType.DIRECT, user("stranger", Presence.ONLINE, isContact = false), order = 900L),
                chat("friend", ChatType.DIRECT, user("friend", Presence.ONLINE, isContact = true), order = 100L)
            )
        )
        assertEquals(listOf("friend", "stranger"), state.people.map { it.id })
    }

    @Test
    fun activeNowIsCapped() {
        val chats = (1..30).map {
            chat("$it", ChatType.DIRECT, user("$it", Presence.ONLINE), order = it.toLong())
        }
        assertEquals(ActiveNow.MAX_PEOPLE, ActiveNow.from(chats).people.size)
    }

    // --- people-first filtering ----------------------------------------------

    @Test
    fun filtersArePeopleFirstWithNoDominantAllTab() {
        assertEquals(
            listOf("People", "Groups", "Channels", "Unread"),
            ChatFilterCategory.entries.map { it.label }
        )
        assertEquals(ChatFilterCategory.PEOPLE, ChatFilterCategory.entries.first())
    }

    @Test
    fun eachFilterMatchesOnlyItsOwnKind() {
        val direct = chat("d", ChatType.DIRECT, user("d", Presence.OFFLINE))
        val saved = chat("s", ChatType.SAVED_MESSAGES)
        val group = chat("g", ChatType.GROUP)
        val channel = chat("c", ChatType.CHANNEL)
        val unread = chat("u", ChatType.GROUP, unread = 3)

        assertTrue(ChatFilterCategory.PEOPLE.matches(direct))
        assertTrue(ChatFilterCategory.PEOPLE.matches(saved))
        assertFalse(ChatFilterCategory.PEOPLE.matches(group))
        assertTrue(ChatFilterCategory.GROUPS.matches(group))
        assertFalse(ChatFilterCategory.GROUPS.matches(channel))
        assertTrue(ChatFilterCategory.CHANNELS.matches(channel))
        assertTrue(ChatFilterCategory.UNREAD.matches(unread))
        assertFalse(ChatFilterCategory.UNREAD.matches(group))
    }
}
