package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDao
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.AnnotationType
import com.example.data.model.ReadingStatus
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.SamplePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfLibraryRepository(
    private val context: Context,
    private val pdfDao: PdfDao,
    val pdfEngine: PdfEngine
) {
    val allDocuments: Flow<List<PdfDocumentEntity>> = pdfDao.observeAllDocuments()
    val allBookmarks: Flow<List<BookmarkEntity>> = pdfDao.observeAllBookmarks()
    val allAnnotations: Flow<List<AnnotationEntity>> = pdfDao.observeAllAnnotations()

    fun observeDocument(uriString: String): Flow<PdfDocumentEntity?> =
        pdfDao.observeDocument(uriString)

    fun observeBookmarksForDocument(uriString: String): Flow<List<BookmarkEntity>> =
        pdfDao.observeBookmarksForDocument(uriString)

    fun observeAnnotationsForDocument(uriString: String): Flow<List<AnnotationEntity>> =
        pdfDao.observeAnnotationsForDocument(uriString)

    suspend fun getDocument(uriString: String): PdfDocumentEntity? =
        pdfDao.getDocument(uriString)

    suspend fun ensureStarterGuideExists() = withContext(Dispatchers.IO) {
        try {
            val guideFile = SamplePdfGenerator.ensureGuidePdfFile(context)
            val guideUriString = Uri.fromFile(guideFile).toString()
            if (pdfDao.getDocument(guideUriString) == null) {
                val pages = pdfEngine.inspectPageCount(guideUriString).coerceAtLeast(1)
                val now = System.currentTimeMillis()
                pdfDao.upsertDocument(
                    PdfDocumentEntity(
                        uriString = guideUriString,
                        title = SamplePdfGenerator.GUIDE_TITLE,
                        folderName = "Guides & Manuals",
                        fileSizeBytes = guideFile.length(),
                        pageCount = pages,
                        currentPage = 0,
                        readingStatus = ReadingStatus.READING.name,
                        isFavorite = true,
                        lastOpenedAt = now,
                        addedAt = now
                    )
                )
                // Seed a starter bookmark & highlight so Bookmarks and My Notes/Highlights showcase immediately
                pdfDao.insertBookmark(
                    BookmarkEntity(
                        documentUri = guideUriString,
                        documentTitle = SamplePdfGenerator.GUIDE_TITLE,
                        pageIndex = 1,
                        title = "Every Tool, One Tap Away"
                    )
                )
                pdfDao.insertAnnotation(
                    AnnotationEntity(
                        documentUri = guideUriString,
                        documentTitle = SamplePdfGenerator.GUIDE_TITLE,
                        pageIndex = 0,
                        type = AnnotationType.HIGHLIGHT.name,
                        quotedText = "Tap anywhere in the center of a page to enter True Edge-to-Edge Full-Screen Mode.",
                        noteText = "Full-screen mode with floating edge shortcut pills.",
                        colorHex = "#FACC15"
                    )
                )
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Non-fatal: starter sample creation should never crash startup on constrained storage.
        }

        try {
            val archFile = SamplePdfGenerator.ensureArchitecturePdfFile(context)
            val archUriString = Uri.fromFile(archFile).toString()
            if (pdfDao.getDocument(archUriString) == null) {
                val pages = pdfEngine.inspectPageCount(archUriString).coerceAtLeast(1)
                val now = System.currentTimeMillis() - 3_600_000L
                pdfDao.upsertDocument(
                    PdfDocumentEntity(
                        uriString = archUriString,
                        title = SamplePdfGenerator.ARCH_TITLE,
                        folderName = "Books & Research",
                        fileSizeBytes = archFile.length(),
                        pageCount = pages,
                        currentPage = 0,
                        readingStatus = ReadingStatus.TO_READ.name,
                        isFavorite = false,
                        lastOpenedAt = now,
                        addedAt = now
                    )
                )
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Non-fatal: starter sample creation should never crash startup on constrained storage.
        }
    }

    suspend fun registerPickedDocument(uri: Uri, targetFolder: String? = null): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val uriString = uri.toString()
            val existing = pdfDao.getDocument(uriString)
            val (displayName, fileSize, detectedFolder) = queryMetadata(uri)
            val folderName = targetFolder?.takeIf { it.isNotBlank() }
                ?: existing?.folderName?.takeIf { it.isNotBlank() }
                ?: detectedFolder
            val pageCount = pdfEngine.inspectPageCount(uriString)
            val now = System.currentTimeMillis()

            val entity = if (existing != null) {
                existing.copy(
                    title = displayName.ifBlank { existing.title },
                    folderName = folderName,
                    fileSizeBytes = if (fileSize > 0L) fileSize else existing.fileSizeBytes,
                    pageCount = if (pageCount > 0) pageCount else existing.pageCount,
                    lastOpenedAt = now
                )
            } else {
                PdfDocumentEntity(
                    uriString = uriString,
                    title = displayName.ifBlank { "Untitled PDF" },
                    folderName = folderName,
                    fileSizeBytes = fileSize,
                    pageCount = pageCount,
                    currentPage = 0,
                    readingStatus = ReadingStatus.READING.name,
                    lastOpenedAt = now,
                    addedAt = now
                )
            }

            pdfDao.upsertDocument(entity)
            entity
        }

    suspend fun updateProgress(
        uriString: String,
        currentPage: Int,
        pageCount: Int,
        zoomScale: Float,
        incognitoMode: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (incognitoMode) return@withContext
        val existing = pdfDao.getDocument(uriString)
        val computedStatus = when {
            existing?.status == ReadingStatus.ON_HOLD -> ReadingStatus.ON_HOLD
            existing?.status == ReadingStatus.ABANDONED -> ReadingStatus.ABANDONED
            pageCount > 0 && currentPage >= pageCount - 1 -> ReadingStatus.COMPLETED
            else -> ReadingStatus.READING
        }
        pdfDao.updateReadingProgress(
            uriString = uriString,
            page = currentPage.coerceAtLeast(0),
            pageCount = pageCount.coerceAtLeast(existing?.pageCount ?: 0),
            zoomScale = zoomScale.coerceIn(1f, 5f),
            timestamp = System.currentTimeMillis(),
            status = computedStatus.name
        )
    }

    suspend fun updateReadingStatus(uriString: String, status: ReadingStatus) {
        pdfDao.updateReadingStatus(uriString, status.name)
    }

    suspend fun moveDocumentToFolder(uriString: String, folderName: String) {
        val clean = folderName.trim().ifBlank { "Downloads" }
        pdfDao.updateDocumentFolder(uriString, clean)
    }

    suspend fun toggleFavorite(uriString: String, currentFavorite: Boolean) {
        pdfDao.updateFavorite(uriString, !currentFavorite)
    }

    suspend fun removeDocument(uriString: String) {
        pdfDao.deleteDocument(uriString)
    }

    suspend fun clearAllHistory() {
        pdfDao.clearAllLibraryData()
    }

    suspend fun addBookmark(
        documentUri: String,
        documentTitle: String,
        pageIndex: Int,
        customTitle: String
    ): Long {
        val cleanTitle = customTitle.trim().ifBlank { "Page ${pageIndex + 1}" }
        return pdfDao.insertBookmark(
            BookmarkEntity(
                documentUri = documentUri,
                documentTitle = documentTitle,
                pageIndex = pageIndex,
                title = cleanTitle
            )
        )
    }

    suspend fun renameBookmark(bookmarkId: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            pdfDao.renameBookmark(bookmarkId, trimmed)
        }
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        pdfDao.deleteBookmark(bookmarkId)
    }

    suspend fun deleteBookmarksAtPage(documentUri: String, pageIndex: Int) {
        pdfDao.deleteBookmarksAtPage(documentUri, pageIndex)
    }

    suspend fun addAnnotation(
        documentUri: String,
        documentTitle: String,
        pageIndex: Int,
        type: AnnotationType,
        quotedText: String,
        noteText: String,
        colorHex: String,
        signatureVectorData: String = ""
    ): Long {
        return pdfDao.insertAnnotation(
            AnnotationEntity(
                documentUri = documentUri,
                documentTitle = documentTitle,
                pageIndex = pageIndex,
                type = type.name,
                quotedText = quotedText.trim(),
                noteText = noteText.trim(),
                colorHex = colorHex,
                signatureVectorData = signatureVectorData
            )
        )
    }

    suspend fun updateAnnotation(annotationId: Long, noteText: String, colorHex: String) {
        pdfDao.updateAnnotation(annotationId, noteText.trim(), colorHex)
    }

    suspend fun deleteAnnotation(annotationId: Long) {
        pdfDao.deleteAnnotation(annotationId)
    }

    fun buildShareIntent(uriString: String, title: String): Intent? {
        val uri = Uri.parse(uriString)
        val shareUri: Uri = when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: return null)
                if (!file.exists()) return null
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            "content" -> uri
            else -> return null
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun launchPrintJob(activityContext: Context, uriString: String, title: String): Boolean {
        val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$title.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val input = when (uri.scheme) {
                        "file" -> File(uri.path ?: error("Missing path")).inputStream()
                        else -> context.contentResolver.openInputStream(uri)
                            ?: error("Cannot open stream")
                    }
                    input.use { src ->
                        FileOutputStream(destination.fileDescriptor).use { dst ->
                            src.copyTo(dst)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { err ->
                    callback.onWriteFailed(err.localizedMessage ?: "Print failed")
                }
            }
        }

        printManager.print(title, adapter, null)
        return true
    }

    suspend fun resolveDocumentTitle(uriString: String): String = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext ""
        val savedTitle = pdfDao.getDocument(uriString)?.title?.trim()
        if (!savedTitle.isNullOrBlank()) return@withContext savedTitle
        val uri = Uri.parse(uriString)
        val (displayName, _, _) = queryMetadata(uri)
        displayName.trim().ifBlank { fallbackTitleFromUri(uriString) }
    }

    fun fallbackTitleFromUri(uriString: String): String {
        if (uriString.isBlank()) return ""
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return ""
        val rawSegment = uri.lastPathSegment
            ?: uri.path
            ?: return ""
        val decodedSegment = runCatching { Uri.decode(rawSegment) }.getOrDefault(rawSegment)
        val fileName = decodedSegment
            .substringAfterLast('/')
            .substringAfterLast(':')
            .trim()
        val stripped = if (fileName.endsWith(".pdf", ignoreCase = true)) {
            fileName.dropLast(4)
        } else {
            fileName
        }
        return stripped.trim()
    }

    private fun queryMetadata(uri: Uri): Triple<String, Long, String> {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            val rawName = file.name
            val cleanName = if (rawName.endsWith(".pdf", ignoreCase = true)) {
                rawName.dropLast(4)
            } else {
                rawName
            }.trim().ifBlank { "PDF Document" }
            val parentFolder = file.parentFile?.name?.takeIf { it.isNotBlank() } ?: "Internal Storage"
            return Triple(cleanName, file.length(), parentFolder)
        }

        var name = ""
        var size = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: ""
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }
        val trimmedName = name.trim()
        val cleanName = (if (trimmedName.endsWith(".pdf", ignoreCase = true)) {
            trimmedName.dropLast(4)
        } else {
            trimmedName
        }).trim().ifBlank {
            fallbackTitleFromUri(uri.toString()).ifBlank { "PDF Document" }
        }

        val rawSegment = Uri.decode(uri.lastPathSegment ?: "")
        val parentFolder = when {
            rawSegment.contains('/') -> rawSegment.substringBeforeLast('/').substringAfterLast('/').ifBlank { "Downloads" }
            uri.authority?.contains("downloads", ignoreCase = true) == true -> "Downloads"
            else -> "Documents"
        }

        return Triple(cleanName, size, parentFolder)
    }
}
