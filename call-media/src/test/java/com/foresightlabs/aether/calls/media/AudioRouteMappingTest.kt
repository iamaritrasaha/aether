package com.foresightlabs.aether.calls.media

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The route the call UI shows must be derived from the device type Android
 * actually selected -- this mapping is that derivation, so every type we
 * promise to handle (and the ones we must ignore) is pinned here.
 */
class AudioRouteMappingTest {

    @Test
    fun builtinRoutesMapDirectly() {
        assertEquals(AudioRoute.EARPIECE, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertEquals(AudioRoute.SPEAKER, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(AudioRoute.SPEAKER, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE))
    }

    @Test
    fun bluetoothRoutesMapToBluetooth() {
        assertEquals(AudioRoute.BLUETOOTH, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertEquals(AudioRoute.BLUETOOTH, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertEquals(AudioRoute.SPEAKER, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }

    @Test
    fun wiredRoutesMapToWiredHeadset() {
        assertEquals(AudioRoute.WIRED_HEADSET, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertEquals(AudioRoute.WIRED_HEADSET, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioRoute.WIRED_HEADSET, DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_USB_HEADSET))
    }

    @Test
    fun unknownAndNullTypesMapToNull() {
        assertNull(DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(null))
        assertNull(DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(AudioDeviceInfo.TYPE_TELEPHONY))
        assertNull(DefaultTelegramCallMediaEngine.mapDeviceTypeToRoute(-1))
    }
}
