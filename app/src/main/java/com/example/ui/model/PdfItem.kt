package com.example.ui.model

import androidx.compose.runtime.Immutable
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.AppThemeMode
import com.example.data.model.FolderKey
import com.example.data.model.ReadingProgress
import com.example.data.model.ReadingStatus
import com.example.ui.theme.ThemeMode

enum class ViewMode { List, Grid }

enum class SortOrder {
    LastOpened,
    Name,
    DateAdded,
    Size,
    Progress;

    companion object {
        fun fromName(name: String): SortOrder =
            entries.firstOrNull { it.name == name } ?: LastOpened
    }
}

@Immutable
data class PdfItem(
    val id: String,
    val title: String,
    val folderName: String,
    val uri: String,
    val currentPage: Int,            // 1-based
    val totalPages: Int,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
    val addedAt: Long = lastOpenedAt,
    val status: ReadingStatus,
    val isPinned: Boolean = false,
    val isMissing: Boolean = false,
    val hasBeenOpened: Boolean = ReadingProgress.hasBeenOpened(lastOpenedAt),
    val progress: Float = ReadingProgress.fraction(
        currentPage = (currentPage - 1).coerceAtLeast(0),
        pageCount = totalPages,
        lastOpenedAt = lastOpenedAt,
        isCompleted = status == ReadingStatus.COMPLETED
    ),
    val percent: Int = ReadingProgress.percent(progress),
    val folderDisplayName: String = FolderKey.displayName(folderName)
)

@Immutable
data class FolderItem(
    val key: String,
    val name: String,
    val pdfCount: Int,
    val totalSizeBytes: Long,
    val lastUpdatedAt: Long,
    val previewUris: List<String> = emptyList()
)

@Immutable
data class BookmarkUiItem(
    val id: Long,
    val documentUri: String,
    val documentTitle: String,
    val pageNumber: Int,
    val title: String
)

@Immutable
data class AnnotationUiItem(
    val id: Long,
    val documentUri: String,
    val documentTitle: String,
    val pageNumber: Int,
    val quotedText: String,
    val noteText: String,
    val colorHex: String,
    val isSignature: Boolean
)

fun AppThemeMode.toThemeMode(): ThemeMode = when (this) {
    AppThemeMode.SYSTEM -> ThemeMode.System
    AppThemeMode.LIGHT -> ThemeMode.Light
    AppThemeMode.DARK, AppThemeMode.AMOLED -> ThemeMode.Dark
}

fun ThemeMode.toAppThemeMode(): AppThemeMode = when (this) {
    ThemeMode.System -> AppThemeMode.SYSTEM
    ThemeMode.Light -> AppThemeMode.LIGHT
    ThemeMode.Dark -> AppThemeMode.DARK
}

fun PdfDocumentEntity.toPdfItem(isMissing: Boolean = false): PdfItem {
    val opened = ReadingProgress.hasBeenOpened(lastOpenedAt)
    val oneBasedPage = if (pageCount <= 0 || !opened) 0 else (currentPage + 1).coerceIn(1, pageCount)
    val fraction = ReadingProgress.fraction(
        currentPage = currentPage,
        pageCount = pageCount,
        lastOpenedAt = lastOpenedAt,
        isCompleted = status == ReadingStatus.COMPLETED
    )
    return PdfItem(
        id = uriString,
        title = title,
        folderName = folderName,
        uri = uriString,
        currentPage = oneBasedPage,
        totalPages = pageCount,
        sizeBytes = fileSizeBytes,
        lastOpenedAt = lastOpenedAt,
        addedAt = addedAt,
        status = status,
        isPinned = isFavorite,
        isMissing = isMissing,
        hasBeenOpened = opened,
        progress = fraction,
        percent = ReadingProgress.percent(fraction),
        folderDisplayName = FolderKey.displayName(folderName)
    )
}

fun BookmarkEntity.toUiItem(): BookmarkUiItem = BookmarkUiItem(
    id = id,
    documentUri = documentUri,
    documentTitle = documentTitle,
    pageNumber = pageIndex + 1,
    title = title
)

fun AnnotationEntity.toUiItem(): AnnotationUiItem = AnnotationUiItem(
    id = id,
    documentUri = documentUri,
    documentTitle = documentTitle,
    pageNumber = pageIndex + 1,
    quotedText = quotedText,
    noteText = noteText,
    colorHex = colorHex,
    isSignature = signatureVectorData.isNotBlank()
)
