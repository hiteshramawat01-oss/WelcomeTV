package com.guestexperience.host.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.guestexperience.host.R

object HostColors {
    val Ink = Color(0xFF0A0C0F)
    val Paper = Color(0xFFF6F1E8)
    val Gold = Color(0xFFD9B97F)
    val Card = Color(0xFF15181D)
    val Line = Color(0xFF262A31)
    val Muted = Color(0xFFBDB7AC)
    val Dim = Color(0xFF9C968C)
    val Warn = Color(0xFFE8A88A)
    val Ok = Color(0xFF8FC79A)
}

val Display = FontFamily(Font(R.font.cormorant_garamond, FontWeight.Normal))
val Ui = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
)

val Overline = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.2.em, color = HostColors.Gold)

@Composable
fun HostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HostColors.Gold,
            onPrimary = HostColors.Ink,
            background = HostColors.Ink,
            onBackground = HostColors.Paper,
            surface = HostColors.Ink,
            onSurface = HostColors.Paper,
            surfaceVariant = HostColors.Card,
            onSurfaceVariant = HostColors.Muted,
            surfaceContainer = HostColors.Card,
            outline = HostColors.Line,
            error = HostColors.Warn,
            secondaryContainer = HostColors.Card,
            onSecondaryContainer = HostColors.Gold,
        ),
        typography = Typography(
            headlineMedium = TextStyle(fontFamily = Display, fontSize = 34.sp, lineHeight = 38.sp),
            titleMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            bodyLarge = TextStyle(fontFamily = Ui, fontSize = 16.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontFamily = Ui, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = Ui, fontSize = 13.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            labelMedium = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        ),
        content = content,
    )
}
