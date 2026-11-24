package com.easyclassshare.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Calm classroom palette: warm paper, chalkboard green, pencil yellow.
val Paper = Color(0xFFFAF7F1)
val Ink = Color(0xFF23303B)
val InkSoft = Color(0xFF5E6A74)
val Line = Color(0xFFE5DED2)

val Chalk = Color(0xFF2F6B4F)        // main actions, online classes
val ChalkLight = Color(0xFFE2EFE7)
val Pencil = Color(0xFFE9A93A)       // same-Wi-Fi send, highlights
val PencilLight = Color(0xFFFBEFD7)
val Sky = Color(0xFF2E6DA8)          // receiving
val SkyLight = Color(0xFFE1ECF6)
val Brick = Color(0xFFB4473A)        // muting, removing, errors
val BrickLight = Color(0xFFF7E1DD)

private val AppColors = lightColorScheme(
    primary = Chalk,
    onPrimary = Color.White,
    primaryContainer = ChalkLight,
    onPrimaryContainer = Ink,
    secondary = Pencil,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkSoft,
    outline = Line,
    outlineVariant = Line,
    error = Brick,
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun EasyClassShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, shapes = AppShapes, content = content)
}
