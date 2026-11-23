package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.local.AnnotationEntity
import com.example.data.model.AnnotationType
import com.example.data.model.HighlightColorOption
import com.example.data.model.QuoteCardTheme
import com.example.data.pdf.DocumentLinkEntry
import com.example.data.pdf.QuoteCardRenderer
import com.example.ui.theme.TeenaTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsBottomSheet(
    currentPage: Int,
    currentPageTextSnippet: String,
    annotations: List<AnnotationEntity>,
    onDismiss: () -> Unit,
    onAddAnnotation: (pageIndex: Int, type: AnnotationType, quote: String, note: String, colorHex: String) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onShareQuoteText: (String, Int) -> Unit,
    onDeleteAnnotation: (Long) -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val readerColors = TeenaTheme.colors.reader

    var selectedTab by remember { mutableIntStateOf(0) }
    var hasEditedQuote by remember(currentPage) { mutableStateOf(false) }
    var quoteInput by remember(currentPage) {
        mutableStateOf(
            currentPageTextSnippet.lines().firstOrNull { it.length > 24 }?.take(180)
                ?: "Highlighted passage on Page ${currentPage + 1}"
        )
    }
    LaunchedEffect(currentPageTextSnippet, currentPage) {
        if (!hasEditedQuote) {
            val extracted = currentPageTextSnippet.lines().firstOrNull { it.length > 24 }?.take(180)
            if (!extracted.isNullOrBlank()) {
                quoteInput = extracted
            }
        }
    }
    var noteInput by remember { mutableStateOf("") }
    var selectedSwatch by remember { mutableStateOf(HighlightColorOption.YELLOW) }

    val filteredList = remember(annotations, selectedTab) {
        when (selectedTab) {
            0 -> annotations.filter { it.annotationType == AnnotationType.HIGHLIGHT }
            1 -> annotations.filter { it.annotationType == AnnotationType.NOTE || it.noteText.isNotBlank() }
            else -> annotations.filter { it.annotationType == AnnotationType.SIGNATURE }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = s.screenH, vertical = s.sm)
        ) {
            Text(
                text = stringResource(R.string.reader_annotations),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(s.md))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(s.md)) {
                    Text(
                        text = stringResource(R.string.reader_current_page_label, currentPage + 1).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(s.sm))

                    OutlinedTextField(
                        value = quoteInput,
                        onValueChange = {
                            hasEditedQuote = true
                            quoteInput = it
                        },
                        label = { Text(stringResource(R.string.reader_note_quote_hint)) },
                        maxLines = 2,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(s.sm))
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text(stringResource(R.string.reader_note_comment_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(s.md))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                            HighlightColorOption.entries.forEach { option ->
                                val swatchColor = parseHexColor(option.hexColor, readerColors.highlightDefault)
                                val isSelected = selectedSwatch == option
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(sz.swatchSize)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) sz.strokeWidth else sz.hairline,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { selectedSwatch = option }
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = option.label,
                                            tint = readerColors.textLightFg,
                                            modifier = Modifier.size(sz.iconSm)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val type = if (noteInput.isNotBlank()) AnnotationType.NOTE else AnnotationType.HIGHLIGHT
                                onAddAnnotation(
                                    currentPage,
                                    type,
                                    quoteInput,
                                    noteInput,
                                    selectedSwatch.hexColor
                                )
                                noteInput = ""
                            },
                            modifier = Modifier.testTag("save_annotation_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(sz.iconSm))
                            Spacer(modifier = Modifier.width(s.xs))
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(s.md))

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_highlights_label)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.notes_highlights)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.reader_signature_signed)) }
                )
            }

            Spacer(modifier = Modifier.height(s.sm))

            if (filteredList.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_annotations_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = s.xl)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(s.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sz.sheetListMaxHeight)
                ) {
                    items(
                        items = filteredList,
                        key = { it.id },
                        contentType = { "annotation_item" }
                    ) { item ->
                        AnnotationItemCard(
                            annotation = item,
                            onClick = {
                                onJumpToPage(item.pageIndex)
                                onDismiss()
                            },
                            onShareQuote = {
                                onShareQuoteText(
                                    item.quotedText.ifBlank { item.noteText },
                                    item.pageIndex + 1
                                )
                            },
                            onDelete = { onDeleteAnnotation(item.id) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(s.xl))
        }
    }
}

