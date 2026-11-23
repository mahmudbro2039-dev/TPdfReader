package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TeenaFonts {
    // To use custom fonts: add .ttf to res/font and replace with FontFamily(Font(R.font.xxx, ...)).
    val brand: FontFamily = FontFamily.Default   // wordmark + titles
    val body: FontFamily = FontFamily.Default    // everything else
}

val TeenaTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = TeenaFonts.brand,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = TeenaFonts.brand,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = TeenaFonts.brand,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = TeenaFonts.body,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = TeenaFonts.body,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = TeenaFonts.body,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = TeenaFonts.body,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = TeenaFonts.body,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = TeenaFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp
    )
)

/** Crisp badge percentage typography (0.sp tracking) centralized in ui/theme. */
val TeenaBadgeNumberStyle: TextStyle = TextStyle(
    fontFamily = TeenaFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.sp
)

/** Dynamic Text Mode typography helper centralized inside ui/theme so screens never hardcode .sp or FontFamily. */
fun readerTextModeStyle(
    useSerif: Boolean,
    fontSizeSp: Float,
    lineHeightMultiplier: Float
): TextStyle = TextStyle(
    fontFamily = if (useSerif) FontFamily.Serif else TeenaFonts.body,
    fontSize = fontSizeSp.sp,
    lineHeight = (fontSizeSp * lineHeightMultiplier).sp
)
