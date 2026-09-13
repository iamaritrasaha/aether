package com.foresightlabs.aether.data.calls.aether

import android.content.Context
import java.util.UUID

/**
 * Per-installation Aether calling identity.
 *
 * Deliberately separate from Telegram identity: an installation gets a stable
 * random aetherId on first run, held in app-private prefs. The mapping from
 * Telegram user -> Aether identity is a DIRECTORY lookup at call time (the
 * service owns it), never an assumption baked into UI code.
 *
 * Production replaces this with a Keystore-protected per-installation
 * identity; see docs/architecture/aether-calls.md.
 */
object AetherInstallIdentity {

    private const val PREFS = "aether_calls"
    private const val KEY_AETHER_ID = "aether_id"

    fun aetherId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_AETHER_ID, null)?.let { return it }
        val fresh = "aether-" + UUID.randomUUID().toString().substring(0, 8)
        prefs.edit().putString(KEY_AETHER_ID, fresh).apply()
        return fresh
    }

    fun toCallingIdentity(context: Context, displayName: String, telegramUserId: Long?): AetherCallingIdentity =
        AetherCallingIdentity(
            aetherId = aetherId(context),
            displayName = displayName,
            telegramUserId = telegramUserId
        )
}
