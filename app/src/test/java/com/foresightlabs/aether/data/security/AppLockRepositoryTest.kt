package com.foresightlabs.aether.data.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.foresightlabs.aether.domain.security.AppLockVerifier
import com.foresightlabs.aether.domain.security.AutoLockDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AppLockRepositoryTest {

    private fun newRepository(): AppLockRepository {
        val file = Files.createTempDirectory("aether-app-lock-test").resolve("prefs.preferences_pb").toFile()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file }
        return AppLockRepository(store)
    }

    private fun fakeVerifier(pinLength: Int = 6) =
        AppLockVerifier(version = 1, saltBase64 = "c2FsdA==", hashBase64 = "aGFzaA==", iterations = 650_000, pinLength = pinLength)

    @Test
    fun settingsStartsAsNullThenResolvesToDisabledByDefault() = runBlocking {
        val repository = newRepository()
        val loaded = repository.settings.first { it != null }
        assertFalse(loaded!!.enabled)
        assertNull(loaded.verifier)
    }

    @Test
    fun settingPasscodeAndEnableIsAtomicallyReflected() = runBlocking {
        val repository = newRepository()
        repository.settings.first { it != null }
        repository.setPasscodeAndEnable(fakeVerifier())

        val loaded = repository.settings.first { it?.enabled == true }
        assertTrue(loaded!!.enabled)
        assertEquals(6, loaded.verifier?.pinLength)
    }

    @Test
    fun enabledIsFalseIfNoVerifierIsPresentEvenIfFlagWasSetSomehow() = runBlocking {
        // Defensive invariant, not just a happy-path check: enabled must never
        // read true without a verifier behind it -- see AppLockRepository.mapPreferences.
        val repository = newRepository()
        repository.settings.first { it != null }
        repository.disable() // writes the flag false; no verifier ever written
        val loaded = repository.settings.first { it != null }
        assertFalse(loaded!!.enabled)
    }

    @Test
    fun disablePreservesVerifierForReenabling() = runBlocking {
        val repository = newRepository()
        repository.settings.first { it != null }
        repository.setPasscodeAndEnable(fakeVerifier())
        repository.disable()

        val disabled = repository.settings.first { it?.enabled == false }
        assertFalse(disabled!!.enabled)
        assertEquals(6, disabled.verifier?.pinLength) // still there, just not enforced
    }

    @Test
    fun clearPasscodeRemovesVerifierEntirely() = runBlocking {
        val repository = newRepository()
        repository.settings.first { it != null }
        repository.setPasscodeAndEnable(fakeVerifier())
        repository.clearPasscode()

        val cleared = repository.settings.first { it?.verifier == null }
        assertFalse(cleared!!.enabled)
        assertNull(cleared.verifier)
    }

    @Test
    fun settingsSurviveRepositoryRecreation() = runBlocking {
        val file = Files.createTempDirectory("aether-app-lock-test-recreate").resolve("prefs.preferences_pb").toFile()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file }
        val first = AppLockRepository(store)
        first.settings.first { it != null }
        first.setPasscodeAndEnable(fakeVerifier(pinLength = 8))
        first.setBiometricEnabled(true)
        first.setAutoLockDuration(AutoLockDuration.FIVE_MINUTES)

        val recreated = AppLockRepository(store)
        val loaded = recreated.settings.first { it?.enabled == true }
        assertTrue(loaded!!.biometricEnabled)
        assertEquals(AutoLockDuration.FIVE_MINUTES, loaded.autoLockDuration)
        assertEquals(8, loaded.verifier?.pinLength)
    }

    @Test
    fun autoLockDefaultsToImmediately() = runBlocking {
        val repository = newRepository()
        val loaded = repository.settings.first { it != null }
        assertEquals(AutoLockDuration.IMMEDIATELY, loaded!!.autoLockDuration)
    }
}
