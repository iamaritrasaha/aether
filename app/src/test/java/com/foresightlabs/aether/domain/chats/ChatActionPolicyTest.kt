package com.foresightlabs.aether.domain.chats
import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.domain.chats.ChatActionPolicy
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chat-list actions must match what the account may actually do in that chat, and
 * must be named for the operation they really perform.
 */
class ChatActionPolicyTest {

    private fun chat(
        type: ChatType = ChatType.DIRECT,
        unreadCount: Int = 0,
        isMarkedAsUnread: Boolean = false,
        isPinned: Boolean = false,
        isMuted: Boolean = false,
        isArchived: Boolean = false,
        canLeave: Boolean = false,
        canRevokeHistory: Boolean = false,
        canDeleteOnlyForSelf: Boolean = true,
        blockableUserId: Long? = 42L,
        isBlocked: Boolean = false
    ) = Chat(
        id = "100",
        title = "Sam",
        type = type,
        lastMessageText = "hi",
        lastMessageTime = "12:00",
        unreadCount = unreadCount,
        isMarkedAsUnread = isMarkedAsUnread,
        isPinned = isPinned,
        isMuted = isMuted,
        isArchived = isArchived,
        canLeave = canLeave,
        canRevokeHistory = canRevokeHistory,
        canDeleteOnlyForSelf = canDeleteOnlyForSelf,
        blockableUserId = blockableUserId,
        isBlocked = isBlocked,
        avatarInitials = "S",
        avatarGradient = listOf(Color.Red, Color.Blue)
    )

    // --- toggles reflect current server state --------------------------------

    @Test
    fun eachToggleOffersOnlyTheDirectionThatChangesSomething() {
        val plain = ChatActionPolicy.actionsFor(chat())
        assertTrue(ChatAction.PIN in plain)
        assertFalse(ChatAction.UNPIN in plain)

        val pinned = ChatActionPolicy.actionsFor(chat(isPinned = true))
        assertTrue(ChatAction.UNPIN in pinned)
        assertFalse(ChatAction.PIN in pinned)
    }

    @Test
    fun aChatWithUnreadMessagesOffersMarkReadAndNotMarkUnread() {
        val actions = ChatActionPolicy.actionsFor(chat(unreadCount = 3))
        assertTrue(ChatAction.MARK_READ in actions)
        assertFalse(ChatAction.MARK_UNREAD in actions)
    }

    @Test
    fun aChatMarkedUnreadOnTheServerOffersMarkRead() {
        val actions = ChatActionPolicy.actionsFor(chat(isMarkedAsUnread = true))
        assertTrue(ChatAction.MARK_READ in actions)
    }

    @Test
    fun aFullyReadChatOffersMarkUnread() {
        val actions = ChatActionPolicy.actionsFor(chat())
        assertTrue(ChatAction.MARK_UNREAD in actions)
    }

    @Test
    fun anArchivedChatOffersUnarchive() {
        val actions = ChatActionPolicy.actionsFor(chat(isArchived = true))
        assertTrue(ChatAction.UNARCHIVE in actions)
        assertFalse(ChatAction.ARCHIVE in actions)
    }

    // --- block belongs to private chats only ---------------------------------

    @Test
    fun onlyAPrivateChatOffersBlock() {
        assertTrue(ChatAction.BLOCK in ChatActionPolicy.actionsFor(chat(type = ChatType.DIRECT)))
        assertFalse(ChatAction.BLOCK in ChatActionPolicy.actionsFor(chat(type = ChatType.GROUP)))
        assertFalse(ChatAction.BLOCK in ChatActionPolicy.actionsFor(chat(type = ChatType.CHANNEL)))
        assertFalse(ChatAction.BLOCK in ChatActionPolicy.actionsFor(chat(type = ChatType.SECRET)))
    }

