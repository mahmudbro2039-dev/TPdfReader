package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ReadingProgress
import com.example.data.model.ReadingStatus

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey
    val uriString: String,
    val title: String,
    val folderName: String = "Internal Storage",
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoomScale: Float = 1f,
    val readingStatus: String = ReadingStatus.TO_READ.name,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val addedAt: Long = System.currentTimeMillis()
) {
    val status: ReadingStatus
        get() = ReadingStatus.fromName(readingStatus)

    val hasBeenOpened: Boolean
        get() = ReadingProgress.hasBeenOpened(lastOpenedAt)

    val progressFraction: Float
        get() = ReadingProgress.fraction(
            currentPage = currentPage,
            pageCount = pageCount,
            lastOpenedAt = lastOpenedAt,
            isCompleted = status == ReadingStatus.COMPLETED
        )

    val progressPercent: Int
        get() = ReadingProgress.percent(progressFraction)
}
