package com.foresightlabs.aether.data.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CameraBackgroundState] is the pure decision logic behind pausing local
 * camera capture on app background and resuming it on app foreground during
 * a video call (see its own doc for why: Aether declares no camera-typed
 * foreground service, so holding the camera open off-screen is not an
 * option). Every test here is a direct, dependency-free exercise of that
 * state machine -- no `DefaultCallsRepository`, `TelegramClient` or
 * `Application` involved.
 */
class CameraBackgroundStateTest {

    @Test
    fun voiceCallIsNeverTouchedByForegroundTransitionsEitherDirection() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = false)

        assertEquals(CameraBackgroundAction.NONE, state.onForegroundChanged(foreground = false, isVideoCall = false, activeCallId = 1))
        assertEquals(CameraBackgroundAction.NONE, state.onForegroundChanged(foreground = true, isVideoCall = false, activeCallId = 1))
    }

    @Test
    fun videoCallWithCameraOnPausesOnBackground() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = true)

        val action = state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)

        assertEquals(CameraBackgroundAction.PAUSE, action)
    }

    @Test
    fun videoCallWithCameraAlreadyOffDoesNothingOnBackground() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = false)

        val action = state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)

        assertEquals(CameraBackgroundAction.NONE, action)
    }

    @Test
    fun repeatedBackgroundEventsPauseOnlyOnce() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = true)

        val first = state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)
        val second = state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)

        assertEquals(CameraBackgroundAction.PAUSE, first)
        assertEquals("A second background event for an already-paused call must be a no-op, not a duplicate pause", CameraBackgroundAction.NONE, second)
    }

    @Test
    fun resumesOnForegroundForTheSameCallWhenUserStillWantsCameraOn() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = true)
        state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)

        val action = state.onForegroundChanged(foreground = true, isVideoCall = true, activeCallId = 1)

        assertEquals(CameraBackgroundAction.RESUME, action)
    }

    @Test
    fun doesNotResumeIfUserTurnedCameraOffWhileBackgrounded() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = true)
        state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)

        // e.g. a call-notification camera-off action while the app is still backgrounded.
        state.onUserSetCameraEnabled(false)

        val action = state.onForegroundChanged(foreground = true, isVideoCall = true, activeCallId = 1)

        assertEquals("Resuming must never override an explicit user choice made while backgrounded", CameraBackgroundAction.NONE, action)
    }

    @Test
    fun foregroundEventForADifferentCallIdThanThePauseNeverResumes() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = true)
        state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)

        // Simulates a stale foreground event arriving after call 1 ended and
        // call 2 is now active, without onCallStarted/onCallEnded having run
        // in between (the race this class exists to close explicitly,
        // rather than relying only on the native engine's own callId check).
        val action = state.onForegroundChanged(foreground = true, isVideoCall = true, activeCallId = 2)

        assertEquals("A foreground event must only resume the exact call it paused, never a different active call", CameraBackgroundAction.NONE, action)
    }

    @Test
    fun foregroundEventWithNothingPausedIsANoOp() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = true)

        val action = state.onForegroundChanged(foreground = true, isVideoCall = true, activeCallId = 1)

        assertEquals(CameraBackgroundAction.NONE, action)
    }

    @Test
    fun callEndedClearsEveryPieceOfState() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = true)
        state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)

        state.onCallEnded()

        assertFalse(state.userWantsCameraOn)
        // A stale foreground callback arriving after teardown, for whatever
        // callId, must never resume anything -- there is no pause left to
        // resume.
        assertEquals(
            CameraBackgroundAction.NONE,
            state.onForegroundChanged(foreground = true, isVideoCall = true, activeCallId = 1)
        )
    }

    @Test
    fun aSecondCallStartsWithCleanStateRegardlessOfTheFirstCallsHistory() {
        val state = CameraBackgroundState()

        // First call: video, paused for background, never resumed (e.g. the
        // call ended while still backgrounded).
        state.onCallStarted(cameraEnabled = true)
        state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)
        state.onCallEnded()

        // Second call, different id, starts clean.
        state.onCallStarted(cameraEnabled = true)
        assertTrue(state.userWantsCameraOn)

        val pauseForSecondCall = state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 2)
        assertEquals("The second call must pause independently, not be blocked by the first call's leftover state", CameraBackgroundAction.PAUSE, pauseForSecondCall)

        val resumeForSecondCall = state.onForegroundChanged(foreground = true, isVideoCall = true, activeCallId = 2)
        assertEquals(CameraBackgroundAction.RESUME, resumeForSecondCall)
    }

    @Test
    fun onCallStartedAloneResetsAnyLeftoverPauseStateEvenWithoutOnCallEnded() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = true)
        state.onForegroundChanged(foreground = false, isVideoCall = true, activeCallId = 1)

        // A new call starts directly (onCallStarted is also called at the
        // top of DefaultCallsRepository.handleRawCallUpdate on every fresh
        // CallStateReady) -- this alone must be enough to discard call 1's
        // pause state, without requiring onCallEnded to have run first.
        state.onCallStarted(cameraEnabled = true)

        val action = state.onForegroundChanged(foreground = true, isVideoCall = true, activeCallId = 2)

        assertEquals("onCallStarted must discard any prior pause state on its own", CameraBackgroundAction.NONE, action)
    }

    @Test
    fun userWantsCameraOnReflectsExplicitToggles() {
        val state = CameraBackgroundState()
        state.onCallStarted(cameraEnabled = false)
        assertFalse(state.userWantsCameraOn)

        state.onUserSetCameraEnabled(true)
        assertTrue(state.userWantsCameraOn)

        state.onUserSetCameraEnabled(false)
        assertFalse(state.userWantsCameraOn)
    }
}
