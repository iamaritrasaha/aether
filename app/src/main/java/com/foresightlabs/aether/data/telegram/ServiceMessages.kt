package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi

/**
 * Renders Telegram's system events as system events.
 *
 * TDLib defines around a hundred `MessageContent` subtypes and a large minority of
 * them are not messages at all — they are things that *happened* to the chat. Aether
 * previously recognised seven of them; everything else fell through to a generic
 * label and was drawn as an ordinary chat bubble, so "changed the group photo"
 * appeared to have been *said* by someone.
 *
 * Two rules hold here:
 *
 * 1. [isServiceEvent] is the single authority on whether a content type is a system
 *    event, and it is exhaustive over the pinned API rather than a list of the ones
 *    someone happened to hit.
 * 2. A system event Aether has no bespoke wording for still renders as a system
 *    event, using a truthful description derived from its actual type — never as a
 *    text bubble.
 */
object ServiceMessages {

    /**
     * Whether this content is something that happened to the chat rather than
     * something a person sent.
     */
    fun isServiceEvent(content: TdApi.MessageContent?): Boolean = when (content) {
        null -> false
        is TdApi.MessagePinMessage,
        is TdApi.MessageChatAddMembers,
        is TdApi.MessageChatJoinByLink,
        is TdApi.MessageChatJoinByRequest,
        is TdApi.MessageChatDeleteMember,
        is TdApi.MessageChatChangeTitle,
        is TdApi.MessageChatChangePhoto,
        is TdApi.MessageChatDeletePhoto,
        is TdApi.MessageChatSetBackground,
        is TdApi.MessageChatSetTheme,
        is TdApi.MessageChatSetMessageAutoDeleteTime,
        is TdApi.MessageChatOwnerChanged,
        is TdApi.MessageChatOwnerLeft,
        is TdApi.MessageChatUpgradeTo,
        is TdApi.MessageChatUpgradeFrom,
        is TdApi.MessageBasicGroupChatCreate,
        is TdApi.MessageSupergroupChatCreate,
        is TdApi.MessageChatHasProtectedContentToggled,
        is TdApi.MessageChatHasProtectedContentDisableRequested,
        is TdApi.MessageForumTopicCreated,
        is TdApi.MessageForumTopicEdited,
        is TdApi.MessageForumTopicIsClosedToggled,
        is TdApi.MessageForumTopicIsHiddenToggled,
        is TdApi.MessageVideoChatStarted,
        is TdApi.MessageVideoChatEnded,
        is TdApi.MessageVideoChatScheduled,
        is TdApi.MessageInviteVideoChatParticipants,
        is TdApi.MessageScreenshotTaken,
        is TdApi.MessageContactRegistered,
        is TdApi.MessageProximityAlertTriggered,
        is TdApi.MessageChatBoost,
        is TdApi.MessageExpiredPhoto,
        is TdApi.MessageExpiredVideo,
        is TdApi.MessageExpiredVideoNote,
        is TdApi.MessageExpiredVoiceNote,
        is TdApi.MessageCustomServiceAction,
        is TdApi.MessageGameScore,
        is TdApi.MessagePaymentSuccessful,
        is TdApi.MessagePaymentRefunded,
        is TdApi.MessageGiftedPremium,
        is TdApi.MessageGiftedStars,
        is TdApi.MessageGiveawayCreated,
        is TdApi.MessageGiveawayCompleted,
        is TdApi.MessagePaidMessagePriceChanged,
        is TdApi.MessageDirectMessagePriceChanged,
        is TdApi.MessageSuggestProfilePhoto,
        is TdApi.MessageSuggestBirthdate,
        is TdApi.MessageUsersShared,
        is TdApi.MessageChatShared,
        is TdApi.MessageBotWriteAccessAllowed,
        is TdApi.MessageWebAppDataSent,
        is TdApi.MessageWebAppDataReceived,
        is TdApi.MessagePassportDataSent,
        is TdApi.MessagePassportDataReceived,
        // Events about a poll or checklist that already exists in the chat.
        is TdApi.MessagePollOptionAdded,
        is TdApi.MessagePollOptionDeleted,
        is TdApi.MessageChecklistTasksAdded,
        is TdApi.MessageChecklistTasksDone,
        // A video chat's own lifecycle in the chat.
        is TdApi.MessageGroupCall -> true
        else -> false
    }

