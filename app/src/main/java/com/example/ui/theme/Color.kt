package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.example.data.model.ReadingStatus

internal fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.09f).compositeOver(Color.White),
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.72f).compositeOver(Color(0xFF475569)),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    background = Color.White,                 // pure crisp white
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F5F8),
    onSurfaceVariant = Color(0xFF5C6675),
    outline = Color(0xFFD5DCE4),
    outlineVariant = Color(0xFFE7ECF2)
)

internal fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF0A1929),
    primaryContainer = accent.copy(alpha = 0.16f).compositeOver(Color(0xFF161B22)),
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.65f).compositeOver(Color(0xFF94A3B8)),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF21262D),
    onSecondaryContainer = Color(0xFFF0F6FC),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF9198A1),
    outline = Color(0xFF3D444D),
    outlineVariant = Color(0xFF282E36)
)

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
    val isDark: Boolean,
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
    private val onHold: StatusColors,
    private val abandoned: StatusColors
) {
    fun status(s: ReadingStatus) = when (s) {
        ReadingStatus.TO_READ -> toRead
        ReadingStatus.READING -> reading
        ReadingStatus.COMPLETED -> completed
        ReadingStatus.ON_HOLD -> onHold
        ReadingStatus.ABANDONED -> abandoned
    }
}

internal fun buildExtras(scheme: ColorScheme, dark: Boolean): TeenaColors {
    fun tone(base: Color) = StatusColors(
        bg = base.copy(alpha = if (dark) 0.18f else 0.12f).compositeOver(scheme.surface),
        fg = base
    )
    val readerColors = ReaderSurfaceColors(
        canvasLight = Color(0xFFF3F5F8),
        canvasDark = Color(0xFF0A0D12),
        canvasSepia = Color(0xFFE6DEC8),
        textLightBg = Color.White,
        textLightFg = Color(0xFF111827),
        textLightSurface = Color(0xFFF3F5F8),
        textSepiaBg = Color(0xFFF5ECD7),
        textSepiaFg = Color(0xFF3E2F1C),
        textSepiaAccent = Color(0xFF8C5000),
        textSepiaSurface = Color(0xFFEAE0C8),
        textDarkBg = Color(0xFF0E1116),
        textDarkFg = Color(0xFFF0F6FC),
        textDarkSurface = Color(0xFF161B22),
        draculaBg = Color(0xFF282A36),
        draculaSurface = Color(0xFF343746),
        draculaFg = Color(0xFFF8F8F2),
        draculaAccent = Color(0xFFBD93F9),
        highlightDefault = Color(0xFFFACC15),
        pagePaper = Color.White
    )
    return TeenaColors(
        isDark = dark,
        hairline = scheme.outlineVariant,
        progressTrack = scheme.outlineVariant,
        progressFill = scheme.primary,
        searchBg = scheme.surfaceVariant.copy(alpha = if (dark) 0.50f else 0.55f).compositeOver(scheme.surface),
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
        onHold = tone(if (dark) Color(0xFFF2B35E) else Color(0xFFB7791F)),
        abandoned = tone(scheme.error)
    )
}
