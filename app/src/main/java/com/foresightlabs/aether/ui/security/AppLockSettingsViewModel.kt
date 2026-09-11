package com.foresightlabs.aether.ui.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.security.AppLockSettings
import com.foresightlabs.aether.domain.security.AutoLockDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppLockSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AetherApplication

    val settings: StateFlow<AppLockSettings?> = app.appLockRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        app.appLockRepository.settings.value
    )

    /** Only reached after the caller has already re-authenticated -- see [AppLockReauthScreen]. */
    fun disableAppLock() {
        viewModelScope.launch { app.appLockRepository.disable() }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { app.appLockRepository.setBiometricEnabled(enabled) }
    }

    fun setAutoLockDuration(duration: AutoLockDuration) {
        viewModelScope.launch { app.appLockRepository.setAutoLockDuration(duration) }
    }

    fun lockNow() {
        app.appLockCoordinator.lockNow()
    }
}
