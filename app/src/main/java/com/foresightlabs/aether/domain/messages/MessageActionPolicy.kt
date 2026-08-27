package com.foresightlabs.aether.domain.messages

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType

/**
 * What Telegram says the current account may do with one specific message.
 *
 * This mirrors the subset of TDLib's `messageProperties` that Aether surfaces. It is
 * a *server* answer: it already accounts for the edit window, the account's rights in
 * the chat, content protection, the age of the message and the chat type. Nothing
 * here may be inferred locally — `isOutgoing` in particular is not a licence to edit
 * or to delete for everyone, and assuming it was is how clients end up showing an
 * action that fails the moment it is tapped.
 */
@Immutable
data class MessageCapabilities(
    val canBeEdited: Boolean = false,
    val canEditMedia: Boolean = false,
    val canBeDeletedOnlyForSelf: Boolean = false,
    val canBeDeletedForAllUsers: Boolean = false,
    val canBeForwarded: Boolean = false,
    val canBeReplied: Boolean = false,
    val canBePinned: Boolean = false,
    val canBeCopied: Boolean = false,
    val canBeSaved: Boolean = false,
    val canGetLink: Boolean = false,
    val canGetReadDate: Boolean = false,
    val canGetViewers: Boolean = false,
    val canDeleteReactions: Boolean = false
) {
    companion object {
        /**
         * What is safe to assume before the server has answered: nothing.
         *
         * A menu built from this shows no destructive or permission-bearing action,
         * which is the correct behaviour while properties are still in flight.
         */
        val Unknown = MessageCapabilities()
    }
}

/** A single action Aether may offer for a message. */
enum class MessageAction {
    REPLY,

    /** Reply carrying a quoted excerpt of the original. */
    QUOTE_REPLY,
    COPY,
    FORWARD,
    EDIT,
    PIN,
    UNPIN,
    SAVE,
    COPY_LINK,
    INFO,

    /**
     * Enters multi-selection.
     *
     * The one action here that is not a Telegram capability — it is a client
     * affordance, so it is always available and is never part of a selection's
     * intersection.
     */
    SELECT,
    DELETE_FOR_ME,
    DELETE_FOR_EVERYONE
}

/**
 * The single place that decides which actions a message offers.
 *
 * Every surface that can act on a message — the long-press menu, multi-select, swipe
 * gestures — resolves through here, so an action can never be reachable from one
 * surface and absent from another.
 */
object MessageActionPolicy {

    /**
     * Actions for one message, in presentation order.
     *
     * @param message the message as Aether models it
     * @param capabilities Telegram's answer for this message
     * @param isReactionAvailable whether the chat allows reactions at all
     */
    fun actionsFor(
        message: Message,
        capabilities: MessageCapabilities,
        isReactionAvailable: Boolean = false,
        allowSelect: Boolean = false
    ): List<MessageAction> = buildList {
        if (capabilities.canBeReplied) {
            add(MessageAction.REPLY)
            // Quoting needs something to quote, and the same permission to reply.
            if (message.text.isNotBlank()) add(MessageAction.QUOTE_REPLY)
        }
        if (capabilities.canBeCopied && hasCopyableText(message)) add(MessageAction.COPY)
        if (capabilities.canBeForwarded) add(MessageAction.FORWARD)
        if (capabilities.canBeEdited) add(MessageAction.EDIT)
        if (capabilities.canBePinned) {
            add(if (message.isPinned) MessageAction.UNPIN else MessageAction.PIN)
        }
        if (capabilities.canBeSaved && isSaveableMedia(message)) add(MessageAction.SAVE)
        if (capabilities.canGetLink) add(MessageAction.COPY_LINK)
        if (capabilities.canGetReadDate || capabilities.canGetViewers) add(MessageAction.INFO)
        if (allowSelect) add(MessageAction.SELECT)
        if (capabilities.canBeDeletedOnlyForSelf) add(MessageAction.DELETE_FOR_ME)
        if (capabilities.canBeDeletedForAllUsers) add(MessageAction.DELETE_FOR_EVERYONE)
    }

    /** Whether the reaction tray should be offered above the action list. */
    fun isReactionTrayAvailable(
        message: Message,
        isReactionAvailable: Boolean
    ): Boolean = isReactionAvailable && message.status != MessageStatus.FAILED

    /**
     * Actions valid for a whole selection: those every selected message supports.
     *
     * Single-message actions are dropped from a multi-selection rather than being
     * applied to an arbitrary member of it.
     */
    fun actionsForSelection(
        selection: List<Pair<Message, MessageCapabilities>>
    ): List<MessageAction> {
        if (selection.isEmpty()) return emptyList()
        if (selection.size == 1) {
            val (message, capabilities) = selection.single()
            return actionsFor(message, capabilities)
        }
        val perMessage = selection.map { (message, capabilities) ->
            actionsFor(message, capabilities).toSet() - SingleMessageOnly
        }
        return MultiSelectOrder.filter { action -> perMessage.all { action in it } }
    }

    /** Actions that only make sense against exactly one message. */
    private val SingleMessageOnly = setOf(
        MessageAction.REPLY,
        MessageAction.QUOTE_REPLY,
        MessageAction.EDIT,
        MessageAction.PIN,
        MessageAction.UNPIN,
        MessageAction.COPY_LINK,
        MessageAction.INFO,
        MessageAction.SELECT
    )

    private val MultiSelectOrder = listOf(
        MessageAction.COPY,
        MessageAction.FORWARD,
        MessageAction.SAVE,
        MessageAction.DELETE_FOR_ME,
        MessageAction.DELETE_FOR_EVERYONE
    )

    private fun hasCopyableText(message: Message): Boolean = message.text.isNotBlank()

    private fun isSaveableMedia(message: Message): Boolean = when (message.type) {
        MessageType.IMAGE,
        MessageType.ALBUM,
        MessageType.FILE,
        MessageType.VOICE -> true
        else -> false
    }
}
