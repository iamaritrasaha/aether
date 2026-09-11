package com.foresightlabs.aether.data.telegram

/**
 * Whether an [AuthorizationStateClosed][org.drinkless.tdlib.TdApi.AuthorizationStateClosed]
 * should recreate the TDLib client, expressed without TDLib so it is testable
 * rather than only reviewable.
 *
 * TDLib's own contract for AuthorizationStateClosed is unconditional: "To
 * continue working, one must create a new instance of the TDLib client." That
 * is expected and necessary after an ordinary LogOut() -- the next phone
 * submission needs a live client to send it to. But Closed reachable a second
 * time with no successful Ready in between means the fresh client closed
 * again on its own, and retrying blindly would spin forever. One restart per
 * successful session is the whole policy.
 */
class TdlibRestartPolicy {
    @Volatile
    private var restartedSinceLastReady = false

    /** Call on AuthorizationStateClosed. Returns true if the client should be recreated. */
    fun onClosed(): Boolean {
        if (restartedSinceLastReady) return false
        restartedSinceLastReady = true
        return true
    }

    /** Call on AuthorizationStateReady: a live session clears the guard for its own future close. */
    fun onReady() {
        restartedSinceLastReady = false
    }
}