    /**
     * A truthful one-line description of the event.
     *
     * [actorName] is the person the event is attributed to, already resolved. Where
     * Telegram does not attribute the event to anyone the name is omitted rather
     * than guessed.
     */
    fun describe(content: TdApi.MessageContent?, actorName: String): String = when (content) {
        is TdApi.MessagePinMessage -> "$actorName pinned a message"
        is TdApi.MessageChatAddMembers -> "$actorName joined the chat"
        is TdApi.MessageChatJoinByLink -> "$actorName joined via an invite link"
        is TdApi.MessageChatJoinByRequest -> "$actorName was approved to join"
        is TdApi.MessageChatDeleteMember -> "$actorName left the chat"
        is TdApi.MessageChatChangeTitle -> "$actorName changed the title to “${content.title}”"
        is TdApi.MessageChatChangePhoto -> "$actorName changed the chat photo"
        is TdApi.MessageChatDeletePhoto -> "$actorName removed the chat photo"
        is TdApi.MessageChatSetBackground -> "$actorName changed the chat background"
        is TdApi.MessageChatSetTheme -> "$actorName changed the chat theme"
        is TdApi.MessageChatSetMessageAutoDeleteTime -> describeAutoDelete(content, actorName)
        is TdApi.MessageChatOwnerChanged -> "$actorName is now the owner"
        is TdApi.MessageChatOwnerLeft -> "The owner left the chat"
        is TdApi.MessageChatUpgradeTo -> "This group became a supergroup"
        is TdApi.MessageChatUpgradeFrom -> "“${content.title}” became a supergroup"
        is TdApi.MessageBasicGroupChatCreate -> "$actorName created “${content.title}”"
        is TdApi.MessageSupergroupChatCreate -> "$actorName created “${content.title}”"
        is TdApi.MessageChatHasProtectedContentToggled ->
            if (content.newHasProtectedContent) {
                "Forwarding and saving were restricted"
            } else {
                "Forwarding and saving were allowed"
            }
        is TdApi.MessageChatHasProtectedContentDisableRequested ->
            "Removing content protection was requested"

        is TdApi.MessageForumTopicCreated -> "Topic “${content.name}” was created"
        is TdApi.MessageForumTopicEdited -> "Topic renamed to “${content.name}”"
        is TdApi.MessageForumTopicIsClosedToggled ->
            if (content.isClosed) "Topic closed" else "Topic reopened"
        is TdApi.MessageForumTopicIsHiddenToggled ->
            if (content.isHidden) "Topic hidden" else "Topic unhidden"

        is TdApi.MessageVideoChatStarted -> "$actorName started a video chat"
        is TdApi.MessageVideoChatEnded -> "Video chat ended · ${formatDuration(content.duration)}"
        is TdApi.MessageVideoChatScheduled -> "A video chat was scheduled"
        is TdApi.MessageInviteVideoChatParticipants -> "$actorName invited people to the video chat"

        is TdApi.MessageScreenshotTaken -> "$actorName took a screenshot"
        is TdApi.MessageContactRegistered -> "$actorName joined Telegram"
        is TdApi.MessageProximityAlertTriggered -> "Someone is nearby"
        is TdApi.MessageChatBoost ->
            if (content.boostCount == 1) {
                "$actorName boosted the chat"
            } else {
                "$actorName boosted the chat ${content.boostCount} times"
            }

        // Self-destructing and expired media: the content is genuinely gone.
        is TdApi.MessageExpiredPhoto -> "Photo expired"
        is TdApi.MessageExpiredVideo -> "Video expired"
        is TdApi.MessageExpiredVideoNote -> "Video message expired"
        is TdApi.MessageExpiredVoiceNote -> "Voice message expired"

        is TdApi.MessagePollOptionAdded ->
            "$actorName added a poll option: ${content.text?.text.orEmpty()}"
        is TdApi.MessagePollOptionDeleted ->
            "$actorName removed a poll option: ${content.text?.text.orEmpty()}"
        is TdApi.MessageChecklistTasksAdded -> {
            val count = content.tasks?.size ?: 0
            if (count == 1) {
                "$actorName added a task"
            } else {
                "$actorName added $count tasks"
            }
        }
        is TdApi.MessageChecklistTasksDone -> describeChecklistProgress(content, actorName)
        is TdApi.MessageGroupCall -> describeGroupCall(content)

        is TdApi.MessageCustomServiceAction -> content.text.orEmpty().ifBlank { "Chat event" }
        is TdApi.MessageGameScore -> "$actorName scored ${content.score}"
        is TdApi.MessagePaymentSuccessful -> "Payment completed"
        is TdApi.MessagePaymentRefunded -> "Payment refunded"
        is TdApi.MessageGiftedPremium -> "$actorName gifted Telegram Premium"
        is TdApi.MessageGiftedStars -> "$actorName sent a gift"
        is TdApi.MessageGiveawayCreated -> "A giveaway started"
        is TdApi.MessageGiveawayCompleted -> "The giveaway ended"
        is TdApi.MessagePaidMessagePriceChanged -> "The message price changed"
        is TdApi.MessageDirectMessagePriceChanged -> "The direct message price changed"
        is TdApi.MessageSuggestProfilePhoto -> "$actorName suggested a profile photo"
        is TdApi.MessageSuggestBirthdate -> "$actorName suggested adding a birthday"
        is TdApi.MessageUsersShared -> "$actorName shared contacts"
        is TdApi.MessageChatShared -> "$actorName shared a chat"
        is TdApi.MessageBotWriteAccessAllowed -> "You allowed this bot to message you"
        is TdApi.MessageWebAppDataSent, is TdApi.MessageWebAppDataReceived -> "Web app data was exchanged"
        is TdApi.MessagePassportDataSent, is TdApi.MessagePassportDataReceived ->
            "Telegram Passport data was exchanged"

        // A system event Aether has no wording for still reads as a system event.
        else -> fallbackDescription(content)
    }

