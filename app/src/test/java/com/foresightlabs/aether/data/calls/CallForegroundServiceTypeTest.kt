package com.foresightlabs.aether.data.calls

import android.app.Application
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.calls.CallPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The crash this guards against was real and reproducible on device: a call
 * connected, the ongoing-call service started with the microphone foreground
 * service type, and Android killed the process because `RECORD_AUDIO` had never
 * been granted --
 *
 *     SecurityException: Starting FGS with type microphone ... requires
 *     permissions: ... [android.permission.RECORD_AUDIO]
 *     at CallService.onStartCommand
 *
 * The service type is therefore derived from the grants that actually exist,
 * never from what the call wishes it had.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class CallForegroundServiceTypeTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    private fun grant(vararg permissions: String) {
        shadowOf(application).grantPermissions(*permissions)
    }

    private fun deny(vararg permissions: String) {
        shadowOf(application).denyPermissions(*permissions)
    }

    @Test
    fun withNoMicrophoneGrantNoServiceTypeIsClaimedAtAll() {
        deny(CallPermissions.MICROPHONE, CallPermissions.CAMERA)

        assertEquals(0, CallService.grantedServiceType(application, isVideo = false))
        assertEquals(0, CallService.grantedServiceType(application, isVideo = true))
    }

    @Test
    fun aVoiceCallWithMicrophoneClaimsOnlyMicrophone() {
        grant(CallPermissions.MICROPHONE)
        deny(CallPermissions.CAMERA)

        val type = CallService.grantedServiceType(application, isVideo = false)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, type)
    }

    /**
     * A voice call must never claim the camera type -- it has no reason to hold
     * `CAMERA`, and claiming it would be the same fatal mismatch in a different
     * costume.
     */
    @Test
    fun aVoiceCallNeverClaimsTheCameraTypeEvenWhenCameraIsGranted() {
        grant(CallPermissions.MICROPHONE, CallPermissions.CAMERA)

        val type = CallService.grantedServiceType(application, isVideo = false)
        assertEquals(0, type and ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
    }

    @Test
    fun aVideoCallWithBothGrantsClaimsBoth() {
        grant(CallPermissions.MICROPHONE, CallPermissions.CAMERA)

        val type = CallService.grantedServiceType(application, isVideo = true)
        assertTrue(type and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0)
        assertTrue(type and ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA != 0)
    }

    /**
     * A video call whose camera was refused degrades to an audio call rather
     * than being refused -- and must not claim a camera type it cannot back up.
     */
    @Test
    fun aVideoCallWithoutCameraDegradesToAMicrophoneOnlyService() {
        grant(CallPermissions.MICROPHONE)
        deny(CallPermissions.CAMERA)

        val type = CallService.grantedServiceType(application, isVideo = true)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, type)
    }

    /**
     * `startForegroundService` promises a `startForeground` call within seconds
     * and crashes the process if none arrives. When nothing can legally be
     * promoted, the promise is never made in the first place.
     */
    @Test
    fun noServiceIsEvenStartedWhenNothingCouldBePromoted() {
        deny(CallPermissions.MICROPHONE, CallPermissions.CAMERA)

        CallService.startService(application, "Contact", isConnected = true, isVideo = false)

        assertEquals(null, shadowOf(application).nextStartedService)
    }

    @Test
    fun theServiceIsStartedOnceTheMicrophoneGrantExists() {
        grant(CallPermissions.MICROPHONE)

        CallService.startService(application, "Contact", isConnected = true, isVideo = false)

        val started = shadowOf(application).nextStartedService
        assertEquals(CallService::class.java.name, started?.component?.className)
    }
}
