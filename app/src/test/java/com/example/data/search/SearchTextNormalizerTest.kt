package com.example.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextNormalizerTest {

    @Test
    fun forIndex_stripsBengaliDandaAndPunctuation() {
        assertEquals(
            "আমার সোনার বাংলা আমি তোমায় ভালোবাসি",
            SearchTextNormalizer.forIndex("আমার সোনার বাংলা, আমি তোমায় ভালোবাসি।")
        )
    }

    @Test
    fun forIndex_dropsZwjAndZwnj() {
        val cleaned = SearchTextNormalizer.forIndex("র\u200D্য\u200C")
        assertFalse(cleaned.contains('\u200D'))
        assertFalse(cleaned.contains('\u200C'))
    }

    @Test
    fun forIndex_lowercasesAndRemovesPunctuation() {
        assertEquals("hello world", SearchTextNormalizer.forIndex("Hello, World!"))
    }

    @Test
    fun forIndex_replacesUnderscoreWithSpace() {
        assertEquals("a b", SearchTextNormalizer.forIndex("a_b"))
    }

    @Test
    fun forIndex_preservesBengaliDigits() {
        assertEquals("১২৩ test", SearchTextNormalizer.forIndex("১২৩ test"))
    }

    @Test
    fun forIndex_normalizesSplitBengaliVowelSignToNfc() {
        assertEquals(
            SearchTextNormalizer.forIndex("\u09CB"),
            SearchTextNormalizer.forIndex("\u09C7\u09BE")
        )
    }

    @Test
    fun forIndex_whitespaceOnlyReturnsEmpty() {
        assertEquals("", SearchTextNormalizer.forIndex("  \n\t "))
    }

    @Test
    fun buildMatchQuery_prefixesLastTokenInBengali() {
        assertEquals("সোনার বা*", SearchTextNormalizer.buildMatchQuery("সোনার বা"))
    }

    @Test
    fun buildMatchQuery_enforcesMinLength() {
        assertNull(SearchTextNormalizer.buildMatchQuery("a"))
        assertNull(SearchTextNormalizer.buildMatchQuery("  "))
        assertEquals("ab*", SearchTextNormalizer.buildMatchQuery("ab"))
    }

    @Test
    fun buildMatchQuery_capsAtEightTokens() {
        val result = SearchTextNormalizer.buildMatchQuery("a b c d e f g h i j")!!
        val tokens = result.split(' ')
        assertTrue(tokens.size <= 8)
        assertEquals("a b c d e f g h*", result)
    }

    @Test
    fun buildMatchQuery_lowercasesFtsOperators() {
        assertEquals("foo or bar*", SearchTextNormalizer.buildMatchQuery("foo OR bar"))
    }

    @Test
    fun buildMatchQuery_stripsFtsSyntaxCharacters() {
        val result = SearchTextNormalizer.buildMatchQuery("\"x\" -y (z)")!!
        assertFalse(result.contains('"'))
        assertFalse(result.contains('-'))
        assertFalse(result.contains('('))
        assertFalse(result.contains(')'))
        assertEquals("x y z*", result)
    }

    @Test
    fun pageRowId_mapsRoundTripAndBounds() {
        val rowId = PageRowId.of(7L, 42)
        assertEquals(7L, PageRowId.docOf(rowId))
        assertEquals(42, PageRowId.pageOf(rowId))

        val maxPageRow = PageRowId.of(7L, 999_999)
        assertTrue(PageRowId.firstOf(7L) <= maxPageRow && maxPageRow <= PageRowId.lastOf(7L))
        assertTrue(PageRowId.lastOf(7L) < PageRowId.firstOf(8L))
    }
}
