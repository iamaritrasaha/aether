package com.foresightlabs.aether.domain.messages

import androidx.compose.runtime.Immutable
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType

/** When a message should actually be delivered. */
@Immutable
sealed interface SendSchedule {
    /** Delivered immediately. */
    data object Now : SendSchedule

    /** Delivered at a specific Unix time, in seconds. */
    data class At(val epochSeconds: Int) : SendSchedule

    /**
     * Held until the recipient next comes online.
     *
     * Only meaningful in a private chat with someone whose presence Telegram
     * actually reports; there is nothing to wait for otherwise.
     */
    data object WhenOnline : SendSchedule
}

/**
 * Delivery options for one outgoing message.
 *
 * These map onto TDLib's own `MessageSendOptions`. Nothing here is simulated by
 * Aether — a scheduled message is scheduled on the server and continues to exist
 * whether or not Aether is running.
 */
@Immutable
data class SendOptions(
    /** Deliver without a notification sound on the recipient's device. */
    val silent: Boolean = false,
    val schedule: SendSchedule = SendSchedule.Now
) {
    val isDefault: Boolean get() = !silent && schedule == SendSchedule.Now

    companion object {
        val Default = SendOptions()
    }
}

/**
 * Which delivery options a given chat can actually honour.
 *
 * Availability is decided from the chat itself rather than shown everywhere and
 * failing on send. "Send when online" in particular is meaningless in a group — there
 * is no single recipient to wait for — and Telegram rejects it there.
 */
object SendOptionsPolicy {

    /** Whether a silent send is possible. */
    fun canSendSilently(chat: Chat?): Boolean = chat != null

    /** Whether messages can be scheduled to a date. */
    fun canSchedule(chat: Chat?): Boolean = when (chat?.type) {
        null -> false
        // Secret chats have no server-side scheduling: messages exist only on the
        // two devices, so there is nothing to hold them.
        ChatType.SECRET -> false
        else -> true
    }

    /** Whether "send when online" applies to this chat. */
    fun canSendWhenOnline(chat: Chat?): Boolean {
        if (chat?.type != ChatType.DIRECT) return false
        // Waiting on presence only makes sense when presence is actually reported.
        return chat.directUser != null
    }

    /** The schedules worth offering for this chat. */
    fun availableSchedules(chat: Chat?): List<SendScheduleKind> = buildList {
        add(SendScheduleKind.NOW)
        if (canSchedule(chat)) add(SendScheduleKind.AT_TIME)
        if (canSendWhenOnline(chat)) add(SendScheduleKind.WHEN_ONLINE)
    }
}

/** The kinds of schedule a picker can offer, without a concrete time attached. */
enum class SendScheduleKind {
    NOW,
    AT_TIME,
    WHEN_ONLINE
}
