package com.foresightlabs.aether.data.push

/**
 * The bookkeeping behind registering this device's FCM token with Telegram:
 * which token still needs registering, whether an attempt is worth repeating,
 * and when to stop.
 *
 * Registration is the single point of failure for background delivery -- if
 * Telegram never accepts a token, Telegram never sends a push, and every later
 * stage of the pipeline is irrelevant because it is never reached. So failures
 * are not all treated alike:
 *
 * - **Rejected** (4xx): the request itself is not acceptable to the server and
 *   repeating it verbatim cannot change that. This covers a token the server
 *   considers invalid, and -- the case that matters most in practice -- the
 *   server-side application configuration missing its push credentials
 *   (`APP_PUSH_APIKEY_MISSING`), which no amount of client retrying fixes. The
 *   attempt is not repeated within this process; a later process start (or a
 *   new token) tries again on its own, which is when a server-side fix would
 *   take effect.
 * - **Retryable** (anything else -- transport failures, server-side errors,
 *   rate limiting): the request was never really answered. These are retried,
 *   but only when something has changed that could plausibly make a difference
 *   (the connection came back), and only [MAX_RETRYABLE_ATTEMPTS] times, so
 *   this is a bounded, event-driven reaction rather than a retry loop.
 *
 * Deliberately holds no coroutines, no timers and no I/O: it decides, the
 * caller acts. That also makes the sequencing testable without TDLib.
 */
class PushRegistration {

    /** How the caller should treat a completed attempt. */
    enum class Failure {
        /** Worth attempting again on the next connection; the caller should keep the token pending. */
        RETRYABLE,

        /** The server refused this request; not repeated in this process. */
        REJECTED
    }

    private val lock = Any()
    private var pendingToken: String? = null
    private var registeredToken: String? = null
    private var attemptsSpent = 0
    private var rejected = false

    /** The token registration is currently holding, if any. Registered tokens are not pending. */
    val tokenAwaitingRegistration: String?
        get() = synchronized(lock) { pendingToken }

    /** The token Telegram has accepted in this process, if any. */
    val currentlyRegisteredToken: String?
        get() = synchronized(lock) { registeredToken }

    /**
     * Records a token Firebase produced.
     *
     * Returns true when this is a token that still needs registering -- so a
     * Service re-delivering the same token, or two callers reporting the same
     * one, does not cause a second RegisterDevice. A genuinely new token
     * clears any earlier rejection: it is a different request, and the server
     * may well answer it differently.
     */
    fun onTokenAvailable(token: String): Boolean = synchronized(lock) {
        if (token.isBlank()) return false
        if (token == registeredToken) return false
        if (token != pendingToken) {
            pendingToken = token
            attemptsSpent = 0
            rejected = false
        }
        true
    }

    /**
     * The token to send in a RegisterDevice attempt right now, or null if
     * there is nothing to attempt -- nothing pending, or the pending token has
     * already used up its attempts or been rejected.
     */
    fun beginAttempt(): String? = synchronized(lock) {
        val token = pendingToken ?: return null
        if (rejected) return null
        if (attemptsSpent >= MAX_RETRYABLE_ATTEMPTS) return null
        attemptsSpent++
        token
    }

    /** Telegram accepted [token]; nothing is pending any more. */
    fun onRegistered(token: String) = synchronized(lock) {
        registeredToken = token
        if (pendingToken == token) pendingToken = null
        attemptsSpent = 0
        rejected = false
    }

    /**
     * Telegram did not accept the attempt. [code] is TDLib's error code.
     *
     * Codes in the 4xx range are the server answering the request and refusing
     * it; anything else (a transport failure, a 5xx, TDLib's own -1) means the
     * request did not get a real answer and is worth repeating.
     */
    fun onAttemptFailed(code: Int): Failure = synchronized(lock) {
        val retryable = code < 400 || code >= 500
        if (!retryable) {
            rejected = true
            return Failure.REJECTED
        }
        Failure.RETRYABLE
    }

    /**
     * The connection came back. Returns the token worth attempting again, or
     * null when there is nothing pending, the pending token was rejected, or
     * its attempts are spent.
     */
    fun tokenToRetryOnReconnect(): String? = synchronized(lock) {
        val token = pendingToken ?: return null
        if (rejected) return null
        if (attemptsSpent >= MAX_RETRYABLE_ATTEMPTS) return null
        token
    }

    /**
     * The session this token was registered against is gone (logout). A future
     * login must register from scratch rather than treating the old
     * registration as still standing.
     */
    fun onSessionCleared() = synchronized(lock) {
        pendingToken = null
        registeredToken = null
        attemptsSpent = 0
        rejected = false
    }

    companion object {
        /**
         * Bounded on purpose: enough for a registration to survive a flaky
         * connection at start-up, few enough that a persistently failing
         * server is not hammered. A later process start registers again
         * regardless.
         */
        const val MAX_RETRYABLE_ATTEMPTS = 3
    }
}
