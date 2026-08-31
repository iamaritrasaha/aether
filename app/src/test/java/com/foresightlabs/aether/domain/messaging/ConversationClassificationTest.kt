package com.foresightlabs.aether.domain.messaging

import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Presence
import com.foresightlabs.aether.domain.model.User
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classification rules that decide what Aether shows and notifies about.
 *
 * The case that matters most is Telegram's service account: TDLib reports it as a
 * bot, so a rule set that consults the bot flag before the service id classifies
 * login codes as marketing content and hides them. Several tests below exist
 * specifically to hold that ordering in place.
 */
class ConversationClassificationTest {

    private fun facts(
        isOneToOne: Boolean = true,
        isForum: Boolean = false,
        isSavedMessages: Boolean = false,
        counterpartUserId: Long? = 42L,
        isBot: Boolean = false,
        isDeleted: Boolean = false,
        isCounterpartKnown: Boolean = true
    ) = ConversationFacts(
        isOneToOne, isForum, isSavedMessages, counterpartUserId, isBot, isDeleted, isCounterpartKnown
    )

    @Test
    fun `a one-to-one chat with a regular person is personal`() {
        assertEquals(ConversationClass.PERSONAL_HUMAN, classifyConversation(facts()))
    }

    @Test
    fun `telegram service id classifies as service, not as a bot to hide`() {
        val result = classifyConversation(
            facts(counterpartUserId = TelegramIdentity.SERVICE_NOTIFICATIONS_USER_ID, isBot = true)
        )
        assertEquals(ConversationClass.TELEGRAM_SERVICE, result)
        assertTrue("Telegram service must remain deliverable", result.isDeliverable)
    }

    @Test
    fun `telegram service wins over every secondary signal`() {
        // Whatever else is true of the account, the service id decides.
        val result = classifyConversation(
            facts(
                counterpartUserId = TelegramIdentity.SERVICE_NOTIFICATIONS_USER_ID,
                isBot = true,
                isDeleted = true
            )
        )
        assertEquals(ConversationClass.TELEGRAM_SERVICE, result)
    }

    @Test
    fun `an ordinary bot is secondary content`() {
        val result = classifyConversation(facts(isBot = true))
        assertEquals(ConversationClass.SECONDARY_TELEGRAM_CONTENT, result)
        assertFalse(result.isDeliverable)
    }

    @Test
    fun `groups channels forums and saved messages are secondary content`() {
        assertEquals(
            ConversationClass.SECONDARY_TELEGRAM_CONTENT,
            classifyConversation(facts(isOneToOne = false, counterpartUserId = null))
        )
        assertEquals(
            ConversationClass.SECONDARY_TELEGRAM_CONTENT,
            classifyConversation(facts(isForum = true))
        )
        assertEquals(
            ConversationClass.SECONDARY_TELEGRAM_CONTENT,
            classifyConversation(facts(isSavedMessages = true, isOneToOne = false))
        )
    }

    @Test
    fun `a deleted account is secondary content`() {
        assertEquals(
            ConversationClass.SECONDARY_TELEGRAM_CONTENT,
            classifyConversation(facts(isDeleted = true))
        )
    }

    @Test
    fun `an unresolved counterpart fails closed as unknown`() {
        val result = classifyConversation(facts(isCounterpartKnown = false))
        assertEquals(ConversationClass.UNKNOWN, result)
        assertFalse("UNKNOWN must never be delivered", result.isDeliverable)
    }

    // --- the same rules, as the Chat model reads them -------------------------

    private fun user(id: String, isBot: Boolean = false, isDeleted: Boolean = false) = User(
        id = id,
        name = "Person $id",
        username = "u$id",
        avatarInitials = "P",
        avatarGradient = listOf(Color.Black, Color.White),
        presence = Presence.UNKNOWN,
        isBot = isBot,
        isDeleted = isDeleted
    )

    private fun chat(
        id: String,
        type: ChatType,
        directUser: User? = null,
        isForum: Boolean = false,
        blockableUserId: Long? = null
    ) = Chat(
        id = id,
        title = "Chat $id",
        type = type,
        lastMessageText = "",
        lastMessageTime = "",
        avatarInitials = "C",
        avatarGradient = listOf(Color.Black, Color.White),
        directUser = directUser,
        isForum = isForum,
        blockableUserId = blockableUserId
    )

    @Test
    fun `the telegram service chat is not personal but is still shown`() {
        val service = chat("777000", ChatType.DIRECT, user("777000", isBot = true))
        assertFalse("Telegram service is not a person", service.isPersonalChat)
        assertTrue(service.isTelegramService)
        assertTrue(
            "The regression this milestone fixes: the service chat must reach Home",
            service.isDeliverableConversation
        )
    }

    @Test
    fun `the service chat is recognised by blockable user id too`() {
        // The chat's own id is not always the counterpart's user id.
        val service = chat("-100200", ChatType.DIRECT, user("777000"), blockableUserId = 777000L)
        assertTrue(service.isTelegramService)
    }

    @Test
    fun `an ordinary person stays personal and deliverable`() {
        val person = chat("55", ChatType.DIRECT, user("55"))
        assertTrue(person.isPersonalChat)
        assertFalse(person.isTelegramService)
        assertTrue(person.isDeliverableConversation)
    }

    @Test
    fun `a group id is never matched against the service user id`() {
        // A group whose chat id happened to read as the service id must not be
        // promoted; the counterpart is only meaningful for one-to-one chats.
        val group = chat("777000", ChatType.GROUP)
        assertFalse(group.isTelegramService)
        assertFalse(group.isDeliverableConversation)
    }

    @Test
    fun `bots and channels remain filtered out of the primary feed`() {
        assertFalse(chat("9", ChatType.DIRECT, user("9", isBot = true)).isDeliverableConversation)
        assertFalse(chat("10", ChatType.CHANNEL).isDeliverableConversation)
        assertFalse(chat("11", ChatType.GROUP).isDeliverableConversation)
    }
}
