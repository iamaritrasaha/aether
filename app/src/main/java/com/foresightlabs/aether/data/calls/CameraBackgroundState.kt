package com.foresightlabs.aether.data.calls

/**
 * What [CameraBackgroundState.onForegroundChanged] decides a foreground/
 * background transition should do to local camera capture. [NONE] is the
 * common case -- most transitions (voice calls, an already-paused call, a
 * foreground event with nothing to resume) do nothing at all.
 */
internal enum class CameraBackgroundAction { NONE, PAUSE, RESUME }

/**
 * Pure, dependency-free state machine for pausing local camera capture when
 * the app backgrounds during a video call and resuming it when the app
 * returns -- extracted out of [DefaultCallsRepository] specifically so its
 * race and stale-call guarantees can be unit tested directly, without
 * constructing a full repository (`TelegramClient`, `Application`,
 * `PermissionCoordinator`).
 *
 * Exists because Aether declares no camera-typed foreground service (see
 * `CallService`'s manifest entry): Android does not allow camera access from
 * a background process without one, and there is no picture-in-picture
 * experience here to justify holding the camera open off-screen anyway.
 *
 * This class owns no threading of its own -- [DefaultCallsRepository] calls
 * it from whatever thread its own collectors run on, and every method here
 * is a plain synchronous state transition with no suspension point, so a
 * caller that already serializes its own access (or accepts the same
 * volatile-field consistency `DefaultCallsRepository`'s other state uses)
 * needs nothing extra.
 */
internal class CameraBackgroundState {

    /**
     * Whether the user currently wants the camera on for this call --
     * independent of whether it is actually open right now. Only ever set
     * by [onCallStarted] (the call's initial video-capture decision) and
     * [onUserSetCameraEnabled] (an explicit user toggle); never touched by
     * [onForegroundChanged] itself, so a background-triggered pause can
     * always tell "off because Aether paused it" from "off because the user
     * turned it off".
     */
    @Volatile
    var userWantsCameraOn: Boolean = false
        private set

    /** True only while local camera capture is paused *because the app is
     * backgrounded*, as opposed to the user's own choice. */
    @Volatile
    private var pausedForBackground: Boolean = false

    /**
     * The call id the pause above belongs to, captured at pause time.
     * [onForegroundChanged] only resumes the camera if this still matches
     * the currently active call -- the app's own foreground/background
     * signal is independent of the TDLib/teardown state (different
     * collectors, no shared ordering guarantee), so a foreground event that
     * happens to run after a call has already ended and a *new* call has
     * started must never be read as "resume camera for whatever call
     * happens to be active now". `NativeTelegramCallMediaEngine`'s own
     * `activeCallId` check is a second, independent line of defence against
     * the same mistake at the hardware level; this makes the guarantee
     * explicit and testable at this layer too.
     */
    @Volatile
    private var pausedForCallId: Int? = null

    /** A new call started (or this repository's state was otherwise reset):
     * nothing from any previous call may leak into this one. */
    fun onCallStarted(cameraEnabled: Boolean) {
        userWantsCameraOn = cameraEnabled
        pausedForBackground = false
        pausedForCallId = null
    }

    /** The call ended. Equivalent to [onCallStarted] with the camera off,
     * named separately for clarity at call sites. */
    fun onCallEnded() {
        userWantsCameraOn = false
        pausedForBackground = false
        pausedForCallId = null
    }

    /** The user explicitly toggled the camera -- the one thing that may
     * change [userWantsCameraOn] outside of [onCallStarted]/[onCallEnded]. */
    fun onUserSetCameraEnabled(enabled: Boolean) {
        userWantsCameraOn = enabled
    }

    /**
     * Decides what a foreground/background transition should do to local
     * camera capture, for the call currently identified by [activeCallId] --
     * or [CameraBackgroundAction.NONE] if [isVideoCall] is false (voice
     * calls are never touched by this at all).
     */
    fun onForegroundChanged(foreground: Boolean, isVideoCall: Boolean, activeCallId: Int): CameraBackgroundAction {
        if (!isVideoCall) return CameraBackgroundAction.NONE

        if (!foreground) {
            if (userWantsCameraOn && !pausedForBackground) {
                pausedForBackground = true
                pausedForCallId = activeCallId
                return CameraBackgroundAction.PAUSE
            }
            return CameraBackgroundAction.NONE
        }

        if (!pausedForBackground) return CameraBackgroundAction.NONE
        val pausedCallId = pausedForCallId
        pausedForBackground = false
        pausedForCallId = null
        return if (userWantsCameraOn && pausedCallId == activeCallId) {
            CameraBackgroundAction.RESUME
        } else {
            CameraBackgroundAction.NONE
        }
    }
}
