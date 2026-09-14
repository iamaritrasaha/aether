package com.foresightlabs.aether.data.telegram

import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.AuthUiState.CodeDelivery
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign-in screen can only be truthful about where a code went if the
 * mapping is. Telegram -- not Aether -- picks the delivery; these pin that
 * each one is described as itself and that a resend is only offered when
 * Telegram named a next way this app can receive.
 */
class AuthCodeDeliveryMappingTest {

    private fun waitCode(
        type: TdApi.AuthenticationCodeType,
        next: TdApi.AuthenticationCodeType? = null,
        timeout: Int = 0
    ) = TelegramMappers.mapAuthState(
        TdApi.AuthorizationStateWaitCode(TdApi.AuthenticationCodeInfo("+15551234567", type, next, timeout))
    ) as AuthUiState.Code

    @Test
    fun aTelegramMessageCodeSendsThePersonToTheirOtherDeviceNotToSms() {
        val code = waitCode(TdApi.AuthenticationCodeTypeTelegramMessage(5), TdApi.AuthenticationCodeTypeSms(5), timeout = 45)
        assertEquals(CodeDelivery.TELEGRAM_APP, code.delivery)
        assertTrue(code.hint.contains("Telegram app on another device"))
        assertFalse("a Telegram-message code is never called an SMS", code.hint.contains("SMS"))
        assertEquals(CodeDelivery.SMS, code.nextDelivery)
        assertEquals(45, code.timeoutSeconds)
        assertTrue(code.canResend)
    }

    @Test
    fun anSmsCodeSaysSmsAndMasksTheNumber() {
        val code = waitCode(TdApi.AuthenticationCodeTypeSms(6))
        assertEquals(CodeDelivery.SMS, code.delivery)
        assertTrue(code.hint.contains("SMS"))
        assertFalse("the full number never appears in copy", code.hint.contains("5551234567"))
        assertTrue(code.hint.contains("67"))
        assertEquals(6, code.codeLength)
    }

    @Test
    fun callCodesExplainTheCall() {
        assertEquals(CodeDelivery.CALL, waitCode(TdApi.AuthenticationCodeTypeCall(5)).delivery)
        assertTrue(waitCode(TdApi.AuthenticationCodeTypeCall(5)).hint.contains("call"))

        val missed = waitCode(TdApi.AuthenticationCodeTypeMissedCall("+1555", 4))
        assertEquals(CodeDelivery.MISSED_CALL, missed.delivery)
        assertTrue("says what to type", missed.hint.contains("last 4 digits"))
        assertEquals(4, missed.codeLength)
    }

    @Test
    fun codeLengthIsWhateverTelegramSaysNeverAssumed() {
        assertEquals(5, waitCode(TdApi.AuthenticationCodeTypeTelegramMessage(5)).codeLength)
        assertEquals(6, waitCode(TdApi.AuthenticationCodeTypeSms(6)).codeLength)
        assertNull("no length: adaptive entry", waitCode(TdApi.AuthenticationCodeTypeSms(0)).codeLength)
        assertFalse("a word by SMS is not numeric", waitCode(TdApi.AuthenticationCodeTypeSmsWord("a")).isNumeric)
    }

    @Test
    fun withNoNextTypeThereIsNothingToResend() {
        val code = waitCode(TdApi.AuthenticationCodeTypeTelegramMessage(5), next = null, timeout = 0)
        assertNull(code.nextDelivery)
        assertFalse("TDLib would reject this resend", code.canResend)
    }

    @Test
    fun aFirebaseSmsCodeIsReportedAsUnreachableWithTheWayOut() {
        val code = waitCode(
            TdApi.AuthenticationCodeTypeFirebaseAndroid(TdApi.FirebaseDeviceVerificationParametersSafetyNet(ByteArray(0)), 6),
            next = TdApi.AuthenticationCodeTypeFirebaseAndroid(TdApi.FirebaseDeviceVerificationParametersSafetyNet(ByteArray(0)), 6)
        )
        assertEquals(CodeDelivery.FIREBASE_SMS, code.delivery)
        assertTrue(code.isUnreachableForThisApp)
        assertFalse("never claims an SMS was sent", code.hint.startsWith("We sent an SMS"))
        assertTrue(code.hint.contains("QR code"))
        assertFalse("a Firebase resend cannot be received either", code.canResend)
    }

    @Test
    fun emailOtherDevicePasswordAndUnsupportedStatesStayDistinct() {
        assertTrue(TelegramMappers.mapAuthState(TdApi.AuthorizationStateWaitEmailAddress(false, false)) is AuthUiState.EmailAddress)
        val emailCode = TelegramMappers.mapAuthState(
            TdApi.AuthorizationStateWaitEmailCode(false, false, TdApi.EmailAddressAuthenticationCodeInfo("a•••@b.c", 6), null)
        )
        assertTrue(emailCode is AuthUiState.EmailCode)

        val qr = TelegramMappers.mapAuthState(TdApi.AuthorizationStateWaitOtherDeviceConfirmation("tg://login?token=x"))
        assertEquals("tg://login?token=x", (qr as AuthUiState.OtherDevice).link)

        val password = TelegramMappers.mapAuthState(TdApi.AuthorizationStateWaitPassword().apply { passwordHint = "cat" })
        assertEquals("a code accepted into 2FA is a password step, not a code failure", "cat", (password as AuthUiState.Password).hint)

        val premium = TelegramMappers.mapAuthState(TdApi.AuthorizationStateWaitPremiumPurchase())
        assertTrue((premium as AuthUiState.Unsupported).description.contains("Premium"))
    }

    @Test
    fun telegramsRefusalsReadAsPlainLanguage() {
        fun msg(raw: String) = TdErrors.userMessage(TdApi.Error(400, raw))
        assertTrue(msg("PHONE_NUMBER_FLOOD").contains("Too many attempts"))
        assertTrue(msg("PHONE_PASSWORD_FLOOD").contains("Too many password attempts"))
        assertTrue(msg("SMS_CODE_CREATE_FAILED").contains("QR code"))
        assertTrue(msg("UPDATE_APP_TO_LOGIN").contains("QR code"))
        assertTrue(msg("PHONE_NUMBER_INVALID").contains("doesn't look valid"))
        assertTrue(msg("PHONE_NUMBER_BANNED").contains("banned"))
        assertTrue(msg("SEND_CODE_UNAVAILABLE").contains("no other way"))
        assertFalse("raw constants stay out of the copy", msg("UPDATE_APP_TO_LOGIN").contains("UPDATE_APP_TO_LOGIN"))
    }

    @Test
    fun theDebugTokenIsTheConstantAndNeverTheFreeText() {
        assertEquals("PHONE_CODE_EXPIRED", TdErrors.token(TdApi.Error(400, "PHONE_CODE_EXPIRED")))
        assertEquals("FLOOD_WAIT_30", TdErrors.token(TdApi.Error(420, "FLOOD_WAIT_30")))
        assertEquals("code_500", TdErrors.token(TdApi.Error(500, "")))
    }
}
