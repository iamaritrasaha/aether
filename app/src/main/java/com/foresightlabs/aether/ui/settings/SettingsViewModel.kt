package com.foresightlabs.aether.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val telegram = (application as AetherApplication).telegram

    val currentUser: StateFlow<User?> = telegram.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.currentUser.value
    )

    private val _confirmLogout = MutableStateFlow(false)
    val confirmLogout: StateFlow<Boolean> = _confirmLogout.asStateFlow()

    fun requestLogout() {
        _confirmLogout.value = true
    }

    fun dismissLogout() {
        _confirmLogout.value = false
    }

    fun confirmLogout() {
        _confirmLogout.value = false
        viewModelScope.launch { telegram.logOut() }
    }
}
