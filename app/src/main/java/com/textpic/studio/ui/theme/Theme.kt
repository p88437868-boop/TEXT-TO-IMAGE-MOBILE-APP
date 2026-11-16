package com.textpic.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val TextPicColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = TextOnAccent,
    primaryContainer = AccentPressed,
    onPrimaryContainer = TextOnAccent,
    secondary = SeriesTwo,
    onSecondary = BgCanvas,
    background = BgCanvas,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSelected,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderStrong,
    error = Danger,
    onError = TextOnAccent,
    inverseSurface = TextPrimary,
    inverseOnSurface = BgCanvas,
    scrim = BgCanvas,
)

private val TextPicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TextPicTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextPicColors provides TextPicPalette) {
        MaterialTheme(
            colorScheme = TextPicColorScheme,
            typography = TextPicTypography,
            shapes = TextPicShapes,
            content = content,
        )
    }
}