@Composable
fun AnnotationItemCard(
    annotation: AnnotationEntity,
    onClick: () -> Unit,
    onShareQuote: () -> Unit,
    onDelete: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val swatchColor = parseHexColor(annotation.colorHex, TeenaTheme.colors.reader.highlightDefault)

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(sz.hairline, TeenaTheme.colors.hairline, MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(s.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(sz.progressHeight)
                    .height(sz.searchHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(swatchColor)
            )
            Spacer(modifier = Modifier.width(s.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${annotation.annotationType.label.uppercase()}  ·  ${stringResource(R.string.page_badge_short, annotation.pageIndex + 1).uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (annotation.annotationType == AnnotationType.SIGNATURE && annotation.signatureVectorData.isNotBlank()) {
                    Spacer(modifier = Modifier.height(s.xs))
                    VectorSignaturePreview(
                        signatureVectorData = annotation.signatureVectorData,
                        strokeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .width(sz.signatureStampWidth)
                            .height(sz.signatureStampHeight)
                    )
                } else if (annotation.quotedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(s.xxs))
                    Text(
                        text = "“${annotation.quotedText}”",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (annotation.noteText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(s.xs))
                    Text(
                        text = annotation.noteText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (annotation.annotationType != AnnotationType.SIGNATURE) {
                IconButton(
                    onClick = onShareQuote,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = stringResource(R.string.reader_share_quote),
                        modifier = Modifier.size(sz.searchIcon)
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(sz.searchIcon)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePadBottomSheet(
    currentPage: Int,
    savedSignatureVector: String,
    onDismiss: () -> Unit,
    onStampSignature: (vectorData: String, label: String) -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val readerColors = TeenaTheme.colors.reader

    val strokes = remember {
        mutableStateListOf<List<Offset>>().apply {
            addAll(deserializeVectorStrokes(savedSignatureVector))
        }
    }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var signerLabel by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = s.screenH, vertical = s.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(s.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reader_sign_pdf),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.reader_signature_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(s.lg))

            val allStrokes = if (currentStroke.isNotEmpty()) strokes + listOf(currentStroke) else strokes.toList()
            val serialized = serializeVectorStrokes(allStrokes)

            Surface(
                shape = MaterialTheme.shapes.large,
                color = readerColors.textLightBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sz.signaturePadHeight)
                    .border(
                        width = sz.borderThick,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.large
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val norm = Offset(
                                    (startOffset.x / size.width).coerceIn(0f, 1f),
                                    (startOffset.y / size.height).coerceIn(0f, 1f)
                                )
                                currentStroke = listOf(norm)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val norm = Offset(
                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f)
                                )
                                currentStroke = currentStroke + norm
                            },
                            onDragEnd = {
                                if (currentStroke.isNotEmpty()) {
                                    strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                }
                            },
                            onDragCancel = {
                                currentStroke = emptyList()
                            }
                        )
                    }
                    .testTag("signature_canvas_pad")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (allStrokes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.reader_signature_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    VectorSignaturePreview(
                        signatureVectorData = serialized,
                        strokeColor = MaterialTheme.colorScheme.primary,
                        strokeWidth = sz.strokeWidthBold,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(s.md)
                    )
                }
            }

            Spacer(modifier = Modifier.height(s.md))

            OutlinedTextField(
                value = signerLabel,
                onValueChange = { signerLabel = it },
                label = { Text(stringResource(R.string.reader_signature_label_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(s.lg))

            Row(
                horizontalArrangement = Arrangement.spacedBy(s.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        strokes.clear()
                        currentStroke = emptyList()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(sz.iconSm))
                    Spacer(modifier = Modifier.width(s.sm))
                    Text(stringResource(R.string.reader_signature_clear))
                }

                Button(
                    onClick = {
                        if (serialized.isNotBlank()) {
                            onStampSignature(serialized, signerLabel.ifBlank { "Signed" })
                            onDismiss()
                        }
                    },
                    enabled = serialized.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stamp_signature_button")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(sz.iconSm))
                    Spacer(modifier = Modifier.width(s.sm))
                    Text(stringResource(R.string.reader_signature_stamp, currentPage + 1))
                }
            }

            Spacer(modifier = Modifier.height(s.xl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareQuoteBottomSheet(
    initialQuoteText: String,
    bookTitle: String,
    pageNumber: Int,
    onDismiss: () -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var quoteText by remember(initialQuoteText) {
        mutableStateOf(
            initialQuoteText.trim().ifBlank {
                "Great reading software disappears behind the content, putting your document and ideas first."
            }
        )
    }
    var selectedTheme by remember { mutableStateOf(QuoteCardTheme.LIGHT) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(quoteText, bookTitle, pageNumber, selectedTheme) {
        renderedBitmap = QuoteCardRenderer.renderQuoteBitmap(
            quoteText = quoteText,
            bookTitle = bookTitle,
            pageNumber = pageNumber,
            theme = selectedTheme
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = s.screenH, vertical = s.sm)
        ) {
            Text(
                text = stringResource(R.string.reader_share_quote),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(s.md))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                items(
                    items = QuoteCardTheme.entries,
                    key = { it.name },
                    contentType = { "quote_theme" }
                ) { theme ->
                    FilterChip(
                        selected = selectedTheme == theme,
                        onClick = { selectedTheme = theme },
                        label = { Text(theme.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(s.md))

            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = sz.cardElevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sz.quotePreviewHeight)
            ) {
                val bmp = renderedBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.reader_share_quote),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator()
                    }
                }
            }

            Spacer(modifier = Modifier.height(s.md))

            OutlinedTextField(
                value = quoteText,
                onValueChange = { quoteText = it },
                label = { Text(stringResource(R.string.reader_quote_hint)) },
                maxLines = 3,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(s.lg))

            val chooserTitle = stringResource(R.string.reader_share_quote)
            Button(
                onClick = {
                    val bmp = renderedBitmap ?: return@Button
                    scope.launch {
                        val shareIntent = QuoteCardRenderer.shareQuoteBitmap(context, bmp, bookTitle)
                        if (shareIntent != null) {
                            context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_quote_card_button")
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(sz.searchIcon))
                Spacer(modifier = Modifier.width(s.sm))
                Text(stringResource(R.string.reader_quote_share_image))
            }

            Spacer(modifier = Modifier.height(s.xl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentLinksBottomSheet(
    links: List<DocumentLinkEntry>,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    val s = TeenaTheme.spacing
    val sz = TeenaTheme.sizes

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = s.screenH, vertical = s.sm)
        ) {
            Text(
                text = stringResource(R.string.reader_links),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(s.md))
            HorizontalDivider(color = TeenaTheme.colors.hairline)

            if (links.isEmpty()) {
                Text(
                    text = stringResource(R.string.reader_no_links),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = s.xl)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sz.sheetListMaxHeight)
                ) {
                    items(
                        items = links,
                        key = { "${it.pageIndex}_${it.urlOrTarget}_${it.isInternalPageLink}" },
                        contentType = { "doc_link" }
                    ) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isInternalPageLink) {
                                        onJumpToPage(entry.targetPageIndex)
                                    } else {
                                        onOpenExternalUrl(entry.urlOrTarget)
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = s.md)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(sz.linkIconBox)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (entry.isInternalPageLink) Icons.Filled.Link else Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(sz.iconTool)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(s.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${entry.urlOrTarget}  ·  ${stringResource(R.string.page_badge_short, entry.pageIndex + 1)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = TeenaTheme.colors.hairline)
                    }
                }
            }
            Spacer(modifier = Modifier.height(s.xl))
        }
    }
}
