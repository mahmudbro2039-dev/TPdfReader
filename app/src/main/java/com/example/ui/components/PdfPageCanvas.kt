package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.local.AnnotationEntity
import com.example.data.model.AnnotationType
import com.example.data.model.PageFitPolicy
import com.example.data.model.RenderQuality
import com.example.data.model.ScrollLayoutMode
import com.example.data.pdf.PdfPageDimension
import com.example.ui.reader.CurrentPagePicker
import com.example.ui.reader.PageSpan
import com.example.ui.reader.UnzoomedPageRect
import com.example.ui.reader.ZoomRegion
import com.example.ui.reader.ZoomRegionRequest
import com.example.ui.theme.Alpha
import com.example.ui.theme.ReaderZoomTokens
import com.example.ui.theme.TeenaTheme
import com.example.ui.viewmodel.ReaderUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private data class SharpTriggerSnapshot(
    val zoomScale: Float,
    val panX: Float,
    val panY: Float,
    val currentPage: Int,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val pages: List<UnzoomedPageRect>
)

private data class RenderedSharpTile(
    val region: ZoomRegionRequest,
    val bitmap: Bitmap
)

@Composable
fun PdfDocumentViewport(
    state: ReaderUiState,
    pageGapSpacing: Boolean,
    renderQuality: RenderQuality = RenderQuality.STANDARD,
    colorFilter: ColorFilter?,
    canvasColor: Color,
    peekPage: (pageIndex: Int, targetWidthPx: Int) -> Bitmap? = { _, _ -> null },
    renderPage: suspend (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    renderRegion: suspend (pageIndex: Int, zoomedPageWidthPx: Int, left: Int, top: Int, outWidth: Int, outHeight: Int) -> Bitmap? = { _, _, _, _, _, _ -> null },
    onPageChanged: (Int) -> Unit,
    onPageIndicatorChanged: (Int) -> Unit = onPageChanged,
    onPageSettled: (Int) -> Unit = onPageChanged,
    onPendingScrollConsumed: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleChrome: () -> Unit,
    onStepPage: (Int) -> Unit,
    onStopAutoScroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var panOffset by remember(state.uriString, state.layoutMode) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(state.zoomScale) {
        if (state.zoomScale <= 1.02f) {
            panOffset = Offset.Zero
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(canvasColor)
            .clipToBounds()
            .pointerInput(state.isZoomLocked, state.zoomScale, state.layoutMode, state.isRtl) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        if (state.layoutMode == ScrollLayoutMode.SINGLE_PAGE && state.zoomScale <= 1.05f) {
                            val leftZone = size.width * 0.22f
                            val rightZone = size.width * 0.78f
                            when {
                                tapOffset.x < leftZone -> {
                                    onStepPage(if (state.isRtl) 1 else -1)
                                }
                                tapOffset.x > rightZone -> {
                                    onStepPage(if (state.isRtl) -1 else 1)
                                }
                                else -> onToggleChrome()
                            }
                        } else {
                            onToggleChrome()
                        }
                    },
                    onDoubleTap = {
                        if (!state.isZoomLocked) {
                            val nextZoom = when {
                                state.zoomScale < 1.5f -> 2.0f
                                state.zoomScale < 2.6f -> 3.0f
                                else -> 1.0f
                            }
                            onZoomChange(nextZoom)
                        }
                    }
                )
            }
            .pointerInput(state.isZoomLocked, state.zoomScale, state.layoutMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!state.isZoomLocked && kotlin.math.abs(zoom - 1f) > 0.01f) {
                        val updatedZoom = (state.zoomScale * zoom)
                            .coerceIn(ReaderZoomTokens.ZoomMin, ReaderZoomTokens.ZoomMax)
                        onZoomChange(updatedZoom)
                    }
                    if (state.zoomScale > 1.02f) {
                        val maxPanX = (size.width * (state.zoomScale - 1f)) / 2f
                        val maxPanY = (size.height * (state.zoomScale - 1f)) / 2f
                        val nextX = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                        val nextY = if (state.layoutMode == ScrollLayoutMode.VERTICAL) {
                            0f
                        } else {
                            (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        }
                        panOffset = Offset(nextX, nextY)
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        val viewportWidthFloat = with(density) { maxWidth.toPx() }
        val viewportHeightFloat = with(density) { maxHeight.toPx() }
        val viewportWidthPx = viewportWidthFloat.toInt().coerceAtLeast(360)

        val visiblePageBounds = remember(state.uriString, state.layoutMode, state.pageFitPolicy, state.hideMargins) {
            mutableStateMapOf<Int, UnzoomedPageRect>()
        }
        var zoomContentCoords by remember(state.uriString, state.layoutMode) {
            mutableStateOf<LayoutCoordinates?>(null)
        }
        var sharpTiles by remember(state.uriString, state.layoutMode, state.pageFitPolicy, state.hideMargins) {
            mutableStateOf<List<RenderedSharpTile>>(emptyList())
        }

        val currentZoomState = rememberUpdatedState(state.zoomScale)
        val currentPageState = rememberUpdatedState(state.currentPage)
        val currentViewportWState = rememberUpdatedState(viewportWidthFloat)
        val currentViewportHState = rememberUpdatedState(viewportHeightFloat)
        val currentRenderRegion by rememberUpdatedState(renderRegion)

        LaunchedEffect(state.uriString, state.layoutMode, state.pageFitPolicy, state.hideMargins) {
            snapshotFlow {
                SharpTriggerSnapshot(
                    zoomScale = currentZoomState.value,
                    panX = panOffset.x,
                    panY = panOffset.y,
                    currentPage = currentPageState.value,
                    viewportWidth = currentViewportWState.value,
                    viewportHeight = currentViewportHState.value,
                    pages = visiblePageBounds.values.toList()
                )
            }.collectLatest { snap ->
                sharpTiles = emptyList()
                if (state.hideMargins ||
                    snap.zoomScale <= ReaderZoomTokens.SharpThreshold ||
                    snap.pages.isEmpty()
                ) {
                    return@collectLatest
                }

                delay(ReaderZoomTokens.SharpSettleMs)

                val requests = ZoomRegion.pickTopRegions(
                    pages = snap.pages,
                    viewportWidth = snap.viewportWidth,
                    viewportHeight = snap.viewportHeight,
                    zoomScale = snap.zoomScale,
                    panX = snap.panX,
                    panY = snap.panY,
                    sharpThreshold = ReaderZoomTokens.SharpThreshold,
                    maxPages = ReaderZoomTokens.MaxSharpPages
                )
                if (requests.isEmpty()) return@collectLatest

                val rendered = ArrayList<RenderedSharpTile>(requests.size)
                for (req in requests) {
                    val bmp = currentRenderRegion(
                        req.pageIndex,
                        req.zoomedPageWidthPx,
                        req.left,
                        req.top,
                        req.width,
                        req.height
                    )
                    if (bmp != null && !bmp.isRecycled) {
                        rendered.add(RenderedSharpTile(region = req, bitmap = bmp))
                    }
                }
                sharpTiles = rendered
            }
        }

        val zoomModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = state.zoomScale
                scaleY = state.zoomScale
                translationX = panOffset.x
                translationY = panOffset.y
            }

        if (state.pageCount <= 0) return@BoxWithConstraints

        val onReportPageBounds: (Int, UnzoomedPageRect) -> Unit = remember(visiblePageBounds) {
            { pageIdx, rect ->
                if (visiblePageBounds[pageIdx] != rect) {
                    visiblePageBounds[pageIdx] = rect
                }
            }
        }
        val onDisposePageBounds: (Int) -> Unit = remember(visiblePageBounds) {
            { pageIdx ->
                visiblePageBounds.remove(pageIdx)
            }
        }

        key(state.uriString) {
            Box(modifier = zoomModifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { zoomContentCoords = it }
                ) {
                    when (state.layoutMode) {
                        ScrollLayoutMode.VERTICAL -> {
                            VerticalPdfViewer(
                                state = state,
                                pageGapSpacing = pageGapSpacing,
                                renderQuality = renderQuality,
                                viewportWidthPx = viewportWidthPx,
                                colorFilter = colorFilter,
                                zoomContentCoords = zoomContentCoords,
                                peekPage = peekPage,
                                renderPage = renderPage,
                                onPageChanged = onPageChanged,
                                onPendingScrollConsumed = onPendingScrollConsumed,
                                onStopAutoScroll = onStopAutoScroll,
                                onReportPageBounds = onReportPageBounds,
                                onDisposePageBounds = onDisposePageBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        ScrollLayoutMode.HORIZONTAL, ScrollLayoutMode.SINGLE_PAGE -> {
                            HorizontalPdfViewer(
                                state = state,
                                renderQuality = renderQuality,
                                viewportWidthPx = viewportWidthPx,
                                colorFilter = colorFilter,
                                zoomContentCoords = zoomContentCoords,
                                peekPage = peekPage,
                                renderPage = renderPage,
                                onPageIndicatorChanged = onPageIndicatorChanged,
                                onPageSettled = onPageSettled,
                                onPendingScrollConsumed = onPendingScrollConsumed,
                                onStopAutoScroll = onStopAutoScroll,
                                onReportPageBounds = onReportPageBounds,
                                onDisposePageBounds = onDisposePageBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        ScrollLayoutMode.DUAL_PAGE -> {
                            DualPagePdfViewer(
                                state = state,
                                renderQuality = renderQuality,
                                viewportWidthPx = (viewportWidthPx / 2).coerceAtLeast(360),
                                colorFilter = colorFilter,
                                zoomContentCoords = zoomContentCoords,
                                peekPage = peekPage,
                                renderPage = renderPage,
                                onPageIndicatorChanged = onPageIndicatorChanged,
                                onPageSettled = onPageSettled,
                                onPendingScrollConsumed = onPendingScrollConsumed,
                                onStopAutoScroll = onStopAutoScroll,
                                onReportPageBounds = onReportPageBounds,
                                onDisposePageBounds = onDisposePageBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        sharpTiles.forEach { tile ->
            if (!tile.bitmap.isRecycled) {
                val widthDp = with(density) { tile.region.overlayWidthPx.toDp() }
                val heightDp = with(density) { tile.region.overlayHeightPx.toDp() }
                Image(
                    bitmap = tile.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = tile.region.overlayLeftPx
                            translationY = tile.region.overlayTopPx
                        }
                        .size(width = widthDp, height = heightDp)
                )
            }
        }
    }
}

@Composable
private fun VerticalPdfViewer(
    state: ReaderUiState,
    pageGapSpacing: Boolean,
    renderQuality: RenderQuality,
    viewportWidthPx: Int,
    colorFilter: ColorFilter?,
    zoomContentCoords: LayoutCoordinates?,
    peekPage: (Int, Int) -> Bitmap?,
    renderPage: suspend (Int, Int) -> Bitmap?,
    onPageChanged: (Int) -> Unit,
    onPendingScrollConsumed: () -> Unit,
    onStopAutoScroll: () -> Unit,
    onReportPageBounds: (Int, UnzoomedPageRect) -> Unit,
    onDisposePageBounds: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)

    LaunchedEffect(state.pendingScrollTargetPage) {
        val target = state.pendingScrollTargetPage ?: return@LaunchedEffect
        if (target in 0 until state.pageCount && listState.firstVisibleItemIndex != target) {
            listState.scrollToItem(target)
        }
        onPendingScrollConsumed()
    }

    LaunchedEffect(listState, state.pageCount) {
        snapshotFlow {
            val info = listState.layoutInfo
            val spans = info.visibleItemsInfo.map { item ->
                PageSpan(
                    index = item.index,
                    start = item.offset,
                    end = item.offset + item.size
                )
            }
            CurrentPagePicker.pick(
                spans = spans,
                viewportStart = info.viewportStartOffset,
                viewportEnd = info.viewportEndOffset,
                totalPages = state.pageCount
            )
        }
            .distinctUntilChanged()
            .collect { pickedPage ->
                if (pickedPage != null) {
                    onPageChanged(pickedPage)
                }
            }
    }

    LaunchedEffect(state.isAutoScrollActive, state.autoScrollSpeed) {
        if (!state.isAutoScrollActive) return@LaunchedEffect
        val pixelsPerTick = 1.4f + (state.autoScrollSpeed * 1.15f)
        while (state.isAutoScrollActive) {
            val consumed = listState.scrollBy(pixelsPerTick)
            if (consumed < 0.1f && !listState.canScrollForward) {
                onStopAutoScroll()
                break
            }
            delay(AUTO_SCROLL_FRAME_MS)
        }
    }

    val annotationsByPage = remember(state.annotations) {
        state.annotations.groupBy { it.pageIndex }
    }
    val bookmarkedPages = remember(state.bookmarks) {
        state.bookmarks.map { it.pageIndex }.toSet()
    }
    val effectiveTargetWidthPx = remember(viewportWidthPx, state.pageFitPolicy) {
        when (state.pageFitPolicy) {
            PageFitPolicy.FIT_WIDTH -> viewportWidthPx
            PageFitPolicy.FIT_HEIGHT -> (viewportWidthPx * 0.9f).toInt().coerceAtLeast(360)
            PageFitPolicy.FIT_PAGE -> (viewportWidthPx * 0.84f).toInt().coerceAtLeast(360)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            horizontal = if (pageGapSpacing && !state.hideMargins) s.sm else s.none,
            vertical = if (pageGapSpacing) s.md else s.none
        ),
        verticalArrangement = Arrangement.spacedBy(if (pageGapSpacing) s.md else s.none),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        items(
            count = state.pageCount,
            key = { it },
            contentType = { "pdf_page" }
        ) { pageIndex ->
            val dim = state.pageDimensions.getOrElse(pageIndex) { PdfPageDimension(595, 842) }
            val pageAnnotations = annotationsByPage[pageIndex].orEmpty()
            val isBookmarked = pageIndex in bookmarkedPages

            PdfPageSurface(
                uriString = state.uriString,
                pageIndex = pageIndex,
                dimension = dim,
                fitPolicy = state.pageFitPolicy,
                hideMargins = state.hideMargins,
                renderQuality = renderQuality,
                isBookmarked = isBookmarked,
                pageAnnotations = pageAnnotations,
                targetWidthPx = effectiveTargetWidthPx,
                colorFilter = colorFilter,
                zoomContentCoords = zoomContentCoords,
                peekPage = peekPage,
                renderPage = renderPage,
                onReportBounds = onReportPageBounds,
                onDisposeBounds = onDisposePageBounds,
                modifier = when (state.pageFitPolicy) {
                    PageFitPolicy.FIT_WIDTH -> Modifier.fillMaxWidth()
                    PageFitPolicy.FIT_HEIGHT -> Modifier.fillMaxWidth(0.9f)
                    PageFitPolicy.FIT_PAGE -> Modifier.fillMaxWidth(0.84f)
                }
            )
        }
    }
}

@Composable
private fun HorizontalPdfViewer(
    state: ReaderUiState,
    renderQuality: RenderQuality,
    viewportWidthPx: Int,
    colorFilter: ColorFilter?,
    zoomContentCoords: LayoutCoordinates?,
    peekPage: (Int, Int) -> Bitmap?,
    renderPage: suspend (Int, Int) -> Bitmap?,
    onPageIndicatorChanged: (Int) -> Unit,
    onPageSettled: (Int) -> Unit,
    onPendingScrollConsumed: () -> Unit,
    onStopAutoScroll: () -> Unit,
    onReportPageBounds: (Int, UnzoomedPageRect) -> Unit,
    onDisposePageBounds: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val pagerState = rememberPagerState(
        initialPage = state.currentPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)),
        pageCount = { state.pageCount.coerceAtLeast(1) }
    )

    LaunchedEffect(state.pendingScrollTargetPage) {
        val target = state.pendingScrollTargetPage ?: return@LaunchedEffect
        if (target in 0 until state.pageCount && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
        onPendingScrollConsumed()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onPageIndicatorChanged(page) }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled -> onPageSettled(settled) }
    }

    LaunchedEffect(state.isAutoScrollActive, state.autoScrollSpeed) {
        if (!state.isAutoScrollActive) return@LaunchedEffect
        val intervalMs = (11_500L - state.autoScrollSpeed * 950L).coerceAtLeast(1_500L)
        while (state.isAutoScrollActive) {
            delay(intervalMs)
            val next = pagerState.currentPage + 1
            if (next < state.pageCount) {
                pagerState.animateScrollToPage(next)
            } else {
                onStopAutoScroll()
                break
            }
        }
    }

    val annotationsByPage = remember(state.annotations) {
        state.annotations.groupBy { it.pageIndex }
    }
    val bookmarkedPages = remember(state.bookmarks) {
        state.bookmarks.map { it.pageIndex }.toSet()
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = state.zoomScale <= 1.05f,
        reverseLayout = state.isRtl,
        beyondViewportPageCount = 1,
        modifier = modifier
    ) { pageIndex ->
        val dim = state.pageDimensions.getOrElse(pageIndex) { PdfPageDimension(595, 842) }
        val pageAnnotations = annotationsByPage[pageIndex].orEmpty()
        val isBookmarked = pageIndex in bookmarkedPages

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (state.hideMargins) s.xxs else s.sm,
                    vertical = if (state.hideMargins) s.xs else s.md
                )
        ) {
            PdfPageSurface(
                uriString = state.uriString,
                pageIndex = pageIndex,
                dimension = dim,
                fitPolicy = state.pageFitPolicy,
                hideMargins = state.hideMargins,
                renderQuality = renderQuality,
                isBookmarked = isBookmarked,
                pageAnnotations = pageAnnotations,
                targetWidthPx = viewportWidthPx,
                colorFilter = colorFilter,
                zoomContentCoords = zoomContentCoords,
                peekPage = peekPage,
                renderPage = renderPage,
                onReportBounds = onReportPageBounds,
                onDisposeBounds = onDisposePageBounds,
                modifier = when (state.pageFitPolicy) {
                    PageFitPolicy.FIT_WIDTH -> Modifier.fillMaxWidth()
                    PageFitPolicy.FIT_HEIGHT -> Modifier.fillMaxHeight()
                    PageFitPolicy.FIT_PAGE -> Modifier.fillMaxSize(0.95f)
                }
            )
        }
    }
}

@Composable
private fun DualPagePdfViewer(
    state: ReaderUiState,
    renderQuality: RenderQuality,
    viewportWidthPx: Int,
    colorFilter: ColorFilter?,
    zoomContentCoords: LayoutCoordinates?,
    peekPage: (Int, Int) -> Bitmap?,
    renderPage: suspend (Int, Int) -> Bitmap?,
    onPageIndicatorChanged: (Int) -> Unit,
    onPageSettled: (Int) -> Unit,
    onPendingScrollConsumed: () -> Unit,
    onStopAutoScroll: () -> Unit,
    onReportPageBounds: (Int, UnzoomedPageRect) -> Unit,
    onDisposePageBounds: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val spreadCount = ((state.pageCount + 1) / 2).coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = (state.currentPage / 2).coerceIn(0, spreadCount - 1),
        pageCount = { spreadCount }
    )

    LaunchedEffect(state.pendingScrollTargetPage) {
        val target = state.pendingScrollTargetPage ?: return@LaunchedEffect
        val spreadIndex = (target / 2).coerceIn(0, spreadCount - 1)
        if (pagerState.currentPage != spreadIndex) {
            pagerState.scrollToPage(spreadIndex)
        }
        onPendingScrollConsumed()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { spreadIndex ->
                onPageIndicatorChanged((spreadIndex * 2).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)))
            }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { spreadIndex ->
                onPageSettled((spreadIndex * 2).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)))
            }
    }

    LaunchedEffect(state.isAutoScrollActive, state.autoScrollSpeed) {
        if (!state.isAutoScrollActive) return@LaunchedEffect
        val intervalMs = (14_000L - state.autoScrollSpeed * 1_100L).coerceAtLeast(2_000L)
        while (state.isAutoScrollActive) {
            delay(intervalMs)
            val next = pagerState.currentPage + 1
            if (next < spreadCount) {
                pagerState.animateScrollToPage(next)
            } else {
                onStopAutoScroll()
                break
            }
        }
    }

    val annotationsByPage = remember(state.annotations) {
        state.annotations.groupBy { it.pageIndex }
    }
    val bookmarkedPages = remember(state.bookmarks) {
        state.bookmarks.map { it.pageIndex }.toSet()
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = state.zoomScale <= 1.05f,
        reverseLayout = state.isRtl,
        beyondViewportPageCount = 1,
        modifier = modifier
    ) { spreadIndex ->
        val firstPage = spreadIndex * 2
        val secondPage = firstPage + 1
        val leftPage = if (state.isRtl) secondPage else firstPage
        val rightPage = if (state.isRtl) firstPage else secondPage

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.xs, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = s.sm, vertical = s.md)
        ) {
            if (leftPage < state.pageCount) {
                val dim = state.pageDimensions.getOrElse(leftPage) { PdfPageDimension(595, 842) }
                PdfPageSurface(
                    uriString = state.uriString,
                    pageIndex = leftPage,
                    dimension = dim,
                    fitPolicy = state.pageFitPolicy,
                    hideMargins = state.hideMargins,
                    renderQuality = renderQuality,
                    isBookmarked = leftPage in bookmarkedPages,
                    pageAnnotations = annotationsByPage[leftPage].orEmpty(),
                    targetWidthPx = viewportWidthPx,
                    colorFilter = colorFilter,
                    zoomContentCoords = zoomContentCoords,
                    peekPage = peekPage,
                    renderPage = renderPage,
                    onReportBounds = onReportPageBounds,
                    onDisposeBounds = onDisposePageBounds,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxHeight())
            }

            if (rightPage < state.pageCount) {
                val dim = state.pageDimensions.getOrElse(rightPage) { PdfPageDimension(595, 842) }
                PdfPageSurface(
                    uriString = state.uriString,
                    pageIndex = rightPage,
                    dimension = dim,
                    fitPolicy = state.pageFitPolicy,
                    hideMargins = state.hideMargins,
                    renderQuality = renderQuality,
                    isBookmarked = rightPage in bookmarkedPages,
                    pageAnnotations = annotationsByPage[rightPage].orEmpty(),
                    targetWidthPx = viewportWidthPx,
                    colorFilter = colorFilter,
                    zoomContentCoords = zoomContentCoords,
                    peekPage = peekPage,
                    renderPage = renderPage,
                    onReportBounds = onReportPageBounds,
                    onDisposeBounds = onDisposePageBounds,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun PdfPageSurface(
    uriString: String,
    pageIndex: Int,
    dimension: PdfPageDimension,
    fitPolicy: PageFitPolicy,
    hideMargins: Boolean,
    renderQuality: RenderQuality,
    isBookmarked: Boolean,
    pageAnnotations: List<AnnotationEntity>,
    targetWidthPx: Int,
    colorFilter: ColorFilter?,
    zoomContentCoords: LayoutCoordinates?,
    peekPage: (Int, Int) -> Bitmap?,
    renderPage: suspend (Int, Int) -> Bitmap?,
    onReportBounds: (Int, UnzoomedPageRect) -> Unit,
    onDisposeBounds: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val readerColors = TeenaTheme.colors.reader

    var bitmap by remember(uriString, pageIndex) {
        val initial = peekPage(pageIndex, targetWidthPx)
            ?: if (pageIndex == 0 && !hideMargins) ThumbnailCache.peek(uriString) else null
        mutableStateOf(initial)
    }

    var showPageSpinner by remember(uriString, pageIndex) {
        mutableStateOf(false)
    }

    LaunchedEffect(uriString, pageIndex, targetWidthPx, hideMargins, renderQuality) {
        val instant = peekPage(pageIndex, targetWidthPx)
        if (instant != null) {
            bitmap = instant
        } else {
            val renderedPage = renderPage(pageIndex, targetWidthPx)
            if (renderedPage != null) {
                bitmap = renderedPage
            }
        }
    }

    LaunchedEffect(uriString, pageIndex, bitmap == null) {
        if (bitmap == null) {
            delay(SPINNER_REVEAL_DELAY_MS)
            showPageSpinner = true
        } else {
            showPageSpinner = false
        }
    }

    DisposableEffect(uriString, pageIndex) {
        onDispose {
            onDisposeBounds(pageIndex)
        }
    }

    val rendered = bitmap
    val dynamicAspectRatio = if (rendered != null && rendered.height > 0) {
        (rendered.width.toFloat() / rendered.height.toFloat()).coerceIn(0.45f, 2.2f)
    } else {
        dimension.aspectRatio.coerceIn(0.45f, 2.2f)
    }

    val contentScale = when (fitPolicy) {
        PageFitPolicy.FIT_WIDTH -> ContentScale.FillWidth
        PageFitPolicy.FIT_HEIGHT, PageFitPolicy.FIT_PAGE -> ContentScale.Fit
    }

    Surface(
        color = readerColors.pagePaper,
        shadowElevation = sz.cardElevation,
        modifier = modifier
            .aspectRatio(dynamicAspectRatio)
            .onGloballyPositioned { pageCoords ->
                val root = zoomContentCoords ?: return@onGloballyPositioned
                if (!root.isAttached || !pageCoords.isAttached) return@onGloballyPositioned
                val rect = root.localBoundingBoxOf(pageCoords, clipBounds = false)
                val fitted = ZoomRegion.fittedContentRect(
                    pageIndex = pageIndex,
                    boxLeft = rect.left,
                    boxTop = rect.top,
                    boxWidth = rect.width,
                    boxHeight = rect.height,
                    pageAspectRatio = dynamicAspectRatio,
                    fillWidth = fitPolicy == PageFitPolicy.FIT_WIDTH
                )
                onReportBounds(pageIndex, fitted)
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (rendered != null && !rendered.isRecycled) {
                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = stringResource(R.string.reader_page_content_desc, pageIndex + 1),
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (showPageSpinner) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ColumnPagePlaceholder(pageNumber = pageIndex + 1)
                }
            }

            if (isBookmarked) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(R.string.reader_bookmarked_page_desc),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = s.md)
                        .size(sz.bookmarkRibbon)
                )
            }

            val signatures = pageAnnotations.filter {
                it.annotationType == AnnotationType.SIGNATURE && it.signatureVectorData.isNotBlank()
            }
            val highlightsAndNotes = pageAnnotations.filter {
                it.annotationType != AnnotationType.SIGNATURE
            }

            if (highlightsAndNotes.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(s.xs),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(s.md)
                ) {
                    highlightsAndNotes.take(2).forEach { ann ->
                        val swatch = parseHexColor(ann.colorHex, readerColors.highlightDefault)
                        Surface(
                            color = swatch.copy(alpha = Alpha.high),
                            shape = MaterialTheme.shapes.extraSmall,
                            shadowElevation = sz.hairline
                        ) {
                            Text(
                                text = ann.noteText.ifBlank { ann.quotedText }.take(42),
                                style = MaterialTheme.typography.labelSmall,
                                color = readerColors.textLightFg,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = s.sm, vertical = s.xs)
                            )
                        }
                    }
                }
            }

            if (signatures.isNotEmpty()) {
                val latestSig = signatures.first()
                Surface(
                    color = readerColors.pagePaper.copy(alpha = Alpha.stampSurface),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(s.lg)
                        .border(
                            width = sz.hairline,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = Alpha.borderMuted),
                            shape = MaterialTheme.shapes.small
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = s.md, vertical = s.xs)
                    ) {
                        VectorSignaturePreview(
                            signatureVectorData = latestSig.signatureVectorData,
                            strokeColor = MaterialTheme.colorScheme.primary,
                            strokeWidth = sz.strokeWidth,
                            modifier = Modifier
                                .width(sz.signatureStampWidth)
                                .height(sz.signatureStampHeight)
                        )
                        Spacer(modifier = Modifier.height(s.xxs))
                        Text(
                            text = latestSig.quotedText.ifBlank {
                                stringResource(R.string.reader_signature_signed)
                            }.take(22),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnPagePlaceholder(pageNumber: Int) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = TeenaTheme.sizes.borderThick)
        Text(
            text = pageNumber.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
