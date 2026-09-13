package com.foresightlabs.aether.calls.media

import io.github.pytgcalls.media.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Camera selection from ntgcalls' own camera metadata.
 *
 * The metadata shapes below are exactly what the pinned engine's
 * `JavaVideoCapturerModule.getDevices()` produces (verified by decompiling
 * the shipped AAR): a JSON object `{"id": <enumerator device name>,
 * "is_front": <bool>}`, where `id` is a camera2 ID on the Camera2Enumerator
 * path and a Camera1Enumerator display name otherwise.
 */
class CameraDeviceSelectionTest {

    private fun info(name: String, id: String, isFront: Boolean) =
        DeviceInfo(name, """{"id":"$id","is_front":$isFront}""")

    @Test
    fun parsesCamera2EnumeratorShape() {
        val parsed = CameraDeviceSelection.parse(info("0", "0", isFront = true))
        assertEquals("0", parsed.enumeratorId)
        assertEquals(true, parsed.isFront)
    }

    @Test
    fun parsesCamera1EnumeratorDisplayName() {
        val name = "Camera 1, Facing back, Orientation 90"
        val parsed = CameraDeviceSelection.parse(info(name, name, isFront = false))
        assertEquals(name, parsed.enumeratorId)
        assertEquals(false, parsed.isFront)
    }

    @Test
    fun malformedMetadataYieldsUnknownFacingButKeepsName() {
        val parsed = CameraDeviceSelection.parse(DeviceInfo("0", "not json at all"))
        assertEquals("0", parsed.enumeratorId)
        assertNull(parsed.isFront)
    }

    @Test
    fun metadataWithoutFacingYieldsUnknownFacing() {
        val parsed = CameraDeviceSelection.parse(DeviceInfo("1", """{"id":"1"}"""))
        assertEquals("1", parsed.enumeratorId)
        assertNull(parsed.isFront)
    }

    @Test
    fun blankIdFallsBackToInfoName() {
        val parsed = CameraDeviceSelection.parse(DeviceInfo("2", """{"is_front":true}"""))
        assertEquals("2", parsed.enumeratorId)
        assertEquals(true, parsed.isFront)
    }

    @Test
    fun selectsFrontAmongMixedCameras() {
        val cameras = listOf(
            info("0", "0", isFront = false),
            info("1", "1", isFront = true)
        ).map(CameraDeviceSelection::parse)
        assertEquals("1", CameraDeviceSelection.select(cameras, front = true)?.name)
        assertEquals("0", CameraDeviceSelection.select(cameras, front = false)?.name)
    }

    @Test
    fun selectionIsDeterministicByIdWhenMultipleMatch() {
        val cameras = listOf(
            info("3", "3", isFront = true),
            info("1", "1", isFront = true),
            info("2", "2", isFront = true)
        ).map(CameraDeviceSelection::parse)
        assertEquals("1", CameraDeviceSelection.select(cameras, front = true)?.name)
    }

    @Test
    fun unknownFacingUsesCameraManagerResolverAsFallback() {
        val cameras = listOf(
            CameraDeviceSelection.parse(DeviceInfo("0", """{"id":"0"}""")),
            CameraDeviceSelection.parse(DeviceInfo("1", """{"id":"1"}"""))
        )
        // The camera2 path: metadata carries no facing claim, Android does.
        val facingOf: (String) -> Boolean? = { id -> when (id) {
            "0" -> false
            "1" -> true
            else -> null
        } }
        assertEquals("1", CameraDeviceSelection.select(cameras, front = true, facingOf = facingOf)?.name)
        assertEquals("0", CameraDeviceSelection.select(cameras, front = false, facingOf = facingOf)?.name)
    }

    @Test
    fun metadataClaimWinsOverDisagreeingResolver() {
        val cameras = listOf(
            CameraDeviceSelection.parse(info("0", "0", isFront = true))
        )
        // A CameraManager that disagrees: metadata is what ntgcalls' capturer
        // acts on, so it wins -- and the front request still selects it.
        val facingOf: (String) -> Boolean? = { false }
        assertEquals("0", CameraDeviceSelection.select(cameras, front = true, facingOf = facingOf)?.name)
    }

    @Test
    fun unknownFacingEverywhereSelectsNothing() {
        val cameras = listOf(
            CameraDeviceSelection.parse(DeviceInfo("0", """{"id":"0"}"""))
        )
        assertNull(CameraDeviceSelection.select(cameras, front = true, facingOf = { null }))
    }

    @Test
    fun emptyCameraListSelectsNothing() {
        assertNull(CameraDeviceSelection.select(emptyList(), front = true))
    }
}
