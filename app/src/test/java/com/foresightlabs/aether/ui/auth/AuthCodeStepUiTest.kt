package com.foresightlabs.aether.ui.auth

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.AuthUiState.CodeDelivery
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The code step as a person meets it: where the code went, when (and how) it
 * can be sent again, and the way in when a code is not coming.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class AuthCodeStepUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var resends = 0
    private var qrRequests = 0
    private var startOvers = 0

    private fun show(state: AuthUiState) {
        composeRule.setContent {
            AuthScreen(
                state = state,
                busy = false,
                error = null,
                onSubmitPhone = {},
                onSubmitCode = {},
                onSubmitPassword = {},
                onRegister = { _, _ -> },
                onResendCode = { resends++ },
                onRequestQrCode = { qrRequests++ },
                onStartOver = { startOvers++ }
            )
        }
        composeRule.waitForIdle()
    }

    private fun code(delivery: CodeDelivery, next: CodeDelivery?, timeout: Int, hint: String = "hint") =
        AuthUiState.Code(
            phoneNumber = "+15551234567",
            codeLength = 5,
            hint = hint,
            timeoutSeconds = timeout,
            nextTypeDescription = when (next) {
                CodeDelivery.SMS -> "SMS"
                CodeDelivery.CALL -> "phone call"
                else -> null
            },
            delivery = delivery,
            nextDelivery = next
        )

    @Test
    fun aTelegramMessageCodeSaysCheckTelegramAndCountsDownToTheNextWay() {
        composeRule.mainClock.autoAdvance = false
        show(code(CodeDelivery.TELEGRAM_APP, CodeDelivery.SMS, timeout = 3, hint = "We sent the code to your Telegram app on another device."))
        composeRule.mainClock.advanceTimeBy(100L)

        composeRule.onNodeWithText("Check Telegram").assertIsDisplayed()
        composeRule.onNodeWithText("We sent the code to your Telegram app on another device.").assertIsDisplayed()
        composeRule.onNodeWithText("Enter the 5-digit code.").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_resend_code").assertIsNotEnabled()
        composeRule.onNodeWithText("Send the code via SMS in 0:03").assertExists()

        composeRule.mainClock.advanceTimeBy(3_500L)
        composeRule.onNodeWithTag("auth_resend_code").assertIsEnabled().performClick()
        assertEquals(1, resends)
    }

    @Test
    fun anSmsCodeSaysCheckYourMessages() {
        show(code(CodeDelivery.SMS, CodeDelivery.CALL, timeout = 0))
        composeRule.onNodeWithText("Check your messages").assertIsDisplayed()
        composeRule.onNodeWithText("Check Telegram").assertDoesNotExist()
        composeRule.onNodeWithText("Send the code via phone call").assertIsDisplayed()
    }

    @Test
    fun withNoNextWayThereIsNoResendButQrIsOffered() {
        show(code(CodeDelivery.TELEGRAM_APP, next = null, timeout = 0))
        composeRule.onNodeWithTag("auth_resend_code").assertDoesNotExist()
        composeRule.onNodeWithTag("auth_code_use_qr").assertIsDisplayed().performClick()
        assertEquals(1, qrRequests)
    }

    @Test
    fun aFirebaseCodeLeadsWithTheWayInItCanActuallyUse() {
        show(code(CodeDelivery.FIREBASE_SMS, CodeDelivery.FIREBASE_SMS, timeout = 0))
        composeRule.onNodeWithText("Sign in another way").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_resend_code").assertDoesNotExist()
        composeRule.onNodeWithText("Sign in with QR code").assertIsDisplayed()
    }

    @Test
    fun anUnsupportedMethodSaysWhyAndOffersAWayBack() {
        show(AuthUiState.Unsupported("Telegram requires a Premium purchase to continue on this account."))
        composeRule.onNodeWithText("Telegram requires a Premium purchase to continue on this account.").assertExists()
        composeRule.onNodeWithText("Start over").performClick()
        assertEquals(1, startOvers)
    }
}
