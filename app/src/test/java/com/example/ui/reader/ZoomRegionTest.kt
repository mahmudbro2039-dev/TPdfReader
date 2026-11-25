package com.example.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomRegionTest {

    @Test
    fun `zoom 2x centered computes center crop of zoomed page`() {
        val page = UnzoomedPageRect(
            pageIndex = 0,
            left = 0f,
            top = 250f,
            width = 1000f,
            height = 1500f
        )

        val region = ZoomRegion.compute(
            page = page,
            viewportWidth = 1000f,
            viewportHeight = 2000f,
            zoomScale = 2f,
            panX = 0f,
            panY = 0f
        )

        assertNotNull(region)
        region!!
        assertEquals(2000, region.zoomedPageWidthPx)
        assertEquals(3000, region.zoomedPageHeightPx)
        assertEquals(500, region.left)
        assertEquals(500, region.top)
        assertEquals(1000, region.width)
        assertEquals(2000, region.height)
        assertEquals(0f, region.overlayLeftPx, 0.5f)
        assertEquals(0f, region.overlayTopPx, 0.5f)
    }

    @Test
    fun `zoom 4x panned to bottom-right corner renders exact bottom-right quadrant`() {
        val page = UnzoomedPageRect(
            pageIndex = 2,
            left = 0f,
            top = 0f,
            width = 1000f,
            height = 2000f
        )

        // At 4x zoom, maxPanX = 1000 * 3 / 2 = 1500, maxPanY = 2000 * 3 / 2 = 3000
        val region = ZoomRegion.compute(
            page = page,
            viewportWidth = 1000f,
            viewportHeight = 2000f,
            zoomScale = 4f,
            panX = -1500f,
            panY = -3000f
        )

        assertNotNull(region)
        region!!
        assertEquals(4000, region.zoomedPageWidthPx)
        assertEquals(8000, region.zoomedPageHeightPx)
        assertEquals(3000, region.left)
        assertEquals(6000, region.top)
        assertEquals(1000, region.width)
        assertEquals(2000, region.height)
        assertEquals(0f, region.overlayLeftPx, 0.5f)
        assertEquals(0f, region.overlayTopPx, 0.5f)
    }

    @Test
    fun `region is clipped by viewport and off-screen page returns null`() {
        val partiallyVisible = UnzoomedPageRect(
            pageIndex = 1,
            left = 0f,
            top = 1700f,
            width = 1000f,
            height = 1400f
        )

        // With zoom 2x centered around (500, 1000), screenPageTop = 1000 + (1700 - 1000)*2 = 2400 (> 2000) -> off screen!
        val offscreen = ZoomRegion.compute(
            page = partiallyVisible,
            viewportWidth = 1000f,
            viewportHeight = 2000f,
            zoomScale = 2f,
            panX = 0f,
            panY = 0f
        )
        assertNull(offscreen)

        // Now with page starting at top = 1200 -> screenPageTop = 1000 + 200*2 = 1400, so [1400..2000] is visible (600px tall)
        val clipped = ZoomRegion.compute(
            page = partiallyVisible.copy(top = 1200f),
            viewportWidth = 1000f,
            viewportHeight = 2000f,
            zoomScale = 2f,
            panX = 0f,
            panY = 0f
        )
        assertNotNull(clipped)
        clipped!!
        assertEquals(500, clipped.left)
        assertEquals(0, clipped.top)
        assertEquals(1000, clipped.width)
        assertEquals(600, clipped.height)
        assertEquals(1400f, clipped.overlayTopPx, 0.5f)
    }

    @Test
    fun `page smaller than viewport renders whole zoomed page at inset screen position`() {
        val smallPage = UnzoomedPageRect(
            pageIndex = 0,
            left = 200f,
            top = 500f,
            width = 600f,
            height = 800f
        )

        val region = ZoomRegion.compute(
            page = smallPage,
            viewportWidth = 1000f,
            viewportHeight = 2000f,
            zoomScale = 1.5f,
            panX = 0f,
            panY = 0f
        )

        assertNotNull(region)
        region!!
        assertEquals(900, region.zoomedPageWidthPx)
        assertEquals(1200, region.zoomedPageHeightPx)
        assertEquals(0, region.left)
        assertEquals(0, region.top)
        assertEquals(900, region.width)
        assertEquals(1200, region.height)
        assertEquals(50f, region.overlayLeftPx, 0.5f)
        assertEquals(250f, region.overlayTopPx, 0.5f)
    }

    @Test
    fun `pickTopRegions limits to max two largest visible pages and skips below threshold`() {
        val pages = listOf(
            UnzoomedPageRect(0, 0f, 0f, 1000f, 900f),
            UnzoomedPageRect(1, 0f, 920f, 1000f, 900f),
            UnzoomedPageRect(2, 0f, 1840f, 1000f, 900f)
        )

        val belowThreshold = ZoomRegion.pickTopRegions(
            pages = pages,
            viewportWidth = 1000f,
            viewportHeight = 2000f,
            zoomScale = 1.15f,
            panX = 0f,
            panY = 0f
        )
        assertEquals(0, belowThreshold.size)

        val picked = ZoomRegion.pickTopRegions(
            pages = pages,
            viewportWidth = 1000f,
            viewportHeight = 2000f,
            zoomScale = 1.5f,
            panX = 0f,
            panY = 0f,
            maxPages = 2
        )
        assertEquals(2, picked.size)
        assertEquals(1, picked[0].pageIndex) // centered page has largest visible area
    }
}
