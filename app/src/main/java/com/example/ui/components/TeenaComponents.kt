package com.example.ui.components

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.R
import com.example.ui.model.FolderItem
import com.example.ui.model.PdfItem
import com.example.ui.model.percent
import com.example.ui.model.progress
import com.example.ui.theme.A4_RATIO
import com.example.ui.theme.ReadingStatus
import com.example.ui.theme.TeenaTheme

@Stable
class FabController {
    var expanded by mutableStateOf(true)
}

@Composable
fun ReadingProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val c = TeenaTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = TeenaTheme.motion.slowSpec(),
        label = "progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TeenaTheme.sizes.progressHeight)
            .clip(CircleShape)
            .background(c.progressTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(c.progressFill)
        )
    }
}

@Composable
fun StatusChip(status: ReadingStatus, modifier: Modifier = Modifier) {
    val sc = TeenaTheme.colors.status(status)
    val s = TeenaTheme.spacing
    val label = stringResource(
        when (status) {
            ReadingStatus.ToRead -> R.string.status_to_read
            ReadingStatus.Reading -> R.string.status_reading
            ReadingStatus.Completed -> R.string.status_completed
            ReadingStatus.OnHold -> R.string.status_on_hold
        }
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = sc.fg,
        maxLines = 1,
        modifier = modifier
            .background(sc.bg, CircleShape)
            .padding(horizontal = s.sm, vertical = s.xs)
    )
}

