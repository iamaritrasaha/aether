package com.foresightlabs.aether.domain.calls

import com.foresightlabs.aether.domain.messaging.ConversationClass

/**
 * Whether a private voice call action can be offered for a conversation.
 *
 * Deliberately reuses [ConversationClass] -- the same classification the
 * notification path uses to decide what a conversation is -- rather than a
 * second, parallel set of chat-type checks. [ConversationClass.PERSONAL_HUMAN]
 * is the only class describing an actual one-to-one human conversation:
 * Telegram's own service account, bots, groups, channels, forums, Saved
 * Messages and unresolved counterparts are all excluded by construction, not
 * by an enumeration this call site would have to keep in sync by hand.
 */
object CallEligibility {
    /**
     * @param conversationClass what this conversation actually is.
     * @param isMediaTransportAvailable whether the backend can carry real
     *   audio right now -- a call must never be offered as functional when
     *   nothing can carry it, even for an otherwise-eligible person.
     */
    fun isEligible(
        conversationClass: ConversationClass,
        isMediaTransportAvailable: Boolean
    ): Boolean = conversationClass == ConversationClass.PERSONAL_HUMAN && isMediaTransportAvailable
}
