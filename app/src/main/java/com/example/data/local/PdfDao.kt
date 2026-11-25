package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

data class IndexCandidateRow(
    val uriString: String,
    val lastOpenedAt: Long
)

@Dao
interface PdfDao {
    @Query("SELECT uriString, lastOpenedAt FROM pdf_documents")
    fun observeIndexCandidates(): Flow<List<IndexCandidateRow>>

    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun observeAllDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE uriString = :uriString LIMIT 1")
    suspend fun getDocument(uriString: String): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents WHERE uriString = :uriString LIMIT 1")
    fun observeDocument(uriString: String): Flow<PdfDocumentEntity?>

    @Query("SELECT uriString FROM pdf_documents")
    suspend fun getAllDocumentUris(): List<String>

    @Query("SELECT uriString FROM pdf_documents WHERE pageCount <= 0 ORDER BY lastOpenedAt DESC, addedAt DESC LIMIT :limit")
    suspend fun getDocumentsMissingPageCount(limit: Int = 30): List<String>

    @Query("UPDATE pdf_documents SET lastOpenedAt = 0 WHERE currentPage = 0 AND readingStatus = 'TO_READ' AND isFavorite = 0 AND lastOpenedAt != 0")
    suspend fun repairFalseLastOpenedTimestamps(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(document: PdfDocumentEntity): Long

    @Update
    suspend fun updateDocument(document: PdfDocumentEntity)

    @Query(
        """
        UPDATE pdf_documents
        SET title = CASE WHEN :title != '' THEN :title ELSE title END,
            fileSizeBytes = CASE WHEN :fileSizeBytes > 0 THEN :fileSizeBytes ELSE fileSizeBytes END,
            pageCount = CASE WHEN :pageCount > 0 THEN :pageCount ELSE pageCount END
        WHERE uriString = :uriString
        """
    )
    suspend fun updateFileMetadata(
        uriString: String,
        title: String,
        fileSizeBytes: Long,
        pageCount: Int
    )

    @Query("UPDATE pdf_documents SET lastOpenedAt = :lastOpenedAt WHERE uriString = :uriString")
    suspend fun updateLastOpenedAt(uriString: String, lastOpenedAt: Long)

    @Transaction
    suspend fun markOpened(uriString: String, now: Long, pageCount: Int = 0) {
        updateLastOpenedAt(uriString, now)
        if (pageCount > 0) {
            fillMissingPageCount(uriString, pageCount)
        }
    }

    @Transaction
    suspend fun registerOrRefresh(document: PdfDocumentEntity) {
        val rowId = insertIfAbsent(document)
        if (rowId == -1L) {
            updateFileMetadata(
                uriString = document.uriString,
                title = document.title,
                fileSizeBytes = document.fileSizeBytes,
                pageCount = document.pageCount
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDocumentsIfAbsent(documents: List<PdfDocumentEntity>): List<Long>

    @Query("UPDATE pdf_documents SET pageCount = :pageCount WHERE uriString = :uriString AND pageCount <= 0")
    suspend fun fillMissingPageCount(uriString: String, pageCount: Int)

    @Transaction
    suspend fun fillMissingPageCounts(updates: Map<String, Int>) {
        updates.forEach { (uriString, pageCount) ->
            if (pageCount > 0) {
                fillMissingPageCount(uriString, pageCount)
            }
        }
    }

    @Query(
        "UPDATE pdf_documents SET currentPage = :page, pageCount = :pageCount, zoomScale = :zoomScale, lastOpenedAt = :timestamp, readingStatus = :status WHERE uriString = :uriString"
    )
    suspend fun updateReadingProgressRow(
        uriString: String,
        page: Int,
        pageCount: Int,
        zoomScale: Float,
        timestamp: Long,
        status: String
    )

    @Transaction
    suspend fun updateProgressTransactional(
        uriString: String,
        currentPage: Int,
        pageCount: Int,
        zoomScale: Float,
        timestamp: Long
    ) {
        val row = getDocument(uriString) ?: return
        val pageCountToUse = if (pageCount > 0) pageCount else row.pageCount
        val safePage = if (pageCountToUse > 0) {
            currentPage.coerceIn(0, pageCountToUse - 1)
        } else {
            currentPage.coerceAtLeast(0)
        }
        val newStatus = ReadingProgress.nextStatus(
            current = row.status,
            currentPage = safePage,
            pageCount = pageCountToUse
        )
        updateReadingProgressRow(
            uriString = uriString,
            page = safePage,
            pageCount = pageCountToUse,
            zoomScale = zoomScale.coerceIn(1f, 5f),
            timestamp = timestamp,
            status = newStatus.name
        )
    }

    @Query("UPDATE pdf_documents SET readingStatus = :status WHERE uriString = :uriString")
    suspend fun updateReadingStatus(uriString: String, status: String)

    @Query("UPDATE pdf_documents SET folderName = :folderName WHERE uriString = :uriString")
    suspend fun updateDocumentFolder(uriString: String, folderName: String)

    @Query(
        """
        UPDATE pdf_documents
        SET folderName = :newFolderKey
        WHERE uriString = :uriString
          AND folderName = :legacyLeafName
        """
    )
    suspend fun upgradeLegacyFolderIfMatches(
        uriString: String,
        legacyLeafName: String,
        newFolderKey: String
    )

    @Transaction
    suspend fun upgradeLegacyFolders(updates: List<Triple<String, String, String>>) {
        updates.forEach { (uriString, legacyLeafName, newFolderKey) ->
            upgradeLegacyFolderIfMatches(uriString, legacyLeafName, newFolderKey)
        }
    }

    @Query("UPDATE pdf_documents SET isFavorite = :isFavorite WHERE uriString = :uriString")
    suspend fun updateFavorite(uriString: String, isFavorite: Boolean)

    @Query("DELETE FROM pdf_documents WHERE uriString = :uriString")
    suspend fun deleteDocument(uriString: String)

    @Query("DELETE FROM pdf_documents")
    suspend fun clearAllDocuments()

    @Query("DELETE FROM bookmarks")
    suspend fun clearAllBookmarks()

    @Query("DELETE FROM annotations")
    suspend fun clearAllAnnotations()

    @Transaction
    suspend fun clearAllLibraryData() {
        clearAllAnnotations()
        clearAllBookmarks()
        clearAllDocuments()
    }

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE documentUri = :documentUri ORDER BY pageIndex ASC, createdAt DESC")
    fun observeBookmarksForDocument(documentUri: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("UPDATE bookmarks SET title = :title WHERE id = :bookmarkId")
    suspend fun renameBookmark(bookmarkId: Long, title: String)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Query("DELETE FROM bookmarks WHERE documentUri = :documentUri AND pageIndex = :pageIndex")
    suspend fun deleteBookmarksAtPage(documentUri: String, pageIndex: Int)

    @Query("SELECT * FROM annotations ORDER BY createdAt DESC")
    fun observeAllAnnotations(): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE documentUri = :documentUri ORDER BY pageIndex ASC, createdAt DESC")
    fun observeAnnotationsForDocument(documentUri: String): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Query("UPDATE annotations SET noteText = :noteText, colorHex = :colorHex WHERE id = :annotationId")
    suspend fun updateAnnotation(annotationId: Long, noteText: String, colorHex: String)

    @Query("DELETE FROM annotations WHERE id = :annotationId")
    suspend fun deleteAnnotation(annotationId: Long)
}
