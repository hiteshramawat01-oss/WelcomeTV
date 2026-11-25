package com.guestexperience.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.guestexperience.shared.qr.QrEncoder
import com.guestexperience.shared.qr.QrMatrix
import com.guestexperience.tv.ui.theme.Gtv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

/**
 * Draws a QR code with whole-pixel modules (no blurry edges) and a quiet zone.
 * Encoding runs off the main thread once per payload; the drawing path is cached.
 */
@Composable
fun QrCode(
    payload: String,
    modifier: Modifier = Modifier,
    description: String = "QR code",
    dark: Color = Gtv.Ink,
    light: Color = Gtv.Paper,
) {
    val matrix by produceState<QrMatrix?>(initialValue = null, payload) {
        value = withContext(Dispatchers.Default) { runCatching { QrEncoder.encode(payload) }.getOrNull() }
    }
    Box(
        modifier
            .background(light, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description },
    ) {
        val m = matrix ?: return@Box
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val quiet = 2
                    val modules = m.size + quiet * 2
                    val cell = floor(size.minDimension / modules).coerceAtLeast(1f)
                    val offsetX = floor((size.width - cell * m.size) / 2f)
                    val offsetY = floor((size.height - cell * m.size) / 2f)
                    val path = Path()
                    for (y in 0 until m.size) for (x in 0 until m.size) {
                        if (m[x, y]) {
                            val l = offsetX + x * cell
                            val t = offsetY + y * cell
                            path.addRect(Rect(l, t, l + cell, t + cell))
                        }
                    }
                    onDrawBehind { drawPath(path, dark) }
                },
        )
    }
}
