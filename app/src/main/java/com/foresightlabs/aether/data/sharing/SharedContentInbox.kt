package com.foresightlabs.aether.data.sharing

import com.foresightlabs.aether.domain.sharing.SharedContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A share that has been addressed to a conversation but not yet sent. */
data class SharedDelivery(val chatId: Long, val content: SharedContent)

/**
 * Where a share waits between arriving and being sent.
 *
 * Held for the process rather than in a composable or a saved-state bundle,
 * because a share outlives all three things that could otherwise drop it: the
 * Activity being recreated, the navigation graph moving between Home, recipient
 * selection and a conversation, and the share arriving while Aether is already
 * running.
 *
 * Nothing here sends anything. [pending] is a share looking for a recipient;
 * [delivery] is a share whose recipient has been chosen and which is waiting for
 * the person to press send in that conversation.
 */
object SharedContentInbox {

    private val _pending = MutableStateFlow<SharedContent?>(null)

    /** The share awaiting recipient selection, if any. */
    val pending: StateFlow<SharedContent?> = _pending.asStateFlow()

    private val _delivery = MutableStateFlow<SharedDelivery?>(null)

    /** The share addressed to a conversation, waiting to be previewed there. */
    val delivery: StateFlow<SharedDelivery?> = _delivery.asStateFlow()

    private var acceptedIdentity: String? = null

    /**
     * Accepts a newly received share.
     *
     * [identity] describes what the share *is*, so the same Intent redelivered
     * after an Activity recreation is recognised and ignored rather than opening
     * recipient selection a second time. Returns whether this call accepted it.
     */
    fun offer(content: SharedContent?, identity: String?): Boolean {
        if (content == null) return false
        if (identity != null && identity == acceptedIdentity) return false
        acceptedIdentity = identity
        _delivery.value = null
        _pending.value = content
        return true
    }

    /** The recipient has been chosen: the share moves on to that conversation. */
    fun addressTo(chatId: Long) {
        val content = _pending.value ?: return
        _pending.value = null
        _delivery.value = SharedDelivery(chatId, content)
    }

    /**
     * Hands the waiting share to [chatId], once.
     *
     * A conversation asks for what it is holding; anything addressed elsewhere is
     * left alone, and taking it clears it so re-entering the conversation does not
     * present the same share again.
     */
    fun consumeDelivery(chatId: Long): SharedContent? {
        val waiting = _delivery.value ?: return null
        if (waiting.chatId != chatId) return null
        _delivery.value = null
        return waiting.content
    }

    /** The share was abandoned -- recipient selection dismissed, or nothing to send. */
    fun clear() {
        _pending.value = null
        _delivery.value = null
    }

    /** Test seam: forgets what has already been accepted. */
    fun reset() {
        acceptedIdentity = null
        clear()
    }
}
