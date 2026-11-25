package com.example.data.text

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfBoxTextEngine(context: Context) : PdfTextEngine {

    private val appContext = context.applicationContext

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val closeScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val tempDir = File(appContext.cacheDir, "pdfbox_tmp")
    private val activeSessions = AtomicInteger(0)
    private var initialized = false

    fun hasActiveSession(): Boolean = activeSessions.get() > 0

    private fun ensureInitializedLocked() {
        if (initialized) return
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        tempDir.mkdirs()
        PDFBoxResourceLoader.init(appContext)
        initialized = true
    }

    override suspend fun open(uri: Uri): OpenResult = withContext(dispatcher) {
        ensureInitializedLocked()
        val memoryUsage = MemoryUsageSetting.setupTempFileOnly().setTempDir(tempDir)

        try {
            val doc = if (uri.scheme == ContentResolver.SCHEME_FILE) {
                val path = uri.path ?: return@withContext OpenResult.Error(OpenFailure.IO)
                val file = File(path)
                if (!file.exists() || !file.canRead()) {
                    return@withContext OpenResult.Error(OpenFailure.IO)
                }
                PDDocument.load(file, memoryUsage)
            } else {
                val stream = try {
                    appContext.contentResolver.openInputStream(uri)
                } catch (e: FileNotFoundException) {
                    return@withContext OpenResult.Error(OpenFailure.IO)
                } catch (e: SecurityException) {
                    return@withContext OpenResult.Error(OpenFailure.IO)
                } catch (e: IOException) {
                    return@withContext OpenResult.Error(OpenFailure.IO)
                } ?: return@withContext OpenResult.Error(OpenFailure.IO)

                stream.use { input ->
                    PDDocument.load(input, memoryUsage)
                }
            }

            activeSessions.incrementAndGet()
            OpenResult.Ok(PdfBoxSession(doc, dispatcher, closeScope) {
                activeSessions.decrementAndGet()
            })
        } catch (e: CancellationException) {
            throw e
        } catch (_: InvalidPasswordException) {
            OpenResult.Error(OpenFailure.ENCRYPTED)
        } catch (_: OutOfMemoryError) {
            OpenResult.Error(OpenFailure.OUT_OF_MEMORY)
        } catch (_: IOException) {
            OpenResult.Error(OpenFailure.CORRUPT)
        } catch (_: Exception) {
            OpenResult.Error(OpenFailure.CORRUPT)
        }
    }

    private class PdfBoxSession(
        private val doc: PDDocument,
        private val dispatcher: CoroutineDispatcher,
        private val closeScope: CoroutineScope,
        private val onClosed: () -> Unit,
    ) : TextSession {

        override val pageCount: Int = doc.numberOfPages
        private val closed = AtomicBoolean(false)
        private val stripper = PDFTextStripper().apply {
            sortByPosition = false
            paragraphEnd = "\n"
        }

        override suspend fun page(index: Int): PageText = withContext(dispatcher) {
            if (closed.get() || index < 0 || index >= pageCount) {
                return@withContext PageText(index, "", TextQuality.EMPTY)
            }
            stripper.startPage = index + 1
            stripper.endPage = index + 1
            val raw = stripper.getText(doc) ?: ""
            val fontNames = fontNamesOf(doc.getPage(index))
            val quality = TextQualityClassifier.classify(raw, fontNames)
            val cleaned = TextNormalizer.forDisplay(raw)
            PageText(
                pageIndex = index,
                text = cleaned,
                quality = quality,
            )
        }

        override fun pages(from: Int): Flow<PageText> = flow {
            val start = from.coerceAtLeast(0)
            for (i in start until pageCount) {
                if (closed.get()) break
                emit(page(i))
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            closeScope.launch {
                try {
                    doc.close()
                } catch (_: Exception) {
                } finally {
                    onClosed()
                }
            }
        }

        private fun fontNamesOf(page: PDPage?): Set<String> {
            val resources = page?.resources ?: return emptySet()
            val names = resources.fontNames ?: return emptySet()
            val result = mutableSetOf<String>()
            for (cosName in names) {
                val fontName = try {
                    resources.getFont(cosName)?.name
                } catch (_: Exception) {
                    null
                }
                if (!fontName.isNullOrBlank()) {
                    result.add(fontName)
                }
            }
            return result
        }
    }
}
