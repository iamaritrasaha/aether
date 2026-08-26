package com.foresightlabs.aether.screenshot

import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User

/**
 * Test-only fixtures for rendering Home on the JVM.
 *
 * These exist solely so screenshots can be inspected without a signed-in Telegram
 * account. They are confined to the unit-test source set and are never reachable
 * from the production APK.
 */
object HomeFixtures {

    private val gradients = listOf(
        listOf(Color(0xFF4DA3FF), Color(0xFF1D4ED8)),
        listOf(Color(0xFF10B981), Color(0xFF047857)),
        listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        listOf(Color(0xFFF59E0B), Color(0xFFB45309)),
        listOf(Color(0xFFF43F5E), Color(0xFFBE123C)),
        listOf(Color(0xFF06B6D4), Color(0xFF0E7490))
    )

    val me = User(
        id = "1",
        name = "Aritra Saha",
        username = "@aritra",
        avatarInitials = "AS",
        avatarGradient = gradients[0],
        presence = Presence.ONLINE
    )

    private fun person(
        index: Int,
        name: String,
        presence: Presence,
        isContact: Boolean = true
    ) = User(
        id = "u$index",
        name = name,
        username = "",
        avatarInitials = name.split(" ").take(2).joinToString("") { it.first().uppercase() },
        avatarGradient = gradients[index % gradients.size],
        presence = presence,
        isContact = isContact,
        lastSeenText = if (presence == Presence.ONLINE) "online" else "last seen recently"
    )

    private fun direct(
        index: Int,
        name: String,
        message: String,
        time: String,
        presence: Presence,
        unread: Int = 0,
        typing: Boolean = false
    ) = Chat(
        id = "c$index",
        title = name,
        type = ChatType.DIRECT,
        lastMessageText = message,
        lastMessageTime = time,
        unreadCount = unread,
        isTyping = typing,
        typingText = if (typing) "typing..." else null,
        avatarInitials = name.split(" ").take(2).joinToString("") { it.first().uppercase() },
        avatarGradient = gradients[index % gradients.size],
        subtitle = if (presence == Presence.ONLINE) "online" else "last seen recently",
        lastMessageStatus = MessageStatus.READ,
        directUser = person(index, name, presence),
        order = (10_000 - index).toLong()
    )

    private fun group(index: Int, title: String, message: String, time: String, unread: Int = 0) = Chat(
        id = "g$index",
        title = title,
        type = ChatType.GROUP,
        lastMessageText = message,
        lastMessageTime = time,
        unreadCount = unread,
        avatarInitials = title.take(2).uppercase(),
        avatarGradient = gradients[(index + 2) % gradients.size],
        order = (9_000 - index).toLong()
    )

    private fun channel(index: Int, title: String, message: String, time: String) = Chat(
        id = "ch$index",
        title = title,
        type = ChatType.CHANNEL,
        lastMessageText = message,
        lastMessageTime = time,
        avatarInitials = title.take(2).uppercase(),
        avatarGradient = gradients[(index + 4) % gradients.size],
        order = (8_000 - index).toLong()
    )

    /** A populated, realistic mix: online and offline people, groups and channels. */
    val populated: List<Chat> = listOf(
        direct(0, "Ishani Roy", "Are we still on for tomorrow?", "9:41 AM", Presence.ONLINE, unread = 2),
        direct(1, "Dev Malhotra", "Sent you the files", "9:12 AM", Presence.ONLINE),
        direct(2, "Priyanka Venkataraghavan", "Long name test for truncation behaviour", "8:55 AM", Presence.RECENTLY, typing = true),
        direct(3, "Kabir", "👍", "Yesterday", Presence.OFFLINE, unread = 14),
        direct(4, "Meera Nair", "Thanks again for yesterday, that helped a lot", "Yesterday", Presence.ONLINE),
        group(5, "Foresight Labs", "Aritra: shipping the beta this week", "10:02 AM", unread = 128),
        group(6, "Weekend Football", "Rohit: who's in?", "Yesterday"),
        channel(7, "Aether Releases", "1.0 closed testing is live", "Mon"),
        direct(8, "Ananya Ghosh", "call me when you're free", "Sun", Presence.OFFLINE),
        direct(9, "Sam", "ok", "Sat", Presence.RECENTLY),
        channel(10, "Design Weekly", "Issue 42 — motion that means something", "Fri")
    )

    /** Everyone offline or privacy-limited: nothing may claim to be online. */
    val noOnlinePeople: List<Chat> = populated.map { chat ->
        val user = chat.directUser ?: return@map chat
        chat.copy(directUser = user.copy(presence = Presence.OFFLINE, isContact = true))
    }

    /** Only privacy-limited approximate activity available. */
    val onlyRecentlyActive: List<Chat> = populated.map { chat ->
        val user = chat.directUser ?: return@map chat
        chat.copy(directUser = user.copy(presence = Presence.RECENTLY, isContact = true))
    }

    val empty: List<Chat> = emptyList()
}
