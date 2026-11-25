/*
 * HOW TO CHANGE THE THEME LATER:
 * - Different brand color -> edit Brand.accentLight / Brand.accentDark only.
 * - Different corner roundness -> edit Shape.kt only.
 * - Different spacing / sizes -> edit Tokens.kt only.
 * - Different fonts -> edit TeenaFonts in Type.kt only.
 * - Different neutral / background palette -> edit lightScheme / darkScheme in Color.kt only.
 */
package com.example.ui.theme

import androidx.compose.ui.graphics.Color

object Brand {
    // ★ Change this ONE color → entire app re-colors (buttons, chips, progress, icons, nav, etc.)
    val accentLight = Color(0xFF2B5B84)   // calm editorial slate blue
    val accentDark  = Color(0xFF7EB4E2)   // soft sky slate blue for dark mode

    const val NAME_STRONG = "Teena"
    const val NAME_LIGHT  = "Reader"
    const val NAME        = "$NAME_STRONG$NAME_LIGHT"
}
