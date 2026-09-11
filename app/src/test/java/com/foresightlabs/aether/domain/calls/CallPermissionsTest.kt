package com.foresightlabs.aether.domain.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a call needs from the OS is a property of the call, not of the screen
 * that starts it. These are the rules three layers -- the UI that asks, the
 * foreground service that declares a type, and the media engine that opens the
 * devices -- all read from.
 */
class CallPermissionsTest {

    @Test
    fun aVoiceCallAsksForTheMicrophoneOnly() {
        val required = CallPermissions.required(isVideo = false)
        assertEquals(listOf(CallPermissions.MICROPHONE), required)
    }

    /** Contextual permissions: the camera is never requested for a voice call. */
    @Test
    fun aVoiceCallNeverAsksForTheCamera() {
        assertFalse(CallPermissions.CAMERA in CallPermissions.required(isVideo = false))
    }

    @Test
    fun aVideoCallAsksForBothWithTheMicrophoneFirst() {
        val required = CallPermissions.required(isVideo = true)
        assertEquals(listOf(CallPermissions.MICROPHONE, CallPermissions.CAMERA), required)
    }

    @Test
    fun aCallIsImpossibleWithoutTheMicrophone() {
        assertFalse(CallPermissions.callAllowed(emptySet()))
        assertFalse(CallPermissions.callAllowed(setOf(CallPermissions.CAMERA)))
        assertTrue(CallPermissions.callAllowed(setOf(CallPermissions.MICROPHONE)))
    }

    /**
     * A refused camera degrades a video call to audio rather than cancelling it.
     * This is the split that keeps video initialisation off a voice call's path.
     */
    @Test
    fun aRefusedCameraDegradesAVideoCallRatherThanBlockingIt() {
        val micOnly = setOf(CallPermissions.MICROPHONE)

        assertTrue(CallPermissions.callAllowed(micOnly))
        assertFalse(CallPermissions.videoCaptureAllowed(isVideo = true, grants = micOnly))
    }

    @Test
    fun videoCaptureNeedsBothTheCallKindAndTheGrant() {
        val both = setOf(CallPermissions.MICROPHONE, CallPermissions.CAMERA)

        assertTrue(CallPermissions.videoCaptureAllowed(isVideo = true, grants = both))
        assertFalse(CallPermissions.videoCaptureAllowed(isVideo = false, grants = both))
    }
}
