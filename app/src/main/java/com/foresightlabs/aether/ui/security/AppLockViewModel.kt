package com.foresightlabs.aether.ui.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.data.security.AppLockCoordinator
import com.foresightlabs.aether.domain.security.AppLockUiState
import com.foresightlabs.aether.domain.security.AppLockVerifier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the lock gate itself (the screen shown while [AppLockUiState] is not [AppLockUiState.Unlocked]). */
class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AetherApplication
    private val coordinator: AppLockCoordinator get() = app.appLockCoordinator

    val lockState: StateFlow<AppLockUiState> = coordinator.lockState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        coordinator.lockState.value
    )

    val biometricEnabled: StateFlow<Boolean> = app.appLockRepository.settings
        .map { it?.biometricEnabled == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Digits in the configured passcode -- only meaningful once [lockState] is past [AppLockUiState.Loading]. */
    val pinLength: StateFlow<Int> = app.appLockRepository.settings
        .map { it?.verifier?.pinLength ?: DEFAULT_PIN_LENGTH }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_PIN_LENGTH)

    private companion object {
        const val DEFAULT_PIN_LENGTH = 6
    }

    fun verifyPasscode(pin: CharArray, onResult: (AppLockCoordinator.PasscodeResult) -> Unit) {
        viewModelScope.launch {
            val result = coordinator.verifyPasscode(pin)
            pin.fill('0')
            onResult(result)
        }
    }

    fun onBiometricSuccess() = coordinator.unlockWithBiometric()

    fun beginTrustedSystemUi() = coordinator.beginTrustedSystemUi()
    fun endTrustedSystemUi() = coordinator.endTrustedSystemUi()
}
