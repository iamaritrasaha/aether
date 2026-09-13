package com.foresightlabs.aether.calls.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.media.VideoDescription
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device VIDEO native smoke test -- everything a video call's camera path
 * does before and around the network, against real hardware, with no
 * signalling, no network and no second participant:
 *
 * 1. `NTgCalls.getMediaDevices()` enumerates real camera devices whose
 *    metadata is the JSON contract `{"id": <enumerator name>, "is_front":
 *    <bool>}` (decompiled from the shipped AAR's JavaVideoCapturerModule).
 * 2. [CameraDeviceSelection] resolves a front AND a back camera from the real
 *    enumeration (skipped only on a device genuinely missing a facing).
 * 3. A real native P2P session accepts CAPTURE with mic + the selected
 *    camera's VideoDescription, and PLAYBACK with speaker + the
 *    MediaSource.EXTERNAL video slot that wires remote-frame delivery.
 * 4. The session reports `videoStopped=false` (a camera writer now exists)
 *    and tears down cleanly.
 *
 * This is deliberately NOT an interoperability test: no call is placed, no
 * frame is recorded or stored, and nothing leaves the device. Opening the
 * camera happens natively only once a connection exists, so this suite never
 * even activates the camera LED.
 */
@RunWith(AndroidJUnit4::class)
class VideoMediaNativeSmokeTest {

    private val fakeCallId = 990_002L
    private val dummyKey = ByteArray(32) { it.toByte() }

    @Before
    fun assertPermissionsGranted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mic = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val cam = context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        org.junit.Assume.assumeTrue(
            "RECORD_AUDIO+CAMERA not granted; run `adb shell pm grant ${context.packageName} " +
                "android.permission.RECORD_AUDIO` and `... android.permission.CAMERA`",
            mic && cam
        )
    }

    private fun engine(): NativeTelegramCallMediaEngine {
        val engine = NativeTelegramCallMediaEngine()
        engine.setContext(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
        return engine
    }

    @Test
    fun cameraDevicesEnumerateWithFacingMetadata() {
        engine()
        val devices = NTgCalls.getMediaDevices()
        assertNotNull(devices)
        org.junit.Assume.assumeTrue(
            "device has no enumerable camera; video smoke test not applicable",
            devices!!.camera.isNotEmpty()
        )
        for (camera in devices.camera) {
            val json = JSONObject(camera.metadata)
            assertTrue("camera metadata must carry an id", json.has("id"))
            assertTrue("camera metadata must carry is_front", json.has("is_front"))
        }
    }

    @Test
    fun frontAndBackCamerasResolveFromRealEnumeration() {
        engine()
        val devices = NTgCalls.getMediaDevices()
        val parsed = devices!!.camera.map { CameraDeviceSelection.parse(it) }

        val hasFrontMetadata = parsed.any { it.isFront == true }
        val hasBackMetadata = parsed.any { it.isFront == false }
        if (hasFrontMetadata && hasBackMetadata) {
            val front = CameraDeviceSelection.select(parsed, front = true)
            val back = CameraDeviceSelection.select(parsed, front = false)
            assertTrue(
                "front selection must return a device whose metadata claims front",
                front != null && JSONObject(front!!.metadata).optBoolean("is_front")
            )
            assertTrue(
                "back selection must return a device whose metadata claims back",
                back != null && !JSONObject(back!!.metadata).optBoolean("is_front")
            )
        } else {
            org.junit.Assume.assumeTrue("device reports a single facing; cross-facing check skipped", false)
        }
    }

    @Test
    fun nativeSessionAcceptsVideoCaptureAndPlaybackSourcesAndTearsDownCleanly() {
        engine()
        val devices = NTgCalls.getMediaDevices()
        assertNotNull(devices)
        val mic = devices!!.microphone.first()
        val speaker = devices.speaker.first()

        val selected = CameraDeviceSelection.select(devices.camera.map { CameraDeviceSelection.parse(it) }, front = true)
        org.junit.Assume.assumeTrue("no front camera resolvable; video config test skipped", selected != null)

        val ntg = NTgCalls()
        try {
            ntg.createP2pCall(fakeCallId)
            ntg.skipExchange(fakeCallId, dummyKey, true)

            // CAPTURE: mic + the selected camera -- the exact configuration a
            // video call builds after the camera-mapping fix.
            ntg.setStreamSources(
                fakeCallId,
                StreamMode.CAPTURE,
                MediaDescription(
                    AudioDescription(MediaSource.DEVICE, 48000, 1, mic.metadata, false),
                    null,
                    VideoDescription(MediaSource.DEVICE, 1280, 720, 30, selected!!.metadata, false),
                    null
                )
            )
            // PLAYBACK: speaker audio + the EXTERNAL video slot that lets
            // decoded remote frames reach onFrames (see applyPlaybackSources).
            ntg.setStreamSources(
                fakeCallId,
                StreamMode.PLAYBACK,
                MediaDescription(
                    AudioDescription(MediaSource.DEVICE, 48000, 1, speaker.metadata, false),
                    null,
                    VideoDescription(MediaSource.EXTERNAL, 0, 0, 0, "", false),
                    null
                )
            )

            val state = ntg.getState(fakeCallId)
            assertNotNull(state)
            assertFalse(
                "a session with a camera writer must not report videoStopped",
                state.videoStopped
            )
        } finally {
            ntg.stop(fakeCallId)
        }

        assertFalse(
            "session must be gone after stop()",
            ntg.calls().containsKey(fakeCallId)
        )
    }
}
