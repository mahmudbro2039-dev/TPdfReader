package com.example.data.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextQualityClassifierTest {

    @Test
    fun blankOrWhitespaceOnlyReturnsEmpty() {
        assertEquals(TextQuality.EMPTY, TextQualityClassifier.classify("   \n\t  "))
    }

    @Test
    fun invisibleFormattingOnlyReturnsEmpty() {
        assertEquals(
            TextQuality.EMPTY,
            TextQualityClassifier.classify("\uFEFF\u200B\u00AD\u200C\u200D"),
        )
    }

    @Test
    fun cleanEnglishTextReturnsOk() {
        assertEquals(
            TextQuality.OK,
            TextQualityClassifier.classify(
                "Quarterly revenue increased by 14.5% in 2026.",
                setOf("ABCDEF+Roboto-Regular"),
            ),
        )
    }

    @Test
    fun cleanBengaliWithVowelMarksReturnsOk() {
        assertEquals(
            TextQuality.OK,
            TextQualityClassifier.classify(
                "বাংলাদেশের মুক্তিযুদ্ধ ও ভাষা আন্দোলন ইতিহাসের গুরুত্বপূর্ণ অধ্যায়।",
                setOf("ABCDEF+Kalpurush"),
            ),
        )
    }

    @Test
    fun replacementCharactersReturnGarbled() {
        assertEquals(
            TextQuality.GARBLED,
            TextQualityClassifier.classify("Abc \uFFFD\uFFFD\uFFFD\uFFFD xyz"),
        )
    }

    @Test
    fun privateUseAreaCharactersReturnGarbled() {
        assertEquals(
            TextQuality.GARBLED,
            TextQualityClassifier.classify("Header \uE001\uE002\uE003\uE004\uE005 footer"),
        )
    }

    @Test
    fun legacyFontWithoutSubsetPrefixReturnsLegacyFont() {
        assertEquals(
            TextQuality.LEGACY_FONT,
            TextQualityClassifier.classify("Avgvi †mvbvi evsjv", setOf("SutonnyMJ")),
        )
    }

    @Test
    fun legacyFontWithSixLetterSubsetPrefixReturnsLegacyFont() {
        assertEquals(
            TextQuality.LEGACY_FONT,
            TextQualityClassifier.classify("Avgvi †mvbvi evsjv", setOf("XYZKLM+SutonnyMJ-Bold")),
        )
    }
}
