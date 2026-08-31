package com.foresightlabs.aether.data.system

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * What the *device* could display for an ongoing, user-initiated activity -- the
 * platform's promoted/Live Update surface, and Xiaomi's vendor equivalent.
 *
 * This answers capability questions only. Nothing here posts a notification, and
 * nothing in Aether currently calls it to publish anything, by design: both
 * surfaces are reserved for ongoing, user-initiated, time-sensitive activities,
 * and an incoming chat message is none of those. Ordinary messages go to
 * Conversation notifications and stop there.
 *
 * The point of putting it behind this boundary is that the rest of the app never
 * imports a vendor class or reads a vendor key: it asks for an
 * [OngoingSurfaceCapability] in Aether's own vocabulary, and this file is the only
 * place that knows how the answer was obtained.
 */
object SystemSurfaceCapabilities {

    /**
     * Whether the platform itself can promote an ongoing notification (Android's
     * Live Update surface, added in API 36).
     *
     * Deliberately has no publisher behind it. Aether has no eligible ongoing
     * activity today -- calls are the obvious future candidate and are not yet
     * shipped -- so adding a publisher would mean shipping code with no valid
     * caller and requesting a promoted-notification permission nothing needs.
     * When a genuinely eligible activity exists, this is the gate it checks.
     */
    val supportsPromotedOngoing: Boolean
        get() = Build.VERSION.SDK_INT >= 36

    /**
     * Aether's own vocabulary for a vendor ongoing-activity surface. Nothing
     * outside this file names Xiaomi, HyperOS, Focus or Super Island.
     */
    enum class OngoingSurfaceCapability {
        /** No vendor surface, or not determinable. The safe default everywhere. */
        NONE,

        /** The first-generation vendor focus surface (vendor protocol 2). */
        FOCUS_OS2,

        /** The expanded vendor island surface (vendor protocol 3). */
        SUPER_ISLAND_OS3
    }

    /**
     * A vendor surface and whether the app is currently allowed to use it.
     *
     * [permissionGranted] being false with a non-[OngoingSurfaceCapability.NONE]
     * capability is the normal state: the vendor grants access per approved
     * scenario, per application, so a capable device still says no until that
     * approval exists.
     */
    data class OngoingSurfaceState(
        val capability: OngoingSurfaceCapability,
        val permissionGranted: Boolean
    ) {
        /** The only question a caller should ever ask before publishing. */
        val isUsable: Boolean
            get() = capability != OngoingSurfaceCapability.NONE && permissionGranted
    }

    private const val XIAOMI_MANUFACTURER = "xiaomi"

    /**
     * The vendor's documented capability key. Read through [Settings.Global], which
     * is a standard Android API -- no vendor class is referenced, loaded, or
     * reflected into, so this file links and runs identically on Pixel, Samsung,
     * OnePlus, AOSP and older Xiaomi builds.
     */
    private const val FOCUS_PROTOCOL_KEY = "notification_focus_protocol"

    private const val PROTOCOL_FOCUS = 2
    private const val PROTOCOL_SUPER_ISLAND = 3

    /**
     * The pure mapping from raw signals to a capability, separated from reading
     * them so every branch is testable without a device.
     *
     * A non-Xiaomi manufacturer short-circuits to [OngoingSurfaceCapability.NONE]
     * before the protocol value is even considered: another vendor could
     * legitimately use the same settings key name for something unrelated, and
     * acting on it would be reading a key that is not ours to interpret.
     */
    fun mapOngoingSurface(
        manufacturer: String?,
        focusProtocol: Int?,
        permissionGranted: Boolean
    ): OngoingSurfaceState {
        if (!manufacturer.equals(XIAOMI_MANUFACTURER, ignoreCase = true)) {
            return OngoingSurfaceState(OngoingSurfaceCapability.NONE, false)
        }
        val capability = when (focusProtocol) {
            PROTOCOL_FOCUS -> OngoingSurfaceCapability.FOCUS_OS2
            PROTOCOL_SUPER_ISLAND -> OngoingSurfaceCapability.SUPER_ISLAND_OS3
            // Unknown, absent, or a future protocol this build has not been taught
            // about. Reported as NONE rather than guessed at.
            else -> OngoingSurfaceCapability.NONE
        }
        return OngoingSurfaceState(
            capability = capability,
            permissionGranted = capability != OngoingSurfaceCapability.NONE && permissionGranted
        )
    }

    /**
     * Reads the device's actual signals.
     *
     * Every failure mode collapses to [OngoingSurfaceCapability.NONE]: a missing
     * key, a SecurityException from a restricted settings read, or anything else
     * thrown by a vendor-modified framework. Detection that cannot complete is
     * never treated as detection that succeeded.
     *
     * [permissionGranted] is currently always false. The vendor grants ongoing-surface
     * access per reviewed scenario, and Aether has no approved scenario, so there
     * is nothing to query for and no vendor permission is requested in the
     * manifest. Wiring a permission probe now would only ever return false while
     * adding a vendor call on the startup path.
     */
    fun readOngoingSurface(context: Context): OngoingSurfaceState {
        val protocol = try {
            Settings.Global.getInt(context.contentResolver, FOCUS_PROTOCOL_KEY)
        } catch (_: Settings.SettingNotFoundException) {
            null
        } catch (_: Throwable) {
            // A vendor-modified framework can throw things this call is not
            // documented to throw. Capability detection must never be able to
            // crash the app that asked.
            null
        }
        return mapOngoingSurface(
            manufacturer = Build.MANUFACTURER,
            focusProtocol = protocol,
            permissionGranted = false
        )
    }
}
