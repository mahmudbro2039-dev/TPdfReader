package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

internal fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.10f).compositeOver(Color.White),
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.72f).compositeOver(Color(0xFF4A4546)),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2EEE9),
    onSecondaryContainer = Color(0xFF1C1A1B),
    background = Color(0xFFFAF8F5),          // warm ivory
    onBackground = Color(0xFF1C1A1B),
    surface = Color.White,
    onSurface = Color(0xFF1C1A1B),
    surfaceVariant = Color(0xFFF2EEE9),
    onSurfaceVariant = Color(0xFF6B6560),
    outline = Color(0xFFD9D2CA),
    outlineVariant = Color(0xFFE8E2DB)
)

internal fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF2A0509),
    primaryContainer = accent.copy(alpha = 0.16f).compositeOver(Color(0xFF1C1A1B)),
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.65f).compositeOver(Color(0xFFB0A8A3)),
    onSecondary = Color(0xFF1C1A1B),
    secondaryContainer = Color(0xFF2A2728),
    onSecondaryContainer = Color(0xFFEDE8E5),
    background = Color(0xFF121112),
    onBackground = Color(0xFFEDE8E5),
    surface = Color(0xFF1C1A1B),
    onSurface = Color(0xFFEDE8E5),
    surfaceVariant = Color(0xFF2A2728),
    onSurfaceVariant = Color(0xFFB0A8A3),
    outline = Color(0xFF4A4546),
    outlineVariant = Color(0xFF332F30)
)

enum class ReadingStatus { ToRead, Reading, Completed, OnHold }

@Immutable
data class StatusColors(val bg: Color, val fg: Color)

@Immutable
data class ReaderSurfaceColors(
    val canvasLight: Color,
    val canvasDark: Color,
    val canvasSepia: Color,
    val textLightBg: Color,
    val textLightFg: Color,
    val textLightSurface: Color,
    val textSepiaBg: Color,
    val textSepiaFg: Color,
    val textSepiaAccent: Color,
    val textSepiaSurface: Color,
    val textDarkBg: Color,
    val textDarkFg: Color,
    val textDarkSurface: Color,
    val draculaBg: Color,
    val draculaSurface: Color,
    val draculaFg: Color,
    val draculaAccent: Color,
    val highlightDefault: Color,
    val pagePaper: Color
)

/** Extra semantic colors, ALL derived from the ColorScheme (so changing Brand changes everything). */
@Immutable
class TeenaColors(
    val hairline: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val searchBg: Color,
    val coverScrim: Color,
    val onCoverScrim: Color,
    val badgeBg: Color,
    val badgeTrack: Color,
    val badgeFill: Color,
    val badgeText: Color,
    val reader: ReaderSurfaceColors,
    private val toRead: StatusColors,
    private val reading: StatusColors,
    private val completed: StatusColors,
    private val onHold: StatusColors
) {
    fun status(s: ReadingStatus) = when (s) {
        ReadingStatus.ToRead -> toRead
        ReadingStatus.Reading -> reading
        ReadingStatus.Completed -> completed
        ReadingStatus.OnHold -> onHold
    }
}

internal fun buildExtras(scheme: ColorScheme, dark: Boolean): TeenaColors {
    fun tone(base: Color) = StatusColors(
        bg = base.copy(alpha = if (dark) 0.18f else 0.12f).compositeOver(scheme.surface),
        fg = base
    )
    val readerColors = ReaderSurfaceColors(
        canvasLight = Color(0xFFE8E2DB),
        canvasDark = Color(0xFF0B0A0B),
        canvasSepia = Color(0xFFE6DEC8),
        textLightBg = Color(0xFFFAF8F5),
        textLightFg = Color(0xFF1C1A1B),
        textLightSurface = Color(0xFFF2EEE9),
        textSepiaBg = Color(0xFFF5ECD7),
        textSepiaFg = Color(0xFF3E2F1C),
        textSepiaAccent = Color(0xFF8C5000),
        textSepiaSurface = Color(0xFFEAE0C8),
        textDarkBg = Color(0xFF121112),
        textDarkFg = Color(0xFFEDE8E5),
        textDarkSurface = Color(0xFF1C1A1B),
        draculaBg = Color(0xFF282A36),
        draculaSurface = Color(0xFF343746),
        draculaFg = Color(0xFFF8F8F2),
        draculaAccent = Color(0xFFBD93F9),
        highlightDefault = Color(0xFFFACC15),
        pagePaper = Color.White
    )
    return TeenaColors(
        hairline = scheme.outlineVariant,
        progressTrack = scheme.outlineVariant,
        progressFill = scheme.primary,
        searchBg = scheme.surfaceVariant,
        coverScrim = Color.Black.copy(alpha = 0.45f),
        onCoverScrim = Color.White,
        badgeBg = scheme.surface,
        badgeTrack = scheme.outlineVariant,
        badgeFill = scheme.primary,
        badgeText = scheme.onSurface,
        reader = readerColors,
        toRead = StatusColors(scheme.surfaceVariant, scheme.onSurfaceVariant),
        reading = tone(scheme.primary),
        completed = tone(if (dark) Color(0xFF6FCF97) else Color(0xFF2E7D5B)),
        onHold = tone(if (dark) Color(0xFFF2B35E) else Color(0xFFB7791F))
    )
}
