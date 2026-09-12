package com.foresightlabs.aether.calls.media

import io.github.pytgcalls.media.DeviceInfo
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaDevices
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallMediaModuleTest {

    @Test
    fun callMediaConfigSecretWipeClearsEncryptionKey() {
        val key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val config = CallMediaConfig(
            callId = 12345L,
            isOutgoing = true,
            videoCaptureEnabled = false,
            encryptionKey = key,
            allowP2p = false,
            servers = emptyList(),
            configJson = "{}",
            customParameters = "{}",
            protocol = CallProtocolInfo()
        )

        assertEquals(12345L, config.callId)
        assertTrue(config.isOutgoing)
        assertFalse(config.allowP2p)

        config.wipeSecrets()
        val expectedZeros = ByteArray(8)
        assertArrayEquals(expectedZeros, config.encryptionKey)
    }

    @Test
    fun callServerEndpointEqualityAndHashcode() {
        val server1 = CallServerEndpoint(
            id = 101L,
            ipAddress = "149.154.167.50",
            ipv6Address = "",
            port = 443,
            peerTag = byteArrayOf(10, 20),
            isTcp = true,
            username = "user",
            password = "pwd",
            supportsTurn = true,
            supportsStun = true
        )
        val server2 = CallServerEndpoint(
            id = 101L,
            ipAddress = "149.154.167.50",
            ipv6Address = "",
            port = 443,
            peerTag = byteArrayOf(10, 20),
            isTcp = true,
            username = "user",
            password = "pwd",
            supportsTurn = true,
            supportsStun = true
        )
        assertEquals(server1, server2)
        assertEquals(server1.hashCode(), server2.hashCode())
    }

    @Test
    fun mediaConnectionStateTransitions() {
        val states = MediaConnectionState.entries
        assertTrue(states.contains(MediaConnectionState.IDLE))
        assertTrue(states.contains(MediaConnectionState.INITIALIZING))
        assertTrue(states.contains(MediaConnectionState.CONNECTING))
        assertTrue(states.contains(MediaConnectionState.CONNECTED))
        assertTrue(states.contains(MediaConnectionState.RECONNECTING))
        assertTrue(states.contains(MediaConnectionState.FAILED))
        assertTrue(states.contains(MediaConnectionState.UNAVAILABLE))
        assertTrue(states.contains(MediaConnectionState.STOPPED))
    }

    /**
     * `io.github.pytgcalls:ntgcalls` ships a real `arm64-v8a` native library
     * (see docs/architecture/calling-native-stack.md) -- but that ELF binary
     * cannot load on the host JVM this test runs on, so `System.loadLibrary`
     * fails here exactly as it would on an unsupported device. That is the
     * behaviour under test: a call must report UNAVAILABLE, never a fabricated
     * CONNECTED, whenever the real transport cannot actually load.
     */
    @Test
    fun nativeEngineReportsUnavailableWhenTheRealTransportCannotLoad() {
        val engine = NativeTelegramCallMediaEngine()
        assertFalse(engine.isMediaTransportAvailable)

        var emittedState: MediaConnectionState? = null
        var emittedError: String? = null

        engine.init(object : NativeCallEngineCallback {
            override fun onConnectionStateChanged(stateOrdinal: Int) {
                emittedState = MediaConnectionState.entries.getOrNull(stateOrdinal)
            }

            override fun onSignalBarsChanged(bars: Int) {}

            override fun onAudioLevelsChanged(localLevel: Float, remoteLevel: Float) {}

            override fun onError(error: String) {
                emittedError = error
            }

            override fun onOutgoingSignalingData(data: ByteArray) {}

            override fun onVideoFrame(frame: DecodedVideoFrame) {}
        })

        val config = CallMediaConfig(
            callId = 1L,
            isOutgoing = true,
            videoCaptureEnabled = false,
            encryptionKey = ByteArray(256),
            allowP2p = false,
            servers = emptyList(),
            configJson = "{}",
            customParameters = "{}",
            protocol = CallProtocolInfo()
        )

        engine.startCall(config)

        assertEquals(MediaConnectionState.UNAVAILABLE, emittedState)
        assertTrue(emittedError?.isNotBlank() == true)
    }

    /**
     * On the host JVM the real `arm64-v8a` native library cannot load (see
     * [nativeEngineReportsUnavailableWhenTheRealTransportCannotLoad]), so
     * querying what protocol it supports must fail closed to null rather than
     * fabricating a capability the engine never actually reported.
     */
    @Test
    fun supportedProtocolIsNullWhenTheRealTransportCannotLoad() {
        assertEquals(null, NativeTelegramCallMediaEngine.supportedProtocol())
    }

    @Test
    fun i420ConverterRejectsATruncatedBuffer() {
        val tooShort = ByteArray(4)
        assertEquals(null, I420Converter.convert(tooShort, 4, 4))
    }

    @Test
    fun i420ConverterProducesOnePixelPerInputPixel() {
        val width = 2
        val height = 2
        val y = ByteArray(width * height) { 235.toByte() }
        val chroma = ByteArray(1) { 128.toByte() } // 1x1 chroma plane for a 2x2 frame
        val image = I420Converter.convert(y + chroma + chroma, width, height)

        assertEquals(width, image?.width)
        assertEquals(height, image?.height)
        assertEquals(width * height, image?.pixels?.size)
        // Neutral chroma at max luma lands very close to opaque white.
        val argb = image!!.pixels[0]
        assertEquals(0xFF, (argb shr 24) and 0xFF)
        assertTrue((argb and 0xFF) > 240)
    }

    // =========================================================================
    // applyStreamSources Truthfulness Tests (rc02 API contract)
    // =========================================================================

    private val sampleDevices = MediaDevices(
        listOf(DeviceInfo("Built-in Mic", "mic_0")),
        listOf(DeviceInfo("Speaker", "spk_0")),
        listOf(DeviceInfo("Front Camera", "cam_front")),
        emptyList()
    )

    /** Records setter invocations from [NativeTelegramCallMediaEngine.applyStreamSourcesForTesting] split by [StreamMode], since a single call now configures both. */
    private class RecordedSources {
        val capture = mutableListOf<MediaDescription>()
        val playback = mutableListOf<MediaDescription>()

        fun record(mode: StreamMode, desc: MediaDescription) {
            when (mode) {
                StreamMode.CAPTURE -> capture.add(desc)
                StreamMode.PLAYBACK -> playback.add(desc)
            }
        }
    }

    @Test
    fun applyStreamSources_voiceCall_succeedsWhenNativeCallSucceeds() {
        val recorded = RecordedSources()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { callId, mode, desc ->
                assertEquals(42L, callId)
                recorded.record(mode, desc)
            }
        )

        assertTrue("Voice call must succeed when setStreamSources succeeds", result)
        assertEquals(1, recorded.capture.size)
        assertEquals("mic_0", recorded.capture[0].microphone?.input)
        assertEquals(null, recorded.capture[0].speaker)
        assertEquals(null, recorded.capture[0].camera)
        assertEquals(null, recorded.capture[0].screen)
    }

    @Test
    fun applyStreamSources_voiceCall_returnsFalseWhenCaptureSetupThrows() {
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, mode, _ ->
                // CAPTURE failing must fail the whole call -- it is the
                // outgoing audio the call itself depends on. PLAYBACK is
                // never reached because applyCaptureSources already
                // returned false.
                if (mode == StreamMode.CAPTURE) {
                    throw RuntimeException("Simulated native setStreamSources failure")
                }
            }
        )

        assertFalse("Voice call must return false when CAPTURE setStreamSources throws", result)
    }

    @Test
    fun applyStreamSources_voiceCall_configuresPlaybackAudioUsingRealSpeakerMetadata() {
        val recorded = RecordedSources()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { callId, mode, desc ->
                assertEquals(42L, callId)
                recorded.record(mode, desc)
            }
        )

        assertTrue(result)
        assertEquals(1, recorded.playback.size)
        val playback = recorded.playback[0]
        // Per pinned rc02 StreamManager::optimize_sources, the real output
        // device goes in the .microphone slot for a PLAYBACK call -- see
        // applyPlaybackSources's doc for the exact native proof. It must be
        // the genuine speaker device's own metadata, never microphone
        // metadata and never synthesized.
        assertEquals("spk_0", playback.microphone?.input)
        assertNotEquals("Playback audio must never reuse the capture microphone's device metadata", "mic_0", playback.microphone?.input)
        assertEquals(null, playback.speaker)
        // Voice call: no playback video slot.
        assertEquals(null, playback.camera)
        assertEquals(null, playback.screen)
    }

    @Test
    fun applyStreamSources_playbackAudioFailsCleanlyWhenNoOutputDeviceExists() {
        val noSpeakerDevices = MediaDevices(
            listOf(DeviceInfo("Built-in Mic", "mic_0")),
            emptyList(),
            emptyList(),
            emptyList()
        )
        val recorded = RecordedSources()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { noSpeakerDevices },
            streamSourceSetter = { _, mode, desc -> recorded.record(mode, desc) }
        )

        // No speaker/output device is never fatal to the call: CAPTURE (this
        // device's own outgoing audio) still succeeded.
        assertTrue("A call must still succeed with no output device available for playback", result)
        assertEquals(1, recorded.capture.size)
        assertEquals(1, recorded.playback.size)
        assertEquals("A playback call with no output device must carry a null audio slot, never synthesized metadata", null, recorded.playback[0].microphone)
    }

    @Test
    fun applyStreamSources_voiceCall_failsCleanlyWhenDeviceProviderReturnsNull() {
        // Physically reproduced on hardware: ntgcalls' native
        // BaseDeviceModule constructor requires AudioDescription.input to be
        // a JSON object with an `is_microphone` key
        // (ntgcalls/src/media/devices/base_device_module.cpp does
        // `json::parse(desc->input)`); an empty string is not valid JSON and
        // setStreamSources throws MediaDeviceError("Invalid device
        // metadata"). Synthesizing a fake microphone with empty metadata
        // when the device provider fails is therefore never safe -- this
        // must fail cleanly instead, exactly like no microphone being
        // enumerated at all.
        var providerCalled = false
        var setterCalled = false
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = {
                providerCalled = true
                null
            },
            streamSourceSetter = { _, _, _ -> setterCalled = true }
        )

        assertFalse("Voice call must fail, not synthesize invalid microphone metadata, when the device provider returns null", result)
        assertTrue("Device provider lambda should be executed", providerCalled)
        assertFalse("Native stream sources must never be called without a real microphone", setterCalled)
    }

    @Test
    fun applyStreamSources_failsWhenNoMicrophoneDeviceAvailable() {
        var setterCalled = false
        val noMicDevices = MediaDevices(emptyList(), emptyList(), emptyList(), emptyList())
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { noMicDevices },
            streamSourceSetter = { _, _, _ -> setterCalled = true }
        )

        assertFalse("Must fail when no microphone is enumerated", result)
        assertFalse("Native stream sources must never be called without a microphone", setterCalled)
    }

    @Test
    fun applyStreamSources_videoCall_succeedsWithBothWhenNativeCallSucceeds() {
        val recorded = RecordedSources()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = true,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, mode, desc -> recorded.record(mode, desc) },
            cameraSelector = { devices, _ -> devices.camera.firstOrNull() }
        )

        assertTrue("Video call must succeed when dual-stream setup succeeds", result)
        assertEquals(1, recorded.capture.size)
        assertEquals("mic_0", recorded.capture[0].microphone?.input)
        assertEquals(null, recorded.capture[0].speaker)
        assertEquals("cam_front", recorded.capture[0].camera?.input)
        assertEquals(null, recorded.capture[0].screen)

        // A video call must ALSO configure EXTERNAL PLAYBACK camera --
        // MediaSource.EXTERNAL in the playback .camera slot, per
        // handle_playback_config/setup_video_playback_callbacks (pinned
        // rc02) -- so decoded remote frames can reach NTgCalls.onFrames.
        // Never MediaSource.DEVICE: that is only valid for CAPTURE.
        assertEquals(1, recorded.playback.size)
        assertEquals(MediaSource.EXTERNAL, recorded.playback[0].camera?.media_source)
    }

    @Test
    fun applyStreamSources_voiceCall_neverEnablesPlaybackVideo() {
        val recorded = RecordedSources()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = false,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, mode, desc -> recorded.record(mode, desc) }
        )

        assertTrue(result)
        assertEquals(1, recorded.playback.size)
        assertEquals("A voice call must never configure a PLAYBACK video slot", null, recorded.playback[0].camera)
    }

    @Test
    fun applyStreamSources_videoCall_degradesToAudioWhenCameraAttemptThrowsAndAudioSucceeds() {
        val recorded = RecordedSources()
        var captureAttempts = 0
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = true,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, mode, desc ->
                if (mode == StreamMode.CAPTURE) {
                    captureAttempts++
                    recorded.record(mode, desc)
                    if (captureAttempts == 1) {
                        throw RuntimeException("Simulated camera stream rejection by WebRTC")
                    }
                } else {
                    recorded.record(mode, desc)
                }
            },
            cameraSelector = { devices, _ -> devices.camera.firstOrNull() }
        )

        assertTrue("Video call must succeed by falling back to audio when camera setup fails", result)
        assertEquals(2, recorded.capture.size)
        // First attempt had both mic and camera
        assertEquals("mic_0", recorded.capture[0].microphone?.input)
        assertEquals("cam_front", recorded.capture[0].camera?.input)
        // Second attempt degraded to audio only
        assertEquals("mic_0", recorded.capture[1].microphone?.input)
        assertEquals(null, recorded.capture[1].speaker)
        assertEquals(null, recorded.capture[1].camera)
        assertEquals(null, recorded.capture[1].screen)
    }

    @Test
    fun applyStreamSources_videoCall_returnsFalseWhenBothCameraAndAudioRetryThrow() {
        val recorded = RecordedSources()
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = true,
            deviceProvider = { sampleDevices },
            streamSourceSetter = { _, mode, desc ->
                recorded.record(mode, desc)
                throw RuntimeException("Both attempts fail")
            },
            cameraSelector = { devices, _ -> devices.camera.firstOrNull() }
        )

        assertFalse("Video call must return false when both dual and fallback attempts throw", result)
        assertEquals(2, recorded.capture.size)
        // CAPTURE failing outright means PLAYBACK must never even be attempted.
        assertEquals(0, recorded.playback.size)
    }

    @Test
    fun applyStreamSources_videoCall_fallsBackToAudioOnlyWhenNoCameraHardwareAvailable() {
        val recorded = RecordedSources()
        val devicesWithoutCamera = MediaDevices(
            listOf(DeviceInfo("Built-in Mic", "mic_0")),
            listOf(DeviceInfo("Speaker", "spk_0")),
            emptyList(),
            emptyList()
        )
        val result = NativeTelegramCallMediaEngine.applyStreamSourcesForTesting(
            callId = 42L,
            cameraEnabled = true,
            deviceProvider = { devicesWithoutCamera },
            streamSourceSetter = { _, mode, desc -> recorded.record(mode, desc) },
            cameraSelector = { devices, _ -> devices.camera.firstOrNull() }
        )

        assertTrue("Video call without camera device must fall back to audio and succeed", result)
        assertEquals(1, recorded.capture.size)
        assertEquals("mic_0", recorded.capture[0].microphone?.input)
        assertEquals(null, recorded.capture[0].speaker)
        assertEquals(null, recorded.capture[0].camera)
        assertEquals(null, recorded.capture[0].screen)
    }

    // =========================================================================
    // startCall Lifecycle Cleanup on Startup Failure Tests
    // =========================================================================

    private fun createSampleConfig(callId: Long = 1001L): CallMediaConfig = CallMediaConfig(
        callId = callId,
        isOutgoing = true,
        videoCaptureEnabled = false,
        encryptionKey = ByteArray(32),
        allowP2p = false,
        servers = emptyList(),
        configJson = "{}",
        customParameters = "{}",
        protocol = CallProtocolInfo()
    )

    private class TestCallback : NativeCallEngineCallback {
        var lastState: Int? = null
        var lastError: String? = null
        override fun onConnectionStateChanged(stateOrdinal: Int) { lastState = stateOrdinal }
        override fun onSignalBarsChanged(bars: Int) {}
        override fun onAudioLevelsChanged(localLevel: Float, remoteLevel: Float) {}
        override fun onError(error: String) { lastError = error }
        override fun onOutgoingSignalingData(data: ByteArray) {}
        override fun onVideoFrame(frame: DecodedVideoFrame) {}
    }

    @Test
    fun startCall_whenCreateP2pCallFails_doesNotInvokeNativeStop() {
        val engine = NativeTelegramCallMediaEngine()
        val callback = TestCallback()
        engine.init(callback)
        NativeTelegramCallMediaEngine.resetStateForTesting()

        var stopInvokedCount = 0
        val config = createSampleConfig(callId = 1001L)

        NativeTelegramCallMediaEngine.startCallForTesting(
            config = config,
            createSession = { throw RuntimeException("createP2pCall simulated failure") },
            stopSession = { stopInvokedCount++ }
        )

        assertEquals("Native stop must NOT be called when session creation itself failed", 0, stopInvokedCount)
        assertEquals("activeCallId must be cleared", null, NativeTelegramCallMediaEngine.getActiveCallIdForTesting())
        assertEquals(MediaConnectionState.FAILED.ordinal, callback.lastState)
        assertEquals("createP2pCall simulated failure", callback.lastError)
    }

    @Test
    fun startCall_whenSkipExchangeThrows_invokesNativeStopExactlyOnce() {
        val engine = NativeTelegramCallMediaEngine()
        val callback = TestCallback()
        engine.init(callback)
        NativeTelegramCallMediaEngine.resetStateForTesting()

        val stoppedCallIds = mutableListOf<Long>()
        val config = createSampleConfig(callId = 2002L)

        NativeTelegramCallMediaEngine.startCallForTesting(
            config = config,
            createSession = { /* success */ },
            skipExchange = { _, _, _ -> throw RuntimeException("skipExchange simulated failure") },
            stopSession = { stoppedCallIds.add(it) }
        )

        assertEquals("Native stop must be invoked exactly once on startup failure after session created", 1, stoppedCallIds.size)
        assertEquals(2002L, stoppedCallIds[0])
        assertEquals("activeCallId must be cleared", null, NativeTelegramCallMediaEngine.getActiveCallIdForTesting())
        assertEquals(MediaConnectionState.FAILED.ordinal, callback.lastState)
        assertEquals("skipExchange simulated failure", callback.lastError)
    }

    @Test
    fun startCall_whenApplyStreamSourcesReturnsFalse_invokesNativeStopExactlyOnce() {
        val engine = NativeTelegramCallMediaEngine()
        val callback = TestCallback()
        engine.init(callback)
        NativeTelegramCallMediaEngine.resetStateForTesting()

        val stoppedCallIds = mutableListOf<Long>()
        val config = createSampleConfig(callId = 3003L)

        NativeTelegramCallMediaEngine.startCallForTesting(
            config = config,
            createSession = { /* success */ },
            skipExchange = { _, _, _ -> /* success */ },
            applySources = { _, _ -> false }, // sources cannot be acquired
            stopSession = { stoppedCallIds.add(it) }
        )

        assertEquals("Native stop must be invoked exactly once when stream sources fail", 1, stoppedCallIds.size)
        assertEquals(3003L, stoppedCallIds[0])
        assertEquals("activeCallId must be cleared", null, NativeTelegramCallMediaEngine.getActiveCallIdForTesting())
        assertEquals(MediaConnectionState.FAILED.ordinal, callback.lastState)
    }

    @Test
    fun startCall_whenConnectP2pThrows_invokesNativeStopExactlyOnce() {
        val engine = NativeTelegramCallMediaEngine()
        val callback = TestCallback()
        engine.init(callback)
        NativeTelegramCallMediaEngine.resetStateForTesting()

        val stoppedCallIds = mutableListOf<Long>()
        val config = createSampleConfig(callId = 4004L)

        NativeTelegramCallMediaEngine.startCallForTesting(
            config = config,
            createSession = { /* success */ },
            skipExchange = { _, _, _ -> /* success */ },
            applySources = { _, _ -> true },
            connectP2p = { _, _, _, _, _ -> throw RuntimeException("connectP2p simulated failure") },
            stopSession = { stoppedCallIds.add(it) }
        )

        assertEquals("Native stop must be invoked exactly once when connectP2p fails", 1, stoppedCallIds.size)
        assertEquals(4004L, stoppedCallIds[0])
        assertEquals("activeCallId must be cleared", null, NativeTelegramCallMediaEngine.getActiveCallIdForTesting())
        assertEquals(MediaConnectionState.FAILED.ordinal, callback.lastState)
        assertEquals("connectP2p simulated failure", callback.lastError)
    }

    @Test
    fun startCall_whenStopThrowsDuringCleanup_doesNotCrashAndStillCleansState() {
        val engine = NativeTelegramCallMediaEngine()
        val callback = TestCallback()
        engine.init(callback)
        NativeTelegramCallMediaEngine.resetStateForTesting()

        val config = createSampleConfig(callId = 5005L)

        NativeTelegramCallMediaEngine.startCallForTesting(
            config = config,
            createSession = { /* success */ },
            skipExchange = { _, _, _ -> throw RuntimeException("Trigger failure") },
            stopSession = { throw RuntimeException("Simulated stop failure in native engine") }
        )

        assertEquals("activeCallId must be cleared even if native stop throws", null, NativeTelegramCallMediaEngine.getActiveCallIdForTesting())
        assertEquals(MediaConnectionState.FAILED.ordinal, callback.lastState)
    }

    @Test
    fun startCall_whenStartupSucceeds_doesNotInvokeNativeStop() {
        val engine = NativeTelegramCallMediaEngine()
        val callback = TestCallback()
        engine.init(callback)
        NativeTelegramCallMediaEngine.resetStateForTesting()

        var stopInvokedCount = 0
        val config = createSampleConfig(callId = 6006L)

        NativeTelegramCallMediaEngine.startCallForTesting(
            config = config,
            createSession = { /* success */ },
            skipExchange = { _, _, _ -> /* success */ },
            applySources = { _, _ -> true },
            connectP2p = { _, _, _, _, _ -> /* success */ },
            stopSession = { stopInvokedCount++ }
        )

        assertEquals("Native stop must NOT be called on successful startup", 0, stopInvokedCount)
        assertEquals("activeCallId must remain set to the callId", 6006L, NativeTelegramCallMediaEngine.getActiveCallIdForTesting())
    }

    // =========================================================================
    // WebRTC Android-context initialization: fail-closed, no latching, retryable
    // =========================================================================

    @Test
    fun webRtcContextInit_succeedsOnlyOnceAndDoesNotReinvokeAfterSuccess() {
        NativeTelegramCallMediaEngine.resetStateForTesting()
        var invocationCount = 0

        val first = NativeTelegramCallMediaEngine.ensureWebRtcContextInitializedForTesting(Unit) {
            invocationCount++
            true
        }
        assertTrue("First successful initialization must report ready", first)
        assertEquals(1, invocationCount)

        val second = NativeTelegramCallMediaEngine.ensureWebRtcContextInitializedForTesting(Unit) {
            invocationCount++
            true
        }
        assertTrue("Already-initialized state must keep reporting ready", second)
        assertEquals("A successful initialization must occur only once, never repeated", 1, invocationCount)
    }

    @Test
    fun webRtcContextInit_failureDoesNotLatchSuccessAndAllowsRetry() {
        NativeTelegramCallMediaEngine.resetStateForTesting()

        val first = NativeTelegramCallMediaEngine.ensureWebRtcContextInitializedForTesting(Unit) { false }
        assertFalse("A failed initializer must never be reported as success", first)

        var retried = false
        val second = NativeTelegramCallMediaEngine.ensureWebRtcContextInitializedForTesting(Unit) {
            retried = true
            true
        }
        assertTrue("A later attempt must be allowed to retry after a prior failure", retried)
        assertTrue("A retry that succeeds must report ready", second)
    }

    @Test
    fun webRtcContextInit_throwingInitializerIsTreatedAsFailureAndAllowsRetry() {
        NativeTelegramCallMediaEngine.resetStateForTesting()

        val first = NativeTelegramCallMediaEngine.ensureWebRtcContextInitializedForTesting(Unit) {
            throw IllegalStateException("simulated PeerConnectionFactory.initialize failure")
        }
        assertFalse("An initializer that throws must be treated as failure, not success", first)

        var retried = false
        val second = NativeTelegramCallMediaEngine.ensureWebRtcContextInitializedForTesting(Unit) {
            retried = true
            true
        }
        assertTrue("A thrown exception must not prevent a later retry", retried)
        assertTrue(second)
    }

    // =========================================================================
    // startCall's WebRTC-readiness gate: fails closed, creates no native session
    // =========================================================================

    @Test
    fun startCall_webRtcNotReady_createsNoNativeSessionAndReportsFailed() {
        val callback = TestCallback()
        NativeTelegramCallMediaEngine().init(callback)
        NativeTelegramCallMediaEngine.resetStateForTesting()

        var createSessionCalled = false
        var skipExchangeCalled = false
        var connectP2pCalled = false

        NativeTelegramCallMediaEngine.startCallWithWebRtcGateForTesting(
            config = createSampleConfig(callId = 7007L),
            webRtcReady = false,
            createSession = { createSessionCalled = true },
            skipExchange = { _, _, _ -> skipExchangeCalled = true },
            connectP2p = { _, _, _, _, _ -> connectP2pCalled = true }
        )

        assertFalse("No native call/session may be created when WebRTC context isn't ready", createSessionCalled)
        assertFalse(skipExchangeCalled)
        assertFalse(connectP2pCalled)
        assertEquals(MediaConnectionState.FAILED.ordinal, callback.lastState)
        assertTrue("A useful error must accompany the failure", callback.lastError?.isNotBlank() == true)
        assertEquals("activeCallId must never be set when the gate blocks startup", null, NativeTelegramCallMediaEngine.getActiveCallIdForTesting())
    }

    @Test
    fun startCall_webRtcReady_proceedsToCreateNativeSession() {
        val callback = TestCallback()
        NativeTelegramCallMediaEngine().init(callback)
        NativeTelegramCallMediaEngine.resetStateForTesting()

        var createSessionCalled = false

        NativeTelegramCallMediaEngine.startCallWithWebRtcGateForTesting(
            config = createSampleConfig(callId = 8008L),
            webRtcReady = true,
            createSession = { createSessionCalled = true },
            skipExchange = { _, _, _ -> /* success */ },
            applySources = { _, _ -> true },
            connectP2p = { _, _, _, _, _ -> /* success */ }
        )

        assertTrue("A ready WebRTC context must allow native session creation to proceed", createSessionCalled)
        assertEquals(8008L, NativeTelegramCallMediaEngine.getActiveCallIdForTesting())
    }

    // =========================================================================
    // RTC server peerTag optionality: proves the fix at the actual native
    // boundary object (io.github.pytgcalls.p2p.RTCServer), not just at
    // CallServerEndpoint. See CallServerEndpoint.peerTag's doc for the exact
    // native source (ntgcalls/src/p2p/rtc_server.cpp's to_rtc_servers) this
    // guards against regressing.
    // =========================================================================

    @Test
    fun toRtcServers_webRtcEndpoint_producesNativeServerWithNullPeerTag() {
        val webrtcEndpoint = CallServerEndpoint(
            id = 2L,
            ipAddress = "8.8.8.8",
            ipv6Address = "2001:4860:4860::8888",
            port = 3478,
            peerTag = null,
            isTcp = false,
            username = "stun-user",
            password = "stun-pass",
            supportsTurn = true,
            supportsStun = true
        )

        val rtcServers = NativeTelegramCallMediaEngine.toRtcServersForTesting(listOf(webrtcEndpoint))
        val rtcServer = rtcServers.single()

        assertEquals(
            "A WebRTC/STUN/TURN endpoint must reach the native RTCServer with peer_tag absent, " +
                "or ntgcalls' to_rtc_servers() misclassifies it as a Telegram reflector",
            null,
            rtcServer.peer_tag
        )
    }

    @Test
    fun toRtcServers_reflectorEndpoint_producesNativeServerWithNonNullPeerTag() {
        val peerTagBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val reflectorEndpoint = CallServerEndpoint(
            id = 7L,
            ipAddress = "149.154.167.50",
            ipv6Address = "",
            port = 443,
            peerTag = peerTagBytes,
            isTcp = true
        )

        val rtcServers = NativeTelegramCallMediaEngine.toRtcServersForTesting(listOf(reflectorEndpoint))
        val rtcServer = rtcServers.single()

        assertArrayEquals(
            "A Telegram reflector endpoint must reach the native RTCServer with its peer_tag preserved",
            peerTagBytes,
            rtcServer.peer_tag
        )
    }

    @Test
    fun toRtcServers_preservesIdsAddressesAndPortsForBothServerTypes() {
        val reflector = CallServerEndpoint(
            id = 1L,
            ipAddress = "1.1.1.1",
            ipv6Address = "::1",
            port = 1000,
            peerTag = byteArrayOf(9, 9)
        )
        val webrtc = CallServerEndpoint(
            id = 2L,
            ipAddress = "2.2.2.2",
            ipv6Address = "::2",
            port = 2000,
            peerTag = null,
            username = "u",
            password = "p",
            supportsTurn = true
        )

        val rtcServers = NativeTelegramCallMediaEngine.toRtcServersForTesting(listOf(reflector, webrtc))

        assertEquals(2, rtcServers.size)
        assertEquals(1L, rtcServers[0].id)
        assertEquals("1.1.1.1", rtcServers[0].ipv4)
        assertEquals("::1", rtcServers[0].ipv6)
        assertEquals(1000, rtcServers[0].port)

        assertEquals(2L, rtcServers[1].id)
        assertEquals("2.2.2.2", rtcServers[1].ipv4)
        assertEquals("::2", rtcServers[1].ipv6)
        assertEquals(2000, rtcServers[1].port)
    }
}
