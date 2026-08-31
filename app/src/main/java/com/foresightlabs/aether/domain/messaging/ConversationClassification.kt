package com.foresightlabs.aether.domain.messaging

/**
 * What kind of thing a conversation is, as one canonical answer.
 *
 * Aether is people-first, and before this existed that intent was expressed as a
 * single boolean -- "personal chat or not" -- evaluated independently in the chat
 * list, in search, and in the notification path. A boolean has room for exactly two
 * outcomes, so everything that was not a person fell into the same bucket as a
 * marketing bot, Telegram's own account included. That was wrong: Telegram's
 * service account is not content Aether is filtering out on the user's behalf, it
 * is the channel Telegram itself uses to deliver login codes and security notices,
 * and losing those is a security problem rather than a cleaner feed.
 *
 * Four outcomes, so the difference can actually be represented:
 *
 * - [PERSONAL_HUMAN] -- the primary Aether messaging experience.
 * - [TELEGRAM_SERVICE] -- Telegram's own account. Always reachable, never filtered.
 * - [SECONDARY_TELEGRAM_CONTENT] -- bots, groups, channels, broadcasts, forums.
 *   Aether's existing secondary/filtering policy applies unchanged.
 * - [UNKNOWN] -- classification could not be completed. Fail closed: treated as
 *   not deliverable, exactly as the previous boolean did when a lookup failed.
 */
enum class ConversationClass {
    PERSONAL_HUMAN,
    TELEGRAM_SERVICE,
    SECONDARY_TELEGRAM_CONTENT,
    UNKNOWN;

    /**
     * Whether Aether surfaces this conversation in its primary messaging surfaces
     * (chat list, search, Android notifications).
     *
     * Deliberately not the same question as "is this a person": service messages
     * are delivered without being treated as people, so they never reach the
     * presence strip or anything else keyed on [PERSONAL_HUMAN].
     */
    val isDeliverable: Boolean
        get() = this == PERSONAL_HUMAN || this == TELEGRAM_SERVICE
}

/** Identifiers Telegram itself defines, rather than ones Aether chose. */
object TelegramIdentity {
    /**
     * Telegram's service/notification account -- login codes, security alerts,
     * account notices. This is a fixed, Telegram-assigned id, not a heuristic
     * about accounts that merely look official, and it is the only account
     * treated as [ConversationClass.TELEGRAM_SERVICE].
     */
    const val SERVICE_NOTIFICATIONS_USER_ID = 777000L
}

/**
 * The facts classification depends on, named separately from where they came from.
 *
 * Both callers -- Aether's own [Chat][com.foresightlabs.aether.domain.model.Chat]
 * model and the raw `TdApi.Chat`/`TdApi.User` pair the notification path holds --
 * can produce these, so both get an identical answer from one rule set instead of
 * two drifting copies. Being plain data also means the rules are testable without
 * TDLib or an Android environment.
 */
data class ConversationFacts(
    /** A one-to-one conversation (private or secret), as opposed to any group form. */
    val isOneToOne: Boolean,
    /** A forum supergroup, which opens as topics rather than as one conversation. */
    val isForum: Boolean = false,
    /** The user's own Saved Messages / self chat. */
    val isSavedMessages: Boolean = false,
    /** The other party's user id, where there is exactly one. */
    val counterpartUserId: Long? = null,
    val isBot: Boolean = false,
    val isDeleted: Boolean = false,
    /**
     * Whether the counterpart was actually resolved. False means the lookup did
     * not complete -- not that the answer was "no" -- and yields [ConversationClass.UNKNOWN].
     */
    val isCounterpartKnown: Boolean = true
)

/**
 * The single rule set deciding what a conversation is.
 *
 * Order matters. Telegram's service account is checked first and unconditionally:
 * it is flagged as a bot by TDLib, so any ordering that consulted [ConversationFacts.isBot]
 * first would classify Telegram's own login codes as marketing content and hide
 * them -- the exact defect this classification replaces. Being Telegram's fixed
 * service id is a stronger fact than the bot flag, so it wins outright.
 *
 * No other Telegram-owned or official-looking account is reclassified. Verified
 * accounts, support bots and official channels stay [ConversationClass.SECONDARY_TELEGRAM_CONTENT]:
 * the exemption is for one specific, Telegram-defined id, not for a category of
 * accounts that look important.
 */
fun classifyConversation(facts: ConversationFacts): ConversationClass = when {
    facts.counterpartUserId == TelegramIdentity.SERVICE_NOTIFICATIONS_USER_ID ->
        ConversationClass.TELEGRAM_SERVICE

    !facts.isOneToOne || facts.isForum || facts.isSavedMessages ->
        ConversationClass.SECONDARY_TELEGRAM_CONTENT

    facts.isBot || facts.isDeleted -> ConversationClass.SECONDARY_TELEGRAM_CONTENT

    // Reached only for a one-to-one chat whose counterpart could not be resolved:
    // there is no evidence either way, so this stays fail-closed rather than being
    // optimistically promoted to a person.
    !facts.isCounterpartKnown -> ConversationClass.UNKNOWN

    else -> ConversationClass.PERSONAL_HUMAN
}
