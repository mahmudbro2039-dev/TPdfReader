package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
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

    val progressFraction: Float
        get() = if (pageCount <= 1) {
            if (pageCount == 1) 1f else 0f
        } else {
            ((currentPage + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}
