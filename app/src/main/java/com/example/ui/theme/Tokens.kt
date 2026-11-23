package com.example.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val screenH: Dp = 16.dp,
    val badgePad: Dp = 6.dp,
    val gridGapH: Dp = 12.dp,
    val gridGapV: Dp = 16.dp,
    val listBottom: Dp = 24.dp,
    val readerBarClearance: Dp = 136.dp
)

@Immutable
data class Sizes(
    val topBarHeight: Dp = 54.dp,
    val searchHeight: Dp = 38.dp,
    val searchButton: Dp = 30.dp,
    val heroPlayButton: Dp = 36.dp,
    val searchIcon: Dp = 16.dp,
    val iconButton: Dp = 48.dp,           // min touch target
    val hairline: Dp = 1.dp,
    val borderThick: Dp = 2.dp,
    val progressHeight: Dp = 4.dp,
    val compactProgressHeight: Dp = 3.dp,
    val thumbWidth: Dp = 60.dp,           // height = width * 1.414 (A4)
    val compactThumbWidth: Dp = 44.dp,
    val heroThumbWidth: Dp = 56.dp,
    val bottomBarHeight: Dp = 62.dp,
    val bottomBarMaxWidth: Dp = 420.dp,
    val bottomNavIcon: Dp = 20.dp,
    val folderIconBox: Dp = 48.dp,
    val iconXs: Dp = 14.dp,
    val iconSm: Dp = 16.dp,
    val iconMd: Dp = 24.dp,
    val iconLg: Dp = 32.dp,
    val emptyCircle: Dp = 72.dp,
    val gridMinCell: Dp = 96.dp,          // 3 columns on 360dp+ phones, 2 on 320dp, more on tablets
    val coverRatio: Float = 3f / 4f,      // width / height
    val folderGridMinCell: Dp = 148.dp,   // 2 columns on phones, 3+ on tablets
    val folderPreviewRatio: Float = 16f / 10f,
    val folderMiniCoverWidth: Dp = 54.dp,
    val folderMiniCoverOffsetX: Dp = 18.dp,
    val folderMiniCoverOffsetY: Dp = 4.dp,
    val progressBadge: Dp = 34.dp,
    val ringStroke: Dp = 2.5.dp,
    val ringInset: Dp = 3.dp,
    val badgeElevation: Dp = 3.dp,
    val coverMenuHit: Dp = 48.dp,         // minimum accessible touch target
    val coverMenuVisual: Dp = 26.dp,      // visible scrim circle
    val coverMenuIcon: Dp = 16.dp,
    val zeroElevation: Dp = 0.dp,
    val cardElevation: Dp = 2.dp,
    val fabElevation: Dp = 4.dp,
    val heroElevation: Dp = 6.dp,
    val menuElevation: Dp = 8.dp,
    val fadeEdgeWidth: Dp = 32.dp,
    val shimmerTitleHeight: Dp = 18.dp,
    val shimmerMetaHeight: Dp = 14.dp,
    val shimmerHeroHeight: Dp = 112.dp,
    val shimmerRowHeight: Dp = 104.dp,
    val sheetListMaxHeight: Dp = 380.dp,
    val signaturePadHeight: Dp = 180.dp,
    val signaturePreviewHeight: Dp = 56.dp,
    val signatureStampWidth: Dp = 120.dp,
    val signatureStampHeight: Dp = 44.dp,
    val swatchSize: Dp = 36.dp,
    val pageMinHeight: Dp = 320.dp,
    val shortcutPillSize: Dp = 44.dp,
    val strokeWidth: Dp = 2.5.dp,
    val strokeWidthBold: Dp = 3.dp,
    val quotePreviewMaxHeight: Dp = 340.dp,
    val quotePreviewHeight: Dp = 230.dp,
    val toolTileHeight: Dp = 68.dp,
    val iconTool: Dp = 20.dp,
    val bookmarkRibbon: Dp = 28.dp,
    val linkIconBox: Dp = 40.dp,
    val dialogMaxWidth: Dp = 600.dp,
    val colorDotSize: Dp = 12.dp,
    val statusDotSize: Dp = 8.dp,
    val edgeHandleWidth: Dp = 26.dp,
    val edgeHandleHeight: Dp = 56.dp,
    val edgeSwipeStripWidth: Dp = 28.dp,
    val fullscreenBadgeSize: Dp = 36.dp,
    val quickMenuTileSize: Dp = 44.dp
)

@Immutable
data class Motion(
    val snap: Int = 85,
    val fast: Int = 150,
    val normal: Int = 250,
    val slow: Int = 450,
    val shimmer: Int = 900,
    val tabEnter: Int = 150,
    val tabExit: Int = 100,
    val tabSlide: Int = 160,
    val searchDebounceMs: Long = 300L
) {
    fun <T> snapSpec(): FiniteAnimationSpec<T> = tween(durationMillis = snap)
    fun <T> fastSpec(): FiniteAnimationSpec<T> = tween(durationMillis = fast)
    fun <T> normalSpec(): FiniteAnimationSpec<T> = tween(durationMillis = normal)
    fun <T> slowSpec(): FiniteAnimationSpec<T> = tween(durationMillis = slow)
    fun navFadeSpec(): FiniteAnimationSpec<Float> = tween(durationMillis = snap, easing = FastOutSlowInEasing)
    fun navSlideSpec(): FiniteAnimationSpec<IntOffset> = tween(durationMillis = normal, easing = FastOutSlowInEasing)
    fun <T> tabEnterSpec(): FiniteAnimationSpec<T> = tween(durationMillis = tabEnter, easing = FastOutSlowInEasing)
    fun <T> tabExitSpec(): FiniteAnimationSpec<T> = tween(durationMillis = tabExit, easing = FastOutSlowInEasing)
    fun tabSlideSpec(): FiniteAnimationSpec<IntOffset> = tween(durationMillis = tabSlide, easing = FastOutSlowInEasing)
    fun tabPillSpringSpec(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    fun shimmerSpec(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = shimmer),
        repeatMode = RepeatMode.Reverse
    )
}

const val A4_RATIO = 1.414f
