package com.foresightlabs.aether.domain.messages
import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.domain.messages.SendOptions
import com.foresightlabs.aether.domain.messages.SendSchedule
import com.foresightlabs.aether.domain.messages.SendScheduleKind
import com.foresightlabs.aether.domain.messages.SendOptionsPolicy
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Delivery options.
 *
 * Availability is decided from the chat rather than offered everywhere and left to
 * fail on send — "send when online" has nothing to wait for in a group, and a secret
 * chat has no server to hold a scheduled message.
 */
class SendOptionsTest {

    private val someone = User(
        id = "42",
        name = "Sam",
        username = "@sam",
        avatarInitials = "S",
        avatarGradient = listOf(Color.Red, Color.Blue),
        presence = Presence.ONLINE
    )

    private fun chat(type: ChatType, withUser: User? = someone) = Chat(
        id = "100",
        title = "Sam",
        type = type,
        lastMessageText = "",
        lastMessageTime = "",
        directUser = withUser,
        avatarInitials = "S",
        avatarGradient = listOf(Color.Red, Color.Blue)
    )

    @Test
    fun theDefaultIsAPlainImmediateSend() {
        assertTrue(SendOptions.Default.isDefault)
        assertEquals(SendSchedule.Now, SendOptions.Default.schedule)
        assertFalse(SendOptions.Default.silent)
    }

    @Test
    fun anySetOptionStopsBeingTheDefault() {
        assertFalse(SendOptions(silent = true).isDefault)
        assertFalse(SendOptions(schedule = SendSchedule.At(1_800_000_000)).isDefault)
        assertFalse(SendOptions(schedule = SendSchedule.WhenOnline).isDefault)
    }

    // --- availability ---------------------------------------------------------

    @Test
    fun sendWhenOnlineIsOfferedOnlyInAPrivateChat() {
        assertTrue(SendOptionsPolicy.canSendWhenOnline(chat(ChatType.DIRECT)))
        assertFalse(SendOptionsPolicy.canSendWhenOnline(chat(ChatType.GROUP)))
        assertFalse(SendOptionsPolicy.canSendWhenOnline(chat(ChatType.CHANNEL)))
        assertFalse(SendOptionsPolicy.canSendWhenOnline(chat(ChatType.SECRET)))
    }

    @Test
    fun sendWhenOnlineNeedsSomeoneWhosePresenceIsKnown() {
        assertFalse(SendOptionsPolicy.canSendWhenOnline(chat(ChatType.DIRECT, withUser = null)))
    }

    @Test
    fun aSecretChatCannotScheduleBecauseNoServerHoldsTheMessage() {
        assertFalse(SendOptionsPolicy.canSchedule(chat(ChatType.SECRET)))
        assertTrue(SendOptionsPolicy.canSchedule(chat(ChatType.DIRECT)))
        assertTrue(SendOptionsPolicy.canSchedule(chat(ChatType.GROUP)))
    }

    @Test
    fun nothingIsOfferedWithoutAChat() {
        assertFalse(SendOptionsPolicy.canSchedule(null))
        assertFalse(SendOptionsPolicy.canSendWhenOnline(null))
        assertFalse(SendOptionsPolicy.canSendSilently(null))
        assertEquals(listOf(SendScheduleKind.NOW), SendOptionsPolicy.availableSchedules(null))
    }

    @Test
    fun aPrivateChatOffersEverySchedule() {
        assertEquals(
            listOf(SendScheduleKind.NOW, SendScheduleKind.AT_TIME, SendScheduleKind.WHEN_ONLINE),
            SendOptionsPolicy.availableSchedules(chat(ChatType.DIRECT))
        )
    }

    @Test
    fun aGroupOffersSchedulingButNotSendWhenOnline() {
        assertEquals(
            listOf(SendScheduleKind.NOW, SendScheduleKind.AT_TIME),
            SendOptionsPolicy.availableSchedules(chat(ChatType.GROUP))
        )
    }

    @Test
    fun aSecretChatOffersOnlyImmediateSend() {
        assertEquals(
            listOf(SendScheduleKind.NOW),
            SendOptionsPolicy.availableSchedules(chat(ChatType.SECRET))
        )
    }
}
