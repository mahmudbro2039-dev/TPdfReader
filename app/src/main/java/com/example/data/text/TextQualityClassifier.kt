package com.example.data.text

object TextQualityClassifier {

    private val subsetPrefix = Regex("^[A-Z]{6}\\+")
    private val legacyFontPattern = Regex(
        "(?i)(sutonny|bijoy|tonny|chandrabati|boishakhi|adarshalipi|proshika|sulekha|moina|karnafuli|niharika|kruti|devlys|chanakya|shusha|walkman)"
    )

    private const val COMMON_PUNCTUATION =
        ".,;:!?\"'()\\[\\]{}\\-–—_/\\\\@#\$%&*+=<>~`|।॥…‘’“”৳₹€£°•‣▪◦"

    fun classify(text: String, fontNames: Set<String> = emptySet()): TextQuality {
        var nonWhitespaceCount = 0
        var corruptCount = 0
        var readableCount = 0
        var bengaliCount = 0

        for (ch in text) {
            if (ch.isWhitespace() ||
                ch == '\u200B' ||
                ch == '\uFEFF' ||
                ch == '\u00AD' ||
                ch == '\u200C' ||
                ch == '\u200D' ||
                ch == '\uFFFC'
            ) {
                continue
            }
            nonWhitespaceCount++

            val code = ch.code
            val isControl = code < 0x20 || code in 0x7F..0x9F
            val isPrivateUse = ch in '\uE000'..'\uF8FF'
            val isReplacement = ch == '\uFFFD'

            if (isControl || isPrivateUse || isReplacement) {
                corruptCount++
                continue
            }

            if (ch in '\u0980'..'\u09FF') {
                bengaliCount++
                readableCount++
                continue
            }

            val charType = Character.getType(ch)
            val isCombiningMark =
                charType == Character.NON_SPACING_MARK.toInt() ||
                    charType == Character.COMBINING_SPACING_MARK.toInt() ||
                    charType == Character.ENCLOSING_MARK.toInt()

            if (ch.isLetterOrDigit() || isCombiningMark || COMMON_PUNCTUATION.indexOf(ch) >= 0) {
                readableCount++
            }
        }

        if (nonWhitespaceCount == 0) {
            return TextQuality.EMPTY
        }

        val corruptRatio = corruptCount.toFloat() / nonWhitespaceCount
        if (corruptRatio >= 0.5f) {
            return TextQuality.GARBLED
        }

        val hasLegacyFont = fontNames.any { rawFont ->
            val stripped = rawFont.replace(subsetPrefix, "")
            legacyFontPattern.containsMatchIn(stripped)
        }
        val bengaliRatio = bengaliCount.toFloat() / nonWhitespaceCount
        if (hasLegacyFont && bengaliRatio < 0.30f) {
            return TextQuality.LEGACY_FONT
        }

        if (corruptRatio >= 0.15f) {
            return TextQuality.GARBLED
        }
        if (nonWhitespaceCount >= 10 && corruptCount >= 3 && corruptRatio >= 0.08f) {
            return TextQuality.GARBLED
        }

        val readableRatio = readableCount.toFloat() / nonWhitespaceCount
        if (nonWhitespaceCount >= 12 && readableRatio < 0.45f) {
            return TextQuality.GARBLED
        }

        return TextQuality.OK
    }
}
