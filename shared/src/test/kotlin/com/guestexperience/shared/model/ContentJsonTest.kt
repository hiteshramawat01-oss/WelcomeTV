package com.guestexperience.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentJsonTest {
    private val sample = PropertyContent(
        propertyName = "Pichola Haveli",
        location = "Udaipur",
        guestName = "Ananya",
        wifi = WifiInfo("Pichola-Guest", "lakeview2026"),
        checkOut = CheckOut("2026-11-26", "11:00"),
        houseNotes = listOf("Rooftop sunset is best at 5:30"),
        places = listOf(Place("Ambrai Ghat", "Sunset views across the lake")),
        hostContact = HostContact("Meera", "+91 00000 00000"),
        timeFormat = TimeFormat.H24,
    )

    @Test
    fun `round trip`() {
        assertEquals(sample, ContentJson.decode(ContentJson.encode(sample)))
    }

    @Test
    fun `json carries the schema version`() {
        assert(ContentJson.encode(PropertyContent()).contains("\"schemaVersion\":1"))
    }

    @Test
    fun `unknown fields from a newer version are ignored`() {
        val future = """
            {"schemaVersion":7,"propertyName":"Pichola Haveli","rooftopCamera":{"url":"x"},
             "wifi":{"ssid":"G","password":"12345678","band":"6GHz"},"newList":[1,2,3]}
        """.trimIndent()
        val decoded = ContentJson.decode(future)
        assertEquals("Pichola Haveli", decoded.propertyName)
        assertEquals("G", decoded.wifi.ssid)
        assertEquals(7, decoded.schemaVersion)
    }

    @Test
    fun `unknown enum values fall back to defaults`() {
        val decoded = ContentJson.decode("""{"propertyName":"P","timeFormat":"H36","wifi":{"ssid":"G","security":"WPA9"}}""")
        assertEquals(TimeFormat.H12, decoded.timeFormat)
        assertEquals(WifiSecurity.WPA, decoded.wifi.security)
    }

    @Test
    fun `missing fields get defaults`() {
        val decoded = ContentJson.decode("""{"propertyName":"P"}""")
        assertEquals(PropertyContent(propertyName = "P"), decoded)
        assertEquals(BuiltInScene.UDAIPUR_DUSK, BuiltInScene.fromId(decoded.background.scene))
        assertEquals(BuiltInScene.UDAIPUR_DUSK, BuiltInScene.fromId("mars"))
    }

    @Test
    fun `garbage is rejected with IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { ContentJson.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { ContentJson.decode("[1,2]") }
        assertThrows(IllegalArgumentException::class.java) { ContentJson.decode("""{"houseNotes":"oops"}""") }
    }
}
