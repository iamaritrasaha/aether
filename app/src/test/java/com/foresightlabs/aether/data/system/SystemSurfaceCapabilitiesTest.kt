package com.foresightlabs.aether.data.system

import com.foresightlabs.aether.data.system.SystemSurfaceCapabilities.OngoingSurfaceCapability
import com.foresightlabs.aether.data.system.SystemSurfaceCapabilities.mapOngoingSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vendor ongoing-surface detection.
 *
 * The whole point of these is that detection fails safe: every uncertain input has
 * to produce NONE, because the alternative is Aether believing it may publish to a
 * surface it has no approval for.
 */
class SystemSurfaceCapabilitiesTest {

    @Test
    fun `a non-vendor device reports no surface whatever the protocol value says`() {
        for (manufacturer in listOf("Google", "samsung", "OnePlus", "unknown", "")) {
            val state = mapOngoingSurface(manufacturer, focusProtocol = 3, permissionGranted = true)
            assertEquals(
                "$manufacturer must not be read as a vendor surface",
                OngoingSurfaceCapability.NONE,
                state.capability
            )
            assertFalse(state.isUsable)
        }
    }

    @Test
    fun `a null manufacturer reports no surface`() {
        assertEquals(
            OngoingSurfaceCapability.NONE,
            mapOngoingSurface(null, 2, true).capability
        )
    }

    @Test
    fun `protocol 2 maps to the first-generation focus surface`() {
        assertEquals(
            OngoingSurfaceCapability.FOCUS_OS2,
            mapOngoingSurface("Xiaomi", 2, permissionGranted = true).capability
        )
    }

    @Test
    fun `protocol 3 maps to the expanded island surface`() {
        assertEquals(
            OngoingSurfaceCapability.SUPER_ISLAND_OS3,
            mapOngoingSurface("Xiaomi", 3, permissionGranted = true).capability
        )
    }

    @Test
    fun `manufacturer matching is case-insensitive`() {
        assertEquals(
            OngoingSurfaceCapability.SUPER_ISLAND_OS3,
            mapOngoingSurface("XIAOMI", 3, true).capability
        )
    }

    @Test
    fun `an absent or unrecognised protocol reports no surface`() {
        assertEquals(OngoingSurfaceCapability.NONE, mapOngoingSurface("Xiaomi", null, true).capability)
        assertEquals(OngoingSurfaceCapability.NONE, mapOngoingSurface("Xiaomi", 0, true).capability)
        assertEquals(OngoingSurfaceCapability.NONE, mapOngoingSurface("Xiaomi", 1, true).capability)
        // A future protocol this build has not been taught about is not guessed at.
        assertEquals(OngoingSurfaceCapability.NONE, mapOngoingSurface("Xiaomi", 4, true).capability)
    }

    @Test
    fun `a capable device without permission is detected but not usable`() {
        val state = mapOngoingSurface("Xiaomi", 3, permissionGranted = false)
        assertEquals(OngoingSurfaceCapability.SUPER_ISLAND_OS3, state.capability)
        assertFalse(state.permissionGranted)
        assertFalse("No approved scenario means no publishing", state.isUsable)
    }

    @Test
    fun `permission alone never makes a non-vendor surface usable`() {
        assertFalse(mapOngoingSurface("Google", null, permissionGranted = true).permissionGranted)
    }

    @Test
    fun `a capable device with permission is usable`() {
        val state = mapOngoingSurface("Xiaomi", 2, permissionGranted = true)
        assertTrue(state.isUsable)
    }
}
