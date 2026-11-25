package com.example.ui.reader

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure Kotlin geometry for computing the visible zoomed sub-region of a PDF page
 * and its 1:1 screen-space overlay placement (outside the scaled graphicsLayer).
 * Keep zero Android / Compose imports so it can be unit-tested on the JVM.
 */
data class UnzoomedPageRect(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

data class ZoomRegionRequest(
    val pageIndex: Int,
    val zoomedPageWidthPx: Int,
    val zoomedPageHeightPx: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val overlayLeftPx: Float,
    val overlayTopPx: Float,
    val overlayWidthPx: Float,
    val overlayHeightPx: Float,
    val visibleAreaPx: Float
)

object ZoomRegion {

    /**
     * Fits a PDF image of [pageAspectRatio] (`width / height`) inside a container box
     * `(boxLeft, boxTop, boxWidth, boxHeight)`. Mirrors `ContentScale.FillWidth` vs `ContentScale.Fit`.
     */
    fun fittedContentRect(
        pageIndex: Int,
        boxLeft: Float,
        boxTop: Float,
        boxWidth: Float,
        boxHeight: Float,
        pageAspectRatio: Float,
        fillWidth: Boolean
    ): UnzoomedPageRect {
        if (fillWidth || boxWidth <= 0f || boxHeight <= 0f || pageAspectRatio <= 0f) {
            return UnzoomedPageRect(
                pageIndex = pageIndex,
                left = boxLeft,
                top = boxTop,
                width = boxWidth.coerceAtLeast(0f),
                height = boxHeight.coerceAtLeast(0f)
            )
        }
        val boxRatio = boxWidth / boxHeight
        return if (boxRatio <= pageAspectRatio) {
            val fittedH = boxWidth / pageAspectRatio
            val topOffset = (boxHeight - fittedH) / 2f
            UnzoomedPageRect(
                pageIndex = pageIndex,
                left = boxLeft,
                top = boxTop + topOffset,
                width = boxWidth,
                height = fittedH
            )
        } else {
            val fittedW = boxHeight * pageAspectRatio
            val leftOffset = (boxWidth - fittedW) / 2f
            UnzoomedPageRect(
                pageIndex = pageIndex,
                left = boxLeft + leftOffset,
                top = boxTop,
                width = fittedW,
                height = boxHeight
            )
        }
    }

    /**
     * Computes the sub-region bitmap parameters and 1:1 screen overlay rect for a single page
     * transformed by `graphicsLayer` with default `TransformOrigin.Center`.
     */
    fun compute(
        page: UnzoomedPageRect,
        viewportWidth: Float,
        viewportHeight: Float,
        zoomScale: Float,
        panX: Float,
        panY: Float,
        sharpThreshold: Float = 1.2f
    ): ZoomRegionRequest? {
        if (zoomScale <= sharpThreshold) return null
        if (page.width <= 0f || page.height <= 0f) return null
        if (viewportWidth <= 0f || viewportHeight <= 0f) return null

        val cx = viewportWidth / 2f
        val cy = viewportHeight / 2f

        val screenPageLeft = cx + (page.left - cx) * zoomScale + panX
        val screenPageTop = cy + (page.top - cy) * zoomScale + panY
        val screenPageWidth = page.width * zoomScale
        val screenPageHeight = page.height * zoomScale
        val screenPageRight = screenPageLeft + screenPageWidth
        val screenPageBottom = screenPageTop + screenPageHeight

        val visibleLeft = maxOf(0f, screenPageLeft)
        val visibleTop = maxOf(0f, screenPageTop)
        val visibleRight = minOf(viewportWidth, screenPageRight)
        val visibleBottom = minOf(viewportHeight, screenPageBottom)

        if (visibleRight <= visibleLeft + 1f || visibleBottom <= visibleTop + 1f) {
            return null
        }

        val zoomedPageWidthPx = screenPageWidth.roundToInt().coerceAtLeast(1)
        val zoomedPageHeightPx = screenPageHeight.roundToInt().coerceAtLeast(1)

        val left = floor(visibleLeft - screenPageLeft).toInt().coerceIn(0, zoomedPageWidthPx - 1)
        val top = floor(visibleTop - screenPageTop).toInt().coerceIn(0, zoomedPageHeightPx - 1)
        val right = ceil(visibleRight - screenPageLeft).toInt().coerceIn(left + 1, zoomedPageWidthPx)
        val bottom = ceil(visibleBottom - screenPageTop).toInt().coerceIn(top + 1, zoomedPageHeightPx)

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        return ZoomRegionRequest(
            pageIndex = page.pageIndex,
            zoomedPageWidthPx = zoomedPageWidthPx,
            zoomedPageHeightPx = zoomedPageHeightPx,
            left = left,
            top = top,
            width = width,
            height = height,
            overlayLeftPx = screenPageLeft + left,
            overlayTopPx = screenPageTop + top,
            overlayWidthPx = width.toFloat(),
            overlayHeightPx = height.toFloat(),
            visibleAreaPx = (visibleRight - visibleLeft) * (visibleBottom - visibleTop)
        )
    }

    /**
     * Picks up to [maxPages] visible page regions ordered by descending visible screen area.
     */
    fun pickTopRegions(
        pages: Collection<UnzoomedPageRect>,
        viewportWidth: Float,
        viewportHeight: Float,
        zoomScale: Float,
        panX: Float,
        panY: Float,
        sharpThreshold: Float = 1.2f,
        maxPages: Int = 2
    ): List<ZoomRegionRequest> {
        if (zoomScale <= sharpThreshold || maxPages <= 0) return emptyList()
        return pages.mapNotNull { page ->
            compute(
                page = page,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                zoomScale = zoomScale,
                panX = panX,
                panY = panY,
                sharpThreshold = sharpThreshold
            )
        }.sortedByDescending { it.visibleAreaPx }
            .take(maxPages)
    }
}
