package com.foresightlabs.aether.data.telegram
import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.chats.ChatActionPolicy
import com.foresightlabs.aether.domain.messages.MessageActionPolicy
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.Message
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Secret chats.
 *
 * The claim "end-to-end encrypted" is only true of a Telegram Secret Chat. Applying
 * it to an ordinary cloud chat is a security misstatement, not a copy choice, so the
 * chat type that carries it is pinned down here.
 */
class SecretChatTest {

    private fun chat(
        type: ChatType,
        canRevokeHistory: Boolean = false,
        canLeave: Boolean = false
    ) = Chat(
        id = "100",
        title = "Sam",
        type = type,
        lastMessageText = "",
        lastMessageTime = "",
        canRevokeHistory = canRevokeHistory,
        canLeave = canLeave,
        blockableUserId = 42L,
        avatarInitials = "S",
        avatarGradient = listOf(Color.Red, Color.Blue)
    )

    @Test
    fun onlyATdlibSecretChatMapsToTheSecretType() {
        assertEquals(
            ChatType.SECRET,
            TelegramMappers.mapChatType(TdApi.ChatTypeSecret(1, 42L), myUserId = 1L)
        )
        assertEquals(
            ChatType.DIRECT,
            TelegramMappers.mapChatType(TdApi.ChatTypePrivate(42L), myUserId = 1L)
        )
        assertEquals(
            ChatType.GROUP,
            TelegramMappers.mapChatType(TdApi.ChatTypeBasicGroup(7L), myUserId = 1L)
        )
    }

    @Test
    fun anUnrecognisedChatTypeIsNeverTreatedAsSecret() {
        // A type this build predates must not inherit the encryption claim.
        assertFalse(TelegramMappers.mapChatType(null, myUserId = 1L) == ChatType.SECRET)
    }

    @Test
    fun aSecretChatIsEndedByClosingItRatherThanByDeletingAHistory() {
        val destructive = ChatActionPolicy.destructiveActions(chat(ChatType.SECRET))
        assertEquals(listOf(ChatAction.CLOSE_SECRET_CHAT), destructive)
        assertFalse(ChatAction.DELETE_FOR_ME in destructive)
        assertFalse(ChatAction.CLEAR_HISTORY in destructive)
        assertFalse(ChatAction.LEAVE in destructive)
    }

    @Test
    fun closingASecretChatIsConfirmedAsSevereAndSaysWhatItDoes() {
        val confirmation = ChatActionPolicy
            .confirmation(chat(ChatType.SECRET), ChatAction.CLOSE_SECRET_CHAT)!!
        assertTrue(confirmation.isSevere)
        assertTrue(confirmation.body.contains("encrypted session"))
    }

    @Test
    fun aSecretChatOffersNoBlockActionBecauseItIsNotAnOrdinaryPrivateChat() {
        assertFalse(ChatAction.BLOCK in ChatActionPolicy.actionsFor(chat(ChatType.SECRET)))
    }

    @Test
    fun forwardingOutOfASecretChatFollowsTelegramNotTheChatType() {
        // TDLib withholds canBeForwarded for secret-chat messages; the policy must
        // take that answer rather than deciding for itself either way.
        val message = Message(
            id = "1",
            chatId = "100",
            senderId = "1",
            senderName = "Sam",
            text = "hi",
            timestamp = "12:00",
            isOutgoing = false
        )
        val restricted = MessageActionPolicy.actionsFor(
            message,
            MessageCapabilities(canBeReplied = true, canBeForwarded = false)
        )
        assertFalse(MessageAction.FORWARD in restricted)
        assertTrue(MessageAction.REPLY in restricted)
    }

    @Test
    fun anOrdinaryCloudChatKeepsTheCloudChatDestructiveOptions() {
        val destructive = ChatActionPolicy.destructiveActions(
            chat(ChatType.DIRECT, canRevokeHistory = true)
        )
        assertTrue(ChatAction.CLEAR_HISTORY in destructive)
        assertTrue(ChatAction.DELETE_FOR_EVERYONE in destructive)
        assertFalse(ChatAction.CLOSE_SECRET_CHAT in destructive)
    }
}
