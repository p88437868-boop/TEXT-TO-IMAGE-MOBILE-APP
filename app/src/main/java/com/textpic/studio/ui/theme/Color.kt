package com.textpic.studio.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BgCanvas = Color(0xFF0B0B10)
val BgGlowStart = Color(0xFF0A1830)
val BgGlowEnd = Color(0xFF2A0F4D)
val SurfaceCard = Color(0xFF1E1E23)
val SurfaceCardHover = Color(0xFF26262C)
val SurfaceInput = Color(0xFF141418)
val SurfaceSelected = Color(0xFF2B2B31)
val SurfaceNav = Color(0xFF17171B)
val KeyFill = Color(0xFF26262C)
val KeyFillSpecial = Color(0xFF323239)

val BorderSubtle = Color(0xFF2E2E35)
val BorderStrong = Color(0xFF44444D)
val BorderFocus = Color(0xFF9B6DDB)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB4B4BD)
val TextMuted = Color(0xFF80808A)
val TextDisabled = Color(0xFF55555E)
val TextOnAccent = Color(0xFFFFFFFF)

val Accent = Color(0xFF9B6DDB)
val AccentHover = Color(0xFFAE86E6)
val AccentPressed = Color(0xFF8456C8)
val AccentSoft = Color(0x2E9B6DDB)
val SeriesTwo = Color(0xFFF4F4F5)
val SeriesThree = Color(0xFF4A4A55)
val Success = Color(0xFF2FA394)
val Warning = Color(0xFFE8B04A)
val Danger = Color(0xFFE5484D)
val Info = Color(0xFF4C8DFF)

data class TextPicColors(
    val bgCanvas: Color,
    val bgGlowStart: Color,
    val bgGlowEnd: Color,
    val surfaceCard: Color,
    val surfaceCardHover: Color,
    val surfaceInput: Color,
    val surfaceSelected: Color,
    val surfaceNav: Color,
    val keyFill: Color,
    val keyFillSpecial: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val borderFocus: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val textOnAccent: Color,
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val seriesTwo: Color,
    val seriesThree: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
)

val TextPicPalette = TextPicColors(
    bgCanvas = BgCanvas,
    bgGlowStart = BgGlowStart,
    bgGlowEnd = BgGlowEnd,
    surfaceCard = SurfaceCard,
    surfaceCardHover = SurfaceCardHover,
    surfaceInput = SurfaceInput,
    surfaceSelected = SurfaceSelected,
    surfaceNav = SurfaceNav,
    keyFill = KeyFill,
    keyFillSpecial = KeyFillSpecial,
    borderSubtle = BorderSubtle,
    borderStrong = BorderStrong,
    borderFocus = BorderFocus,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    textDisabled = TextDisabled,
    textOnAccent = TextOnAccent,
    accent = Accent,
    accentHover = AccentHover,
    accentPressed = AccentPressed,
    accentSoft = AccentSoft,
    seriesTwo = SeriesTwo,
    seriesThree = SeriesThree,
    success = Success,
    warning = Warning,
    danger = Danger,
    info = Info,
)

val LocalTextPicColors = staticCompositionLocalOf { TextPicPalette }
