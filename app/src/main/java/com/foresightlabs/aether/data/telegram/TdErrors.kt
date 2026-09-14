package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi

object TdErrors {
    fun userMessage(error: TdApi.Error): String {
        val raw = error.message.orEmpty()
        val floodMatch = Regex("FLOOD_WAIT_(\\d+)", RegexOption.IGNORE_CASE).find(raw)
        if (floodMatch != null) {
            val seconds = floodMatch.groupValues[1].toIntOrNull() ?: 0
            val durationText = when {
                seconds >= 3600 -> "${seconds / 3600} hours"
                seconds >= 120 -> "${seconds / 60} minutes"
                seconds > 0 -> "$seconds seconds"
                else -> ""
            }
            return if (durationText.isNotEmpty()) {
                "Too many attempts. Please wait $durationText before trying again."
            } else {
                "Too many attempts. Please wait before trying again."
            }
        }
        return when {
            raw.contains("PHONE_NUMBER_INVALID", ignoreCase = true) ->
                "That phone number doesn't look valid. Check the country code and try again."
            raw.contains("PHONE_NUMBER_BANNED", ignoreCase = true) ->
                "This phone number is banned on Telegram."
            raw.contains("PHONE_NUMBER_FLOOD", ignoreCase = true) ->
                "Too many attempts for this phone number. Please wait before requesting another code."
            // Telegram restricts some code deliveries (Firebase SMS above all) to
            // its own apps. These say so, and point at what Aether CAN use.
            raw.contains("UPDATE_APP_TO_LOGIN", ignoreCase = true) ->
                "Telegram only lets its own apps finish this sign-in for this account. Sign in with a QR code from a device where you're already logged in."
            raw.contains("SMS_CODE_CREATE_FAILED", ignoreCase = true) ->
                "Telegram couldn't send an SMS code right now. Check Telegram on your other device, or sign in with a QR code."
            raw.contains("SEND_CODE_UNAVAILABLE", ignoreCase = true) ->
                "Telegram has no other way to send a code right now. Use the code you already have, or sign in with a QR code."
            raw.contains("PHONE_CODE_EMPTY", ignoreCase = true) ->
                "Enter the verification code."
            raw.contains("AUTH_RESTART", ignoreCase = true) ->
                "Telegram restarted this sign-in. Enter your phone number again."
            raw.contains("PHONE_NUMBER_APP_SIGNUP_FORBIDDEN", ignoreCase = true) ->
                "Signing up is disabled for this application. Please create your account in an official Telegram app first, then sign in here."
            raw.contains("PHONE_CODE_INVALID", ignoreCase = true) ->
                "That verification code is incorrect."
            raw.contains("PHONE_CODE_EXPIRED", ignoreCase = true) ->
                "That verification code has expired. Request a new one."
            raw.contains("EMAIL_HASH_INVALID", ignoreCase = true) ||
                raw.contains("EMAIL_CODE_INVALID", ignoreCase = true) ->
                "That email verification code is incorrect."
            raw.contains("EMAIL_CODE_EXPIRED", ignoreCase = true) ->
                "That email verification code has expired. Request a new one."
            raw.contains("FIRSTNAME_INVALID", ignoreCase = true) ->
                "Please enter a valid first name."
            raw.contains("LASTNAME_INVALID", ignoreCase = true) ->
                "Please enter a valid last name."
            raw.contains("USERS_TOO_MUCH", ignoreCase = true) ->
                "The maximum number of Telegram accounts has been reached."
            raw.contains("USER_ALREADY_PARTICIPANT", ignoreCase = true) ->
                "This account is already registered."
            raw.contains("PASSWORD_HASH_INVALID", ignoreCase = true) ||
                raw.contains("PASSWORD_MISSING", ignoreCase = true) ->
                "That 2-step verification password is incorrect."
            raw.contains("PHONE_PASSWORD_FLOOD", ignoreCase = true) ->
                "Too many password attempts. Please wait before trying again."
            raw.contains("FLOOD_WAIT", ignoreCase = true) ||
                raw.contains("TOO_MANY_REQUESTS", ignoreCase = true) ||
                raw.contains("Too Many Requests", ignoreCase = true) ||
                error.code == 429 ->
                "Too many attempts. Please wait before trying again."
            raw.contains("NETWORK", ignoreCase = true) || error.code == 500 ->
                "A network problem interrupted Telegram. Try again."
            raw.isBlank() -> "Telegram returned an error (${error.code})."
            else -> "Couldn't complete that request. ${sanitize(raw)}"
        }
    }

    /**
     * The error's Telegram constant ("PHONE_CODE_EXPIRED", "FLOOD_WAIT_30"),
     * safe for a debug log: never the free text, which can echo input back.
     */
    fun token(error: TdApi.Error): String =
        Regex("[A-Z][A-Z0-9_]{2,}").find(error.message.orEmpty())?.value ?: "code_${error.code}"

    private fun sanitize(raw: String): String {
        return raw
            .replace(Regex("api_hash=[^\\s]+", RegexOption.IGNORE_CASE), "api_hash=***")
            .replace(Regex("password=[^\\s]+", RegexOption.IGNORE_CASE), "password=***")
            .replace(Regex("code=[0-9]{4,8}"), "code=***")
    }
}
