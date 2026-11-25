package com.easyclassshare.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyclassshare.app.settings.ThemeMode

/** Calm classroom palette: warm paper, chalkboard green, pencil yellow. A darker copy for dark mode. */
@Immutable
data class Palette(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val chalk: Color,
    val chalkLight: Color,
    val pencil: Color,
    val pencilLight: Color,
    val sky: Color,
    val skyLight: Color,
    val brick: Color,
    val brickLight: Color,
)

private val LightPalette = Palette(
    paper = Color(0xFFFAF7F1),
    card = Color.White,
    ink = Color(0xFF23303B),
    inkSoft = Color(0xFF5E6A74),
    line = Color(0xFFE5DED2),
    chalk = Color(0xFF2F6B4F),
    chalkLight = Color(0xFFE2EFE7),
    pencil = Color(0xFFE9A93A),
    pencilLight = Color(0xFFFBEFD7),
    sky = Color(0xFF2E6DA8),
    skyLight = Color(0xFFE1ECF6),
    brick = Color(0xFFB4473A),
    brickLight = Color(0xFFF7E1DD),
)

// Strong colours stay dark enough for white text on top (buttons, active tiles).
private val DarkPalette = Palette(
    paper = Color(0xFF151A1F),
    card = Color(0xFF1F262D),
    ink = Color(0xFFE8ECEF),
    inkSoft = Color(0xFFA3AEB8),
    line = Color(0xFF333D46),
    chalk = Color(0xFF3B8462),
    chalkLight = Color(0xFF1F3A2D),
    pencil = Color(0xFFE9A93A),
    pencilLight = Color(0xFF3D321C),
    sky = Color(0xFF3F7EBD),
    skyLight = Color(0xFF1C2E40),
    brick = Color(0xFFC4584A),
    brickLight = Color(0xFF42221E),
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

val Paper: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.paper

/** Background of panels and cards (white in light mode). */
val CardBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val InkSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.inkSoft
val Line: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.line

/** Main actions, online classes. */
val Chalk: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.chalk
val ChalkLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.chalkLight

/** Same-Wi-Fi send, highlights. */
val Pencil: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.pencil
val PencilLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.pencilLight

/** Receiving. */
val Sky: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.sky
val SkyLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.skyLight

/** Muting, removing, errors. */
val Brick: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.brick
val BrickLight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.brickLight

/** "Eyes on me" is always dark, also in light mode. */
val AttentionBackground = Color(0xFF23303B)

private fun colorScheme(p: Palette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.chalk,
        onPrimary = Color.White,
        primaryContainer = p.chalkLight,
        onPrimaryContainer = p.ink,
        secondary = p.pencil,
        onSecondary = Color(0xFF23303B),
        background = p.paper,
        onBackground = p.ink,
        surface = p.card,
        onSurface = p.ink,
        surfaceVariant = p.paper,
        onSurfaceVariant = p.inkSoft,
        surfaceContainer = p.card,
        surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.card,
        outline = p.line,
        outlineVariant = p.line,
        error = p.brick,
    )
} else {
    lightColorScheme(
        primary = p.chalk,
        onPrimary = Color.White,
        primaryContainer = p.chalkLight,
        onPrimaryContainer = p.ink,
        secondary = p.pencil,
        onSecondary = p.ink,
        background = p.paper,
        onBackground = p.ink,
        surface = p.card,
        onSurface = p.ink,
        surfaceVariant = p.paper,
        onSurfaceVariant = p.inkSoft,
        outline = p.line,
        outlineVariant = p.line,
        error = p.brick,
    )
}

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

/** Whether [mode] means dark right now. */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun EasyClassShareTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme(palette, dark),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
