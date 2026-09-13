package com.foresightlabs.aether.data.calls.aether

import io.livekit.android.RoomOptions
import io.livekit.android.e2ee.BaseKeyProvider
import io.livekit.android.e2ee.E2EEOptions

/**
 * Development E2EE wiring for LiveKit.
 *
 * The media frames ARE encrypted end-to-end by LiveKit with the key from
 * [devOptions] -- but that key is DISTRIBUTED BY THE DEV CALL SERVICE, which
 * makes this development-scoped ONLY. See [AetherCallSecurity] for the exact
 * boundary; do not describe Aether Calls as production-E2EE.
 */
object AetherCallE2EE {

    fun devOptions(keyBase64: String): E2EEOptions {
        val keyProvider = BaseKeyProvider()
        // BaseKeyProvider.setKey(sharedKey, participantKey, index): the DEV
        // service hands every participant the same key, so all three coincide.
        keyProvider.setKey(keyBase64, keyBase64, 0)
        return E2EEOptions(keyProvider)
    }

    /** Room options with the dev E2EE applied, kept in one place for clarity. */
    fun roomOptions(keyBase64: String?): RoomOptions =
        keyBase64?.let { RoomOptions(e2eeOptions = devOptions(it)) } ?: RoomOptions()

    /**
     * SAFE fingerprint of the distributed key string (first 8 hex chars of
     * its SHA-256) for caller/callee key-agreement checks in DEBUG builds.
     * Never log the key itself; this is a comparison handle only.
     */
    fun keyFingerprint(keyBase64: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(keyBase64.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
