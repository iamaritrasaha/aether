package com.foresightlabs.aether

/**
 * Central capability feature flags for Aether.
 *
 * Used to cleanly put unfinished, experimental, or non-milestone features on hold
 * without scattering ad-hoc conditionals across UI and data layers.
 *
 * Held features are deactivated at runtime, hidden from UI entry points, and do not
 * declare or request unused system permissions or foreground services.
 */
object AetherFeatureFlags {

    /**
     * Voice and video calling via TDLib signalling and a real Telegram-compatible
     * media transport (ntgcalls; see docs/architecture/calling-native-stack.md),
     * plus the Aether Calls / LiveKit backend and the shared CallHub.
     *
     * This is a BUILD capability, not a hand-edited switch: development and
     * internal builds compile it true, the Play release compiles it false
     * (BuildConfig.CALLING_ENABLED; -PcallingEnabled forces it on for an
     * installable release-like internal build). With calling held, no call
     * entry point renders, no invite polling or LiveKit registration runs,
     * TDLib call updates never ring, the call foreground service never starts,
     * and the release manifest does not declare the call-only service or its
     * permissions. The implementation stays intact for the experimental track.
     */
    val CALLS_ENABLED: Boolean = BuildConfig.CALLING_ENABLED

    /**
     * Aether Calls specifically (LiveKit rooms + the development call
     * service). Gates the incoming-invite polling and LiveKit registration;
     * never true when [CALLS_ENABLED] is false.
     */
    val AETHER_CALLS_ENABLED: Boolean = BuildConfig.AETHER_CALLS_ENABLED

    /**
     * Telegram Beta calling specifically (TDLib signalling + ntgcalls). Gates
     * the reaction to TDLib call updates -- with it false an incoming Telegram
     * call never rings, notifies, or starts the call service.
     */
    val TELEGRAM_CALLS_ENABLED: Boolean = BuildConfig.TELEGRAM_CALLS_ENABLED

    /**
     * Background continuous live location tracking and streaming.
     * Held for this milestone to eliminate FOREGROUND_SERVICE_LOCATION Play Console declaration.
     * Static location and venue sharing remain fully enabled.
     */
    const val LIVE_LOCATION_ENABLED = false

    /**
     * Local device contact book syncing and matching with Telegram users.
     * Held for this milestone to eliminate READ_CONTACTS permission request.
     * Telegram-native cloud contacts remain fully functional.
     */
    const val DEVICE_CONTACTS_SYNC_ENABLED = false

    /**
     * Passcode/biometric App Lock gate, its Settings entry, and its setup/
     * reauth screens.
     * Held for this milestone: the feature is not ready for normal product
     * behavior. The implementation and any passcode a user already set stay
     * intact on disk -- this flag only controls whether the app reads them
     * on a normal launch.
     */
    const val APP_LOCK_ENABLED = false
}
