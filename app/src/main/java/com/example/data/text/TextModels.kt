package com.example.data.text

import android.net.Uri
import java.io.Closeable
import kotlinx.coroutines.flow.Flow

enum class TextQuality {
    OK,
    EMPTY,
    GARBLED,
    LEGACY_FONT,
}

data class PageText(
    val pageIndex: Int,
    val text: String,
    val quality: TextQuality,
)

enum class OpenFailure {
    ENCRYPTED,
    CORRUPT,
    IO,
    OUT_OF_MEMORY,
}

sealed interface OpenResult {
    data class Ok(val session: TextSession) : OpenResult
    data class Error(val failure: OpenFailure) : OpenResult
}

interface TextSession : Closeable {
    val pageCount: Int
    suspend fun page(index: Int): PageText
    fun pages(from: Int = 0): Flow<PageText>
}

interface PdfTextEngine {
    suspend fun open(uri: Uri): OpenResult
}
