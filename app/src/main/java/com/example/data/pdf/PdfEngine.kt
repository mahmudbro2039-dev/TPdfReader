package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

data class PdfPageDimension(
    val width: Int,
    val height: Int
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 0.707f
}

data class OpenedPdfMetadata(
    val pageCount: Int,
    val pageDimensions: List<PdfPageDimension>
)

class PdfEngine(private val context: Context) {

    private val renderMutex = Mutex()
    private val activeVisibleRenders = AtomicInteger(0)
    private var currentUriString: String? = null
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentRenderer: PdfRenderer? = null

    private val maxCacheKb: Int = ((Runtime.getRuntime().maxMemory() / 1024L) / 5L)
        .coerceIn(24_576L, 81_920L).toInt()

    private val pageBitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val thumbnailCacheDir: File
        get() = File(context.cacheDir, "pdf_thumbnails").apply { if (!exists()) mkdirs() }

    suspend fun openDocument(
        uriString: String,
        priorityPageIndex: Int = 0
    ): Result<OpenedPdfMetadata> = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            runCatching {
                if (currentUriString == uriString && currentRenderer != null) {
                    val renderer = currentRenderer!!
                    return@runCatching readAllPageDimensions(renderer, priorityPageIndex)
                }

                closeInternal()

                val pfd = openParcelFileDescriptor(uriString)
                    ?: throw IllegalStateException("Cannot open file descriptor for PDF")

                val renderer = try {
                    PdfRenderer(pfd)
                } catch (e: Exception) {
                    runCatching { pfd.close() }
                    throw e
                }

                if (renderer.pageCount <= 0) {
                    runCatching { renderer.close() }
                    runCatching { pfd.close() }
                    throw IllegalStateException("The PDF document contains no readable pages")
                }

                currentUriString = uriString
                currentPfd = pfd
                currentRenderer = renderer

                readAllPageDimensions(renderer, priorityPageIndex)
            }
        }
    }

    /**
     * Scans remaining page dimensions lazily after opening so mixed portrait/landscape
     * documents update aspect ratios without slowing initial document open.
     */
    suspend fun inspectAllPageDimensions(uriString: String): List<PdfPageDimension>? =
        withContext(Dispatchers.IO) {
            if (activeVisibleRenders.get() > 0) return@withContext null
            renderMutex.withLock {
                if (currentUriString != uriString) return@withContext null
                val renderer = currentRenderer ?: return@withContext null
                val count = renderer.pageCount
                if (count <= 1) return@withContext null
                val result = ArrayList<PdfPageDimension>(count)
                for (i in 0 until count) {
                    if (activeVisibleRenders.get() > 0 || currentUriString != uriString) {
                        return@withContext null
                    }
                    val dim = runCatching {
                        renderer.openPage(i).use { page ->
                            PdfPageDimension(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1))
                        }
                    }.getOrNull() ?: result.lastOrNull() ?: PdfPageDimension(595, 842)
                    result.add(dim)
                }
                result
            }
        }

    private fun readAllPageDimensions(
        renderer: PdfRenderer,
        priorityPageIndex: Int = 0
    ): OpenedPdfMetadata {
        val count = renderer.pageCount
        if (count <= 0) {
            return OpenedPdfMetadata(pageCount = 0, pageDimensions = emptyList())
        }
        val firstDim = renderer.openPage(0).use { page ->
            PdfPageDimension(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1))
        }
        val targetIdx = priorityPageIndex.coerceIn(0, count - 1)
        val priorityDim = if (targetIdx > 0) {
            renderer.openPage(targetIdx).use { page ->
                PdfPageDimension(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1))
            }
        } else {
            firstDim
        }
        val dims = MutableList(count) { firstDim }
        if (targetIdx > 0) {
            dims[targetIdx] = priorityDim
        }
        return OpenedPdfMetadata(pageCount = count, pageDimensions = dims)
    }

    private fun buildCacheKey(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        qualityScale: Float,
        hideMargins: Boolean
    ): Pair<Int, String> {
        val quantizedWidth = ((targetWidthPx * qualityScale).toInt().coerceIn(360, 1440) + 15) / 16 * 16
        val clampedWidth = quantizedWidth.coerceIn(360, 1440)
        val marginKey = if (hideMargins) '1' else '0'
        return clampedWidth to "$uriString|$pageIndex|$clampedWidth|$marginKey"
    }

    fun peekCachedPage(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        qualityScale: Float,
        hideMargins: Boolean = false
    ): Bitmap? {
        if (targetWidthPx <= 0) return null
        val (_, cacheKey) = buildCacheKey(uriString, pageIndex, targetWidthPx, qualityScale, hideMargins)
        return synchronized(pageBitmapCache) {
            pageBitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }
        }
    }

    suspend fun prefetchPage(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        qualityScale: Float,
        hideMargins: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (targetWidthPx <= 0) return@withContext
        val (clampedWidth, cacheKey) = buildCacheKey(uriString, pageIndex, targetWidthPx, qualityScale, hideMargins)
        synchronized(pageBitmapCache) {
            pageBitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withContext
            }
        }
        // Yield briefly while a visible foreground page is rendering instead of dropping prefetch
        var attempts = 0
        while (activeVisibleRenders.get() > 0 && attempts < 10) {
            kotlinx.coroutines.delay(25L)
            attempts++
        }
        if (activeVisibleRenders.get() > 0) return@withContext
        renderMutex.withLock {
            if (activeVisibleRenders.get() > 0) return@withContext
            renderLocked(uriString, pageIndex, clampedWidth, cacheKey, hideMargins)
        }
    }

    suspend fun renderPage(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        qualityScale: Float,
        hideMargins: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        val (clampedWidth, cacheKey) = buildCacheKey(uriString, pageIndex, targetWidthPx, qualityScale, hideMargins)

        synchronized(pageBitmapCache) {
            pageBitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withContext cached
            }
        }

        activeVisibleRenders.incrementAndGet()
        try {
            renderMutex.withLock {
                renderLocked(uriString, pageIndex, clampedWidth, cacheKey, hideMargins)
            }
        } finally {
            activeVisibleRenders.decrementAndGet()
        }
    }

    private fun renderLocked(
        uriString: String,
        pageIndex: Int,
        clampedWidth: Int,
        cacheKey: String,
        hideMargins: Boolean
    ): Bitmap? {
        synchronized(pageBitmapCache) {
            pageBitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return cached
            }
        }

        if (currentUriString != uriString || currentRenderer == null) {
            val pfd = openParcelFileDescriptor(uriString) ?: return null
            closeInternal()
            val renderer = runCatching { PdfRenderer(pfd) }.getOrElse {
                runCatching { pfd.close() }
                return null
            }
            currentUriString = uriString
            currentPfd = pfd
            currentRenderer = renderer
        }

        val renderer = currentRenderer ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

        return runCatching {
            renderer.openPage(pageIndex).use { page ->
                val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                val bitmapWidth = clampedWidth
                val bitmapHeight = (bitmapWidth * ratio).toInt().coerceIn(360, 2240)

                val rawBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                }

                page.render(
                    rawBitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                val finalBitmap = if (hideMargins) {
                    cropOuterWhitespaceMargins(rawBitmap)
                } else {
                    rawBitmap
                }

                synchronized(pageBitmapCache) {
                    pageBitmapCache.put(cacheKey, finalBitmap)
                }
                finalBitmap
            }
        }.getOrNull()
    }

    /**
     * MJ PDF's signature "Hide Margins" feature: scans outer borders of rendered page bitmap
     * to detect excess white margins and trims them cleanly while preserving a comfortable 2.5% padding.
     */
    private fun cropOuterWhitespaceMargins(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 100 || h < 100) return source

        val stepX = (w / 64).coerceAtLeast(2)
        val stepY = (h / 80).coerceAtLeast(2)
        val rowPixels = IntArray(w)

        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0

        for (y in stepY until (h - stepY) step stepY) {
            source.getPixels(rowPixels, 0, w, 0, y, w, 1)
            for (x in stepX until (w - stepX) step stepX) {
                val pixel = rowPixels[x]
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                if (r < 244 || g < 244 || b < 244) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (minX >= maxX || minY >= maxY) return source

        val padX = (w * 0.025f).toInt()
        val padY = (h * 0.025f).toInt()
        val cropLeft = (minX - padX).coerceAtLeast(0)
        val cropTop = (minY - padY).coerceAtLeast(0)
        val cropRight = (maxX + padX).coerceAtMost(w)
        val cropBottom = (maxY + padY).coerceAtMost(h)

        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop

        // Only crop if we actually save at least 6% margin width or height
        if (cropW > w * 0.94f && cropH > h * 0.94f) return source
        if (cropW < w * 0.45f || cropH < h * 0.45f) return source

        return runCatching {
            val rect = Rect(cropLeft, cropTop, cropRight, cropBottom)
            Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        }.getOrDefault(source)
    }

    suspend fun getOrGenerateThumbnail(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        val diskFile = File(thumbnailCacheDir, "${md5(uriString)}.png")
        if (diskFile.exists() && diskFile.length() > 0L) {
            val decoded = BitmapFactory.decodeFile(diskFile.absolutePath)
            if (decoded != null) return@withContext decoded
        }

        val pfd = openParcelFileDescriptor(uriString) ?: return@withContext null
        runCatching {
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount <= 0) return@runCatching null
                    renderer.openPage(0).use { page ->
                        val targetWidth = 280
                        val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                        val targetHeight = (targetWidth * ratio).toInt().coerceIn(220, 440)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        runCatching {
                            FileOutputStream(diskFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                        }
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun inspectPageCount(uriString: String): Int = withContext(Dispatchers.IO) {
        val pfd = openParcelFileDescriptor(uriString) ?: return@withContext 0
        runCatching {
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(0)
    }

    suspend fun clearMemoryAndDiskCache() = withContext(Dispatchers.IO) {
        synchronized(pageBitmapCache) {
            pageBitmapCache.evictAll()
        }
        thumbnailCacheDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
        File(context.cacheDir, "pdf_covers").listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    suspend fun closeDocument() = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            closeInternal()
        }
    }

    private fun closeInternal() {
        runCatching { currentRenderer?.close() }
        runCatching { currentPfd?.close() }
        currentRenderer = null
        currentPfd = null
        currentUriString = null
    }

    fun openParcelFileDescriptor(uriString: String): ParcelFileDescriptor? {
        return runCatching {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: return null)
                    if (!file.exists()) return null
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                null -> {
                    val file = File(uriString)
                    if (!file.exists()) return null
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
                else -> context.contentResolver.openFileDescriptor(uri, "r")
            }
        }.getOrNull()
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
