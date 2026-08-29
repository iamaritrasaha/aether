package com.foresightlabs.aether.ui.auth

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
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
import org.json.JSONObject

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val telegram = (application as AetherApplication).telegram
    private val onboardingRepository = (application as AetherApplication).onboardingRepository

    val onboardingCompleted = onboardingRepository.completed

    val authState: StateFlow<AuthUiState> = telegram.authState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.authState.value
    )

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _passwordRecoveryRequested = MutableStateFlow(false)
    val passwordRecoveryRequested: StateFlow<Boolean> = _passwordRecoveryRequested.asStateFlow()

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
        val trimmed = code.trim()
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
        if (_passwordRecoveryRequested.value) {
            runRequest {
                telegram.submitPasswordRecoveryCode(password).also { result ->
                    if (result.isSuccess) _passwordRecoveryRequested.value = false
                }
            }
        } else {
            runRequest { telegram.submitPassword(password) }
        }
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

    fun submitEmailAddress(email: String) {
        if (!email.contains('@') || email.length < 5) {
            _error.value = "Enter a valid email address."
            return
        }
        runRequest { telegram.submitEmailAddress(email.trim()) }
    }

    fun submitEmailCode(code: String) {
        val normalized = code.filter { it.isDigit() }
        if (normalized.isEmpty()) {
            _error.value = "Enter the email verification code."
            return
        }
        runRequest { telegram.submitEmailCode(normalized) }
    }

    fun resetEmailAddress() {
        runRequest { telegram.resetAuthenticationEmailAddress() }
    }

    fun requestQrCodeAuthentication() {
        runRequest { telegram.requestQrCodeAuthentication() }
    }

    fun requestPasswordRecovery() {
        runRequest {
            telegram.requestPasswordRecovery().also { result ->
                if (result.isSuccess) _passwordRecoveryRequested.value = true
            }
        }
    }

    fun usePasskey(context: Context) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching {
                val requestJson = telegram.getAuthenticationPasskeyParameters().getOrThrow()
                val credential = CredentialManager.create(context).getCredential(
                    context = context,
                    request = GetCredentialRequest(
                        credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson))
                    )
                ).credential as? PublicKeyCredential
                    ?: error("No passkey was selected.")
                val response = JSONObject(credential.authenticationResponseJson)
                val responseData = response.getJSONObject("response")
                telegram.submitPasskey(
                    credentialId = response.getString("id"),
                    clientData = responseData.getString("clientDataJSON"),
                    authenticatorData = decodeBase64Url(responseData.getString("authenticatorData")),
                    signature = decodeBase64Url(responseData.getString("signature")),
                    userHandle = responseData.optString("userHandle").takeIf { it.isNotEmpty() }
                        ?.let(::decodeBase64Url) ?: ByteArray(0)
                ).getOrThrow()
            }.onFailure { failure ->
                _error.value = failure.message ?: "Passkey sign-in was not completed."
            }
            _busy.value = false
        }
    }

    fun markOnboardingCompleted() {
        viewModelScope.launch { onboardingRepository.markCompleted() }
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

    private fun decodeBase64Url(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
