package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class QuotePalette(
    val outerBackground: Color,
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color
)

object QuotePalettes {
    val light = QuotePalette(
        outerBackground = Color(0xFFEDEAE4),
        background = Color(0xFFFCFBF8),
        text = Color(0xFF18181B),
        secondaryText = Color(0xFF52525B),
        accent = Brand.accentLight
    )
    val dark = QuotePalette(
        outerBackground = Color(0xFF09090B),
        background = Color(0xFF17171C),
        text = Color(0xFFF4F4F5),
        secondaryText = Color(0xFFA1A1AA),
        accent = Brand.accentDark
    )
    val sepia = QuotePalette(
        outerBackground = Color(0xFFE5D9BF),
        background = Color(0xFFF5ECD7),
        text = Color(0xFF3E2F1C),
        secondaryText = Color(0xFF6E563A),
        accent = Color(0xFFB45309)
    )
    val dracula = QuotePalette(
        outerBackground = Color(0xFF1E1F29),
        background = Color(0xFF282A36),
        text = Color(0xFFF8F8F2),
        secondaryText = Color(0xFF94A3B8),
        accent = Color(0xFFBD93F9)
    )
}
