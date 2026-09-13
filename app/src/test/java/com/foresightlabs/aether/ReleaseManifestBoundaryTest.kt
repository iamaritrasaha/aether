package com.foresightlabs.aether

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Play release's manifest boundary for the (unshipped) calling feature.
 *
 * Calling code stays compiled into internal builds, and its libraries (LiveKit,
 * audioswitch) merge call-only components and permissions into every variant.
 * The release source set strips them; these tests pin each strip so a later
 * manifest edit or dependency bump cannot silently hand the Play build a
 * foreground-service declaration for a feature it does not ship.
 *
 * Reads the source manifests (unit tests run from the module directory);
 * the merged release manifest itself is verified during release audits.
 */
class ReleaseManifestBoundaryTest {

    private val releaseManifest = File("src/release/AndroidManifest.xml").readText()
    private val mainManifest = File("src/main/AndroidManifest.xml").readText()

    private fun assertRemovedInRelease(name: String) {
        val removal = Regex(
            """android:name="${Regex.escape(name)}"\s+tools:node="remove""""
        )
        assertTrue("release manifest must strip $name", removal.containsMatchIn(releaseManifest))
    }

    @Test
    fun callOnlyForegroundServicesAreStripped() {
        assertRemovedInRelease("com.foresightlabs.aether.data.calls.CallService")
        assertRemovedInRelease("io.livekit.android.room.track.screencapture.ScreenCaptureService")
    }

    @Test
    fun callOnlyPermissionsAreStripped() {
        assertRemovedInRelease("android.permission.FOREGROUND_SERVICE")
        assertRemovedInRelease("android.permission.FOREGROUND_SERVICE_MICROPHONE")
        assertRemovedInRelease("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
        assertRemovedInRelease("android.permission.MODIFY_AUDIO_SETTINGS")
        assertRemovedInRelease("android.permission.BLUETOOTH")
    }

    @Test
    fun messagingMediaPermissionsAreKept() {
        // Voice notes, camera capture and video messages need these whether
        // or not calling ships; stripping them would break shipping features.
        for (name in listOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA")) {
            assertTrue("main manifest must declare $name", mainManifest.contains("\"$name\""))
            assertFalse(
                "release must not strip $name",
                Regex("""android:name="${Regex.escape(name)}"\s+tools:node="remove"""").containsMatchIn(releaseManifest)
            )
        }
    }

    @Test
    fun cleartextTrafficIsNeverAllowedOutsideDebug() {
        // The LAN development call service is plain HTTP; only the debug
        // source set may allow it.
        assertFalse(mainManifest.contains("usesCleartextTraffic=\"true\""))
        assertFalse(releaseManifest.contains("usesCleartextTraffic=\"true\""))
    }

    @Test
    fun theReleaseNeverShipsTheLanCallServiceUrl() {
        val buildScript = File("build.gradle.kts").readText()
        val releaseBlock = buildScript.substringAfter("buildTypes {").substringAfter("release {")
        assertTrue(
            "release must override AETHER_CALL_SERVICE_URL away from the LAN default",
            releaseBlock.contains("buildConfigField(\"String\", \"AETHER_CALL_SERVICE_URL\"")
        )
    }
}
