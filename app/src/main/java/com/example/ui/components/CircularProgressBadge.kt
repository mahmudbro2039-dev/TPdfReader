package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import com.example.R
import com.example.ui.theme.TeenaBadgeNumberStyle
import com.example.ui.theme.TeenaTheme
import kotlin.math.roundToInt

@Composable
fun CircularProgressBadge(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val sz = TeenaTheme.sizes
    val c = TeenaTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = TeenaTheme.motion.slowSpec(),
        label = "ring"
    )
    val percent = (clamped * 100f).roundToInt()
    val track = c.badgeTrack
    val fill = c.badgeFill
    val density = LocalDensity.current
    val strokePx = with(density) { sz.ringStroke.toPx() }
    val accessibilityLabel = stringResource(R.string.percent_read, percent)

    Box(
        modifier = modifier
            .size(sz.progressBadge)
            .shadow(sz.badgeElevation, CircleShape)
            .background(c.badgeBg, CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(sz.ringInset)
        ) {
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = fill,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = stroke
            )
        }
        if (percent >= 100) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.status_completed),
                modifier = Modifier.size(sz.iconSm),
                tint = c.badgeFill
            )
        } else {
            // Keep badge label fixed-scale inside the fixed-Dp ring so 1.3x+ font scale never clips it.
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f)
            ) {
                Text(
                    text = "$percent",
                    maxLines = 1,
                    softWrap = false,
                    color = c.badgeText,
                    style = TeenaBadgeNumberStyle
                )
            }
        }
    }
}
