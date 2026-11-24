package com.easyclassshare.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Bright, high-contrast, playful palette.
val Ink = Color(0xFF1B2A41)
val Cream = Color(0xFFFFF8E7)
val Sunny = Color(0xFFFFD166)
val SunnyLight = Color(0xFFFFF1C7)
val SendOrange = Color(0xFFFF9F1C)
val ReceiveBlue = Color(0xFF1E6FD9)
val SkyLight = Color(0xFFDDEBFF)
val Leaf = Color(0xFF2E8B45)
val Berry = Color(0xFFD62839)
val BerryLight = Color(0xFFFFE0E3)
val Outline = Color(0xFFE3D5B8)
val ClassPurple = Color(0xFF6C4AB6)
val PurpleLight = Color(0xFFECE4FF)

private val KidColors = lightColorScheme(
    primary = ReceiveBlue,
    onPrimary = Color.White,
    secondary = SendOrange,
    onSecondary = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    error = Berry,
)

private val KidTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp),
    )
}

@Composable
fun EasyClassShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KidColors, typography = KidTypography, content = content)
}
