package com.foresightlabs.aether.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foresightlabs.aether.domain.security.AppLockSettings
import com.foresightlabs.aether.domain.security.AppLockVerifier
import com.foresightlabs.aether.domain.security.AutoLockDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.appLockDataStore: DataStore<Preferences> by preferencesDataStore(name = "aether_app_lock_prefs")

/**
 * Single authoritative store for App Lock configuration: whether it's on,
 * whether biometrics may be used, the auto-lock timeout, and the passcode
 * verifier (never the raw passcode -- see [PasscodeCrypto]).
 *
 * [settings] starts at `null` rather than a default-valued struct so a caller
 * can tell "not loaded yet" apart from "loaded, and App Lock is off" --
 * [com.foresightlabs.aether.data.security.AppLockCoordinator] relies on that
 * distinction to never draw protected content before it knows whether the
 * app should be locked.
 */
class AppLockRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("lock_biometric_enabled")
        val AUTO_LOCK_DURATION = stringPreferencesKey("lock_auto_lock_duration")
        val VERIFIER_VERSION = intPreferencesKey("lock_verifier_version")
        val VERIFIER_SALT = stringPreferencesKey("lock_verifier_salt")
        val VERIFIER_HASH = stringPreferencesKey("lock_verifier_hash")
        val VERIFIER_ITERATIONS = intPreferencesKey("lock_verifier_iterations")
        val VERIFIER_PIN_LENGTH = intPreferencesKey("lock_verifier_pin_length")
    }

    /**
     * `null` until the first read from disk completes, then always a real
     * value (App Lock defaults to off, not "unknown," once loaded).
     */
    val settings: StateFlow<AppLockSettings?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map(::mapPreferences)
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

    private fun mapPreferences(prefs: Preferences): AppLockSettings {
        val verifier = decodeVerifier(prefs)
        return AppLockSettings(
            enabled = prefs[Keys.ENABLED] == true && verifier != null,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] == true,
            autoLockDuration = AutoLockDuration.fromId(prefs[Keys.AUTO_LOCK_DURATION]),
            verifier = verifier
        )
    }

    private fun decodeVerifier(prefs: Preferences): AppLockVerifier? {
        val version = prefs[Keys.VERIFIER_VERSION] ?: return null
        val salt = prefs[Keys.VERIFIER_SALT] ?: return null
        val hash = prefs[Keys.VERIFIER_HASH] ?: return null
        val iterations = prefs[Keys.VERIFIER_ITERATIONS] ?: return null
        val pinLength = prefs[Keys.VERIFIER_PIN_LENGTH] ?: return null
        return AppLockVerifier(version, salt, hash, iterations, pinLength)
    }

    /**
     * Sets a new passcode and turns App Lock on in one write, so there's no
     * window where [Keys.ENABLED] is true with no verifier behind it.
     */
    suspend fun setPasscodeAndEnable(verifier: AppLockVerifier) {
        dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = true
            prefs[Keys.VERIFIER_VERSION] = verifier.version
            prefs[Keys.VERIFIER_SALT] = verifier.saltBase64
            prefs[Keys.VERIFIER_HASH] = verifier.hashBase64
            prefs[Keys.VERIFIER_ITERATIONS] = verifier.iterations
            prefs[Keys.VERIFIER_PIN_LENGTH] = verifier.pinLength
        }
    }

    /** Disables App Lock. The verifier is left in place so re-enabling doesn't need a fresh passcode -- callers that want it forgotten call [clearPasscode] explicitly. */
    suspend fun disable() {
        dataStore.edit { prefs -> prefs[Keys.ENABLED] = false }
    }

    suspend fun clearPasscode() {
        dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = false
            prefs.remove(Keys.VERIFIER_VERSION)
            prefs.remove(Keys.VERIFIER_SALT)
            prefs.remove(Keys.VERIFIER_HASH)
            prefs.remove(Keys.VERIFIER_ITERATIONS)
            prefs.remove(Keys.VERIFIER_PIN_LENGTH)
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setAutoLockDuration(duration: AutoLockDuration) {
        dataStore.edit { prefs -> prefs[Keys.AUTO_LOCK_DURATION] = duration.name }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppLockRepository? = null

        fun getInstance(context: Context): AppLockRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppLockRepository(
                    dataStore = context.applicationContext.appLockDataStore
                ).also { INSTANCE = it }
            }
        }
    }
}
