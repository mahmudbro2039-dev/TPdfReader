package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedAt DESC")
    fun observeAllDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE uriString = :uriString LIMIT 1")
    suspend fun getDocument(uriString: String): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents WHERE uriString = :uriString LIMIT 1")
    fun observeDocument(uriString: String): Flow<PdfDocumentEntity?>

    @Query("SELECT uriString FROM pdf_documents")
    suspend fun getAllDocumentUris(): List<String>

    @Query("SELECT uriString FROM pdf_documents WHERE pageCount <= 0 ORDER BY lastOpenedAt DESC LIMIT :limit")
    suspend fun getDocumentsMissingPageCount(limit: Int = 30): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: PdfDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDocumentsIfAbsent(documents: List<PdfDocumentEntity>): List<Long>

    @Query("UPDATE pdf_documents SET pageCount = :pageCount WHERE uriString = :uriString AND pageCount <= 0")
    suspend fun fillMissingPageCount(uriString: String, pageCount: Int)

    @Query(
        "UPDATE pdf_documents SET currentPage = :page, pageCount = :pageCount, zoomScale = :zoomScale, lastOpenedAt = :timestamp, readingStatus = :status WHERE uriString = :uriString"
    )
    suspend fun updateReadingProgress(
        uriString: String,
        page: Int,
        pageCount: Int,
        zoomScale: Float,
        timestamp: Long,
        status: String
    )

    @Query("UPDATE pdf_documents SET readingStatus = :status WHERE uriString = :uriString")
    suspend fun updateReadingStatus(uriString: String, status: String)

    @Query("UPDATE pdf_documents SET folderName = :folderName WHERE uriString = :uriString")
    suspend fun updateDocumentFolder(uriString: String, folderName: String)

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
