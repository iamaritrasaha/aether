package com.foresightlabs.aether.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Login-code qualifying rules.
 *
 * Two failure directions matter and both are covered: missing a real code (the
 * security behaviour silently does nothing) and finding a "code" in an ordinary
 * message (a stranger's phone number invalidating something, or an unrelated chat
 * being scanned for numbers at all).
 */
class LoginCodeSecurityTest {

    private val service = TelegramIdentity.SERVICE_NOTIFICATIONS_USER_ID

    @Test
    fun `only telegram service text messages qualify`() {
        assertTrue(LoginCodeSecurity.qualifiesForCodeExtraction(service, isTextMessage = true))
        assertFalse(LoginCodeSecurity.qualifiesForCodeExtraction(service, isTextMessage = false))
        assertFalse(LoginCodeSecurity.qualifiesForCodeExtraction(12345L, isTextMessage = true))
    }

    @Test
    fun `a plain code is extracted`() {
        assertEquals(
            listOf("12345"),
            LoginCodeSecurity.extractLoginCodes(service, true, "Login code: 12345. Do not give it to anyone.")
        )
    }

    @Test
    fun `separators are permitted and stripped for the normalized code`() {
        assertEquals(
            listOf("123456"),
            LoginCodeSecurity.extractLoginCodes(service, true, "Your code is 123-456")
        )
        assertEquals(
            listOf("1234567"),
            LoginCodeSecurity.extractLoginCodes(service, true, "Code 1234-567 expires soon")
        )
    }

    @Test
    fun `code lengths outside five to seven digits are not codes`() {
        assertEquals(emptyList<String>(), LoginCodeSecurity.extractLoginCodes(service, true, "only 1234 here"))
        assertEquals(emptyList<String>(), LoginCodeSecurity.extractLoginCodes(service, true, "id 123456789"))
    }

    @Test
    fun `a longer digit run never yields a code from its middle`() {
        // Without boundary anchoring a phone number would surrender a "code".
        assertEquals(
            emptyList<String>(),
            LoginCodeSecurity.extractLoginCodes(service, true, "Call +8801712345678 for help")
        )
    }

    @Test
    fun `a message from any other sender is never scanned`() {
        // The exact same text, from a person rather than Telegram.
        assertEquals(
            emptyList<String>(),
            LoginCodeSecurity.extractLoginCodes(99L, true, "Login code: 12345")
        )
    }

    @Test
    fun `non-text service content is never scanned`() {
        assertEquals(
            emptyList<String>(),
            LoginCodeSecurity.extractLoginCodes(service, isTextMessage = false, text = "12345")
        )
    }

    @Test
    fun `multiple distinct codes are all returned once each`() {
        assertEquals(
            listOf("12345", "678901"),
            LoginCodeSecurity.extractLoginCodes(service, true, "Codes 12345 and 678-901 and 12345 again")
        )
    }

    // --- screen-capture decision ---------------------------------------------

    @Test
    fun `a capture with a qualifying code visible reports that code`() {
        val visible = listOf(
            LoginCodeSecurity.VisibleMessage(service, true, "Login code: 24680"),
            LoginCodeSecurity.VisibleMessage(11L, true, "see you at 12345")
        )
        assertEquals(listOf("24680"), LoginCodeSecurity.codesExposedByCapture(visible))
    }

    @Test
    fun `a capture with no service message visible reports nothing`() {
        // A screenshot of an ordinary conversation is not a login-code event, even
        // when a number happens to be on screen.
        val visible = listOf(LoginCodeSecurity.VisibleMessage(11L, true, "my flat is 12345"))
        assertEquals(emptyList<String>(), LoginCodeSecurity.codesExposedByCapture(visible))
    }

    @Test
    fun `a capture of an empty screen reports nothing`() {
        assertEquals(emptyList<String>(), LoginCodeSecurity.codesExposedByCapture(emptyList()))
    }

    @Test
    fun `the pinned tdlib revision exposes no invalidation call`() {
        // Guards the report rather than the behaviour: if a TDLib bump ever makes
        // this true, the forward and capture sites must actually be wired up.
        assertFalse(
            "InvalidateSignInCodes is absent from the pinned TdApi; nothing may claim otherwise",
            LoginCodeSecurity.INVALIDATION_SUPPORTED_BY_PINNED_TDLIB
        )
    }
}
