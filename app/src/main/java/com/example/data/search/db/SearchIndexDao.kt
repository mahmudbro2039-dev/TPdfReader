package com.example.data.search.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchIndexDao {

    @Query("SELECT * FROM indexed_doc WHERE uriString = :uri LIMIT 1")
    suspend fun getDoc(uri: String): IndexedDocEntity?

    @Query("SELECT * FROM indexed_doc WHERE docId = :id LIMIT 1")
    suspend fun getDocById(id: Long): IndexedDocEntity?

    @Query("SELECT * FROM indexed_doc")
    suspend fun getAllDocs(): List<IndexedDocEntity>

    @Query("SELECT * FROM indexed_doc")
    fun observeDocs(): Flow<List<IndexedDocEntity>>

    @Query("SELECT * FROM indexed_doc WHERE uriString = :uri LIMIT 1")
    fun observeDoc(uri: String): Flow<IndexedDocEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDoc(doc: IndexedDocEntity): Long

    @Update
    suspend fun updateDoc(doc: IndexedDocEntity)

    @Query("DELETE FROM indexed_doc WHERE docId = :id")
    suspend fun deleteDocById(id: Long)

    @Query("DELETE FROM indexed_doc")
    suspend fun deleteAllDocs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageTextFts>)

    @Query("DELETE FROM page_text_fts WHERE rowid BETWEEN :from AND :to")
    suspend fun deletePagesInRange(from: Long, to: Long)

    @Query("DELETE FROM page_text_fts")
    suspend fun deleteAllPages()

    @Query("SELECT rowid FROM page_text_fts WHERE rowid BETWEEN :from AND :to")
    suspend fun pageRowIdsInRange(from: Long, to: Long): List<Long>

    @Query("UPDATE indexed_doc SET textPages = (SELECT COUNT(*) FROM page_text_fts WHERE rowid BETWEEN :from AND :to) WHERE docId = :id")
    suspend fun recountTextPages(id: Long, from: Long, to: Long)

    @Query("UPDATE indexed_doc SET status = :status, updatedAt = :now WHERE docId = :id")
    suspend fun setStatus(id: Long, status: IndexStatus, now: Long)

    @Query("UPDATE indexed_doc SET pageCount = :pageCount, updatedAt = :now WHERE docId = :id")
    suspend fun setPageCount(id: Long, pageCount: Int, now: Long)

    @Query("UPDATE indexed_doc SET nextPage = :nextPage, updatedAt = :now WHERE docId = :id")
    suspend fun setNextPage(id: Long, nextPage: Int, now: Long)

    @Query("UPDATE indexed_doc SET ocrNextPage = :ocrNextPage, ocrFinished = :ocrFinished, updatedAt = :now WHERE docId = :id")
    suspend fun setOcrProgress(id: Long, ocrNextPage: Int, ocrFinished: Boolean, now: Long)

    @Query(
        """
        SELECT rowid AS rowid,
               snippet(page_text_fts, :open, :close, :ellipsis, -1, 12) AS snippet
        FROM page_text_fts WHERE page_text_fts MATCH :match LIMIT :limit
        """
    )
    suspend fun searchAll(
        match: String,
        open: String,
        close: String,
        ellipsis: String,
        limit: Int
    ): List<RawHit>

    @Query(
        """
        SELECT rowid AS rowid,
               snippet(page_text_fts, :open, :close, :ellipsis, -1, 12) AS snippet
        FROM page_text_fts WHERE page_text_fts MATCH :match AND rowid BETWEEN :from AND :to
        ORDER BY rowid LIMIT :limit
        """
    )
    suspend fun searchInDoc(
        match: String,
        from: Long,
        to: Long,
        open: String,
        close: String,
        ellipsis: String,
        limit: Int
    ): List<RawHit>
}
