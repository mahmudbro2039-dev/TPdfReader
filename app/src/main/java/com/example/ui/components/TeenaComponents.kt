package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun ReadingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val c = TeenaTheme.colors
    val barHeight = if (compact) TeenaTheme.sizes.compactProgressHeight else TeenaTheme.sizes.progressHeight
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(CircleShape)
            .background(c.progressTrack)
    ) {
        if (clampedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clampedProgress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(c.progressFill)
            )
        }
    }
}

@Composable
fun StatusChip(
    status: ReadingStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
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
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = sc.fg,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(sc.bg, CircleShape)
            .padding(
                horizontal = if (compact) s.sm else s.sm,
                vertical = if (compact) s.xxs else s.xs
            )
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
    val widthPx = remember(width, density) { with(density) { (width.roundToPx() * 2).coerceIn(160, 320) } }
    val cached = remember(uri) { ThumbnailCache.peek(uri) }
    val bmp by produceState<Bitmap?>(cached, uri, widthPx) {
        if (value == null) {
            value = ThumbnailCache.load(context, uri, widthPx)
        }
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
        val bitmap = bmp
        if (bitmap != null && !bitmap.isRecycled) {
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

@Composable
fun PdfCover(
    uri: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    targetWidth: Dp = TeenaTheme.sizes.gridMinCell
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = remember(targetWidth, density) {
        with(density) { (targetWidth.roundToPx() * 2).coerceIn(160, 360) }
    }
    val cached = remember(uri) { ThumbnailCache.peek(uri) }
    val bmp by produceState<Bitmap?>(cached, uri, widthPx) {
        if (value == null) {
            value = ThumbnailCache.load(context, uri, widthPx)
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = TeenaTheme.sizes.hairline,
                color = TeenaTheme.colors.hairline,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = bmp
        if (bitmap != null && !bitmap.isRecycled) {
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

@Composable
fun PdfRowCard(
    item: PdfItem,
    onClick: () -> Unit,
    onMore: (PdfItem) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
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
        shape = if (compact) MaterialTheme.shapes.medium else MaterialTheme.shapes.large,
        color = cs.surface,
        border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = s.md,
                vertical = if (compact) s.sm else s.md
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) s.md else s.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfThumbnail(
                uri = item.uri,
                width = if (compact) sz.compactThumbWidth else sz.thumbWidth
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) s.xs else s.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(sz.iconXs),
                            tint = cs.primary
                        )
                        Spacer(Modifier.width(s.xs))
                    }
                    Text(
                        text = item.title,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(s.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(item.status, compact = compact)
                    val metaText = if (item.totalPages > 0) {
                        stringResource(R.string.page_of, item.currentPage, item.totalPages)
                    } else {
                        stringResource(R.string.file_meta_ready, formatFileSize(item.sizeBytes))
                    }
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                ReadingProgressBar(item.progress, compact = compact)
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMore(item)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("pdf_more_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(if (compact) sz.iconTool else sz.iconMd)
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
        shape = MaterialTheme.shapes.large,
        color = cs.surface,
        border = BorderStroke(sz.hairline, TeenaTheme.colors.hairline),
        shadowElevation = sz.cardElevation
    ) {
        Row(
            modifier = Modifier.padding(s.md),
            horizontalArrangement = Arrangement.spacedBy(s.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfThumbnail(uri = item.uri, width = sz.heroThumbWidth)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(s.xs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.xs)
                ) {
                    Text(
                        text = stringResource(R.string.continue_reading),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.in_folder, item.folderName),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 2,
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
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("hero_resume_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(sz.heroPlayButton)
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.resume),
                        tint = cs.onPrimary,
                        modifier = Modifier.size(sz.iconTool)
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
                        maxLines = 1,
                        softWrap = false
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
fun FolderCard(
    folder: FolderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val c = TeenaTheme.colors
    val countLabel = pluralStringResource(R.plurals.pdf_count, folder.pdfCount, folder.pdfCount)
    val sizeLabel = formatFileSize(folder.totalSizeBytes)
    val previews = folder.previewUris

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("folder_row_${folder.name}"),
        shape = MaterialTheme.shapes.large,
        color = cs.surface,
        border = BorderStroke(sz.hairline, c.hairline),
        shadowElevation = sz.cardElevation
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(sz.folderPreviewRatio)
                    .clipToBounds()
                    .background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (previews.size) {
                    0 -> Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(sz.iconLg)
                    )
                    1 -> PdfCover(
                        uri = previews[0],
                        shape = MaterialTheme.shapes.small,
                        targetWidth = sz.folderMiniCoverWidth,
                        modifier = Modifier
                            .width(sz.folderMiniCoverWidth)
                            .aspectRatio(sz.coverRatio)
                            .shadow(sz.cardElevation, MaterialTheme.shapes.small)
                    )
                    2 -> {
                        PdfCover(
                            uri = previews[1],
                            shape = MaterialTheme.shapes.small,
                            targetWidth = sz.folderMiniCoverWidth,
                            modifier = Modifier
                                .offset(x = sz.folderMiniCoverOffsetX, y = sz.folderMiniCoverOffsetY)
                                .graphicsLayer { rotationZ = 6f }
                                .alpha(0.92f)
                                .width(sz.folderMiniCoverWidth)
                                .aspectRatio(sz.coverRatio)
                                .shadow(sz.cardElevation, MaterialTheme.shapes.small)
                        )
                        PdfCover(
                            uri = previews[0],
                            shape = MaterialTheme.shapes.small,
                            targetWidth = sz.folderMiniCoverWidth,
                            modifier = Modifier
                                .offset(x = -sz.folderMiniCoverOffsetX / 2)
                                .graphicsLayer { rotationZ = -3f }
                                .width(sz.folderMiniCoverWidth)
                                .aspectRatio(sz.coverRatio)
                                .shadow(sz.fabElevation, MaterialTheme.shapes.small)
                        )
                    }
                    else -> {
                        PdfCover(
                            uri = previews[1],
                            shape = MaterialTheme.shapes.small,
                            targetWidth = sz.folderMiniCoverWidth,
                            modifier = Modifier
                                .offset(x = -sz.folderMiniCoverOffsetX, y = sz.folderMiniCoverOffsetY)
                                .graphicsLayer { rotationZ = -8f }
                                .alpha(0.9f)
                                .width(sz.folderMiniCoverWidth)
                                .aspectRatio(sz.coverRatio)
                                .shadow(sz.cardElevation, MaterialTheme.shapes.small)
                        )
                        PdfCover(
                            uri = previews[2],
                            shape = MaterialTheme.shapes.small,
                            targetWidth = sz.folderMiniCoverWidth,
                            modifier = Modifier
                                .offset(x = sz.folderMiniCoverOffsetX, y = sz.folderMiniCoverOffsetY)
                                .graphicsLayer { rotationZ = 8f }
                                .alpha(0.9f)
                                .width(sz.folderMiniCoverWidth)
                                .aspectRatio(sz.coverRatio)
                                .shadow(sz.cardElevation, MaterialTheme.shapes.small)
                        )
                        PdfCover(
                            uri = previews[0],
                            shape = MaterialTheme.shapes.small,
                            targetWidth = sz.folderMiniCoverWidth,
                            modifier = Modifier
                                .width(sz.folderMiniCoverWidth)
                                .aspectRatio(sz.coverRatio)
                                .shadow(sz.fabElevation, MaterialTheme.shapes.small)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = sz.hairline, color = c.hairline)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.md, vertical = s.md),
                verticalArrangement = Arrangement.spacedBy(s.xs)
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.xs)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(sz.iconXs)
                    )
                    Text(
                        text = stringResource(R.string.folder_meta_format, countLabel, sizeLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderBreadcrumb(
    currentFolder: String?,
    pdfCount: Int,
    totalSizeBytes: Long,
    onRootClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentFolder == null) return
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val countLabel = pluralStringResource(R.plurals.pdf_count, pdfCount, pdfCount)
    val sizeLabel = formatFileSize(totalSizeBytes)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("folder_breadcrumb")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = s.xs, end = s.screenH, top = s.xs, bottom = s.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.xs)
        ) {
            IconButton(
                onClick = onRootClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("breadcrumb_root")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = cs.primary,
                    modifier = Modifier.size(sz.iconMd)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(s.xxs)
            ) {
                Text(
                    text = currentFolder,
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
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(thickness = sz.hairline, color = TeenaTheme.colors.hairline)
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
                .clip(MaterialTheme.shapes.large)
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
                Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
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
}

@Composable
fun TeenaBottomBar(
    currentTab: TeenaTab,
    onSelectTab: (TeenaTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val c = TeenaTheme.colors
    val motion = TeenaTheme.motion

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = s.screenH, vertical = s.sm),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = cs.surface,
            border = BorderStroke(sz.hairline, c.hairline.copy(alpha = 0.85f)),
            shadowElevation = sz.heroElevation,
            modifier = Modifier
                .widthIn(max = sz.bottomBarMaxWidth)
                .fillMaxWidth()
        ) {
            val tabs = TeenaTab.entries
            val pillIndex by animateFloatAsState(
                targetValue = currentTab.ordinal.toFloat(),
                animationSpec = motion.tabPillSpringSpec(),
                label = "nav_pill_slide"
            )
            val pillBgColor = cs.primaryContainer

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sz.bottomBarHeight)
                    .selectableGroup()
                    .padding(s.xs)
                    .drawBehind {
                        val slotWidth = size.width / tabs.size.coerceAtLeast(1)
                        val radius = size.height / 2f
                        drawRoundRect(
                            color = pillBgColor,
                            topLeft = Offset(x = pillIndex * slotWidth, y = 0f),
                            size = Size(width = slotWidth, height = size.height),
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = currentTab == tab
                    val label = stringResource(
                        when (tab) {
                            TeenaTab.Recent -> R.string.tab_recent
                            TeenaTab.Library -> R.string.tab_library
                            TeenaTab.Folders -> R.string.tab_folders
                        }
                    )
                    val icon = when (tab) {
                        TeenaTab.Recent -> Icons.Rounded.History
                        TeenaTab.Library -> Icons.AutoMirrored.Rounded.LibraryBooks
                        TeenaTab.Folders -> Icons.Rounded.Folder
                    }
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
                        animationSpec = motion.tabEnterSpec(),
                        label = "nav_tint_${tab.name}"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 1f,
                        animationSpec = motion.tabPillSpringSpec(),
                        label = "nav_scale_${tab.name}"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .selectable(
                                selected = selected,
                                onClick = { onSelectTab(tab) },
                                role = Role.Tab
                            )
                            .testTag("bottom_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(s.xxs)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(sz.bottomNavIcon)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceScanProgressStrip(
    foundCount: Int,
    modifier: Modifier = Modifier
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme

    Surface(
        color = cs.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH, vertical = s.xs)
            .testTag("device_scan_progress_strip")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.md, vertical = s.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.sm)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(sz.iconSm)
                )
                Text(
                    text = stringResource(R.string.scanner_scanning_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (foundCount > 0) {
                    Text(
                        text = stringResource(R.string.scanner_found_count, foundCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sz.progressHeight),
                color = cs.primary,
                trackColor = cs.primaryContainer
            )
        }
    }
}

@Composable
fun DeviceAccessStatusBanner(
    hasAllFilesAccess: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val needsFiles = !hasAllFilesAccess
    val needsBattery = !isBatteryOptimizationIgnored
    if (!needsFiles && !needsBattery) return

    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val cs = MaterialTheme.colorScheme
    val c = TeenaTheme.colors

    val description = if (needsFiles) {
        stringResource(R.string.perm_banner_files_desc)
    } else {
        stringResource(R.string.perm_banner_battery_desc)
    }
    val actionLabel = if (needsFiles) {
        stringResource(R.string.perm_grant_action)
    } else {
        stringResource(R.string.perm_disable_battery_action)
    }
    val bannerIcon = if (needsFiles) Icons.Rounded.FolderOpen else Icons.Rounded.BatteryChargingFull
    val onAction = if (needsFiles) onRequestAllFilesAccess else onRequestBatteryExemption

    Surface(
        color = cs.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(sz.hairline, c.hairline),
        shadowElevation = sz.cardElevation,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = s.screenH, vertical = s.xs)
            .testTag("device_access_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = s.md, top = s.sm, bottom = s.sm, end = s.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.sm)
        ) {
            Icon(
                imageVector = bannerIcon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(sz.iconMd)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = onAction,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = s.md, vertical = s.xs),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary
                ),
                modifier = Modifier.testTag("banner_grant_permission_button")
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("banner_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(sz.iconSm)
                )
            }
        }
    }
}

