package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.example.data.search.SearchTextNormalizer
import com.example.data.text.TextNormalizer
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

data class OcrPageResult(
    val text: String,
    val meanConfidence: Int
)

interface OcrSession : Closeable {
    suspend fun recognize(bitmap: Bitmap): OcrPageResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class OcrEngine(
    private val appContext: Context
) {
    private val ocrDispatcher = Dispatchers.Default.limitedParallelism(1)

    suspend fun open(): OcrSession? {
        val data = OcrDataInstaller.ensureInstalled(appContext) ?: return null
        return withContext(ocrDispatcher) {
            val api = TessBaseAPI()
            val ok = runCatching {
                api.init(data.dataPath, data.languages)
            }.getOrDefault(false)
            if (!ok) {
                runCatching { api.recycle() }
                return@withContext null
            }
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            TessOcrSession(api, ocrDispatcher)
        }
    }

    private class TessOcrSession(
        private val api: TessBaseAPI,
        private val dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ) : OcrSession {
        private val lock = Any()
        @Volatile
        private var closed = false

        override suspend fun recognize(bitmap: Bitmap): OcrPageResult = withContext(dispatcher) {
            synchronized(lock) {
                if (closed) return@synchronized OcrPageResult(text = "", meanConfidence = 0)
                api.setImage(bitmap)
                val raw = api.utF8Text.orEmpty()
                val confidence = api.meanConfidence()
                api.clear()
                val cleaned = SearchTextNormalizer.forIndex(TextNormalizer.clean(raw))
                OcrPageResult(text = cleaned, meanConfidence = confidence)
            }
        }

        override fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                runCatching { api.recycle() }
            }
        }
    }
}
