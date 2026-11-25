package com.example.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentPagePickerTest {

    @Test
    fun pageThreeVisibleOnePixelAtTop_pageFourFillsScreen_returnsFour() {
        val spans = listOf(
            PageSpan(index = 3, start = -999, end = 1),
            PageSpan(index = 4, start = 17, end = 1217)
        )
        assertEquals(
            4,
            CurrentPagePicker.pick(
                spans = spans,
                viewportStart = 0,
                viewportEnd = 1000,
                totalPages = 10
            )
        )
    }

    @Test
    fun centerInsidePageFive_returnsFive() {
        val spans = listOf(
            PageSpan(index = 4, start = -400, end = 300),
            PageSpan(index = 5, start = 316, end = 1116)
        )
        assertEquals(
            5,
            CurrentPagePicker.pick(
                spans = spans,
                viewportStart = 0,
                viewportEnd = 1000,
                totalPages = 10
            )
        )
    }

    @Test
    fun topOfDocumentWithShortFirstPage_returnsZero() {
        val spans = listOf(
            PageSpan(index = 0, start = 0, end = 250),
            PageSpan(index = 1, start = 266, end = 1266)
        )
        assertEquals(
            0,
            CurrentPagePicker.pick(
                spans = spans,
                viewportStart = 0,
                viewportEnd = 1000,
                totalPages = 10
            )
        )
    }

    @Test
    fun bottomOfDocument_returnsLastPage() {
        val spans = listOf(
            PageSpan(index = 8, start = -200, end = 700),
            PageSpan(index = 9, start = 716, end = 980)
        )
        assertEquals(
            9,
            CurrentPagePicker.pick(
                spans = spans,
                viewportStart = 0,
                viewportEnd = 1000,
                totalPages = 10
            )
        )
    }

    @Test
    fun emptyList_returnsNull() {
        assertNull(
            CurrentPagePicker.pick(
                spans = emptyList(),
                viewportStart = 0,
                viewportEnd = 1000,
                totalPages = 10
            )
        )
    }

    @Test
    fun centerInGap_returnsPageWithMoreVisiblePixels() {
        val spans = listOf(
            PageSpan(index = 2, start = -300, end = 490),
            PageSpan(index = 3, start = 510, end = 1200)
        )
        // viewport = 0..1000 -> center = 500 (in gap 490..510)
        // page 2 visible = 490 - 0 = 490px; page 3 visible = 1000 - 510 = 490px.
        // Adjust so page 3 has 510px vs page 2 has 460px:
        val asymmetricSpans = listOf(
            PageSpan(index = 2, start = -400, end = 460),
            PageSpan(index = 3, start = 520, end = 1300)
        )
        // center = 500 (in gap 460..520); visible(2) = 460px, visible(3) = 480px -> 3 wins
        assertEquals(
            3,
            CurrentPagePicker.pick(
                spans = asymmetricSpans,
                viewportStart = 0,
                viewportEnd = 1000,
                totalPages = 10
            )
        )
    }
}
