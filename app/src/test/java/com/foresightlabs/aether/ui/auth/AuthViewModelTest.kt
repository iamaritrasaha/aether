package com.foresightlabs.aether.ui.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.model.AuthUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeTelegramClient(app: Application) : TelegramClient(app) {
        val mutableAuthState = MutableStateFlow<AuthUiState>(AuthUiState.Phone())
        override val authState: StateFlow<AuthUiState> = mutableAuthState.asStateFlow()

        var submitPhoneCallCount = 0
        var submitCodeCallCount = 0
        var submitPasswordCallCount = 0

        var submitCodeDelayMillis = 0L
        var submitCodeResult: Result<Unit> = Result.success(Unit)

        override suspend fun submitPhoneNumber(phone: String): Result<Unit> {
            submitPhoneCallCount++
            return Result.success(Unit)
        }

        override suspend fun submitCode(code: String): Result<Unit> {
            submitCodeCallCount++
            if (submitCodeDelayMillis > 0) {
                delay(submitCodeDelayMillis)
            }
            return submitCodeResult
        }

        override suspend fun submitPassword(password: String): Result<Unit> {
            submitPasswordCallCount++
            return Result.success(Unit)
        }
    }

    @Test
    fun rapidDuplicateContinueEventsResultInOnlyOneAuthRequest() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        fakeClient.mutableAuthState.value = AuthUiState.Code("+15551212", 5, "Hint")
        fakeClient.submitCodeDelayMillis = 100L

        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        // Rapid duplicate submissions
        viewModel.submitCode("12345")
        viewModel.submitCode("12345")
        viewModel.submitCode("12345")

        advanceUntilIdle()

        assertEquals("Rapid duplicate Continue taps must produce exactly ONE TDLib request", 1, fakeClient.submitCodeCallCount)
    }

    @Test
    fun continueAndImeDoneCannotIssueTwoSimultaneousRequests() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        fakeClient.mutableAuthState.value = AuthUiState.Code("+15551212", 5, "Hint")
        fakeClient.submitCodeDelayMillis = 100L

        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        // Continue button tap + IME Done in rapid succession
        viewModel.submitCode("12345")
        viewModel.submitCode("12345")

        advanceUntilIdle()

        assertEquals("Continue + IME Done must issue exactly ONE TDLib request", 1, fakeClient.submitCodeCallCount)
    }

    @Test
    fun phoneToCodeClearsPreviousPhoneError() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        // Set phone error
        viewModel.submitPhone("123") // Invalid short phone
        advanceUntilIdle()
        assertEquals("Enter a valid phone number with country code.", viewModel.error.value)

        // Auth state transitions Phone -> Code
        fakeClient.mutableAuthState.value = AuthUiState.Code("+15551212", 5, "Hint")
        advanceUntilIdle()

        assertNull("Transitioning Phone -> Code must clear previous Phone error", viewModel.error.value)
    }

    @Test
    fun codeToPasswordClearsPreviousCodeError() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        fakeClient.mutableAuthState.value = AuthUiState.Code("+15551212", 5, "Hint")
        fakeClient.submitCodeResult = Result.failure(Exception("Incorrect code"))

        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        viewModel.submitCode("00000")
        advanceUntilIdle()
        assertEquals("Incorrect code", viewModel.error.value)

        // Auth state transitions Code -> Password
        fakeClient.mutableAuthState.value = AuthUiState.Password("Hint")
        advanceUntilIdle()

        assertNull("Transitioning Code -> Password must clear previous Code error", viewModel.error.value)
    }

    @Test
    fun codeToReadyClearsPreviousCodeError() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        fakeClient.mutableAuthState.value = AuthUiState.Code("+15551212", 5, "Hint")
        fakeClient.submitCodeResult = Result.failure(Exception("Incorrect code"))

        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        viewModel.submitCode("00000")
        advanceUntilIdle()
        assertEquals("Incorrect code", viewModel.error.value)

        // Auth state transitions Code -> Ready
        fakeClient.mutableAuthState.value = AuthUiState.Ready
        advanceUntilIdle()

        assertNull("Transitioning Code -> Ready must clear previous Code error", viewModel.error.value)
    }

    @Test
    fun passwordToReadyClearsPreviousPasswordError() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        fakeClient.mutableAuthState.value = AuthUiState.Password("Hint")
        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        // Enter invalid password
        viewModel.submitPassword("")
        assertEquals("Enter your 2-step verification password.", viewModel.error.value)

        // Auth state transitions Password -> Ready
        fakeClient.mutableAuthState.value = AuthUiState.Ready
        advanceUntilIdle()

        assertNull("Transitioning Password -> Ready must clear previous Password error", viewModel.error.value)
    }

    @Test
    fun genuineRejectedCodeKeepsErrorWhileInWaitCode() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        fakeClient.mutableAuthState.value = AuthUiState.Code("+15551212", 5, "Hint")
        fakeClient.submitCodeResult = Result.failure(Exception("That verification code is incorrect."))

        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        viewModel.submitCode("11111")
        advanceUntilIdle()

        assertEquals("That verification code is incorrect.", viewModel.error.value)
    }

    @Test
    fun lateErrorFromObsoleteRequestDoesNotAppearOnNewAuthState() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        fakeClient.mutableAuthState.value = AuthUiState.Code("+15551212", 5, "Hint")
        fakeClient.submitCodeDelayMillis = 100L
        fakeClient.submitCodeResult = Result.failure(Exception("Late error from obsolete code request"))

        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        viewModel.submitCode("12345")

        // Before request finishes, authState advances to Ready (e.g. via parallel success or QR)
        fakeClient.mutableAuthState.value = AuthUiState.Ready
        advanceUntilIdle()

        assertNull("Late error from an obsolete request must NOT appear on new auth state (Ready)", viewModel.error.value)
    }

    @Test
    fun busyAndInFlightStateAlwaysReleasesAfterFailure() = runTest(testDispatcher) {
        val fakeClient = FakeTelegramClient(application)
        fakeClient.mutableAuthState.value = AuthUiState.Code("+15551212", 5, "Hint")
        fakeClient.submitCodeResult = Result.failure(RuntimeException("Network failure"))

        val viewModel = AuthViewModel(application, fakeClient)
        advanceUntilIdle()

        viewModel.submitCode("12345")
        advanceUntilIdle()

        assertFalse("Busy state must release back to false after request failure", viewModel.busy.value)

        // Verify subseqent request can execute
        viewModel.submitCode("12345")
        advanceUntilIdle()
        assertEquals(2, fakeClient.submitCodeCallCount)
    }
}
