package com.example.data.search.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

enum class IndexStatus {
    PENDING,
    INDEXING,
    DONE,
    FAILED,
    ENCRYPTED
}

@Entity(
    tableName = "indexed_doc",
    indices = [Index(value = ["uriString"], unique = true)]
)
data class IndexedDocEntity(
    @PrimaryKey(autoGenerate = true)
    val docId: Long = 0, // AUTOINCREMENT: ids are never reused
    val uriString: String,
    val fingerprint: String,
    val indexVersion: Int,
    val pageCount: Int = 0,
    val nextPage: Int = 0,          // text pass resume pointer (0-based)
    val textPages: Int = 0,         // pages that currently have a row in the FTS table
    val status: IndexStatus = IndexStatus.PENDING,
    val ocrNextPage: Int = 0,       // OCR resume pointer (0-based)
    val ocrFinished: Boolean = false,
    val updatedAt: Long = 0L
)

@Fts4(tokenizer = FtsOptions.TOKENIZER_SIMPLE)
@Entity(tableName = "page_text_fts")
data class PageTextFts(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long, // = PageRowId.of(docId, pageIndex)
    val text: String // already passed through forIndex()
)

data class RawHit(
    val rowid: Long,
    val snippet: String
)