@Composable
fun PdfThumbnail(
    uri: String,
    width: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = remember(width, density) { with(density) { width.roundToPx() * 2 } }
    val bmp by produceState<Bitmap?>(ThumbnailCache.peek(uri), uri, widthPx) {
        value = ThumbnailCache.load(context, uri, widthPx)
    }
    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(1f / A4_RATIO)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(TeenaTheme.sizes.hairline, TeenaTheme.colors.hairline, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = bmp,
            animationSpec = TeenaTheme.motion.normalSpec(),
            label = "thumb"
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PdfCover(
    uri: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = TeenaTheme.sizes.hairline,
                color = TeenaTheme.colors.hairline,
                shape = MaterialTheme.shapes.large
            ),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = remember(maxWidth, density) {
            with(density) { (maxWidth.roundToPx() * 2).coerceAtLeast(120) }
        }
        val bmp by produceState<Bitmap?>(ThumbnailCache.peek(uri), uri, widthPx) {
            value = ThumbnailCache.load(context, uri, widthPx)
        }
        Crossfade(
            targetState = bmp,
            animationSpec = TeenaTheme.motion.normalSpec(),
            label = "cover"
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PdfRowCard(
    item: PdfItem,
    onClick: () -> Unit,
    onMore: (PdfItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("pdf_row_${item.id}"),
        shape = MaterialTheme.shapes.large,
        color = cs.surface,
        border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline)
    ) {
        Row(
            modifier = Modifier.padding(s.md),
            horizontalArrangement = Arrangement.spacedBy(s.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfThumbnail(uri = item.uri, width = sz.thumbWidth)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(s.sm)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(sz.iconSm),
                            tint = cs.primary
                        )
                        Spacer(Modifier.width(s.xs))
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(s.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(item.status)
                    Text(
                        text = stringResource(R.string.page_of, item.currentPage, item.totalPages),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ReadingProgressBar(item.progress)
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMore(item)
                },
                modifier = Modifier.testTag("pdf_more_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HeroContinueCard(
    item: PdfItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_continue_card"),
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.surface,
        border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline),
        shadowElevation = sz.heroElevation
    ) {
        Row(
            modifier = Modifier.padding(s.lg),
            horizontalArrangement = Arrangement.spacedBy(s.lg),
            verticalAlignment = Alignment.Top
        ) {
            PdfThumbnail(uri = item.uri, width = sz.heroThumbWidth)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(s.sm)
            ) {
                Text(
                    text = stringResource(R.string.continue_reading),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.in_folder, item.folderName),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.sm)
                ) {
                    ReadingProgressBar(
                        progress = item.progress,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.percent_format, item.percent),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = onClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = s.lg, vertical = s.sm),
                    modifier = Modifier.testTag("hero_resume_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(sz.iconSm)
                    )
                    Spacer(Modifier.width(s.xs))
                    Text(
                        text = stringResource(R.string.resume),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun NotesSummaryCard(
    highlightCount: Int,
    bookmarkCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val totalCount = highlightCount + bookmarkCount
    val subtitle = if (totalCount == 0) {
        stringResource(R.string.notes_hub_empty)
    } else {
        stringResource(
            R.string.notes_hub_summary,
            pluralStringResource(R.plurals.highlight_count, highlightCount, highlightCount),
            pluralStringResource(R.plurals.bookmark_count, bookmarkCount, bookmarkCount)
        )
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("notes_highlights_card"),
        shape = MaterialTheme.shapes.large,
        color = cs.surface,
        border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline)
    ) {
        Row(
            modifier = Modifier.padding(s.md),
            horizontalArrangement = Arrangement.spacedBy(s.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(sz.folderIconBox)
                    .clip(MaterialTheme.shapes.medium)
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmarks,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(sz.iconMd)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(s.xxs)
            ) {
                Text(
                    text = stringResource(R.string.notes_highlights),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant
            )
        }
    }
}


@Composable
fun FilterChipsRow(
    selectedStatus: ReadingStatus?,
    counts: Map<ReadingStatus, Int>,
    totalCount: Int,
    onSelectStatus: (ReadingStatus?) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val c = TeenaTheme.colors

    val chipEntries = remember(counts, totalCount) {
        listOf<Pair<ReadingStatus?, Int>>(
            null to totalCount,
            ReadingStatus.ToRead to (counts[ReadingStatus.ToRead] ?: 0),
            ReadingStatus.Reading to (counts[ReadingStatus.Reading] ?: 0),
            ReadingStatus.Completed to (counts[ReadingStatus.Completed] ?: 0),
            ReadingStatus.OnHold to (counts[ReadingStatus.OnHold] ?: 0)
        )
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .fadingEndEdge(sz.fadeEdgeWidth)
            .testTag("library_filter_chips"),
        contentPadding = PaddingValues(horizontal = s.screenH),
        horizontalArrangement = Arrangement.spacedBy(s.sm)
    ) {
        items(
            items = chipEntries,
            key = { (status, _) -> status?.name ?: "ALL" },
            contentType = { "filter_chip" }
        ) { (status, count) ->
            val selected = selectedStatus == status
            val baseLabel = stringResource(
                when (status) {
                    null -> R.string.filter_all
                    ReadingStatus.ToRead -> R.string.status_to_read
                    ReadingStatus.Reading -> R.string.status_reading
                    ReadingStatus.Completed -> R.string.status_completed
                    ReadingStatus.OnHold -> R.string.status_on_hold
                }
            )
            FilterChip(
                selected = selected,
                onClick = { onSelectStatus(status) },
                label = {
                    Text(
                        text = stringResource(R.string.filter_chip_with_count, baseLabel, count),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = cs.surface,
                    labelColor = cs.onSurfaceVariant,
                    selectedContainerColor = cs.primaryContainer,
                    selectedLabelColor = cs.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = c.hairline,
                    selectedBorderColor = cs.primaryContainer,
                    borderWidth = sz.hairline,
                    selectedBorderWidth = sz.hairline
                ),
                modifier = Modifier.testTag("filter_chip_${status?.name ?: "all"}")
            )
        }
    }
}

@Composable
fun FolderRow(
    folder: FolderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val countLabel = pluralStringResource(R.plurals.pdf_count, folder.pdfCount, folder.pdfCount)
    val sizeLabel = formatFileSize(folder.totalSizeBytes)
    val relativeUpdated = remember(folder.lastUpdatedAt) {
        DateUtils.getRelativeTimeSpanString(
            folder.lastUpdatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("folder_row_${folder.name}"),
        shape = MaterialTheme.shapes.large,
        color = cs.surface,
        border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline)
    ) {
        Row(
            modifier = Modifier.padding(s.md),
            horizontalArrangement = Arrangement.spacedBy(s.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(sz.folderIconBox)
                    .clip(MaterialTheme.shapes.medium)
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(sz.iconMd)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(s.xxs)
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.folder_meta_format, countLabel, sizeLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.folder_updated_format, relativeUpdated),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FolderBreadcrumb(
    currentFolder: String?,
    onRootClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentFolder == null) return
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH, vertical = s.xs)
            .testTag("folder_breadcrumb"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.xs)
    ) {
        IconButton(
            onClick = onRootClick,
            modifier = Modifier.testTag("breadcrumb_root")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = cs.primary,
                modifier = Modifier.size(sz.iconMd)
            )
        }
        Text(
            text = currentFolder,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier.padding(top = s.sm, bottom = s.xs)
    )
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    showOpenButton: Boolean,
    onOpenPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = s.xl, vertical = s.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(sz.emptyCircle)
                .clip(CircleShape)
                .background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(sz.iconLg)
            )
        }
        Spacer(Modifier.height(s.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(s.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (showOpenButton) {
            Spacer(Modifier.height(s.xl))
            Button(
                onClick = onOpenPdf,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary
                ),
                modifier = Modifier.testTag("empty_open_pdf_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(sz.iconSm)
                )
                Spacer(Modifier.width(s.sm))
                Text(
                    text = stringResource(R.string.open_pdf),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun ShimmerLoadingList(modifier: Modifier = Modifier) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = TeenaTheme.motion.shimmerSpec(),
        label = "shimmer_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = s.screenH, vertical = s.sm),
        verticalArrangement = Arrangement.spacedBy(s.md)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sz.shimmerHeroHeight)
                .alpha(alpha)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(cs.surfaceVariant)
        )
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sz.shimmerRowHeight)
                    .alpha(alpha)
                    .clip(MaterialTheme.shapes.large)
                    .background(cs.surfaceVariant)
            )
        }
    }
}

@Composable
fun ShimmerLoadingGrid(modifier: Modifier = Modifier) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer_grid")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = TeenaTheme.motion.shimmerSpec(),
        label = "shimmer_grid_alpha"
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(sz.gridMinCell),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = s.screenH,
            end = s.screenH,
            top = s.sm,
            bottom = s.listBottom
        ),
        horizontalArrangement = Arrangement.spacedBy(s.gridGapH),
        verticalArrangement = Arrangement.spacedBy(s.gridGapV)
    ) {
        items(count = 9, key = { "shimmer_$it" }, contentType = { "shimmer_grid" }) {
            Column(verticalArrangement = Arrangement.spacedBy(s.sm)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(sz.coverRatio)
                        .alpha(alpha)
                        .clip(MaterialTheme.shapes.large)
                        .background(cs.surfaceVariant)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sz.shimmerTitleHeight)
                        .alpha(alpha)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(cs.surfaceVariant)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(sz.shimmerMetaHeight)
                        .alpha(alpha)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(cs.surfaceVariant)
                )
            }
        }
    }
}

