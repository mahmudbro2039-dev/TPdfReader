package com.example.data.pdf

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.PdfDao
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.ReadingStatus
import com.example.data.system.DevicePermissionsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class PdfScanProgress(
    val isScanning: Boolean = false,
    val totalFoundCount: Int = 0,
    val newlyAddedCount: Int = 0,
    val lastCompletedAtMs: Long = 0L
)

class DevicePdfScanner(
    private val context: Context,
    private val pdfDao: PdfDao,
    private val pdfEngine: PdfEngine
) {
    private val _progress = MutableStateFlow(PdfScanProgress())
    val progress: StateFlow<PdfScanProgress> = _progress.asStateFlow()

    private val scanMutex = Mutex()

    suspend fun scanDevicePdfs(
        includeMediaStore: Boolean = true,
        customRoots: List<File>? = null
    ): PdfScanProgress = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            _progress.update {
                it.copy(
                    isScanning = true,
                    totalFoundCount = 0,
                    newlyAddedCount = 0
                )
            }

            val knownUris = Collections.synchronizedSet(HashSet<String>(256))
            val knownCanonicalPaths = Collections.synchronizedSet(HashSet<String>(256))
            val existingUris = pdfDao.getAllDocumentUris()
            existingUris.forEach { rawUri ->
                knownUris.add(rawUri)
                runCatching {
                    val parsed = Uri.parse(rawUri)
                    if (parsed.scheme == "file" && !parsed.path.isNullOrBlank()) {
                        knownCanonicalPaths.add(canonicalOrAbsolute(File(parsed.path!!)))
                    }
                }
            }

            val totalDiscoveredCounter = AtomicInteger(0)
            val totalInsertedCounter = AtomicInteger(0)
            val now = System.currentTimeMillis()

            // Phase 1: Instant indexed MediaStore query (fast metadata batch)
            if (includeMediaStore && (DevicePermissionsHelper.hasLegacyReadPermission(context) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
                val mediaStoreDocs = scanMediaStoreFast(
                    knownUris = knownUris,
                    knownCanonicalPaths = knownCanonicalPaths,
                    discoveredCounter = totalDiscoveredCounter,
                    nowMs = now
                )
                if (mediaStoreDocs.isNotEmpty()) {
                    val added = insertBatchCountingNew(mediaStoreDocs)
                    totalInsertedCounter.addAndGet(added)
                    _progress.update {
                        it.copy(
                            totalFoundCount = totalDiscoveredCounter.get(),
                            newlyAddedCount = totalInsertedCounter.get()
                        )
                    }
                }
            }

            // Phase 2: Ultra-fast parallel multi-worker filesystem sweep across storage volumes
            val roots = customRoots ?: discoverStorageRoots()
            val canTraverseFilesystem = customRoots != null || DevicePermissionsHelper.hasFullFileAccess(context)
            if (canTraverseFilesystem && roots.isNotEmpty()) {
                scanRootsInParallel(
                    roots = roots,
                    knownUris = knownUris,
                    knownCanonicalPaths = knownCanonicalPaths,
                    discoveredCounter = totalDiscoveredCounter,
                    insertedCounter = totalInsertedCounter,
                    nowMs = now
                )
            }

            val finalState = PdfScanProgress(
                isScanning = false,
                totalFoundCount = totalDiscoveredCounter.get(),
                newlyAddedCount = totalInsertedCounter.get(),
                lastCompletedAtMs = System.currentTimeMillis()
            )
            _progress.value = finalState

            // Phase 3: Lightweight asynchronous backfill of missing page counts without blocking UI
            backfillMissingPageCounts(limit = 25)

            finalState
        }
    }

    private fun scanMediaStoreFast(
        knownUris: MutableSet<String>,
        knownCanonicalPaths: MutableSet<String>,
        discoveredCounter: AtomicInteger,
        nowMs: Long
    ): List<PdfDocumentEntity> {
        val discovered = ArrayList<PdfDocumentEntity>(64)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            null
        }

        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATA)
            if (relativePathColumn != null) add(relativePathColumn)
        }.toTypedArray()

        val selection = "${MediaStore.MediaColumns.MIME_TYPE} = ? OR LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?"
        val selectionArgs = arrayOf("application/pdf", "%.pdf")
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val relCol = relativePathColumn?.let { cursor.getColumnIndex(it) } ?: -1

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else -1L
                    if (id < 0L) continue
                    val rawPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val rawName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    val modifiedSec = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
                    val relPath = if (relCol >= 0) cursor.getString(relCol).orEmpty() else ""

                    val file = rawPath.takeIf { it.isNotBlank() }?.let { File(it) }
                    val canReadDirectFile = file != null && file.isFile && file.canRead() && file.length() > 0L

                    if (!canReadDirectFile && size <= 0L) continue

                    val uriString = if (canReadDirectFile) {
                        Uri.fromFile(file!!).toString()
                    } else {
                        ContentUris.withAppendedId(collection, id).toString()
                    }

                    val canonicalPath = if (file != null && file.exists()) {
                        canonicalOrAbsolute(file)
                    } else {
                        rawPath
                    }

                    discoveredCounter.incrementAndGet()
                    val alreadyTracked = knownUris.contains(uriString) ||
                        (canonicalPath.isNotBlank() && knownCanonicalPaths.contains(canonicalPath))
                    if (alreadyTracked) continue

                    knownUris.add(uriString)
                    if (canonicalPath.isNotBlank()) knownCanonicalPaths.add(canonicalPath)

                    val cleanTitle = extractCleanTitle(
                        rawName = rawName.ifBlank { file?.name.orEmpty() }
                    )
                    val folderName = extractFolderName(
                        file = file,
                        relativePath = relPath
                    )
                    val timestamp = when {
                        modifiedSec > 0L -> (modifiedSec * 1000L).coerceAtMost(nowMs)
                        file != null && file.lastModified() > 0L -> file.lastModified().coerceAtMost(nowMs)
                        else -> nowMs - 60_000L
                    }

                    discovered.add(
                        PdfDocumentEntity(
                            uriString = uriString,
                            title = cleanTitle,
                            folderName = folderName,
                            fileSizeBytes = if (file != null && file.length() > 0L) file.length() else size,
                            pageCount = 0,
                            currentPage = 0,
                            readingStatus = ReadingStatus.TO_READ.name,
                            isFavorite = false,
                            lastOpenedAt = timestamp,
                            addedAt = timestamp
                        )
                    )
                }
            }
        }
        return discovered
    }

    private suspend fun scanRootsInParallel(
        roots: List<File>,
        knownUris: MutableSet<String>,
        knownCanonicalPaths: MutableSet<String>,
        discoveredCounter: AtomicInteger,
        insertedCounter: AtomicInteger,
        nowMs: Long
    ) = coroutineScope {
        val dirChannel = Channel<File>(Channel.UNLIMITED)
        val pendingDirs = AtomicInteger(0)
        val visitedDirPaths = ConcurrentHashMap.newKeySet<String>()

        roots.forEach { root ->
            if (root.exists() && root.isDirectory && root.canRead()) {
                val canonical = canonicalOrAbsolute(root)
                if (visitedDirPaths.add(canonical)) {
                    pendingDirs.incrementAndGet()
                    dirChannel.trySend(root)
                }
            }
        }

        if (pendingDirs.get() == 0) {
            dirChannel.close()
            return@coroutineScope
        }

        val workerCount = 4
        val workers = List(workerCount) {
            launch(Dispatchers.IO) {
                val localBatch = ArrayList<PdfDocumentEntity>(BATCH_FLUSH_SIZE)
                for (dir in dirChannel) {
                    try {
                        val children = dir.listFiles()
                        if (children != null) {
                            for (child in children) {
                                val name = child.name
                                if (name.startsWith(".")) continue
                                if (child.isDirectory) {
                                    if (shouldSkipDirectory(child)) continue
                                    val canonicalSub = canonicalOrAbsolute(child)
                                    if (visitedDirPaths.add(canonicalSub)) {
                                        pendingDirs.incrementAndGet()
                                        dirChannel.trySend(child)
                                    }
                                } else if (child.isFile && name.endsWith(".pdf", ignoreCase = true)) {
                                    val len = child.length()
                                    if (len < MIN_PDF_BYTES) continue
                                    if (!hasPdfMagicHeader(child)) continue

                                    val canonicalFile = canonicalOrAbsolute(child)
                                    val uriString = Uri.fromFile(child).toString()

                                    val isDuplicatePath = !knownCanonicalPaths.add(canonicalFile)
                                    val isDuplicateUri = !knownUris.add(uriString)
                                    if (isDuplicatePath || isDuplicateUri) continue

                                    discoveredCounter.incrementAndGet()
                                    val timestamp = child.lastModified()
                                        .takeIf { it > 0L }
                                        ?.coerceAtMost(nowMs)
                                        ?: (nowMs - 60_000L)

                                    localBatch.add(
                                        PdfDocumentEntity(
                                            uriString = uriString,
                                            title = extractCleanTitle(name),
                                            folderName = extractFolderName(file = child, relativePath = ""),
                                            fileSizeBytes = len,
                                            pageCount = 0,
                                            currentPage = 0,
                                            readingStatus = ReadingStatus.TO_READ.name,
                                            isFavorite = false,
                                            lastOpenedAt = timestamp,
                                            addedAt = timestamp
                                        )
                                    )

                                    if (localBatch.size >= BATCH_FLUSH_SIZE) {
                                        val added = insertBatchCountingNew(localBatch)
                                        localBatch.clear()
                                        if (added > 0) {
                                            val newAdded = insertedCounter.addAndGet(added)
                                            _progress.update {
                                                it.copy(
                                                    totalFoundCount = discoveredCounter.get(),
                                                    newlyAddedCount = newAdded
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        if (pendingDirs.decrementAndGet() == 0) {
                            dirChannel.close()
                        }
                    }
                }

                if (localBatch.isNotEmpty()) {
                    val added = insertBatchCountingNew(localBatch)
                    localBatch.clear()
                    if (added > 0) {
                        val newAdded = insertedCounter.addAndGet(added)
                        _progress.update {
                            it.copy(
                                totalFoundCount = discoveredCounter.get(),
                                newlyAddedCount = newAdded
                            )
                        }
                    }
                }
            }
        }
        workers.forEach { it.join() }
    }

    private suspend fun insertBatchCountingNew(batch: List<PdfDocumentEntity>): Int {
        if (batch.isEmpty()) return 0
        val rowIds = pdfDao.insertDocumentsIfAbsent(batch.toList())
        return rowIds.count { it != -1L }
    }

    private suspend fun backfillMissingPageCounts(limit: Int) {
        val pendingUris = pdfDao.getDocumentsMissingPageCount(limit)
        if (pendingUris.isEmpty()) return
        val updates = LinkedHashMap<String, Int>(pendingUris.size)
        for (uri in pendingUris) {
            val pages = runCatching { pdfEngine.inspectPageCount(uri) }.getOrDefault(0)
            if (pages > 0) {
                updates[uri] = pages
            }
        }
        if (updates.isNotEmpty()) {
            pdfDao.fillMissingPageCounts(updates)
        }
    }

    private fun discoverStorageRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        @Suppress("DEPRECATION")
        runCatching { Environment.getExternalStorageDirectory() }
            .getOrNull()
            ?.takeIf { it.exists() && it.isDirectory }
            ?.let { roots.add(it) }

        // Detect secondary mounted volumes (SD cards / USB OTG) via externalFilesDirs prefix
        runCatching {
            context.getExternalFilesDirs(null).forEach { dir ->
                val path = dir?.absolutePath ?: return@forEach
                val androidIdx = path.indexOf("/Android/")
                if (androidIdx > 0) {
                    val volumeRoot = File(path.substring(0, androidIdx))
                    if (volumeRoot.exists() && volumeRoot.isDirectory) {
                        roots.add(volumeRoot)
                    }
                }
            }
        }
        return roots.toList()
    }

    private fun shouldSkipDirectory(dir: File): Boolean {
        val nameLower = dir.name.lowercase()
        if (SKIPPED_DIR_NAMES.contains(nameLower)) return true
        if (nameLower == "data" || nameLower == "obb") {
            if (dir.parentFile?.name.equals("Android", ignoreCase = true)) return true
        }
        return false
    }

    private fun hasPdfMagicHeader(file: File): Boolean {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val buf = ByteArray(16)
                val read = raf.read(buf)
                if (read < 4) return false
                // Allow leading whitespace/BOM before %PDF-
                val text = String(buf, 0, read, Charsets.US_ASCII)
                text.contains("%PDF")
            }
        }.getOrDefault(false)
    }

    private fun canonicalOrAbsolute(file: File): String =
        runCatching { file.canonicalPath }.getOrElse { file.absolutePath }

    private fun extractCleanTitle(rawName: String): String {
        val stripped = if (rawName.endsWith(".pdf", ignoreCase = true)) {
            rawName.dropLast(4)
        } else {
            rawName
        }.trim()
        return stripped.ifBlank { "PDF Document" }
    }

    private fun extractFolderName(file: File?, relativePath: String): String {
        val fromFile = file?.parentFile?.name?.trim()
        if (!fromFile.isNullOrBlank() &&
            fromFile != "0" &&
            !fromFile.equals("emulated", ignoreCase = true)
        ) {
            return fromFile
        }
        val cleanedRel = relativePath.trim().trim('/')
        if (cleanedRel.isNotBlank()) {
            val segment = cleanedRel.substringAfterLast('/').trim()
            if (segment.isNotBlank()) return segment
        }
        return "Internal Storage"
    }

    private companion object {
        const val BATCH_FLUSH_SIZE = 64
        const val MIN_PDF_BYTES = 16L
        val SKIPPED_DIR_NAMES = setOf(
            "lost+found",
            ".thumbnails",
            "thumbnails",
            "cache",
            "temp",
            "tmp",
            "node_modules",
            "__macosx"
        )
    }
}