    @Test
    fun aBlockedContactOffersUnblock() {
        val actions = ChatActionPolicy.actionsFor(chat(isBlocked = true))
        assertTrue(ChatAction.UNBLOCK in actions)
        assertFalse(ChatAction.BLOCK in actions)
    }

    @Test
    fun aPrivateChatWithNoResolvedUserOffersNoBlock() {
        assertFalse(ChatAction.BLOCK in ChatActionPolicy.actionsFor(chat(blockableUserId = null)))
    }

    // --- destructive actions are chat-type specific --------------------------

    @Test
    fun aGroupOffersLeaveRatherThanDelete() {
        val actions = ChatActionPolicy.actionsFor(chat(type = ChatType.GROUP, canLeave = true))
        assertTrue(ChatAction.LEAVE in actions)
        assertFalse(ChatAction.DELETE_FOR_ME in actions)
        assertFalse(ChatAction.DELETE_FOR_EVERYONE in actions)
    }

    @Test
    fun aGroupTheAccountCannotLeaveDoesNotOfferLeave() {
        val actions = ChatActionPolicy.actionsFor(chat(type = ChatType.GROUP, canLeave = false))
        assertFalse(ChatAction.LEAVE in actions)
    }

    @Test
    fun aSecretChatOffersOnlyCloseSecretChat() {
        val destructive = ChatActionPolicy.destructiveActions(chat(type = ChatType.SECRET))
        assertEquals(listOf(ChatAction.CLOSE_SECRET_CHAT), destructive)
    }

    @Test
    fun deleteForEveryoneIsOfferedOnlyWhenTelegramPermitsRevoking() {
        assertFalse(
            ChatAction.DELETE_FOR_EVERYONE in
                ChatActionPolicy.actionsFor(chat(canRevokeHistory = false))
        )
        assertTrue(
            ChatAction.DELETE_FOR_EVERYONE in
                ChatActionPolicy.actionsFor(chat(canRevokeHistory = true))
        )
    }

    @Test
    fun deleteForMeIsOfferedOnlyWhenTelegramPermitsIt() {
        assertFalse(
            ChatAction.DELETE_FOR_ME in
                ChatActionPolicy.actionsFor(chat(canDeleteOnlyForSelf = false))
        )
    }

    // --- confirmations say what will really happen ---------------------------

    @Test
    fun everyDestructiveActionCarriesAConfirmationThatNamesItsScope() {
        val types = listOf(ChatType.DIRECT, ChatType.GROUP, ChatType.CHANNEL, ChatType.SECRET)
        for (type in types) {
            val subject = chat(type = type, canLeave = true, canRevokeHistory = true)
            for (action in ChatActionPolicy.destructiveActions(subject)) {
                val confirmation = ChatActionPolicy.confirmation(subject, action)
                assertNotNull("$type/$action has no confirmation", confirmation)
                assertTrue(confirmation!!.body.isNotBlank())
                assertTrue(confirmation.confirmLabel.isNotBlank())
            }
        }
    }

    @Test
    fun clearHistoryIsNotDescribedAsDeletingTheConversation() {
        val confirmation = ChatActionPolicy.confirmation(chat(), ChatAction.CLEAR_HISTORY)!!
        assertTrue(
            "Clear history must say the conversation stays: ${confirmation.body}",
            confirmation.body.contains("stays in your list")
        )
    }

    @Test
    fun deletingForEveryoneIsMarkedSevere() {
        assertTrue(
            ChatActionPolicy.confirmation(chat(), ChatAction.DELETE_FOR_EVERYONE)!!.isSevere
        )
    }

    @Test
    fun aReversibleActionNeedsNoConfirmation() {
        assertNull(ChatActionPolicy.confirmation(chat(), ChatAction.PIN))
        assertNull(ChatActionPolicy.confirmation(chat(), ChatAction.MUTE))
        assertNull(ChatActionPolicy.confirmation(chat(), ChatAction.ARCHIVE))
    }
}
