package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.UriAccess
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDao
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.AnnotationType
import com.example.data.model.FolderKey
import com.example.data.model.ReadingStatus
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.SamplePdfGenerator
import com.example.data.search.SearchIndexRepository
import com.example.ui.theme.ContentColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class PdfLibraryRepository(
    private val context: Context,
    private val pdfDao: PdfDao,
    val pdfEngine: PdfEngine,
    private val searchIndexRepository: SearchIndexRepository? = null
) {
    val allDocuments: Flow<List<PdfDocumentEntity>> = pdfDao.observeAllDocuments()
    val allBookmarks: Flow<List<BookmarkEntity>> = pdfDao.observeAllBookmarks()
    val allAnnotations: Flow<List<AnnotationEntity>> = pdfDao.observeAllAnnotations()

    private val _missingUris = MutableStateFlow<Set<String>>(emptySet())
    val missingUris: StateFlow<Set<String>> = _missingUris.asStateFlow()

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
                pdfDao.registerOrRefresh(
                    PdfDocumentEntity(
                        uriString = guideUriString,
                        title = SamplePdfGenerator.GUIDE_TITLE,
                        folderName = FolderKey.virtual(context.getString(R.string.starter_guide_folder)),
                        fileSizeBytes = guideFile.length(),
                        pageCount = pages,
                        currentPage = 0,
                        readingStatus = ReadingStatus.TO_READ.name,
                        isFavorite = true,
                        lastOpenedAt = 0L,
                        addedAt = now
                    )
                )
                // Seed a starter bookmark & highlight so Bookmarks and My Notes/Highlights showcase immediately
                pdfDao.insertBookmark(
                    BookmarkEntity(
                        documentUri = guideUriString,
                        documentTitle = SamplePdfGenerator.GUIDE_TITLE,
                        pageIndex = 1,
                        title = context.getString(R.string.starter_bookmark_title)
                    )
                )
                pdfDao.insertAnnotation(
                    AnnotationEntity(
                        documentUri = guideUriString,
                        documentTitle = SamplePdfGenerator.GUIDE_TITLE,
                        pageIndex = 0,
                        type = AnnotationType.HIGHLIGHT.name,
                        quotedText = context.getString(R.string.starter_highlight_quote),
                        noteText = context.getString(R.string.starter_highlight_note),
                        colorHex = ContentColors.highlightYellowHex
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
                pdfDao.registerOrRefresh(
                    PdfDocumentEntity(
                        uriString = archUriString,
                        title = SamplePdfGenerator.ARCH_TITLE,
                        folderName = FolderKey.virtual(context.getString(R.string.starter_arch_folder)),
                        fileSizeBytes = archFile.length(),
                        pageCount = pages,
                        currentPage = 0,
                        readingStatus = ReadingStatus.TO_READ.name,
                        isFavorite = false,
                        lastOpenedAt = 0L,
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

    suspend fun registerPickedDocument(
        uri: Uri,
        targetFolder: String? = null,
        incognitoMode: Boolean = false
    ): PdfDocumentEntity =
        withContext(Dispatchers.IO) {
            val (displayName, fileSize, detectedFolder) = queryMetadata(uri)
            val persisted = UriAccess.persistRead(context, uri)
            val effectiveUri = if (persisted) {
                uri
            } else {
                copyUriToPrivateStorage(uri, fileSize) ?: uri
            }

            val uriString = effectiveUri.toString()
            val effectiveSize = if (effectiveUri.scheme == ContentResolver.SCHEME_FILE) {
                effectiveUri.path?.let { File(it).length() }?.takeIf { it > 0L } ?: fileSize
            } else {
                fileSize
            }
            val folderName = targetFolder?.takeIf { it.isNotBlank() }?.let { FolderKey.canonical(it) }
                ?: detectedFolder
            val pageCount = pdfEngine.inspectPageCount(uriString)
            val now = System.currentTimeMillis()

            val newDoc = PdfDocumentEntity(
                uriString = uriString,
                title = displayName.ifBlank { context.getString(R.string.untitled_pdf) },
                folderName = folderName,
                fileSizeBytes = effectiveSize,
                pageCount = pageCount,
                currentPage = 0,
                readingStatus = ReadingStatus.TO_READ.name,
                lastOpenedAt = if (incognitoMode) 0L else now,
                addedAt = now
            )

            pdfDao.registerOrRefresh(newDoc)
            if (!incognitoMode) {
                pdfDao.updateLastOpenedAt(uriString, now)
            }
            _missingUris.update { it - uriString }
            UriAccess.trimPersisted(context, pdfDao.getAllDocumentUris().toSet())
            pdfDao.getDocument(uriString) ?: newDoc
        }

    suspend fun refreshAvailability() = withContext(Dispatchers.IO) {
        val docs = pdfDao.getAllDocumentUris()
        val externalMounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        val internalFilesPath = context.filesDir.absolutePath
        val internalCachePath = context.cacheDir.absolutePath
        val missing = HashSet<String>()
        for (rawUri in docs) {
            val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: continue
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                val path = uri.path.orEmpty()
                val isAppPrivate = path.startsWith(internalFilesPath) || path.startsWith(internalCachePath)
                if (!isAppPrivate && !externalMounted) {
                    continue
                }
            }
            if (!UriAccess.isReadable(context, uri)) {
                missing.add(rawUri)
            }
        }
        _missingUris.value = missing
    }

    private fun copyUriToPrivateStorage(sourceUri: Uri, expectedSize: Long): Uri? {
        return runCatching {
            val dir = File(context.filesDir, "imported_pdfs")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "${sha256Hex(sourceUri.toString()).take(16)}.pdf")
            if (destFile.exists() && expectedSize > 0L && destFile.length() == expectedSize) {
                return Uri.fromFile(destFile)
            }
            val tmpFile = File(dir, "${destFile.name}.tmp")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (tmpFile.length() <= 0L) {
                tmpFile.delete()
                return null
            }
            if (destFile.exists()) destFile.delete()
            if (!tmpFile.renameTo(destFile)) {
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }
            Uri.fromFile(destFile)
        }.getOrNull()
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun markOpened(
        uriString: String,
        now: Long = System.currentTimeMillis(),
        pageCount: Int = 0
    ) = withContext(Dispatchers.IO) {
        pdfDao.markOpened(uriString = uriString, now = now, pageCount = pageCount)
    }

    suspend fun updateProgress(
        uriString: String,
        currentPage: Int,
        pageCount: Int,
        zoomScale: Float,
        incognitoMode: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (incognitoMode) return@withContext
        pdfDao.updateProgressTransactional(
            uriString = uriString,
            currentPage = currentPage,
            pageCount = pageCount,
            zoomScale = zoomScale,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun updateReadingStatus(uriString: String, status: ReadingStatus) {
        pdfDao.updateReadingStatus(uriString, status.name)
    }

    suspend fun moveDocumentToFolder(uriString: String, folderName: String) {
        val clean = FolderKey.canonical(folderName)
        pdfDao.updateDocumentFolder(uriString, clean)
    }

    suspend fun toggleFavorite(uriString: String, currentFavorite: Boolean) {
        pdfDao.updateFavorite(uriString, !currentFavorite)
    }

    suspend fun removeDocument(uriString: String) {
        pdfDao.deleteDocument(uriString)
        _missingUris.update { it - uriString }
        searchIndexRepository?.remove(uriString)
    }

    suspend fun clearAllHistory() {
        pdfDao.clearAllLibraryData()
        searchIndexRepository?.clearAll()
    }

    suspend fun runOneTimeLastOpenedRepair(): Int = withContext(Dispatchers.IO) {
        pdfDao.repairFalseLastOpenedTimestamps()
    }

    suspend fun addBookmark(
        documentUri: String,
        documentTitle: String,
        pageIndex: Int,
        customTitle: String
    ): Long {
        val cleanTitle = customTitle.trim().ifBlank {
            context.getString(R.string.page_badge_short, pageIndex + 1)
        }
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

    suspend fun stampSignatureAnnotation(
        documentUri: String,
        documentTitle: String,
        pageIndex: Int,
        signerLabel: String,
        colorHex: String,
        signatureVectorData: String
    ): Long {
        return addAnnotation(
            documentUri = documentUri,
            documentTitle = documentTitle,
            pageIndex = pageIndex,
            type = AnnotationType.SIGNATURE,
            quotedText = signerLabel.ifBlank { context.getString(R.string.reader_signature_default_label) },
            noteText = context.getString(R.string.reader_signed_on_page, pageIndex + 1),
            colorHex = colorHex,
            signatureVectorData = signatureVectorData
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
                    callback.onWriteFailed(
                        err.localizedMessage ?: context.getString(R.string.print_failed)
                    )
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
        displayName.trim().ifBlank {
            fallbackTitleFromUri(uriString).ifBlank { context.getString(R.string.reader_default_title) }
        }
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
            }.trim().ifBlank { context.getString(R.string.reader_default_title) }
            val parentPath = file.parentFile?.let { parent ->
                runCatching { parent.canonicalPath }.getOrElse { parent.absolutePath }
            }
            val parentFolder = FolderKey.device(parentPath)
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
            fallbackTitleFromUri(uri.toString()).ifBlank { context.getString(R.string.reader_default_title) }
        }

        val rawSegment = runCatching { Uri.decode(uri.lastPathSegment ?: "") }.getOrDefault("")
        val parentFolder = if (rawSegment.contains('/')) {
            val relPath = rawSegment.substringAfter(':', rawSegment).substringBeforeLast('/').trim('/')
            if (relPath.isNotBlank()) {
                @Suppress("DEPRECATION")
                val externalRoot = if (uri.authority == "com.android.externalstorage.documents" &&
                    rawSegment.startsWith("primary:")
                ) {
                    runCatching { Environment.getExternalStorageDirectory()?.absolutePath }.getOrNull()
                } else {
                    null
                }
                if (!externalRoot.isNullOrBlank()) {
                    FolderKey.device("$externalRoot/$relPath")
                } else {
                    FolderKey.device(relPath)
                }
            } else {
                FolderKey.IMPORTED
            }
        } else {
            FolderKey.IMPORTED
        }

        return Triple(cleanName, size, parentFolder)
    }
}
