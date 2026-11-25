package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb

object ContentColors {
    // Intentionally red like a real ink pen; independent of Brand.
    val signatureInk: Color = Color(0xFFB91C1C)
    val signatureInkHex: String = toHex(signatureInk)

    // PDF page & sample handbook rendering palette
    val pagePaper: Color = Color.White
    val sampleGuideAccent: Color = Brand.accentLight
    val sampleGuideHeading: Color = Color(0xFF18181B)
    val sampleGuideBody: Color = Color(0xFF27272A)
    val sampleGuideMuted: Color = Color(0xFF71717A)
    val sampleGuideDivider: Color = Color(0xFFE4E2DD)
    val sampleGuideTipBg: Color = Brand.accentLight.copy(alpha = 0.10f).compositeOver(Color.White)
    val sampleGuideTipText: Color = Brand.accentLight

    // Reader highlight swatch palette
    val highlightYellow: Color = Color(0xFFFACC15)
    val highlightGreen: Color = Color(0xFF4ADE80)
    val highlightBlue: Color = Color(0xFF38BDF8)
    val highlightPink: Color = Color(0xFFF472B6)
    val highlightOrange: Color = Color(0xFFFB923C)

    val highlightYellowHex: String = toHex(highlightYellow)
    val highlightGreenHex: String = toHex(highlightGreen)
    val highlightBlueHex: String = toHex(highlightBlue)
    val highlightPinkHex: String = toHex(highlightPink)
    val highlightOrangeHex: String = toHex(highlightOrange)

    fun parseHex(hex: String, fallback: Color): Color =
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

    private fun toHex(color: Color): String =
        String.format("#%06X", color.toArgb() and 0xFFFFFF)
}