@Composable
fun OpenPdfFab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sz = TeenaTheme.sizes
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(Icons.Rounded.FolderOpen, null) },
        text = { Text(stringResource(R.string.open_pdf), style = MaterialTheme.typography.labelLarge) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.large,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = sz.fabElevation),
        modifier = modifier.testTag("open_pdf_fab")
    )
}

@Composable
fun TeenaBottomBar(
    currentTab: TeenaTab,
    onSelectTab: (TeenaTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val sz = TeenaTheme.sizes
    val c = TeenaTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = sz.hairline, color = c.hairline)
        NavigationBar(
            containerColor = cs.surface,
            tonalElevation = sz.zeroElevation
        ) {
            TeenaTab.entries.forEach { tab ->
                val selected = currentTab == tab
                val label = stringResource(
                    when (tab) {
                        TeenaTab.Recent -> R.string.tab_recent
                        TeenaTab.Library -> R.string.tab_library
                        TeenaTab.Folders -> R.string.tab_folders
                    }
                )
                val icon = when (tab) {
                    TeenaTab.Recent -> if (selected) Icons.Rounded.History else Icons.Outlined.History
                    TeenaTab.Library -> if (selected) Icons.Rounded.LibraryBooks else Icons.Outlined.LibraryBooks
                    TeenaTab.Folders -> if (selected) Icons.Rounded.Folder else Icons.Outlined.Folder
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = { Icon(icon, contentDescription = label) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = cs.primary,
                        selectedTextColor = cs.primary,
                        indicatorColor = cs.primaryContainer,
                        unselectedIconColor = cs.onSurfaceVariant,
                        unselectedTextColor = cs.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("bottom_tab_${tab.name.lowercase()}")
                )
            }
        }
    }
}
