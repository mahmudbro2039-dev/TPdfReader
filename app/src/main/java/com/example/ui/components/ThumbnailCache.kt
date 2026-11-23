package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
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

    @Volatile
    private var cachedDiskDir: File? = null

    fun peek(uri: String): Bitmap? {
        if (uri.isBlank()) return null
        return cache.get(uri)?.takeIf { !it.isRecycled }
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

        val coversDir = File(context.cacheDir, "pdf_covers").also { dir ->
            if (!dir.exists()) dir.mkdirs()
            cachedDiskDir = dir
        }
        val diskFile = File(coversDir, "${md5(uri)}.jpg")

        return withContext(Dispatchers.IO) {
            peek(uri)?.let { return@withContext it }

            // Fast lock-free disk cache read
            if (diskFile.exists() && diskFile.length() > 0L) {
                val decoded = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
                if (decoded != null && !decoded.isRecycled) {
                    cache.put(uri, decoded)
                    return@withContext decoded
                }
            }

            renderSemaphore.withPermit {
                peek(uri)?.let { return@withPermit it }

                if (diskFile.exists() && diskFile.length() > 0L) {
                    val decoded = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
                    if (decoded != null && !decoded.isRecycled) {
                        cache.put(uri, decoded)
                        return@withPermit decoded
                    }
                }

                runCatching {
                    openFileDescriptor(context, uri)?.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        try {
                            if (renderer.pageCount <= 0) return@use null
                            val page = renderer.openPage(0)
                            try {
                                val targetWidth = widthPx.coerceIn(320, 520)
                                val ratio = if (page.width > 0) page.height / page.width.toFloat() else 1.414f
                                val targetHeight = (targetWidth * ratio).toInt().coerceIn(240, 820)
                                val bitmap = Bitmap.createBitmap(
                                    targetWidth,
                                    targetHeight,
                                    Bitmap.Config.ARGB_8888
                                ).apply {
                                    eraseColor(Color.WHITE)
                                }
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                cache.put(uri, bitmap)

                                runCatching {
                                    FileOutputStream(diskFile).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                                    }
                                }
                                bitmap
                            } finally {
                                page.close()
                            }
                        } finally {
                            renderer.close()
                        }
                    }
                }.getOrNull()
            }
        }
    }

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
