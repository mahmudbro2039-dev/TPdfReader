package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.ui.model.PdfItem
import com.example.ui.model.progress
import com.example.ui.theme.TeenaTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfGridCard(
    item: PdfItem,
    onClick: () -> Unit,
    onMore: (PdfItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val c = TeenaTheme.colors
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val timeText = remember(item.lastOpenedAt) {
        DateUtils.getRelativeTimeSpanString(
            item.lastOpenedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.background, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMore(item)
                }
            )
            .padding(bottom = s.xs)
            .testTag("pdf_grid_${item.id}"),
        verticalArrangement = Arrangement.spacedBy(s.sm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(sz.coverRatio)
        ) {
            PdfCover(
                uri = item.uri,
                modifier = Modifier.fillMaxSize()
            )

            if (item.isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(s.badgePad)
                        .size(sz.coverMenuVisual)
                        .background(c.coverScrim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(sz.iconXs),
                        tint = c.onCoverScrim
                    )
                }
            }

            // 3-dots: 48dp accessible touch target with crisp scrim circle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(sz.coverMenuHit)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMore(item)
                        }
                    )
                    .testTag("pdf_grid_more_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(sz.coverMenuVisual)
                        .background(c.coverScrim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        modifier = Modifier.size(sz.coverMenuIcon),
                        tint = c.onCoverScrim
                    )
                }
            }

            if (item.progress > 0f) {
                CircularProgressBadge(
                    progress = item.progress,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(s.badgePad)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.xs),
            verticalArrangement = Arrangement.spacedBy(s.xxs)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
