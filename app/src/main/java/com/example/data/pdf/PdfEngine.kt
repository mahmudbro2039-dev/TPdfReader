package com.example.data.pdf

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import com.example.BuildConfig
import com.example.ui.theme.ContentColors
import com.example.ui.theme.ReaderZoomTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

data class PageCacheKey(
    val uri: String,
    val pageIndex: Int,
    val widthBucket: Int,
    val hideMargins: Boolean
)

class PdfEngine(private val context: Context) {

    private val renderMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeVisibleRenders = AtomicInteger(0)
    private var currentUriString: String? = null
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentRenderer: PdfRenderer? = null

    private val cacheBudgetBytes: Int = computeCacheBudgetBytes(context)

    private val pageBitmapCache = object : LruCache<PageCacheKey, Bitmap>(cacheBudgetBytes) {
        override fun sizeOf(key: PageCacheKey, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(1)
    }

    private val inFlightRenders = HashMap<PageCacheKey, CompletableDeferred<Bitmap?>>()
    private val cacheHits = AtomicLong(0L)
    private val cacheMisses = AtomicLong(0L)
    private val cacheRequests = AtomicLong(0L)

    private val thumbnailCacheDir: File
        get() = File(context.cacheDir, "pdf_thumbnails").apply { if (!exists()) mkdirs() }

    private fun computeCacheBudgetBytes(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java)
        val classMb = (am?.memoryClass ?: 128).takeIf { it > 0 } ?: 128
        val cacheMb = (classMb / 4).coerceIn(48, 128)
        return cacheMb * 1024 * 1024
    }

