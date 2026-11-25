package com.guestexperience.shared.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR code as a square grid of modules, without the quiet zone. `true` = dark module. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

object QrEncoder {
    /** Encodes [text] as UTF-8 with medium error correction. Rendering (scale, quiet zone, colours) is the caller's job. */
    fun encode(text: String): QrMatrix {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
        )
        // Width/height 0 = one pixel per module.
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = bits.width
        val modules = BooleanArray(size * size) { i -> bits.get(i % size, i / size) }
        return QrMatrix(size, modules)
    }
}
