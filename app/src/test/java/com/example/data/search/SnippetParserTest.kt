package com.example.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetParserTest {

    @Test
    fun parsesHighlightedSpanBetweenMarkers() {
        val result = parseSnippet("a${SNIPPET_OPEN}b${SNIPPET_CLOSE}c")
        assertEquals(
            listOf(
                "a" to false,
                "b" to true,
                "c" to false
            ),
            result
        )
    }

    @Test
    fun returnsSingleChunkWhenNoMarkersPresent() {
        val result = parseSnippet("plain snippet text")
        assertEquals(listOf("plain snippet text" to false), result)
    }

    @Test
    fun unbalancedMarkersDoNotCrash() {
        val onlyOpen = parseSnippet("prefix${SNIPPET_OPEN}tail")
        assertEquals(
            listOf("prefix" to false, "tail" to true),
            onlyOpen
        )

        val onlyClose = parseSnippet("prefix${SNIPPET_CLOSE}tail")
        assertEquals(
            listOf("prefix" to false, "tail" to false),
            onlyClose
        )

        val trailingMarkers = parseSnippet("${SNIPPET_OPEN}${SNIPPET_CLOSE}")
        assertTrue(trailingMarkers.isEmpty())
    }

    @Test
    fun emptyInputReturnsEmptyList() {
        assertTrue(parseSnippet("").isEmpty())
    }
}
