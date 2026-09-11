package com.foresightlabs.aether.domain.calls

import android.Manifest

/**
 * What a call genuinely needs from the OS, in one place.
 *
 * This exists because the answer is needed by three different layers that must
 * never disagree: the UI that asks the user, the foreground service that
 * declares a service type to the system, and the media engine that opens the
 * devices. They previously disagreed -- the UI asked for nothing when *placing*
 * a call, while the service still declared the microphone service type, and
 * Android killed the process for it:
 *
 *     SecurityException: Starting FGS with type microphone ... requires
 *     permissions: ... [android.permission.RECORD_AUDIO]
 *
 * A call's requirements are a property of the call, not of the screen that
 * happens to start it.
 */
object CallPermissions {

    /** Microphone: every call carries audio, video calls included. */
    const val MICROPHONE: String = Manifest.permission.RECORD_AUDIO

    /** Camera: video calls only. A voice call must never ask for it. */
    const val CAMERA: String = Manifest.permission.CAMERA

    /**
     * Everything a call of this kind must hold before it may be placed or
     * accepted. Ordered so the microphone is always requested first: it is the
     * one a call cannot proceed without.
     */
    fun required(isVideo: Boolean): List<String> =
        if (isVideo) listOf(MICROPHONE, CAMERA) else listOf(MICROPHONE)

    /**
     * Whether a call of this kind may proceed at all, given what is granted.
     *
     * A video call whose camera was refused is still a *call* -- it degrades to
     * audio rather than being refused outright, which is what
     * [videoCaptureAllowed] separately decides. Only a missing microphone makes
     * the call itself impossible.
     */
    fun callAllowed(grants: Set<String>): Boolean = MICROPHONE in grants

    /** Whether the camera may actually be opened for this call. */
    fun videoCaptureAllowed(isVideo: Boolean, grants: Set<String>): Boolean =
        isVideo && CAMERA in grants
}
