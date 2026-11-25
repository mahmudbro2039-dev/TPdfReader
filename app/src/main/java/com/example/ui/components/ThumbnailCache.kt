package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    // Allow 2 independent PdfRenderer cover decodes concurrently; disk hits run lock-free.
    private val renderSemaphore = Semaphore(2)
    private val diskWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedDiskDir: File? = null

    private fun resolveDiskDir(context: Context): File {
        cachedDiskDir?.let { return it }
        val dir = File(context.cacheDir, "pdf_covers")
        if (!dir.exists()) dir.mkdirs()
        cachedDiskDir = dir
        return dir
    }

    fun peek(uri: String): Bitmap? {
        if (uri.isBlank()) return null
        return cache.get(uri)?.takeIf { !it.isRecycled }
    }

    @Suppress("UNUSED_PARAMETER")
    fun peekMemory(uri: String, widthPx: Int = 0): ImageBitmap? {
        return peek(uri)?.asImageBitmap()
    }

    fun evict(uri: String) {
        if (uri.isBlank()) return
        cache.remove(uri)
        cachedDiskDir?.let { dir ->
            runCatching { File(dir, "${md5(uri)}.jpg").delete() }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    suspend fun load(context: Context, uri: String, widthPx: Int): Bitmap? {
        if (uri.isBlank()) return null
        peek(uri)?.let { return it }

        val appContext = context.applicationContext ?: context
        return withContext(Dispatchers.IO) {
            ensureActive()
            peek(uri)?.let { return@withContext it }

            val diskFile = File(resolveDiskDir(appContext), "${md5(uri)}.jpg")

            // Fast lock-free disk cache read using RGB_565 (half memory & faster GPU upload)
            if (diskFile.exists() && diskFile.length() > 0L) {
                val decoded = decodeDiskBitmap(diskFile)
                if (decoded != null && !decoded.isRecycled) {
                    cache.put(uri, decoded)
                    return@withContext decoded
                }
            }

            val rendered = renderSemaphore.withPermit {
                ensureActive()
                peek(uri)?.let { return@withPermit it }

                if (diskFile.exists() && diskFile.length() > 0L) {
                    val decoded = decodeDiskBitmap(diskFile)
                    if (decoded != null && !decoded.isRecycled) {
                        cache.put(uri, decoded)
                        return@withPermit decoded
                    }
                }

                try {
                    openFileDescriptor(appContext, uri)?.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        try {
                            if (renderer.pageCount <= 0) return@use null
                            ensureActive()
                            val page = renderer.openPage(0)
                            try {
                                val targetWidth = widthPx.coerceIn(200, 360)
                                val ratio = if (page.width > 0) page.height / page.width.toFloat() else 1.414f
                                val targetHeight = (targetWidth * ratio).toInt().coerceIn(220, 560)
                                val bitmap = Bitmap.createBitmap(
                                    targetWidth,
                                    targetHeight,
                                    Bitmap.Config.ARGB_8888
                                ).apply {
                                    eraseColor(Color.WHITE)
                                }
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                cache.put(uri, bitmap)
                                bitmap
                            } finally {
                                page.close()
                            }
                        } finally {
                            renderer.close()
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    null
                }
            }

            if (rendered != null && !diskFile.exists()) {
                diskWriteScope.launch {
                    runCatching {
                        FileOutputStream(diskFile).use { out ->
                            rendered.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                    }
                }
            }

            rendered
        }
    }

    suspend fun prewarm(context: Context, uris: List<String>, widthPx: Int = 280) {
        if (uris.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (uri in uris) {
                if (peek(uri) == null) {
                    load(context, uri, widthPx)
                }
            }
        }
    }

    private fun decodeDiskBitmap(diskFile: File): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(diskFile.absolutePath, opts)
    }.getOrNull()

    private fun openFileDescriptor(context: Context, uriString: String): ParcelFileDescriptor? {
        val parsed = Uri.parse(uriString)
        return when (parsed.scheme) {
            "file" -> {
                val path = parsed.path ?: return null
                val file = File(path)
                if (file.exists()) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) else null
            }
            null -> {
                val file = File(uriString)
                if (file.exists()) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) else null
            }
            else -> context.contentResolver.openFileDescriptor(parsed, "r")
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
