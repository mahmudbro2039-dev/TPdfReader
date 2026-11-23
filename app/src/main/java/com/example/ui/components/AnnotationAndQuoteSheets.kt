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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AnnotationEntity
import com.example.data.model.AnnotationType
import com.example.data.model.HighlightColorOption
import com.example.data.model.QuoteCardTheme
import com.example.data.pdf.DocumentLinkEntry
import com.example.data.pdf.QuoteCardRenderer
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
    var selectedTab by remember { mutableIntStateOf(0) }
    var quoteInput by remember(currentPageTextSnippet) {
        mutableStateOf(
            currentPageTextSnippet.lines().firstOrNull { it.length > 24 }?.take(180)
                ?: "Highlighted passage on Page ${currentPage + 1}"
        )
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
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_annotations),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Quick Add Highlight / Note Creator for Current Page
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ADD HIGHLIGHT OR NOTE ON PAGE ${currentPage + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quoteInput,
                        onValueChange = { quoteInput = it },
                        label = { Text("Highlighted passage") },
                        maxLines = 2,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Attach personal note (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HighlightColorOption.entries.forEach { option ->
                                val swatchColor = parseHexColor(option.hexColor)
                                val isSelected = selectedSwatch == option
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedSwatch = option }
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = option.label,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
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
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Highlights") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("My Notes") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Signatures") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredList.isEmpty()) {
                Text(
                    text = "No items in this section yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
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
            Spacer(modifier = Modifier.height(20.dp))
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
    val swatchColor = parseHexColor(annotation.colorHex)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(swatchColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${annotation.annotationType.label.uppercase()}  •  PAGE ${annotation.pageIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (annotation.annotationType == AnnotationType.SIGNATURE && annotation.signatureVectorData.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    VectorSignaturePreview(
                        signatureVectorData = annotation.signatureVectorData,
                        strokeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .width(140.dp)
                            .height(42.dp)
                    )
                } else if (annotation.quotedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "“${annotation.quotedText}”",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (annotation.noteText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
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
                IconButton(onClick = onShareQuote, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = stringResource(R.string.reader_share_quote),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(18.dp)
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
    val strokes = remember {
        mutableStateListOf<List<Offset>>().apply {
            addAll(deserializeVectorStrokes(savedSignatureVector))
        }
    }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var signerLabel by remember { mutableStateOf("Authorized Signature") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reader_sign_pdf),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Draw sharp vector strokes to stamp onto Page ${currentPage + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Vector Drawing Pad
            val allStrokes = if (currentStroke.isNotEmpty()) strokes + listOf(currentStroke) else strokes.toList()
            val serialized = serializeVectorStrokes(allStrokes)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFCFBF8),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
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
                            text = "Sign here with your finger or stylus",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF71717A),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    VectorSignaturePreview(
                        signatureVectorData = serialized,
                        strokeColor = Color(0xFF991B1B),
                        strokeWidthDp = 3.2f,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = signerLabel,
                onValueChange = { signerLabel = it },
                label = { Text("Signature label / stamp note") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        strokes.clear()
                        currentStroke = emptyList()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Pad")
                }

                Button(
                    onClick = {
                        if (serialized.isNotBlank()) {
                            onStampSignature(serialized, signerLabel)
                            onDismiss()
                        }
                    },
                    enabled = serialized.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stamp_signature_button")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stamp Page ${currentPage + 1}")
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
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
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_share_quote),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QuoteCardTheme.entries) { theme ->
                    FilterChip(
                        selected = selectedTheme == theme,
                        onClick = { selectedTheme = theme },
                        label = { Text(theme.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Rendered Quote Card Preview
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                val bmp = renderedBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Quote card preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator()
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = quoteText,
                onValueChange = { quoteText = it },
                label = { Text("Edit quote text") },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val bmp = renderedBitmap ?: return@Button
                    scope.launch {
                        val shareIntent = QuoteCardRenderer.shareQuoteBitmap(context, bmp, bookTitle)
                        if (shareIntent != null) {
                            context.startActivity(Intent.createChooser(shareIntent, "Share Quote Card"))
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_quote_card_button")
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share High-Res Quote Card Image")
            }

            Spacer(modifier = Modifier.height(22.dp))
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_links),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Embedded URLs and cross-reference links detected across pages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()

            if (links.isEmpty()) {
                Text(
                    text = "No hyperlinks or cross-references were detected in this PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 28.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(links) { entry ->
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
                                .padding(vertical = 12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (entry.isInternalPageLink) Icons.Filled.Link else Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${entry.urlOrTarget}  •  Found on Page ${entry.pageIndex + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
