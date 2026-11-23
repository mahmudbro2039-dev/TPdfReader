package com.example.ui.model

import androidx.compose.runtime.Immutable
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.AppThemeMode
import com.example.ui.theme.ReadingStatus
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt
import com.example.data.model.ReadingStatus as DataReadingStatus

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
    val isPinned: Boolean = false
)

/** THE ONLY progress formula in the whole app. Bar width and % must both use it. */
val PdfItem.progress: Float
    get() = if (totalPages <= 0) 0f else (currentPage.coerceIn(0, totalPages).toFloat() / totalPages).coerceIn(0f, 1f)

val PdfItem.percent: Int
    get() = (progress * 100).roundToInt()

@Immutable
data class FolderItem(
    val name: String,
    val pdfCount: Int,
    val totalSizeBytes: Long,
    val lastUpdatedAt: Long
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

fun DataReadingStatus.toUiStatus(): ReadingStatus = when (this) {
    DataReadingStatus.TO_READ -> ReadingStatus.ToRead
    DataReadingStatus.READING -> ReadingStatus.Reading
    DataReadingStatus.COMPLETED -> ReadingStatus.Completed
    DataReadingStatus.ON_HOLD, DataReadingStatus.ABANDONED -> ReadingStatus.OnHold
}

fun ReadingStatus.toDataStatus(): DataReadingStatus = when (this) {
    ReadingStatus.ToRead -> DataReadingStatus.TO_READ
    ReadingStatus.Reading -> DataReadingStatus.READING
    ReadingStatus.Completed -> DataReadingStatus.COMPLETED
    ReadingStatus.OnHold -> DataReadingStatus.ON_HOLD
}

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

fun PdfDocumentEntity.toPdfItem(): PdfItem {
    val oneBasedPage = if (pageCount <= 0) 0 else (currentPage + 1).coerceIn(1, pageCount)
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
        status = status.toUiStatus(),
        isPinned = isFavorite
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
