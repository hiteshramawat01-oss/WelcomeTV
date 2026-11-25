package com.guestexperience.shared.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.guestexperience.shared.model.WifiInfo
import com.guestexperience.shared.model.WifiSecurity
import com.guestexperience.shared.wifi.WifiQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrRoundTripTest {

    /** Renders the matrix like the app does (scaled, with a 4-module quiet zone) and decodes it with ZXing. */
    private fun decode(matrix: QrMatrix, scale: Int = 8, quiet: Int = 4): String {
        val side = (matrix.size + quiet * 2) * scale
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (!matrix[x, y]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0xFF000000.toInt()
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        val hints = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8", DecodeHintType.PURE_BARCODE to true)
        return QRCodeReader().decode(bitmap, hints).text
    }

    @Test
    fun `wifi payloads survive encode and decode`() {
        val networks = listOf(
            WifiInfo("Pichola-Guest", "lakeview2026"),
            WifiInfo("""a\b;c,d:e"f""", """p\;,:"q"""),
            WifiInfo("उदयपुर हवेली", "झील-२०२६-☀️"),
            WifiInfo("Lobby", "", WifiSecurity.OPEN, hidden = true),
        )
        for (wifi in networks) {
            val payload = WifiQrPayload.build(wifi)
            val decoded = decode(QrEncoder.encode(payload))
            assertEquals(payload, decoded)
            assertEquals(wifi.copy(password = if (wifi.security == WifiSecurity.OPEN) "" else wifi.password), WifiQrPayload.parse(decoded))
        }
    }

    @Test
    fun `matrix is square and has finder patterns`() {
        val m = QrEncoder.encode("WIFI:T:WPA;S:x;P:12345678;;")
        assertTrue(m.size >= 21)
        // Top-left finder pattern corner modules are dark.
        assertTrue(m[0, 0] && m[6, 0] && m[0, 6] && m[6, 6])
    }
}
