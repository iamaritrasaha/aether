package com.foresightlabs.aether.data.calls.aether

/**
 * The E2EE boundary for Aether Calls -- stated honestly.
 *
 * TARGET (production):
 * - per-installation identity, private material in the Android Keystore;
 * - authenticated per-call key establishment between the two participants
 *   (the call service relays opaque key material it cannot read);
 * - a unique per-call media key, never reused, never persisted.
 *
 * DEVELOPMENT PROTOTYPE (what exists today):
 * - LiveKit media E2EE (keyProvider-based frame encryption) is wired through
 *   [devKeyProvider], whose key is handed to each participant BY THE DEV CALL
 *   SERVICE over the local network at accept time.
 *
 * EXACT LIMITATIONS -- read before claiming any security:
 * 1. The dev key transits the call service in the clear; the service (and
 *    anyone on the LAN who can query it) has every dev call's media key.
 * 2. The key is shared across dev calls rather than freshly established
 *    per call by the participants.
 * 3. No participant authentication binds the key to a verified Aether
 *    identity: any dev-installation that asks receives the key.
 *
 * Therefore: E2EE is ON in media (LiveKit frame encryption) but the key
 * DISTRIBUTION IS NOT SECURE. This is a development-scoped mechanism only.
 * Nothing here may be described as production E2EE until items in TARGET
 * are implemented; see docs/architecture/aether-calls.md.
 */
object AetherCallSecurity {

    /**
     * Explicit marker for diagnostics and the UI: what E2EE state may be
     * claimed for a call. Never shown as a security promise.
     */
    const val E2EE_STATUS: String = "dev-key-distribution (NOT production secure)"
}
