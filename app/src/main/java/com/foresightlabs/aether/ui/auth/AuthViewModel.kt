package com.foresightlabs.aether.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.model.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val telegram = (application as AetherApplication).telegram

    val authState: StateFlow<AuthUiState> = telegram.authState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.authState.value
    )

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun submitPhone(raw: String) {
        val phone = raw.filter { it.isDigit() || it == '+' }
        if (phone.filter { it.isDigit() }.length < 7) {
            _error.value = "Enter a valid phone number with country code."
            return
        }
        val normalized = if (phone.startsWith("+")) phone else "+$phone"
        runRequest { telegram.submitPhoneNumber(normalized) }
    }

    fun submitCode(code: String) {
        val trimmed = code.filter { it.isDigit() }
        if (trimmed.isEmpty()) {
            _error.value = "Enter the verification code."
            return
        }
        runRequest { telegram.submitCode(trimmed) }
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) {
            _error.value = "Enter your 2-step verification password."
            return
        }
        runRequest { telegram.submitPassword(password) }
    }

    fun register(firstName: String, lastName: String) {
        if (firstName.isBlank()) {
            _error.value = "Enter your first name."
            return
        }
        runRequest { telegram.registerUser(firstName.trim(), lastName.trim()) }
    }

    fun resendCode() {
        runRequest { telegram.resendCode() }
    }

    fun resetToPhone() {
        _error.value = null
        telegram.resetAuthToPhone()
    }

    fun clearError() {
        _error.value = null
    }

    private fun runRequest(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val result = block()
            _busy.value = false
            result.exceptionOrNull()?.message?.let { _error.value = it }
        }
    }
}
