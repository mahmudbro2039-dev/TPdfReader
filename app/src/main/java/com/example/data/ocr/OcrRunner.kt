package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.search.IndexConfig
import com.example.data.search.IndexPolicy
import com.example.data.search.PauseReason
import com.example.data.search.SearchIndexRepository
import com.example.data.search.db.IndexStatus
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OcrFailure {
    NO_DATA,
    OPEN_FAILED,
    CANCELLED_LOW_BATTERY,
    OTHER
}

sealed interface OcrState {
    data object Idle : OcrState
    data class Running(val uri: String, val page: Int, val total: Int) : OcrState
    data class Finished(val uri: String, val addedPages: Int) : OcrState
    data class Failed(val uri: String, val reason: OcrFailure) : OcrState
}

class OcrRunner(
    private val appContext: Context,
    private val appScope: CoroutineScope,
    private val repo: SearchIndexRepository,
    private val ocrEngine: OcrEngine
) {
    private val _state = MutableStateFlow<OcrState>(OcrState.Idle)
    val state: StateFlow<OcrState> = _state.asStateFlow()

    private val jobLock = Any()
    private var activeJob: Job? = null

    fun start(uri: String) {
        if (uri.isBlank()) return
        synchronized(jobLock) {
            if (activeJob?.isActive == true) return
            activeJob = appScope.launch(Dispatchers.Default) {
                runSingleDocument(uri)
            }
        }
    }

    fun stop() {
        synchronized(jobLock) {
            activeJob?.cancel()
            activeJob = null
        }
        if (_state.value is OcrState.Running) {
            _state.value = OcrState.Idle
        }
    }

    fun clearTerminalState() {
        val current = _state.value
        if (current is OcrState.Finished || current is OcrState.Failed) {
            _state.value = OcrState.Idle
        }
    }

    internal suspend fun runSingleDocument(uri: String): OcrState {
        var ownedPfd: ParcelFileDescriptor? = null
        var ownedRenderer: PdfRenderer? = null
        var tempCopyFile: File? = null

        try {
            val doc = repo.getDoc(uri) ?: return _state.value
            if (doc.status != IndexStatus.DONE) return _state.value

            val session = ocrEngine.open()
            if (session == null) {
                val failed = OcrState.Failed(uri, OcrFailure.NO_DATA)
                _state.value = failed
                return failed
            }

            return session.use { activeSession ->
                val have = repo.indexedPageSet(doc.docId)
                val opened = openOwnRenderer(Uri.parse(uri))
                if (opened == null) {
                    val failed = OcrState.Failed(uri, OcrFailure.OPEN_FAILED)
                    _state.value = failed
                    return@use failed
                }

                ownedPfd = opened.pfd
                ownedRenderer = opened.renderer
                tempCopyFile = opened.tempFile
                val renderer = opened.renderer

                val docPages = if (doc.pageCount > 0) doc.pageCount else renderer.pageCount
                val cappedTotal = minOf(docPages, renderer.pageCount, IndexConfig.MAX_PAGES_PER_DOC)
                if (cappedTotal <= 0 || doc.ocrNextPage >= cappedTotal) {
                    repo.finishOcr(doc.docId)
                    val finished = OcrState.Finished(uri, addedPages = 0)
                    _state.value = finished
                    return@use finished
                }

                var addedPages = 0
                for (p in doc.ocrNextPage until cappedTotal) {
                    currentCoroutineContext().ensureActive()

                    if (p in have) {
                        repo.advanceOcrPointer(doc.docId, p + 1)
                        continue
                    }

                    if (IndexPolicy.canRunNow(appContext) == PauseReason.LOW_BATTERY) {
                        val lowBattery = OcrState.Failed(uri, OcrFailure.CANCELLED_LOW_BATTERY)
                        _state.value = lowBattery
                        return@use lowBattery
                    }

                    _state.value = OcrState.Running(uri, page = p + 1, total = cappedTotal)

                    val bmp = renderPageForOcr(renderer, p)
                    if (bmp == null) {
                        repo.advanceOcrPointer(doc.docId, p + 1)
                        continue
                    }

                    val ocrResult = try {
                        activeSession.recognize(bmp)
                    } finally {
                        bmp.recycle()
                    }

                    if (ocrResult.meanConfidence >= OcrConfig.MIN_CONFIDENCE &&
                        ocrResult.text.isNotBlank()
                    ) {
                        repo.writeOcrPage(
                            docId = doc.docId,
                            pageIndex = p,
                            text = ocrResult.text,
                            ocrNextPage = p + 1
                        )
                        addedPages++
                    } else {
                        repo.advanceOcrPointer(doc.docId, p + 1)
                    }
                }

                repo.finishOcr(doc.docId)
                val finished = OcrState.Finished(uri, addedPages)
                _state.value = finished
                finished
            }
        } catch (ce: CancellationException) {
            if (_state.value is OcrState.Running) {
                _state.value = OcrState.Idle
            }
            throw ce
        } catch (_: Throwable) {
            val failed = OcrState.Failed(uri, OcrFailure.OTHER)
            _state.value = failed
            return failed
        } finally {
            runCatching { ownedRenderer?.close() }
            runCatching { ownedPfd?.close() }
            runCatching { tempCopyFile?.delete() }
        }
    }

    private data class OpenedRenderer(
        val pfd: ParcelFileDescriptor,
        val renderer: PdfRenderer,
        val tempFile: File?
    )

    private fun openOwnRenderer(uri: Uri): OpenedRenderer? {
        var directPfd: ParcelFileDescriptor? = null
        try {
            directPfd = if (uri.scheme == "file" && uri.path != null) {
                ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                appContext.contentResolver.openFileDescriptor(uri, "r")
            }
            if (directPfd != null) {
                try {
                    return OpenedRenderer(directPfd, PdfRenderer(directPfd), tempFile = null)
                } catch (_: IOException) {
                    runCatching { directPfd.close() }
                    directPfd = null
                } catch (_: SecurityException) {
                    runCatching { directPfd.close() }
                    return null
                }
            }
        } catch (_: Throwable) {
            runCatching { directPfd?.close() }
            return null
        }

        val tmpDir = File(appContext.cacheDir, "ocr_tmp").apply { mkdirs() }
        val tmpFile = File(tmpDir, "${uri.toString().hashCode().toUInt().toString(16)}.pdf")
        var copyPfd: ParcelFileDescriptor? = null
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                tmpFile.delete()
                return null
            }
            copyPfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY)
            OpenedRenderer(copyPfd, PdfRenderer(copyPfd), tempFile = tmpFile)
        } catch (_: Throwable) {
            runCatching { copyPfd?.close() }
            runCatching { tmpFile.delete() }
            null
        }
    }

    private fun renderPageForOcr(renderer: PdfRenderer, pageIndex: Int): Bitmap? {
        val page = renderer.openPage(pageIndex)
        return try {
            val maxSide = maxOf(page.width, page.height).coerceAtLeast(1)
            val baseScale = OcrConfig.RENDER_MAX_SIDE_PX.toFloat() / maxSide.toFloat()
            var w = (page.width * baseScale).roundToInt().coerceAtLeast(1)
            var h = (page.height * baseScale).roundToInt().coerceAtLeast(1)
            while (w.toLong() * h.toLong() > OcrConfig.RENDER_MAX_PIXELS.toLong() && w > 1 && h > 1) {
                w = (w * 0.9f).toInt().coerceAtLeast(1)
                h = (h * 0.9f).toInt().coerceAtLeast(1)
            }
            renderBitmapOnce(page, w, h) ?: renderBitmapOnce(
                page,
                (w / 2).coerceAtLeast(1),
                (h / 2).coerceAtLeast(1)
            )
        } finally {
            page.close()
        }
    }

    private fun renderBitmapOnce(page: PdfRenderer.Page, width: Int, height: Int): Bitmap? {
        var bmp: Bitmap? = null
        return try {
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(OcrConfig.PAGE_BACKGROUND)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            bmp
        } catch (_: OutOfMemoryError) {
            runCatching { bmp?.recycle() }
            null
        }
    }
}
