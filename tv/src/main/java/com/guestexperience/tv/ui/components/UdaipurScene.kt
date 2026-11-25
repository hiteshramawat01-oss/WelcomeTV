package com.guestexperience.tv.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * The built-in background: Lake Pichola at dusk (or night) with a palace silhouette and its
 * reflection, drawn as vectors in a 1920×1080 design space. No bitmap, so it costs almost no memory.
 */
@Composable
fun UdaipurScene(night: Boolean, modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val palace = palacePath()
            val hillsFar = Path().apply {
                moveTo(0f, 600f)
                cubicTo(220f, 548f, 420f, 566f, 620f, 590f)
                cubicTo(780f, 610f, 900f, 560f, 1100f, 556f)
                cubicTo(1360f, 552f, 1600f, 520f, 1920f, 548f)
                lineTo(1920f, 640f); lineTo(0f, 640f); close()
            }
            val hillsNear = Path().apply {
                moveTo(0f, 622f)
                cubicTo(260f, 600f, 520f, 612f, 760f, 624f)
                cubicTo(1000f, 636f, 1300f, 604f, 1920f, 612f)
                lineTo(1920f, 640f); lineTo(0f, 640f); close()
            }
            val windows = windowRects()
            val sky = if (night) {
                Brush.verticalGradient(0f to Color(0xFF07060D), 0.55f to Color(0xFF1C1426), 1f to Color(0xFF3B2433), startY = 0f, endY = 640f)
            } else {
                Brush.verticalGradient(0f to Color(0xFF110D1B), 0.45f to Color(0xFF3A2238), 0.8f to Color(0xFFB4553F), 1f to Color(0xFFE8A35E), startY = 0f, endY = 640f)
            }
            val lake = if (night) {
                Brush.verticalGradient(0f to Color(0xFF241824), 1f to Color(0xFF050607), startY = 640f, endY = 1080f)
            } else {
                Brush.verticalGradient(0f to Color(0xFF5A2E33), 0.35f to Color(0xFF24161F), 1f to Color(0xFF0A0C0F), startY = 640f, endY = 1080f)
            }
            val palaceColor = if (night) Color(0xFF0E0A12) else Color(0xFF1A1220)
            val windowColor = Color(0xFFF0B86A).copy(alpha = if (night) 0.7f else 0.85f)

            onDrawBehind {
                // Cover-fit the 1920×1080 design into whatever size we are given.
                val s = maxOf(size.width / 1920f, size.height / 1080f)
                val dx = (size.width - 1920f * s) / 2f
                val dy = (size.height - 1080f * s) / 2f
                withTransform({
                    translate(dx, dy)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    drawRect(sky, size = Size(1920f, 640f))
                    if (night) {
                        // Moon on the right, clear of the text-side scrim.
                        val moon = Offset(1250f, 190f)
                        drawCircle(
                            Brush.radialGradient(listOf(Color(0xFFE9E3D8).copy(alpha = 0.16f), Color.Transparent), center = moon, radius = 120f),
                            radius = 120f,
                            center = moon,
                        )
                        drawCircle(Color(0xFFE9E3D8).copy(alpha = 0.8f), radius = 34f, center = moon)
                    } else {
                        drawCircle(Color(0xFFF6C27E).copy(alpha = 0.9f), radius = 44f, center = Offset(760f, 606f))
                    }
                    drawPath(hillsFar, if (night) Color(0xFF20172A) else Color(0xFF4A2A36))
                    if (!night) drawPath(hillsNear, Color(0xFF2C1B26))
                    drawPath(palace, palaceColor)
                    windows.forEach { drawRect(windowColor, it.topLeft, it.size) }
                    drawRect(windowColor.copy(alpha = 0.4f), Offset(1010f, 600f), Size(820f, 3f))
                    drawRect(lake, topLeft = Offset(0f, 640f), size = Size(1920f, 440f))
                    // Reflection: the palace mirrored about the waterline.
                    scale(1f, -1f, pivot = Offset(0f, 640f)) {
                        drawPath(palace, Color(0xFF120C16).copy(alpha = if (night) 0.22f else 0.28f))
                    }
                    if (!night) shimmer()
                }
            }
        },
    )
}

private fun DrawScope.shimmer() {
    val c = Color(0xFFF6C27E)
    drawRect(c.copy(alpha = 0.5f), Offset(700f, 660f), Size(120f, 3f))
    drawRect(c.copy(alpha = 0.35f), Offset(680f, 690f), Size(160f, 2f))
    drawRect(c.copy(alpha = 0.3f), Offset(720f, 724f), Size(90f, 2f))
    drawRect(c.copy(alpha = 0.18f), Offset(660f, 770f), Size(200f, 2f))
}

private fun palacePath(): Path = Path().apply {
    fun rect(l: Float, t: Float, w: Float, h: Float) = addRect(Rect(l, t, l + w, t + h))
    fun dome(cx: Float, base: Float, r: Float) = addArc(Rect(cx - r, base - r, cx + r, base + r), 180f, 180f)
    rect(980f, 568f, 860f, 72f)
    rect(1040f, 512f, 170f, 60f)
    rect(1230f, 478f, 230f, 94f)
    rect(1478f, 440f, 150f, 132f)
    rect(1646f, 496f, 160f, 76f)
    dome(1088f, 512f, 22f); dome(1166f, 512f, 22f)
    dome(1265f, 478f, 15f); dome(1327f, 478f, 15f); dome(1389f, 478f, 15f); dome(1441f, 478f, 15f)
    dome(1520f, 440f, 26f); dome(1588f, 440f, 26f)
    dome(1686f, 496f, 18f); dome(1770f, 496f, 18f)
    rect(1518f, 402f, 4f, 14f); rect(1586f, 402f, 4f, 14f)
    rect(1086f, 480f, 4f, 12f); rect(1164f, 480f, 4f, 12f)
}

private fun windowRects(): List<Rect> {
    val list = mutableListOf<Rect>()
    fun w(x: Float, y: Float, h: Float) = list.add(Rect(x, y, x + 8f, y + h))
    listOf(1260f, 1300f, 1340f, 1380f, 1420f).forEach { w(it, 520f, 14f) }
    listOf(1500f, 1540f, 1580f).forEach { w(it, 480f, 16f) }
    w(1500f, 530f, 16f); w(1580f, 530f, 16f)
    listOf(1070f, 1120f, 1170f).forEach { w(it, 534f, 12f) }
    listOf(1680f, 1730f, 1780f).forEach { w(it, 524f, 12f) }
    return list
}
