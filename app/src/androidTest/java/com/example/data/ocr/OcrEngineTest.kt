package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.search.SearchIndexRepository
import com.example.data.search.db.IndexStatus
import com.example.data.search.db.SearchIndexDatabase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrEngineTest {

    private lateinit var context: Context
    private lateinit var db: SearchIndexDatabase
    private lateinit var repo: SearchIndexRepository
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SearchIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SearchIndexRepository(db)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun ocrIndexesRenderedBitmapPdfAndMatchesHelloQuery() = runBlocking {
        val installed = OcrDataInstaller.ensureInstalled(context)
        assumeTrue(installed?.languages?.contains("eng") == true)

        val sourceBitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sourceBitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 96f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("HELLO 123", 60f, 180f, paint)

        val pdfFile = File(context.cacheDir, "ocr_engine_test.pdf")
        val docPdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(800, 300, 1).create()
            val page = docPdf.startPage(pageInfo)
            page.canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
            docPdf.finishPage(page)
            pdfFile.outputStream().use { docPdf.writeTo(it) }
        } finally {
            docPdf.close()
            sourceBitmap.recycle()
        }

        try {
            val uri = Uri.fromFile(pdfFile).toString()
            val docEntity = repo.getOrCreate(uri, "fp-ocr-test")
            repo.setPageCount(docEntity.docId, 1)
            repo.setStatus(docEntity.docId, IndexStatus.DONE)

            val runner = OcrRunner(
                appContext = context,
                appScope = scope,
                repo = repo,
                ocrEngine = OcrEngine(context)
            )
            val result = runner.runSingleDocument(uri)
            assertTrue(result is OcrState.Finished)

            val hits = repo.searchInDocument(uri, "hello")
            assertEquals(listOf(0), hits.map { it.pageIndex })
        } finally {
            pdfFile.delete()
        }
    }
}
