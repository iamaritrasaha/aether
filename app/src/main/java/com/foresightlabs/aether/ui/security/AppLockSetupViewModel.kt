package com.foresightlabs.aether.ui.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.data.security.PasscodeCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AetherApplication

    /** Derives the verifier off the main thread (PBKDF2 at this work factor is deliberately not instant) and persists it. */
    fun enable(pin: CharArray, onDone: () -> Unit) {
        viewModelScope.launch {
            val verifier = withContext(Dispatchers.Default) { PasscodeCrypto.createVerifier(pin) }
            pin.fill('0')
            app.appLockRepository.setPasscodeAndEnable(verifier)
            // Typing the same passcode correctly twice, seconds apart, is
            // already proof of it -- without this, App Lock turning on would
            // immediately demand it a third time before returning to the app.
            app.appLockCoordinator.markUnlockedAfterPasscodeSetup()
            onDone()
        }
    }
}
