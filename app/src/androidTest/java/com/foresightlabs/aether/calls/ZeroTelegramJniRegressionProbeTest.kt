package com.foresightlabs.aether.calls

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.calls.media.NativeTelegramCallMediaEngine
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Aether in-app JNI smoke test.
 *
 * Verifies that the native library (libntgcalls.so) loaded via NTgCalls
 * properly retains WebRTC's jni_zero exported entry points (such as
 * `Java_J_N_MM6G5xGU` for SoftwareVideoEncoderFactory) inside Aether's
 * runtime process.
 *
 * Note: Historical zero-Telegram isolated A/B evidence (stock UnsatisfiedLinkError
 * vs. fixed success) was executed via standalone off-repo probe modules
 * (`com.probe.stock` and `com.probe.fixed`). This in-app smoke test runs within
 * the Aether test process to confirm that symbols remain loadable in Aether.
 */
@RunWith(AndroidJUnit4::class)
class ZeroTelegramJniRegressionProbeTest {

    @Test
    fun verifyNtgCallsNativeLibraryLoadsAndPings() {
        val ntgCallsClass = Class.forName("io.github.pytgcalls.NTgCalls")
        val pingMethod = ntgCallsClass.getMethod("ping")
        pingMethod.invoke(null)

        val protocol = NativeTelegramCallMediaEngine.supportedProtocol()
        assertNotNull("Protocol must be non-null when native library is loaded", protocol)
        assertTrue("Library versions must not be empty", protocol!!.libraryVersions.isNotEmpty())
    }

    @Test
    fun verifyWebRtcJniZeroSymbolsRetainedWithoutUnsatisfiedLinkError() {
        // Ensure native library is loaded
        val ntgCallsClass = Class.forName("io.github.pytgcalls.NTgCalls")
        val pingMethod = ntgCallsClass.getMethod("ping")
        pingMethod.invoke(null)

        // In broken stock rc02, instantiating SoftwareVideoEncoderFactory()
        // immediately failed with:
        // java.lang.UnsatisfiedLinkError: No implementation found for long J.N.MM6G5xGU()
        //
        // In the fixed build, Java_J_N_MM6G5xGU is retained in libntgcalls.so.
        val factoryClass = Class.forName("org.webrtc.SoftwareVideoEncoderFactory")
        val constructor = factoryClass.getDeclaredConstructor()
        val factoryInstance = constructor.newInstance()
        assertNotNull("SoftwareVideoEncoderFactory must be instantiated successfully", factoryInstance)

        val getSupportedCodecsMethod = factoryClass.getMethod("getSupportedCodecs")
        val codecs = getSupportedCodecsMethod.invoke(factoryInstance) as? Array<*>
        assertNotNull("Supported codecs list must not be null", codecs)
        assertTrue("Supported codecs must contain at least one codec (e.g. VP8)", codecs!!.isNotEmpty())
    }
}
