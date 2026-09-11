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
     * media transport (ntgcalls; see docs/architecture/calling-native-stack.md).
     *
     * Held again: physical testing found that a connected call crashes the
     * process. Root cause is proven (not hypothesised) via static analysis --
     * see docs/architecture/ntgcalls-jni-forensics.md -- but no fix has been
     * built or verified yet. Do not re-enable until a rebuilt/patched
     * ntgcalls artifact has passed the standalone JNI probe described there.
     */
    const val CALLS_ENABLED = false

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
}