    /**
     * A neutral description derived from the event's actual TDLib type.
     *
     * Deliberately dull and deliberately generic. It says something happened without
     * claiming to know what, which is the honest position for a type this build has
     * never been taught about.
     */
    fun fallbackDescription(content: TdApi.MessageContent?): String {
        val name = content?.javaClass?.simpleName?.removePrefix("Message").orEmpty()
        if (name.isBlank()) return "Chat event"
        val spaced = name
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .lowercase()
        return spaced.replaceFirstChar { it.uppercase() }
    }

    private fun describeChecklistProgress(
        content: TdApi.MessageChecklistTasksDone,
        actorName: String
    ): String {
        val done = content.markedAsDoneTaskIds?.size ?: 0
        val undone = content.markedAsNotDoneTaskIds?.size ?: 0
        return when {
            done > 0 && undone > 0 -> "$actorName updated the checklist"
            done == 1 -> "$actorName completed a task"
            done > 1 -> "$actorName completed $done tasks"
            undone == 1 -> "$actorName reopened a task"
            undone > 1 -> "$actorName reopened $undone tasks"
            else -> "$actorName updated the checklist"
        }
    }

    private fun describeGroupCall(content: TdApi.MessageGroupCall): String {
        val kind = if (content.isVideo) "Video chat" else "Voice chat"
        return when {
            content.isActive -> "$kind in progress"
            content.wasMissed -> "Missed $kind"
            content.duration > 0 -> "$kind ended · ${formatDuration(content.duration)}"
            else -> "$kind ended"
        }
    }

    private fun describeAutoDelete(
        content: TdApi.MessageChatSetMessageAutoDeleteTime,
        actorName: String
    ): String {
        val seconds = content.messageAutoDeleteTime
        return if (seconds <= 0) {
            "$actorName turned off auto-delete"
        } else {
            "$actorName set messages to delete after ${formatDuration(seconds)}"
        }
    }

    private fun formatDuration(seconds: Int): String = when {
        seconds <= 0 -> "0s"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60} min"
        seconds < 86400 -> "${seconds / 3600} h"
        seconds % 604800 == 0 -> "${seconds / 604800} week${if (seconds / 604800 == 1) "" else "s"}"
        else -> "${seconds / 86400} day${if (seconds / 86400 == 1) "" else "s"}"
    }
}
