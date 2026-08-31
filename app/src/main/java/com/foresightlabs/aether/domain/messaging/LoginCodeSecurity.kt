package com.foresightlabs.aether.domain.messaging

/**
 * Telegram's login-code security rules: which messages actually contain a sign-in
 * code, and what Aether is able to do about it on the pinned TDLib revision.
 *
 * Telegram treats a login code that leaves the device -- forwarded to someone, or
 * captured in a screenshot that gets shared -- as compromised, and official clients
 * invalidate the code at that moment so a code the user was socially engineered
 * into sending is already dead by the time it arrives. That behaviour needs a
 * server call; see [InvalidationSupport] for why Aether cannot currently make it.
 *
 * Everything in this file is pure. It decides *whether* a message qualifies and
 * *which* codes it contains; it never performs, logs, or displays anything.
 */
object LoginCodeSecurity {

    /**
     * Whether the pinned TDLib revision exposes a way to invalidate sign-in codes.
     *
     * It does not. TDLib's `invalidateSignInCodes` is absent from the generated
     * API at the revision this project pins (`BuildConfig.TDLIB_COMMIT`), verified
     * against `tdlib/src/main/java/org/drinkless/tdlib/TdApi.java` -- there is no
     * such Function class, and no differently-named equivalent.
     *
     * The two dishonest ways to appear to support it are both deliberately not
     * taken: hiding the Forward button imitates the security property without
     * providing it (the code stays valid, and the user can still relay it by
     * hand), and issuing raw MTProto around TDLib would mean running a second,
     * unsupervised protocol implementation against the user's session. So the
     * qualifying rules below are implemented and tested, the capability is
     * reported as unsupported, and no call is faked.
     *
     * When TDLib is next bumped to a revision that defines it, the wiring is one
     * call at the forward site plus flipping this constant.
     */
    const val INVALIDATION_SUPPORTED_BY_PINNED_TDLIB = false

    /**
     * Telegram's own code shape: 5 to 7 decimal digits, which the server may
     * present split by `-` separators (e.g. `123-456`). Anything else is not a
     * login code.
     */
    private const val MIN_CODE_DIGITS = 5
    private const val MAX_CODE_DIGITS = 7

    /**
     * A run of digits and `-` that is not touching further digits or separators on
     * either side. The boundary conditions are the point: without them `1234567890`
     * would yield its own first seven digits as a "code", and a phone number in an
     * unrelated message would look like a login code.
     */
    private val CANDIDATE = Regex("(?<![0-9-])[0-9][0-9-]*[0-9](?![0-9-])")

    /**
     * Whether a message is even eligible to be searched for login codes.
     *
     * All three conditions are required, and the sender check is the one that
     * matters most: only Telegram's own service account issues login codes, so
     * scanning any other chat for digit sequences would mean treating an ordinary
     * message containing a number as a security event. [isTextMessage] keeps this
     * to text content -- a caption or filename that happens to contain digits is
     * not a login code either.
     */
    fun qualifiesForCodeExtraction(senderUserId: Long, isTextMessage: Boolean): Boolean =
        senderUserId == TelegramIdentity.SERVICE_NOTIFICATIONS_USER_ID && isTextMessage

    /**
     * The login codes contained in a qualifying message, normalized to digits only
     * (separators removed) as Telegram's invalidation call expects them.
     *
     * Returns empty for any message that does not qualify, rather than scanning it
     * anyway: a non-service chat is never searched for numbers at all.
     */
    fun extractLoginCodes(senderUserId: Long, isTextMessage: Boolean, text: String): List<String> {
        if (!qualifiesForCodeExtraction(senderUserId, isTextMessage)) return emptyList()
        return CANDIDATE.findAll(text)
            .map { it.value.replace("-", "") }
            .filter { it.length in MIN_CODE_DIGITS..MAX_CODE_DIGITS }
            .distinct()
            .toList()
    }

    /**
     * Whether a screen capture taken right now should invalidate any code.
     *
     * Screenshot detection is device-wide: the OS reports that the user captured
     * the screen, not what was on it. Invalidating on every capture anywhere in
     * Aether would kill valid codes for users who screenshot an unrelated
     * conversation, so the decision is tied to what was actually visible --
     * [visibleServiceMessages] is the set of on-screen messages, and a capture only
     * matters when at least one of them qualifies and carries a code.
     *
     * Returns the codes that were exposed, empty if none were. Nothing about the
     * screenshot itself is examined: the image is never read, stored, or inspected,
     * and the decision rests entirely on conversation state Aether already holds.
     */
    fun codesExposedByCapture(visibleServiceMessages: List<VisibleMessage>): List<String> =
        visibleServiceMessages
            .flatMap { extractLoginCodes(it.senderUserId, it.isTextMessage, it.text) }
            .distinct()

    /** The minimum an on-screen message needs to expose for a capture decision. */
    data class VisibleMessage(
        val senderUserId: Long,
        val isTextMessage: Boolean,
        val text: String
    )
}
