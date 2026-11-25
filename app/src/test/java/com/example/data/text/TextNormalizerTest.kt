package com.example.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun cleanNormalizesCrlfAndCrToLf() {
        val raw = "Line one\r\nLine two\rLine three"
        assertEquals("Line one\nLine two\nLine three", TextNormalizer.clean(raw))
    }

    @Test
    fun cleanStripsSoftHyphenBomAndZeroWidthSpace() {
        val raw = "\uFEFFExam\u00ADple\u200B text"
        assertEquals("Example text", TextNormalizer.clean(raw))
    }

    @Test
    fun cleanPreservesZwjAndZwnjForBengali() {
        val bengaliWithZwjAndZwnj = "র\u200D্যাব এবং হস্\u200C"
        val cleaned = TextNormalizer.clean(bengaliWithZwjAndZwnj)
        assertTrue(cleaned.contains('\u200D'))
        assertTrue(cleaned.contains('\u200C'))
        assertEquals(bengaliWithZwjAndZwnj, cleaned)
    }

    @Test
    fun cleanAppliesNfcForDecomposedBengaliAndLatin() {
        val decomposed = "Cafe\u0301 ব\u09C7\u09BE ড\u09BC"
        val cleaned = TextNormalizer.clean(decomposed)
        assertEquals("Café বো ড়", cleaned)
    }

    @Test
    fun cleanCollapsesExtraSpacesAndBlankLines() {
        val raw = "  Hello\t  world  \n\n\n\n   Next\u00A0line   "
        assertEquals("Hello world\n\nNext line", TextNormalizer.clean(raw))
    }

    @Test
    fun forDisplayExpandsCommonLatinLigatures() {
        val raw = "\uFB01le \uFB02ow e\uFB00ect o\uFB03ce wa\uFB04e"
        assertEquals("file flow effect office waffle", TextNormalizer.forDisplay(raw))
    }

    @Test
    fun reflowJoinsWrappedEnglishLines() {
        val raw = "This is a sentence that wraps\nacross two lines cleanly."
        assertEquals(
            "This is a sentence that wraps across two lines cleanly.",
            TextNormalizer.reflow(raw),
        )
    }

    @Test
    fun reflowDehyphenatesWrappedEnglishWords() {
        val raw = "PDF docu-\nment extraction works."
        assertEquals("PDF document extraction works.", TextNormalizer.reflow(raw))
    }

    @Test
    fun reflowPreservesParagraphBreaks() {
        val raw = "First paragraph line one\nline two.\n\nSecond paragraph starts here."
        assertEquals(
            "First paragraph line one line two.\n\nSecond paragraph starts here.",
            TextNormalizer.reflow(raw),
        )
    }

    @Test
    fun reflowJoinsWrappedBengaliSentenceAndRespectsDandaParagraphBreak() {
        val wrapped = "আমার সোনার বাংলা\nআমি তোমা\u09AF\u09BC ভালোবাসি।"
        assertEquals("আমার সোনার বাংলা আমি তোমা\u09AF\u09BC ভালোবাসি।", TextNormalizer.reflow(wrapped))

        val shortLineWithDanda =
            "এই প্রথম অনুচ্ছেদটি এখানে শেষ হলো।\nএখন থেকে দ্বিতীয় অনুচ্ছেদটি শুরু হচ্ছে এবং লাইনটি তুলনামূলকভাবে বেশ বড়।"
        assertEquals(
            "এই প্রথম অনুচ্ছেদটি এখানে শেষ হলো।\n\nএখন থেকে দ্বিতী\u09AF\u09BC অনুচ্ছেদটি শুরু হচ্ছে এবং লাইনটি তুলনামূলকভাবে বেশ ব\u09A1\u09BC।",
            TextNormalizer.reflow(shortLineWithDanda),
        )
    }

    @Test
    fun reflowPreservesBulletListItems() {
        val raw = "Key points:\n• First item\n• Second item"
        assertEquals("Key points:\n• First item\n• Second item", TextNormalizer.reflow(raw))
    }
}
