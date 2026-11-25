package com.guestexperience.shared.validation

import com.guestexperience.shared.model.WifiSecurity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

enum class FieldError {
    REQUIRED,
    TOO_SHORT,
    TOO_LONG,
    DIGITS_ONLY,
    INVALID_FORMAT,
    MISMATCH,
}

/** Pure input validation shared by the TV app and the Host phone app. Every function returns null when valid. */
object Validators {
    const val PIN_MIN = 4
    const val PIN_MAX = 6
    const val SSID_MAX_BYTES = 32
    const val NAME_MAX = 40
    const val PROPERTY_MAX = 60
    const val MESSAGE_MAX = 200
    const val NOTE_MAX = 120
    const val PAIRING_CODE_LENGTH = 6

    fun pin(pin: String): FieldError? = when {
        pin.isEmpty() -> FieldError.REQUIRED
        !pin.all { it in '0'..'9' } -> FieldError.DIGITS_ONLY
        pin.length < PIN_MIN -> FieldError.TOO_SHORT
        pin.length > PIN_MAX -> FieldError.TOO_LONG
        else -> null
    }

    fun pinConfirmation(pin: String, confirmation: String): FieldError? =
        pin(confirmation) ?: if (pin != confirmation) FieldError.MISMATCH else null

    fun ssid(ssid: String): FieldError? = when {
        ssid.isBlank() -> FieldError.REQUIRED
        ssid.toByteArray(Charsets.UTF_8).size > SSID_MAX_BYTES -> FieldError.TOO_LONG
        else -> null
    }

    fun wifiPassword(password: String, security: WifiSecurity): FieldError? = when (security) {
        WifiSecurity.OPEN -> null
        WifiSecurity.WPA -> when {
            password.isEmpty() -> FieldError.REQUIRED
            password.length == 64 && password.isHex() -> null
            password.length < 8 -> FieldError.TOO_SHORT
            password.length > 63 -> FieldError.TOO_LONG
            else -> null
        }
        WifiSecurity.WEP -> when {
            password.isEmpty() -> FieldError.REQUIRED
            password.length == 5 || password.length == 13 -> null
            (password.length == 10 || password.length == 26) && password.isHex() -> null
            else -> FieldError.INVALID_FORMAT
        }
    }

    /** Required single-line text such as a guest or property name. */
    fun requiredText(value: String, max: Int = NAME_MAX): FieldError? = when {
        value.isBlank() -> FieldError.REQUIRED
        value.length > max -> FieldError.TOO_LONG
        else -> null
    }

    fun optionalText(value: String, max: Int): FieldError? = if (value.length > max) FieldError.TOO_LONG else null

    /** `HH:mm`, 24-hour. */
    fun time(value: String): FieldError? = when {
        value.isBlank() -> FieldError.REQUIRED
        !Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(value) -> FieldError.INVALID_FORMAT
        else -> try {
            LocalTime.parse(value); null
        } catch (_: DateTimeParseException) {
            FieldError.INVALID_FORMAT
        }
    }

    /** ISO date, `yyyy-MM-dd`. */
    fun date(value: String): FieldError? = when {
        value.isBlank() -> FieldError.REQUIRED
        else -> try {
            LocalDate.parse(value); null
        } catch (_: DateTimeParseException) {
            FieldError.INVALID_FORMAT
        }
    }

    fun pairingCode(code: String): FieldError? = when {
        code.isEmpty() -> FieldError.REQUIRED
        !code.all { it in '0'..'9' } -> FieldError.DIGITS_ONLY
        code.length != PAIRING_CODE_LENGTH -> FieldError.INVALID_FORMAT
        else -> null
    }

    private fun String.isHex() = all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
