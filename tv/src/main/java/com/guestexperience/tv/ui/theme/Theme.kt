package com.guestexperience.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.guestexperience.tv.R

/** Palette from the design canvas. */
object Gtv {
    val Ink = Color(0xFF0A0C0F)
    val Paper = Color(0xFFF6F1E8)
    val Gold = Color(0xFFD9B97F)
    val Card = Color(0xFF15181D)
    val CardDim = Color(0xFF121418)
    val Line = Color(0xFF262A31)
    val LineStrong = Color(0xFF3A3E45)
    val Soft = Color(0xFFE4DDD1)
    val Muted = Color(0xFFBDB7AC)
    val Dim = Color(0xFF9C968C)
    val Warn = Color(0xFFE8A88A)
    val Ok = Color(0xFF8FC79A)
}

/** Cormorant Garamond for the greeting, clock and titles. Variable font: weight picks the axis value. */
val DisplayFont = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Light),
    Font(R.font.cormorant_garamond, FontWeight.Normal),
)

/** Inter for everything else. */
val UiFont = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
)

/**
 * Sizes are in sp on a 960×540 dp TV screen (1920×1080 px at xhdpi), matching the design canvas
 * where 1 sp = 2 px. Body text is never below 14 sp (28 px), readable from ~3 m.
 */
object GtvType {
    val Greeting = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Light, fontSize = 54.sp, lineHeight = 56.sp, color = Gtv.Paper)
    val Subtitle = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Light, fontSize = 24.sp, lineHeight = 30.sp, color = Gtv.Soft)
    val Clock = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Light, fontSize = 60.sp, lineHeight = 60.sp, color = Gtv.Paper)
    val Title = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 44.sp, color = Gtv.Paper)
    val Overline = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.22.em, color = Gtv.Gold)
    val Body = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = Gtv.Soft)
    val BodyStrong = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, color = Gtv.Paper)
    val Label = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = Gtv.Muted)
    val Button = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

@Composable
fun GuestTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gtv.Gold,
            onPrimary = Gtv.Ink,
            background = Gtv.Ink,
            onBackground = Gtv.Paper,
            surface = Gtv.Card,
            onSurface = Gtv.Paper,
            surfaceVariant = Gtv.CardDim,
            onSurfaceVariant = Gtv.Muted,
            border = Gtv.Line,
            error = Gtv.Warn,
        ),
        typography = Typography(
            bodyLarge = GtvType.BodyStrong,
            bodyMedium = GtvType.Body,
            labelLarge = GtvType.Button,
        ),
        content = content,
    )
}
