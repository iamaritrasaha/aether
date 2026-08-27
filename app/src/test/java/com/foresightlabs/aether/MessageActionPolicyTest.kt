package com.foresightlabs.aether

import com.foresightlabs.aether.domain.messages.MessageAction
import com.foresightlabs.aether.domain.messages.MessageActionPolicy
import com.foresightlabs.aether.domain.messages.MessageCapabilities
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The policy is the only thing that decides what a message offers, so these tests
 * are the contract: a capability Telegram withheld must not appear anywhere, and no
 * property of the message itself may talk the policy into offering it.
 */
class MessageActionPolicyTest {

    private fun message(
        id: String = "1",
        text: String = "Hello",
        isOutgoing: Boolean = true,
        isPinned: Boolean = false,
        type: MessageType = MessageType.TEXT,
        status: MessageStatus = MessageStatus.SENT
    ) = Message(
        id = id,
        chatId = "100",
        senderId = "1",
        senderName = "Me",
        text = text,
        timestamp = "12:00",
        isOutgoing = isOutgoing,
        isPinned = isPinned,
        type = type,
        status = status
    )

    private val everything = MessageCapabilities(
        canBeEdited = true,
        canEditMedia = true,
        canBeDeletedOnlyForSelf = true,
        canBeDeletedForAllUsers = true,
        canBeForwarded = true,
        canBeReplied = true,
        canBePinned = true,
        canBeCopied = true,
        canBeSaved = true,
        canGetLink = true,
        canGetReadDate = true,
        canGetViewers = true
    )

    // --- a withheld capability is a withheld action --------------------------

    @Test
    fun editIsAbsentWhenTelegramSaysTheMessageCannotBeEdited() {
        val actions = MessageActionPolicy.actionsFor(message(), everything.copy(canBeEdited = false))
        assertFalse(MessageAction.EDIT in actions)
    }

    @Test
    fun pinIsAbsentWhenTelegramSaysTheMessageCannotBePinned() {
        val actions = MessageActionPolicy.actionsFor(message(), everything.copy(canBePinned = false))
        assertFalse(MessageAction.PIN in actions)
        assertFalse(MessageAction.UNPIN in actions)
    }

    @Test
    fun forwardIsAbsentWhenTelegramSaysTheMessageCannotBeForwarded() {
        val actions = MessageActionPolicy.actionsFor(message(), everything.copy(canBeForwarded = false))
        assertFalse(MessageAction.FORWARD in actions)
    }

    @Test
    fun replyIsAbsentWhenTelegramSaysTheMessageCannotBeReplied() {
        val actions = MessageActionPolicy.actionsFor(message(), everything.copy(canBeReplied = false))
        assertFalse(MessageAction.REPLY in actions)
    }

    @Test
    fun copyIsAbsentForProtectedContent() {
        val actions = MessageActionPolicy.actionsFor(message(), everything.copy(canBeCopied = false))
        assertFalse(MessageAction.COPY in actions)
    }

    // --- the message's own properties are not a licence ----------------------

    @Test
    fun anOutgoingMessageIsNotEditableJustBecauseItIsOutgoing() {
        val actions = MessageActionPolicy.actionsFor(
            message(isOutgoing = true),
            MessageCapabilities.Unknown
        )
        assertTrue(
            "isOutgoing must not stand in for a server capability: $actions",
            actions.isEmpty()
        )
    }

    @Test
    fun anIncomingMessageIsEditableWhenTelegramSaysSo() {
        val actions = MessageActionPolicy.actionsFor(
            message(isOutgoing = false),
            MessageCapabilities(canBeEdited = true)
        )
        assertEquals(listOf(MessageAction.EDIT), actions)
    }

    @Test
    fun beforePropertiesArriveNothingIsOffered() {
        assertEquals(
            emptyList<MessageAction>(),
            MessageActionPolicy.actionsFor(message(), MessageCapabilities.Unknown)
        )
    }

    // --- delete scope --------------------------------------------------------

