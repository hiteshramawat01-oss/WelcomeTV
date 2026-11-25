package com.guestexperience.shared.validation

import com.guestexperience.shared.model.CheckOut
import com.guestexperience.shared.model.PropertyContent
import com.guestexperience.shared.model.WifiInfo
import com.guestexperience.shared.model.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    @Test
    fun pins() {
        assertEquals(FieldError.REQUIRED, Validators.pin(""))
        assertEquals(FieldError.TOO_SHORT, Validators.pin("123"))
        assertNull(Validators.pin("1234"))
        assertNull(Validators.pin("123456"))
        assertEquals(FieldError.TOO_LONG, Validators.pin("1234567"))
        assertEquals(FieldError.DIGITS_ONLY, Validators.pin("12a4"))
        assertEquals(FieldError.DIGITS_ONLY, Validators.pin("१२३४")) // Devanagari digits are not remote keys
        assertEquals(FieldError.MISMATCH, Validators.pinConfirmation("1234", "4321"))
        assertNull(Validators.pinConfirmation("1234", "1234"))
    }

    @Test
    fun ssids() {
        assertEquals(FieldError.REQUIRED, Validators.ssid("  "))
        assertNull(Validators.ssid("Pichola-Guest"))
        assertNull(Validators.ssid("a".repeat(32)))
        assertEquals(FieldError.TOO_LONG, Validators.ssid("a".repeat(33)))
        // 11 Devanagari characters = 33 UTF-8 bytes
        assertEquals(FieldError.TOO_LONG, Validators.ssid("उ".repeat(11)))
    }

    @Test
    fun wifiPasswords() {
        assertNull(Validators.wifiPassword("", WifiSecurity.OPEN))
        assertEquals(FieldError.REQUIRED, Validators.wifiPassword("", WifiSecurity.WPA))
        assertEquals(FieldError.TOO_SHORT, Validators.wifiPassword("1234567", WifiSecurity.WPA))
        assertNull(Validators.wifiPassword("12345678", WifiSecurity.WPA))
        assertNull(Validators.wifiPassword("x".repeat(63), WifiSecurity.WPA))
        assertEquals(FieldError.TOO_LONG, Validators.wifiPassword("x".repeat(64), WifiSecurity.WPA))
        assertNull(Validators.wifiPassword("ab".repeat(32), WifiSecurity.WPA)) // 64 hex = raw PSK
        assertNull(Validators.wifiPassword("abcde", WifiSecurity.WEP))
        assertNull(Validators.wifiPassword("0123456789", WifiSecurity.WEP))
        assertEquals(FieldError.INVALID_FORMAT, Validators.wifiPassword("abcdef", WifiSecurity.WEP))
    }

    @Test
    fun textDateTimeAndCodes() {
        assertEquals(FieldError.REQUIRED, Validators.requiredText(" "))
        assertEquals(FieldError.TOO_LONG, Validators.requiredText("x".repeat(41)))
        assertNull(Validators.optionalText("", 10))
        assertNull(Validators.time("11:00"))
        assertNull(Validators.time("23:59"))
        assertEquals(FieldError.INVALID_FORMAT, Validators.time("24:00"))
        assertEquals(FieldError.INVALID_FORMAT, Validators.time("9:00"))
        assertNull(Validators.date("2026-11-26"))
        assertEquals(FieldError.INVALID_FORMAT, Validators.date("2026-02-30"))
        assertNull(Validators.pairingCode("482913"))
        assertEquals(FieldError.INVALID_FORMAT, Validators.pairingCode("48291"))
        assertEquals(FieldError.DIGITS_ONLY, Validators.pairingCode("48291x"))
    }

    @Test
    fun wholeContent() {
        val good = PropertyContent(
            propertyName = "Pichola Haveli",
            wifi = WifiInfo("Pichola-Guest", "lakeview2026"),
            checkOut = CheckOut("2026-11-26", "11:00"),
        )
        assertNull(ContentValidator.validate(good))
        assertEquals("propertyName", ContentValidator.validate(good.copy(propertyName = ""))?.field)
        assertEquals("wifi.password", ContentValidator.validate(good.copy(wifi = WifiInfo("x", "short")))?.field)
        assertEquals("checkOut.time", ContentValidator.validate(good.copy(checkOut = CheckOut("2026-11-26", "25:00")))?.field)
        assertEquals("houseNotes", ContentValidator.validate(good.copy(houseNotes = List(9) { "n" }))?.field)
    }
}
