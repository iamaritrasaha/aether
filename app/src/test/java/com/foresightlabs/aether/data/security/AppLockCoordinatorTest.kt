package com.foresightlabs.aether.data.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.security.AppLockUiState
import com.foresightlabs.aether.domain.security.AutoLockDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files

/** Robolectric only because [PasscodeCrypto] calls android.util.Base64 -- the state machine under test has no other Android dependency. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppLockCoordinatorTest {

    private fun newRepository(): AppLockRepository {
        val file = Files.createTempDirectory("aether-lock-coordinator-test").resolve("prefs.preferences_pb").toFile()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file }
        return AppLockRepository(store)
    }

    private class FakeClock(startMillis: Long = 0L) {
        var now: Long = startMillis
        fun advance(millis: Long) { now += millis }
        val fn: () -> Long = { now }
    }

    private fun newCoordinator(repository: AppLockRepository, clock: FakeClock) =
        AppLockCoordinator(
            repository = repository,
            scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            elapsedRealtime = clock.fn
        )

    private suspend fun enableWithPin(repository: AppLockRepository, pin: String, duration: AutoLockDuration = AutoLockDuration.IMMEDIATELY) {
        repository.settings.first { it != null }
        repository.setPasscodeAndEnable(PasscodeCrypto.createVerifier(pin.toCharArray()))
        repository.setAutoLockDuration(duration)
    }

    @Test
    fun lockDisabledIsAlwaysUnlocked() = runBlocking {
        val repository = newRepository()
        repository.settings.first { it != null }
        val coordinator = newCoordinator(repository, FakeClock())
        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.first { it != AppLockUiState.Loading })
    }

    @Test
    fun freshProcessWithLockEnabledStartsLocked() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456")
        val coordinator = newCoordinator(repository, FakeClock())
        val state = coordinator.lockState.first { it != AppLockUiState.Loading }
        assertTrue(state is AppLockUiState.Locked)
    }

    @Test
    fun correctPinUnlocks() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456")
        val coordinator = newCoordinator(repository, FakeClock())
        coordinator.lockState.first { it != AppLockUiState.Loading }

        val result = coordinator.verifyPasscode("123456".toCharArray())
        assertTrue(result is AppLockCoordinator.PasscodeResult.Correct)
        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)
    }

    @Test
    fun wrongPinStaysLockedAndFirstFewAttemptsHaveNoDelay() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456")
        val coordinator = newCoordinator(repository, FakeClock())
        coordinator.lockState.first { it != AppLockUiState.Loading }

        val result = coordinator.verifyPasscode("000000".toCharArray())
        assertTrue(result is AppLockCoordinator.PasscodeResult.Incorrect)
        assertEquals(0L, (result as AppLockCoordinator.PasscodeResult.Incorrect).delaySeconds)
        assertTrue(coordinator.lockState.value is AppLockUiState.Locked)
    }

    @Test
    fun repeatedFailuresTemporarilyBlockRetrying() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456")
        val coordinator = newCoordinator(repository, FakeClock())
        coordinator.lockState.first { it != AppLockUiState.Loading }

        repeat(4) { coordinator.verifyPasscode("000000".toCharArray()) }
        assertTrue(coordinator.lockState.value is AppLockUiState.TemporarilyBlocked)
    }

    @Test
    fun successfulUnlockResetsFailureStreak() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456")
        val coordinator = newCoordinator(repository, FakeClock())
        coordinator.lockState.first { it != AppLockUiState.Loading }

        repeat(3) { coordinator.verifyPasscode("000000".toCharArray()) }
        coordinator.verifyPasscode("123456".toCharArray())
        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)

        coordinator.lockNow()
        // If the failure streak had survived, this 4th-ever wrong attempt would
        // already be inside the backoff window -- it isn't, because unlocking reset it.
        val result = coordinator.verifyPasscode("000000".toCharArray())
        assertEquals(0L, (result as AppLockCoordinator.PasscodeResult.Incorrect).delaySeconds)
    }

    @Test
    fun lockNowLocksImmediatelyRegardlessOfAutoLockPolicy() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456", AutoLockDuration.ONE_HOUR)
        val coordinator = newCoordinator(repository, FakeClock())
        coordinator.lockState.first { it != AppLockUiState.Loading }
        coordinator.verifyPasscode("123456".toCharArray())
        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)

        coordinator.lockNow()
        assertTrue(coordinator.lockState.value is AppLockUiState.Locked)
    }

    @Test
    fun immediateAutoLockLocksOnAnyReturnToForeground() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456", AutoLockDuration.IMMEDIATELY)
        val clock = FakeClock()
        val coordinator = newCoordinator(repository, clock)
        coordinator.lockState.first { it != AppLockUiState.Loading }
        coordinator.verifyPasscode("123456".toCharArray())

        coordinator.onAppBackgrounded()
        clock.advance(10L) // barely any time at all
        coordinator.onAppForegrounded()

        assertTrue(coordinator.lockState.value is AppLockUiState.Locked)
    }

    @Test
    fun oneMinuteAutoLockDoesNotLockBeforeTimeoutElapses() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456", AutoLockDuration.ONE_MINUTE)
        val clock = FakeClock()
        val coordinator = newCoordinator(repository, clock)
        coordinator.lockState.first { it != AppLockUiState.Loading }
        coordinator.verifyPasscode("123456".toCharArray())

        coordinator.onAppBackgrounded()
        clock.advance(30_000L) // 30s < 60s threshold
        coordinator.onAppForegrounded()

        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)
    }

    @Test
    fun oneMinuteAutoLockLocksAfterTimeoutElapses() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456", AutoLockDuration.ONE_MINUTE)
        val clock = FakeClock()
        val coordinator = newCoordinator(repository, clock)
        coordinator.lockState.first { it != AppLockUiState.Loading }
        coordinator.verifyPasscode("123456".toCharArray())

        coordinator.onAppBackgrounded()
        clock.advance(61_000L) // just past the 60s threshold
        coordinator.onAppForegrounded()

        assertTrue(coordinator.lockState.value is AppLockUiState.Locked)
    }

    @Test
    fun shortBackgroundBelowFiveMinuteTimeoutReturnsUnlocked() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456", AutoLockDuration.FIVE_MINUTES)
        val clock = FakeClock()
        val coordinator = newCoordinator(repository, clock)
        coordinator.lockState.first { it != AppLockUiState.Loading }
        coordinator.verifyPasscode("123456".toCharArray())

        coordinator.onAppBackgrounded()
        clock.advance(4 * 60_000L)
        coordinator.onAppForegrounded()

        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)
    }

    @Test
    fun enablingAppLockDoesNotImmediatelyRelockTheProcessThatJustSetItUp() = runBlocking {
        // Regression guard: entering the same passcode correctly twice during
        // setup must count as proof of it -- the process must not demand it a
        // third time the instant `enabled` flips true.
        val repository = newRepository()
        val coordinator = newCoordinator(repository, FakeClock())
        coordinator.lockState.first { it != AppLockUiState.Loading }
        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)

        repository.setPasscodeAndEnable(PasscodeCrypto.createVerifier("123456".toCharArray()))
        coordinator.markUnlockedAfterPasscodeSetup()

        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.first { it != AppLockUiState.Loading })
    }

    @Test
    fun trustedSystemUiSuppressesBackgroundTrackingSoItNeverFalseLocks() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456", AutoLockDuration.IMMEDIATELY)
        val clock = FakeClock()
        val coordinator = newCoordinator(repository, clock)
        coordinator.lockState.first { it != AppLockUiState.Loading }
        coordinator.verifyPasscode("123456".toCharArray())

        // e.g. a permission dialog or the biometric prompt itself briefly
        // stopping the Activity -- must never count as leaving Aether.
        coordinator.beginTrustedSystemUi()
        coordinator.onAppBackgrounded()
        clock.advance(5_000L)
        coordinator.onAppForegrounded()
        coordinator.endTrustedSystemUi()

        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)
    }

    @Test
    fun biometricSuccessUnlocks() = runBlocking {
        val repository = newRepository()
        enableWithPin(repository, "123456")
        val coordinator = newCoordinator(repository, FakeClock())
        coordinator.lockState.first { it != AppLockUiState.Loading }

        coordinator.unlockWithBiometric()
        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)
    }

    @Test
    fun biometricUnlockAfterALongBackgroundIsNotImmediatelyUndoneByAStaleForegroundCheck() = runBlocking {
        // Regression guard for the exact race this architecture is built to avoid:
        // a long-ago backgroundedAt timestamp must not survive an unlock and
        // re-trigger on the next unrelated foreground evaluation.
        val repository = newRepository()
        enableWithPin(repository, "123456", AutoLockDuration.ONE_HOUR)
        val clock = FakeClock()
        val coordinator = newCoordinator(repository, clock)
        coordinator.lockState.first { it != AppLockUiState.Loading }
        coordinator.verifyPasscode("123456".toCharArray())

        coordinator.onAppBackgrounded()
        clock.advance(2 * 60 * 60_000L) // 2 hours -- well past the 1-hour policy
        coordinator.onAppForegrounded() // this is the legitimate re-lock
        assertTrue(coordinator.lockState.value is AppLockUiState.Locked)

        coordinator.unlockWithBiometric()
        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)

        // A stray extra onAppForegrounded (e.g. a duplicate lifecycle callback)
        // must not see the old backgroundedAt and re-lock what was just unlocked.
        coordinator.onAppForegrounded()
        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)
    }

    @Test
    fun noPasscodeConfiguredIsReportedRatherThanCrashing() = runBlocking {
        val repository = newRepository()
        repository.settings.first { it != null }
        val coordinator = newCoordinator(repository, FakeClock())
        // Lock is off, so this coordinator is Unlocked -- verifyPasscode is only
        // ever invoked from a locked screen, but must still fail safely if called.
        val result = coordinator.verifyPasscode("123456".toCharArray())
        assertEquals(AppLockCoordinator.PasscodeResult.NoPasscodeConfigured, result)
    }

    @Test
    fun wallClockChangeDoesNotAffectSameProcessTimeout() = runBlocking {
        // elapsedRealtime is the only clock this logic reads -- there is no
        // System.currentTimeMillis() anywhere in the auto-lock path to perturb.
        val repository = newRepository()
        enableWithPin(repository, "123456", AutoLockDuration.FIVE_MINUTES)
        val clock = FakeClock(startMillis = Long.MAX_VALUE / 2) // arbitrary large offset
        val coordinator = newCoordinator(repository, clock)
        coordinator.lockState.first { it != AppLockUiState.Loading }
        coordinator.verifyPasscode("123456".toCharArray())

        coordinator.onAppBackgrounded()
        clock.advance(60_000L) // 1 minute of elapsed (monotonic) time
        coordinator.onAppForegrounded()

        assertEquals(AppLockUiState.Unlocked, coordinator.lockState.value)
    }
}
