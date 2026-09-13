package com.foresightlabs.aether.calls.media

import io.github.pytgcalls.media.DeviceInfo
import org.json.JSONObject

/**
 * Camera-device selection for outgoing video capture.
 *
 * ntgcalls enumerates cameras through WebRTC's CameraEnumerator
 * (`io.github.pytgcalls.devices.JavaVideoCapturerModule.getDevices`) and
 * hands each one to us as a [DeviceInfo] whose `metadata` is a JSON object:
 *
 *     {"id": <enumerator device name>, "is_front": <bool>}
 *
 * Two facts make `is_front` the authoritative facing signal, not a guess:
 * - it is computed by the enumerator itself
 *   (`CameraEnumerator.isFrontFacing(name)`) -- the exact enumerator that
 *   will later open the device (`SmartVideoSource.open()` recreates the same
 *   enumerator and calls `createCapturer(id)` with the metadata's `id`);
 * - `id` is the enumerator's device name: a camera2 ID for
 *   Camera2Enumerator (the normal path), or a Camera1Enumerator display
 *   name on devices without camera2. Only in the camera2 case is it also a
 *   valid `CameraManager` id.
 *
 * Passing `metadata` itself to `CameraManager.getCameraCharacteristics` --
 * the behaviour this object replaces -- was wrong on both counts (a JSON
 * string is not a camera id): every lookup threw, selection returned null,
 * and every "video" call silently degraded to audio-only.
 */
object CameraDeviceSelection {

    /** One camera device as ntgcalls reports it, with its metadata parsed. */
    data class CameraDevice(
        val info: DeviceInfo,
        /** Enumerator device name ntgcalls' capturer will open. */
        val enumeratorId: String,
        /** Facing claimed by the enumerator: true=front, false=back, null=unknown. */
        val isFront: Boolean?
    )

    fun parse(info: DeviceInfo): CameraDevice {
        val json = runCatching { JSONObject(info.metadata) }.getOrNull()
        val id = json?.optString("id").orEmpty().ifBlank { info.name }
        val isFront = if (json != null && json.has("is_front")) {
            json.optBoolean("is_front")
        } else {
            null
        }
        return CameraDevice(info, id, isFront)
    }

    /**
     * Selects the device whose facing matches [front].
     *
     * [facingOf] optionally resolves the enumerator id against Android's
     * authoritative CameraCharacteristics (camera2 ids only; the resolver
     * returns null when the id is not a camera2 id or lookup fails). The
     * metadata claim wins on any disagreement, because that is what
     * ntgcalls' capturer itself acts on; the resolver is the fallback for
     * devices whose metadata carries no `is_front`. Devices with unknown
     * facing either way are skipped -- never guessed from list order.
     * Deterministic: ties are broken by enumerator id.
     */
    fun select(
        cameras: List<CameraDevice>,
        front: Boolean,
        facingOf: (String) -> Boolean? = { null }
    ): DeviceInfo? {
        return cameras
            .filter { it.isFront == front || (it.isFront == null && facingOf(it.enumeratorId) == front) }
            .sortedBy { it.enumeratorId }
            .firstOrNull()
            ?.info
    }
}
