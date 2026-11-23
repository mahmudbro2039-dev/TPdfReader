package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val lock = Mutex() // PdfRenderer allows one open page at a time

    fun peek(uri: String): Bitmap? = cache.get(uri)

    fun clear() {
        cache.evictAll()
    }

    suspend fun load(context: Context, uri: String, widthPx: Int): Bitmap? {
        cache.get(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock {
                cache.get(uri)?.let { return@withLock it }
                runCatching {
                    openFileDescriptor(context, uri)?.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        try {
                            if (renderer.pageCount <= 0) return@use null
                            val page = renderer.openPage(0)
                            try {
                                val targetWidth = widthPx.coerceAtLeast(1)
                                val targetHeight = (targetWidth * page.height / page.width.toFloat())
                                    .toInt()
                                    .coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(
                                    targetWidth,
                                    targetHeight,
                                    Bitmap.Config.ARGB_8888
                                ).apply {
                                    eraseColor(android.graphics.Color.WHITE)
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
            else -> context.contentResolver.openFileDescriptor(parsed, "r")
        }
    }
}
