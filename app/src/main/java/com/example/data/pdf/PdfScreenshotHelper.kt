package com.example.data.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.PdfColorMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Captures the current PDF page with active MJ PDF color filter (Normal, Dark, Sepia, High Contrast)
 * and saves a crisp PNG screenshot using zero-permission MediaStore (API 29+) or FileProvider fallback.
 */
object PdfScreenshotHelper {

    suspend fun savePageScreenshot(
        context: Context,
        sourceBitmap: Bitmap,
        documentTitle: String,
        pageNumberOneBased: Int,
        colorMode: PdfColorMode
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val filteredBitmap = applyColorModeToBitmap(sourceBitmap, colorMode)
            val safeName = documentTitle.replace(Regex("[^A-Za-z0-9._-]"), "_").take(36)
            val fileName = "MJPDF_${safeName}_p${pageNumberOneBased}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MJ PDF")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create screenshot entry")

                resolver.openOutputStream(uri)?.use { out ->
                    filteredBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("Unable to open screenshot output stream")

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                if (filteredBitmap !== sourceBitmap) filteredBitmap.recycle()
                uri
            } else {
                val dir = File(context.cacheDir, "screenshots").apply { if (!exists()) mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    filteredBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (filteredBitmap !== sourceBitmap) filteredBitmap.recycle()
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
    }

    private fun applyColorModeToBitmap(source: Bitmap, mode: PdfColorMode): Bitmap {
        val values: FloatArray = when (mode) {
            PdfColorMode.NORMAL -> return source
            PdfColorMode.DARK_INVERT -> floatArrayOf(
                -0.88f, 0f, 0f, 0f, 238f,
                0f, -0.88f, 0f, 0f, 238f,
                0f, 0f, -0.88f, 0f, 238f,
                0f, 0f, 0f, 1f, 0f
            )
            PdfColorMode.SEPIA -> floatArrayOf(
                0.96f, 0f, 0f, 0f, 0f,
                0f, 0.91f, 0f, 0f, 0f,
                0f, 0f, 0.80f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            PdfColorMode.HIGH_CONTRAST -> {
                val contrast = 1.35f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                floatArrayOf(
                    0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                    0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                    0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            }
        }

        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(values))
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }
}
