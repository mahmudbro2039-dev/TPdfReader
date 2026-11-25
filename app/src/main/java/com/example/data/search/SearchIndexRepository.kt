package com.example.data.search

import androidx.room.withTransaction
import com.example.data.search.db.IndexStatus
import com.example.data.search.db.IndexedDocEntity
import com.example.data.search.db.PageTextFts
import com.example.data.search.db.SearchIndexDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

const val INDEX_VERSION = 1 // bump when SearchTextNormalizer or page rules change => full re-index
const val SNIPPET_OPEN = "\u0001"
const val SNIPPET_CLOSE = "\u0002"
const val SNIPPET_ELLIPSIS = "\u2026"

data class IndexHit(
    val uriString: String,
    val pageIndex: Int,
    val snippet: String
)

class SearchIndexRepository(
    private val db: SearchIndexDatabase
) {
    private val dao get() = db.searchIndexDao()

    suspend fun getDoc(uri: String): IndexedDocEntity? = withContext(Dispatchers.IO) {
        dao.getDoc(uri)
    }

    suspend fun getAllDocs(): List<IndexedDocEntity> = withContext(Dispatchers.IO) {
        dao.getAllDocs()
    }

    suspend fun getOrCreate(uri: String, fingerprint: String): IndexedDocEntity =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val now = System.currentTimeMillis()
                val existing = dao.getDoc(uri)
                if (existing == null) {
                    val insertedId = dao.insertDoc(
                        IndexedDocEntity(
                            uriString = uri,
                            fingerprint = fingerprint,
                            indexVersion = INDEX_VERSION,
                            updatedAt = now
                        )
                    )
                    if (insertedId != -1L) {
                        dao.getDocById(insertedId)!!
                    } else {
                        dao.getDoc(uri)!!
                    }
                } else if (existing.fingerprint != fingerprint || existing.indexVersion != INDEX_VERSION) {
                    dao.deletePagesInRange(
                        from = PageRowId.firstOf(existing.docId),
                        to = PageRowId.lastOf(existing.docId)
                    )
                    val reset = existing.copy(
                        fingerprint = fingerprint,
                        indexVersion = INDEX_VERSION,
                        pageCount = 0,
                        nextPage = 0,
                        textPages = 0,
                        status = IndexStatus.PENDING,
                        ocrNextPage = 0,
                        ocrFinished = false,
                        updatedAt = now
                    )
                    dao.updateDoc(reset)
                    reset
                } else {
                    existing
                }
            }
        }

    suspend fun writeTextBatch(
        docId: Long,
        pages: List<Pair<Int, String>>,
        nextPage: Int
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val ftsRows = pages.mapNotNull { (pageIndex, text) ->
                if (text.isBlank()) null
                else PageTextFts(rowId = PageRowId.of(docId, pageIndex), text = text)
            }
            if (ftsRows.isNotEmpty()) {
                dao.insertPages(ftsRows)
            }
            dao.setNextPage(docId, nextPage, System.currentTimeMillis())
            dao.recountTextPages(
                id = docId,
                from = PageRowId.firstOf(docId),
                to = PageRowId.lastOf(docId)
            )
        }
    }

    suspend fun writeOcrPage(
        docId: Long,
        pageIndex: Int,
        text: String,
        ocrNextPage: Int
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (text.isNotBlank()) {
                dao.insertPages(
                    listOf(PageTextFts(rowId = PageRowId.of(docId, pageIndex), text = text))
                )
            }
            val finished = dao.getDocById(docId)?.ocrFinished ?: false
            dao.setOcrProgress(docId, ocrNextPage, finished, System.currentTimeMillis())
            dao.recountTextPages(
                id = docId,
                from = PageRowId.firstOf(docId),
                to = PageRowId.lastOf(docId)
            )
        }
    }

    suspend fun advanceOcrPointer(docId: Long, ocrNextPage: Int) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val finished = dao.getDocById(docId)?.ocrFinished ?: false
            dao.setOcrProgress(docId, ocrNextPage, finished, System.currentTimeMillis())
        }
    }

    suspend fun setStatus(docId: Long, status: IndexStatus) = withContext(Dispatchers.IO) {
        dao.setStatus(docId, status, System.currentTimeMillis())
    }

    suspend fun setPageCount(docId: Long, pageCount: Int) = withContext(Dispatchers.IO) {
        dao.setPageCount(docId, pageCount, System.currentTimeMillis())
    }

    suspend fun finishOcr(docId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val doc = dao.getDocById(docId) ?: return@withTransaction
            dao.setOcrProgress(docId, doc.pageCount, true, System.currentTimeMillis())
        }
    }

    suspend fun indexedPageSet(docId: Long): Set<Int> = withContext(Dispatchers.IO) {
        dao.pageRowIdsInRange(
            from = PageRowId.firstOf(docId),
            to = PageRowId.lastOf(docId)
        ).mapTo(HashSet()) { rowId ->
            PageRowId.pageOf(rowId)
        }
    }

    suspend fun remove(uri: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val doc = dao.getDoc(uri) ?: return@withTransaction
            dao.deletePagesInRange(
                from = PageRowId.firstOf(doc.docId),
                to = PageRowId.lastOf(doc.docId)
            )
            dao.deleteDocById(doc.docId)
        }
    }

    suspend fun pruneTo(knownUris: Set<String>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val docs = dao.getAllDocs()
            for (doc in docs) {
                if (doc.uriString !in knownUris) {
                    dao.deletePagesInRange(
                        from = PageRowId.firstOf(doc.docId),
                        to = PageRowId.lastOf(doc.docId)
                    )
                    dao.deleteDocById(doc.docId)
                }
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.deleteAllPages()
            dao.deleteAllDocs()
        }
    }

    fun observeDoc(uri: String): Flow<IndexedDocEntity?> = dao.observeDoc(uri)

    fun observeAllDocs(): Flow<List<IndexedDocEntity>> = dao.observeDocs()

    suspend fun searchAll(userQuery: String, limit: Int = 300): List<IndexHit> =
        withContext(Dispatchers.IO) {
            val match = SearchTextNormalizer.buildMatchQuery(userQuery) ?: return@withContext emptyList()
            val rawHits = dao.searchAll(
                match = match,
                open = SNIPPET_OPEN,
                close = SNIPPET_CLOSE,
                ellipsis = SNIPPET_ELLIPSIS,
                limit = limit
            )
            if (rawHits.isEmpty()) return@withContext emptyList()
            val docsById = dao.getAllDocs().associateBy { it.docId }
            rawHits.mapNotNull { hit ->
                val uri = docsById[PageRowId.docOf(hit.rowid)]?.uriString ?: return@mapNotNull null
                IndexHit(
                    uriString = uri,
                    pageIndex = PageRowId.pageOf(hit.rowid),
                    snippet = hit.snippet
                )
            }
        }

    suspend fun searchInDocument(
        uri: String,
        userQuery: String,
        limit: Int = 500
    ): List<IndexHit> = withContext(Dispatchers.IO) {
        val match = SearchTextNormalizer.buildMatchQuery(userQuery) ?: return@withContext emptyList()
        val doc = dao.getDoc(uri) ?: return@withContext emptyList()
        val rawHits = dao.searchInDoc(
            match = match,
            from = PageRowId.firstOf(doc.docId),
            to = PageRowId.lastOf(doc.docId),
            open = SNIPPET_OPEN,
            close = SNIPPET_CLOSE,
            ellipsis = SNIPPET_ELLIPSIS,
            limit = limit
        )
        rawHits.map { hit ->
            IndexHit(
                uriString = uri,
                pageIndex = PageRowId.pageOf(hit.rowid),
                snippet = hit.snippet
            )
        }
    }
}
