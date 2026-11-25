package com.guestexperience.shared.wifi

import com.guestexperience.shared.model.WifiInfo
import com.guestexperience.shared.model.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiQrPayloadTest {
    @Test
    fun `plain WPA network`() {
        val payload = WifiQrPayload.build(WifiInfo("Pichola-Guest", "lakeview2026"))
        assertEquals("WIFI:T:WPA;S:Pichola-Guest;P:lakeview2026;;", payload)
    }

    @Test
    fun `every special character is backslash escaped`() {
        val wifi = WifiInfo(ssid = """a\b;c,d:e"f""", password = """p\;,:"q""")
        val payload = WifiQrPayload.build(wifi)
        assertEquals("""WIFI:T:WPA;S:a\\b\;c\,d\:e\"f;P:p\\\;\,\:\"q;;""", payload)
    }

    @Test
    fun `escape leaves ordinary text untouched`() {
        assertEquals("Haveli Guest 5G", WifiQrPayload.escape("Haveli Guest 5G"))
    }

    @Test
    fun `unicode ssid and password pass through unescaped`() {
        val wifi = WifiInfo(ssid = "उदयपुर हवेली", password = "झील-२०२६-☀️")
        assertEquals("WIFI:T:WPA;S:उदयपुर हवेली;P:झील-२०२६-☀️;;", WifiQrPayload.build(wifi))
    }

    @Test
    fun `open network uses nopass and omits the password`() {
        val payload = WifiQrPayload.build(WifiInfo("Lobby", "ignored", WifiSecurity.OPEN))
        assertEquals("WIFI:T:nopass;S:Lobby;;", payload)
    }

    @Test
    fun `hidden and WEP flags`() {
        val payload = WifiQrPayload.build(WifiInfo("Old", "abcde", WifiSecurity.WEP, hidden = true))
        assertEquals("WIFI:T:WEP;S:Old;P:abcde;H:true;;", payload)
    }

    @Test
    fun `parse reverses build including escapes and unicode`() {
        val cases = listOf(
            WifiInfo("""a\b;c,d:e"f""", """p\;,:"q"""),
            WifiInfo("उदयपुर हवेली", "झील-२०२६"),
            WifiInfo("Lobby", "", WifiSecurity.OPEN),
            WifiInfo("Old", "abcde", WifiSecurity.WEP, hidden = true),
            WifiInfo(";;", "::::::::"),
        )
        for (wifi in cases) {
            assertEquals(wifi, WifiQrPayload.parse(WifiQrPayload.build(wifi)))
        }
    }

    @Test
    fun `parse rejects non wifi text`() {
        assertNull(WifiQrPayload.parse("https://example.com"))
        assertNull(WifiQrPayload.parse("WIFI:S:unterminated"))
    }
}
