package com.foresightlabs.aether.ui.auth

import android.app.Application
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.model.AuthCategory
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class AuthViewModel @JvmOverloads constructor(
    application: Application,
    private val telegramClientOverride: TelegramClient? = null
) : AndroidViewModel(application) {
    private val telegram = telegramClientOverride
        ?: (application as AetherApplication).telegram
    private val onboardingRepository = (application as AetherApplication).onboardingRepository

    val onboardingCompleted = onboardingRepository.completed

    val authState: StateFlow<AuthUiState> = telegram.authState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        telegram.authState.value
    )

    private val inFlight = AtomicBoolean(false)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _passwordRecoveryRequested = MutableStateFlow(false)
    val passwordRecoveryRequested: StateFlow<Boolean> = _passwordRecoveryRequested.asStateFlow()

    private var lastCategory: AuthCategory = authState.value.category

    init {
        viewModelScope.launch {
            authState.collect { newState ->
                val newCategory = newState.category
                if (newCategory != lastCategory) {
                    lastCategory = newCategory
                    _error.value = null
                }
            }
        }
    }

    fun submitPhone(raw: String) {
        val phone = raw.filter { it.isDigit() || it == '+' }
        if (phone.filter { it.isDigit() }.length < 7) {
            _error.value = "Enter a valid phone number with country code."
            return
        }
        val normalized = if (phone.startsWith("+")) phone else "+$phone"
        runRequest("submitPhone") { telegram.submitPhoneNumber(normalized) }
    }

    fun submitCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            _error.value = "Enter the verification code."
            return
        }
        runRequest("submitCode") { telegram.submitCode(trimmed) }
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) {
            _error.value = "Enter your 2-step verification password."
            return
        }
        if (_passwordRecoveryRequested.value) {
            runRequest("submitPasswordRecoveryCode") {
                telegram.submitPasswordRecoveryCode(password).also { result ->
                    if (result.isSuccess) _passwordRecoveryRequested.value = false
                }
            }
        } else {
            runRequest("submitPassword") { telegram.submitPassword(password) }
        }
    }

    fun register(firstName: String, lastName: String) {
        if (firstName.isBlank()) {
            _error.value = "Enter your first name."
            return
        }
        runRequest("registerUser") { telegram.registerUser(firstName.trim(), lastName.trim()) }
    }

    fun resendCode() {
        // TDLib rejects a resend when Telegram named no next way (and a
        // Firebase one cannot be received here): never send one it will refuse.
        val state = authState.value
        if (state is AuthUiState.Code && !state.canResend) {
            if (BuildConfig.DEBUG) Log.d("AetherAuth", "RESEND_RESULT=skipped_no_usable_next_type")
            return
        }
        runRequest("resendCode") { telegram.resendCode() }
    }

    /** Back to the phone step from a sign-in Aether cannot finish. */
    fun startOver() {
        runRequest("restartSignIn") { telegram.restartSignIn() }
    }

    fun submitEmailAddress(email: String) {
        if (!email.contains('@') || email.length < 5) {
            _error.value = "Enter a valid email address."
            return
        }
        runRequest("submitEmailAddress") { telegram.submitEmailAddress(email.trim()) }
    }

    fun submitEmailCode(code: String) {
        val normalized = code.filter { it.isDigit() }
        if (normalized.isEmpty()) {
            _error.value = "Enter the email verification code."
            return
        }
        runRequest("submitEmailCode") { telegram.submitEmailCode(normalized) }
    }

    fun resetEmailAddress() {
        runRequest("resetAuthenticationEmailAddress") { telegram.resetAuthenticationEmailAddress() }
    }

    fun requestQrCodeAuthentication() {
        runRequest("requestQrCodeAuthentication") { telegram.requestQrCodeAuthentication() }
    }

    fun requestPasswordRecovery() {
        runRequest("requestPasswordRecovery") {
            telegram.requestPasswordRecovery().also { result ->
                if (result.isSuccess) _passwordRecoveryRequested.value = true
            }
        }
    }

    fun usePasskey(context: Context) {
        if (!inFlight.compareAndSet(false, true)) return
        _busy.value = true
        _error.value = null
        val originCategory = authState.value.category
        viewModelScope.launch {
            try {
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
            } catch (failure: Exception) {
                if (authState.value.category == originCategory) {
                    _error.value = failure.message ?: "Passkey sign-in was not completed."
                }
            } finally {
                _busy.value = false
                inFlight.set(false)
            }
        }
    }

    fun markOnboardingCompleted() {
        viewModelScope.launch { onboardingRepository.markCompleted() }
    }

    fun clearError() {
        _error.value = null
    }

    private fun runRequest(actionName: String, block: suspend () -> Result<Unit>) {
        if (!inFlight.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) {
                Log.d("AetherAuth", "AUTH_REQUEST $actionName DROPPED_DUPLICATE_IN_FLIGHT")
            }
            return
        }
        _busy.value = true
        _error.value = null
        val originCategory = authState.value.category
        if (BuildConfig.DEBUG) {
            Log.d("AetherAuth", "AUTH_REQUEST $actionName originCategory=$originCategory")
        }
        viewModelScope.launch {
            try {
                val result = block()
                val currentCategory = authState.value.category
                val err = result.exceptionOrNull()
                if (err != null) {
                    if (BuildConfig.DEBUG) {
                        Log.d("AetherAuth", "AUTH_REQUEST_RESULT $actionName ERROR category=$originCategory currentCategory=$currentCategory")
                    }
                    if (currentCategory == originCategory) {
                        _error.value = err.message ?: "Request failed."
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("AetherAuth", "AUTH_REQUEST_RESULT $actionName OK")
                    }
                }
            } catch (e: Exception) {
                val currentCategory = authState.value.category
                if (BuildConfig.DEBUG) {
                    Log.d("AetherAuth", "AUTH_REQUEST_RESULT $actionName EXCEPTION category=$originCategory currentCategory=$currentCategory")
                }
                if (currentCategory == originCategory) {
                    _error.value = e.message ?: "An unexpected error occurred."
                }
            } finally {
                _busy.value = false
                inFlight.set(false)
            }
        }
    }

    private fun decodeBase64Url(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