    @Test
    fun deleteForEveryoneIsOfferedOnlyWhenPermitted() {
        val selfOnly = MessageActionPolicy.actionsFor(
            message(),
            MessageCapabilities(canBeDeletedOnlyForSelf = true)
        )
        assertEquals(listOf(MessageAction.DELETE_FOR_ME), selfOnly)

        val both = MessageActionPolicy.actionsFor(message(), everything)
        assertTrue(MessageAction.DELETE_FOR_ME in both)
        assertTrue(MessageAction.DELETE_FOR_EVERYONE in both)
    }

    @Test
    fun aMessageDeletableOnlyForEveryoneDoesNotOfferDeleteForMe() {
        val actions = MessageActionPolicy.actionsFor(
            message(),
            MessageCapabilities(canBeDeletedForAllUsers = true)
        )
        assertEquals(listOf(MessageAction.DELETE_FOR_EVERYONE), actions)
    }

    // --- pin/unpin are the same capability, different labels -----------------

    @Test
    fun anAlreadyPinnedMessageOffersUnpin() {
        val actions = MessageActionPolicy.actionsFor(
            message(isPinned = true),
            MessageCapabilities(canBePinned = true)
        )
        assertEquals(listOf(MessageAction.UNPIN), actions)
    }

    // --- content-shaped actions ----------------------------------------------

    @Test
    fun copyIsNotOfferedForAMessageWithNoText() {
        val actions = MessageActionPolicy.actionsFor(
            message(text = "", type = MessageType.IMAGE),
            MessageCapabilities(canBeCopied = true)
        )
        assertFalse(MessageAction.COPY in actions)
    }

    @Test
    fun saveIsOfferedOnlyForMediaThatCanBeSaved() {
        assertFalse(
            MessageAction.SAVE in MessageActionPolicy.actionsFor(
                message(type = MessageType.TEXT),
                MessageCapabilities(canBeSaved = true)
            )
        )
        assertTrue(
            MessageAction.SAVE in MessageActionPolicy.actionsFor(
                message(type = MessageType.IMAGE),
                MessageCapabilities(canBeSaved = true)
            )
        )
    }

    // --- multi-selection is an intersection ----------------------------------

    @Test
    fun aSelectionOffersOnlyWhatEveryMessageSupports() {
        val forwardable = message(id = "1") to everything
        val protectedOne = message(id = "2") to everything.copy(canBeForwarded = false)

        val actions = MessageActionPolicy.actionsForSelection(listOf(forwardable, protectedOne))

        assertFalse("One protected message must remove Forward for the whole selection",
            MessageAction.FORWARD in actions)
        assertTrue(MessageAction.DELETE_FOR_ME in actions)
    }

    @Test
    fun singleMessageActionsAreNotOfferedForAMultiSelection() {
        val selection = listOf(
            message(id = "1") to everything,
            message(id = "2") to everything
        )
        val actions = MessageActionPolicy.actionsForSelection(selection)

        assertFalse(MessageAction.EDIT in actions)
        assertFalse(MessageAction.REPLY in actions)
        assertFalse(MessageAction.PIN in actions)
    }

    @Test
    fun aSelectionOfOneBehavesExactlyLikeASingleMessage() {
        val single = message(id = "1")
        assertEquals(
            MessageActionPolicy.actionsFor(single, everything),
            MessageActionPolicy.actionsForSelection(listOf(single to everything))
        )
    }

    @Test
    fun anEmptySelectionOffersNothing() {
        assertEquals(emptyList<MessageAction>(), MessageActionPolicy.actionsForSelection(emptyList()))
    }

    // --- reaction tray --------------------------------------------------------

    @Test
    fun theReactionTrayFollowsTheChatsOwnPermission() {
        assertFalse(MessageActionPolicy.isReactionTrayAvailable(message(), isReactionAvailable = false))
        assertTrue(MessageActionPolicy.isReactionTrayAvailable(message(), isReactionAvailable = true))
    }

    @Test
    fun aFailedMessageOffersNoReactionTray() {
        assertFalse(
            MessageActionPolicy.isReactionTrayAvailable(
                message(status = MessageStatus.FAILED),
                isReactionAvailable = true
            )
        )
    }
}
