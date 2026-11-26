package com.guestexperience.shared.qr

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the Samsung app's own QR encoder (`samsung-tv/qr.js`) with zxing's decoder.
 * Opt-in: generate matrices with `node tools/qr-matrices.js samsung-tv/qr.js <out.json>`
 * and run with `QR_MATRICES=<out.json>`.
 */
class QrJsCompatTest {
    @Test
    fun `zxing decodes every code the Samsung encoder made`() {
        val path = System.getenv("QR_MATRICES")
        assumeTrue("set QR_MATRICES to run", path != null)
        val codes = Json.parseToJsonElement(File(path!!).readText()).jsonArray
        assumeTrue(codes.isNotEmpty())
        codes.forEach { code ->
            val obj = code.jsonObject
            val text = obj["text"]!!.jsonPrimitive.content
            val size = obj["size"]!!.jsonPrimitive.int
            val rows = obj["rows"]!!.jsonArray.map { it.jsonPrimitive.content }
            val matrix = BitMatrix(size)
            rows.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c == '1') matrix.set(x, y) } }
            assertEquals("payload of ${text.length} chars", text, Decoder().decode(matrix).text)
        }
    }
}
