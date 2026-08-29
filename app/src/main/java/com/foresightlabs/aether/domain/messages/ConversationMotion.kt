package com.foresightlabs.aether.domain.messages

/** A server-backed message change that may deserve a small visual transition. */
enum class MessageMotionEventType {
    INITIAL_HISTORY,
    PAGINATION_HISTORY,
    NEW_INCOMING,
    NEW_OUTGOING,
    SEND_CONFIRMED,
    EDITED,
    DELETED,
    MEDIA_UPDATED,
    REACTION_UPDATED,
    FAILED
}

data class MessageMotionEvent(
    val chatId: Long,
    val messageId: String,
    val type: MessageMotionEventType,
    val token: Long
)

/** Shared timing and entrance values for Conversation motion. */
object ConversationMotion {
    const val FAST_MS = 140
    const val STANDARD_MS = 230
    const val EMPHASIZED_MS = 280
    const val COMPOSER_TEXT_FADE_MS = 100

    data class Entrance(
        val translationX: Float,
        val translationY: Float,
        val scale: Float,
        val durationMs: Int
    )

    fun entrance(type: MessageMotionEventType, reducedMotion: Boolean): Entrance? {
        if (reducedMotion) return when (type) {
            MessageMotionEventType.NEW_INCOMING,
            MessageMotionEventType.NEW_OUTGOING -> Entrance(0f, 0f, 1f, FAST_MS)
            else -> null
        }
        return when (type) {
            MessageMotionEventType.NEW_OUTGOING -> Entrance(8f, 15f, 0.975f, STANDARD_MS)
            MessageMotionEventType.NEW_INCOMING -> Entrance(-8f, 6f, 0.985f, STANDARD_MS)
            else -> null
        }
    }

    fun usesShortChange(type: MessageMotionEventType): Boolean = when (type) {
        MessageMotionEventType.EDITED,
        MessageMotionEventType.MEDIA_UPDATED,
        MessageMotionEventType.REACTION_UPDATED,
        MessageMotionEventType.SEND_CONFIRMED,
        MessageMotionEventType.FAILED -> true
        else -> false
    }
}