    fun onTrimMemory(level: Int) {
        synchronized(pageBitmapCache) {
            when {
                level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                    pageBitmapCache.trimToSize(cacheBudgetBytes / 2)
                }
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                    pageBitmapCache.evictAll()
                }
            }
        }
    }

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
    ): PageCacheKey {
        val effectiveScale = qualityScale.coerceIn(0.5f, 1.0f)
        val rawWidth = (targetWidthPx * effectiveScale).roundToInt().coerceIn(360, 1440)
        val bucketWidth = (((rawWidth + 60) / 120) * 120).coerceIn(360, 1440)
        return PageCacheKey(
            uri = uriString,
            pageIndex = pageIndex,
            widthBucket = bucketWidth,
            hideMargins = hideMargins
        )
    }

    private fun recordCacheStat(hit: Boolean) {
        if (hit) {
            cacheHits.incrementAndGet()
        } else {
            cacheMisses.incrementAndGet()
        }
        val total = cacheRequests.incrementAndGet()
        if (BuildConfig.DEBUG && total % 50L == 0L) {
            val currentSize = synchronized(pageBitmapCache) { pageBitmapCache.size() }
            Log.d(
                TAG,
                "PageCache stats: requests=$total hits=${cacheHits.get()} misses=${cacheMisses.get()} sizeBytes=$currentSize/$cacheBudgetBytes"
            )
        }
    }

    private fun acquireOrCreateInFlight(key: PageCacheKey): Pair<CompletableDeferred<Bitmap?>, Boolean> {
        synchronized(inFlightRenders) {
            val existing = inFlightRenders[key]
            if (existing != null) {
                return existing to false
            }
            val created = CompletableDeferred<Bitmap?>()
            inFlightRenders[key] = created
            return created to true
        }
    }

    private fun finishInFlight(
        key: PageCacheKey,
        deferred: CompletableDeferred<Bitmap?>,
        result: Bitmap?
    ) {
        synchronized(inFlightRenders) {
            if (inFlightRenders[key] === deferred) {
                inFlightRenders.remove(key)
            }
        }
        deferred.complete(result)
    }

    fun peekCachedPage(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        qualityScale: Float,
        hideMargins: Boolean = false
    ): Bitmap? {
        if (targetWidthPx <= 0) return null
        val key = buildCacheKey(uriString, pageIndex, targetWidthPx, qualityScale, hideMargins)
        val cached = synchronized(pageBitmapCache) {
            pageBitmapCache.get(key)?.takeIf { !it.isRecycled }
        }
        if (cached != null) {
            recordCacheStat(hit = true)
        }
        return cached
    }

    suspend fun prefetchPage(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        qualityScale: Float,
        hideMargins: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (targetWidthPx <= 0) return@withContext
        val key = buildCacheKey(uriString, pageIndex, targetWidthPx, qualityScale, hideMargins)
        val alreadyCached = synchronized(pageBitmapCache) {
            pageBitmapCache.get(key)?.let { !it.isRecycled } ?: false
        }
        if (alreadyCached) return@withContext

        val alreadyInFlight = synchronized(inFlightRenders) { inFlightRenders[key] != null }
        if (alreadyInFlight) return@withContext

        // Yield briefly while a visible foreground page is rendering instead of dropping prefetch
        var attempts = 0
        while (activeVisibleRenders.get() > 0 && attempts < 10) {
            delay(25L)
            attempts++
        }
        if (activeVisibleRenders.get() > 0) return@withContext

        val (deferred, isOwner) = acquireOrCreateInFlight(key)
        if (!isOwner) {
            deferred.await()
            return@withContext
        }

        var result: Bitmap? = null
        try {
            result = renderMutex.withLock {
                if (activeVisibleRenders.get() > 0) return@withLock null
                renderLocked(key)
            }
        } finally {
            finishInFlight(key, deferred, result)
        }
    }

    suspend fun renderPage(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int,
        qualityScale: Float,
        hideMargins: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (targetWidthPx <= 0) return@withContext null
        val key = buildCacheKey(uriString, pageIndex, targetWidthPx, qualityScale, hideMargins)

        for (attempt in 0..1) {
            val cached = synchronized(pageBitmapCache) {
                pageBitmapCache.get(key)?.takeIf { !it.isRecycled }
            }
            if (cached != null) {
                recordCacheStat(hit = true)
                return@withContext cached
            }

            val (deferred, isOwner) = acquireOrCreateInFlight(key)
            if (!isOwner) {
                val awaited = deferred.await()
                if (awaited != null) {
                    recordCacheStat(hit = true)
                    return@withContext awaited
                }
                continue
            }

            recordCacheStat(hit = false)
            var result: Bitmap? = null
            activeVisibleRenders.incrementAndGet()
            try {
                result = renderMutex.withLock {
                    renderLocked(key)
                }
            } finally {
                activeVisibleRenders.decrementAndGet()
                finishInFlight(key, deferred, result)
            }
            return@withContext result
        }
        null
    }

    private fun renderLocked(key: PageCacheKey): Bitmap? {
        synchronized(pageBitmapCache) {
            pageBitmapCache.get(key)?.let { cached ->
                if (!cached.isRecycled) return cached
            }
        }

        if (currentUriString != key.uri || currentRenderer == null) {
            val pfd = openParcelFileDescriptor(key.uri) ?: return null
            closeInternal()
            val renderer = runCatching { PdfRenderer(pfd) }.getOrElse {
                runCatching { pfd.close() }
                return null
            }
            currentUriString = key.uri
            currentPfd = pfd
            currentRenderer = renderer
        }

        val renderer = currentRenderer ?: return null
        if (key.pageIndex < 0 || key.pageIndex >= renderer.pageCount) return null

        return runCatching {
            renderer.openPage(key.pageIndex).use { page ->
                val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                val bitmapWidth = key.widthBucket
                val bitmapHeight = (bitmapWidth * ratio).toInt().coerceIn(360, 2240)

                val rawBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(ContentColors.pagePaper.toArgb())
                }

                page.render(
                    rawBitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                val finalBitmap = if (key.hideMargins) {
                    cropOuterWhitespaceMargins(rawBitmap)
                } else {
                    rawBitmap
                }

                synchronized(pageBitmapCache) {
                    pageBitmapCache.put(key, finalBitmap)
                }
                finalBitmap
            }
        }.getOrNull()
    }

    suspend fun renderRegion(
        uriString: String,
        pageIndex: Int,
        zoomedPageWidthPx: Int,
        left: Int,
        top: Int,
        outWidth: Int,
        outHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank() || zoomedPageWidthPx <= 0 || outWidth <= 0 || outHeight <= 0) {
            return@withContext null
        }

        val pixelCount = outWidth.toLong() * outHeight.toLong()
        val downscale = if (pixelCount > ReaderZoomTokens.RegionMaxPixels) {
            sqrt(ReaderZoomTokens.RegionMaxPixels.toDouble() / pixelCount.toDouble()).toFloat()
        } else {
            1f
        }
        val bmpWidth = (outWidth * downscale).toInt().coerceAtLeast(1)
        val bmpHeight = (outHeight * downscale).toInt().coerceAtLeast(1)

        activeVisibleRenders.incrementAndGet()
        try {
            renderMutex.withLock {
                if (currentUriString != uriString || currentRenderer == null) {
                    val pfd = openParcelFileDescriptor(uriString) ?: return@withLock null
                    closeInternal()
                    val renderer = runCatching { PdfRenderer(pfd) }.getOrElse {
                        runCatching { pfd.close() }
                        return@withLock null
                    }
                    currentUriString = uriString
                    currentPfd = pfd
                    currentRenderer = renderer
                }

                val renderer = currentRenderer ?: return@withLock null
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withLock null

                try {
                    renderer.openPage(pageIndex).use { page ->
                        val pageWidthPt = page.width.coerceAtLeast(1).toFloat()
                        val pageToZoomedScale = zoomedPageWidthPx.toFloat() / pageWidthPt
                        val matrix = Matrix().apply {
                            postScale(pageToZoomedScale, pageToZoomedScale)
                            postTranslate(-left.toFloat(), -top.toFloat())
                            if (downscale < 1f) {
                                postScale(downscale, downscale)
                            }
                        }

                        val regionBitmap = Bitmap.createBitmap(
                            bmpWidth,
                            bmpHeight,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            eraseColor(ContentColors.pagePaper.toArgb())
                        }

                        page.render(
                            regionBitmap,
                            null,
                            matrix,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        regionBitmap
                    }
                } catch (_: OutOfMemoryError) {
                    null
                } catch (_: Exception) {
                    null
                }
            }
        } finally {
            activeVisibleRenders.decrementAndGet()
        }
    }

    /**
     * TeenaReader's signature "Hide Margins" feature: scans outer borders of rendered page bitmap
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

    fun closeDocumentAsync() {
        cleanupScope.launch {
            renderMutex.withLock {
                closeInternal()
            }
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

    companion object {
        private const val TAG = "PdfEngine"
    }
}
