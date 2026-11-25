package com.example.data.text

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBoxTextEngineTest {

    private lateinit var context: Context
    private lateinit var engine: PdfBoxTextEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        engine = PdfBoxTextEngine(context)
    }

    @Test
    fun pageAccuracy() = runBlocking {
        val file = createTwoPagePdf(
            filename = "page_accuracy_test.pdf",
            page1Text = "UniqueMarkerAlpha first page content",
            page2Text = "UniqueMarkerBeta second page content",
        )
        try {
            val result = engine.open(Uri.fromFile(file))
            assertTrue(result is OpenResult.Ok)
            val session = (result as OpenResult.Ok).session
            session.use { s ->
                assertEquals(2, s.pageCount)

                val p0 = s.page(0)
                assertEquals(0, p0.pageIndex)
                assertEquals(TextQuality.OK, p0.quality)
                assertTrue(p0.text.contains("UniqueMarkerAlpha"))
                assertFalse(p0.text.contains("UniqueMarkerBeta"))

                val p1 = s.page(1)
                assertEquals(1, p1.pageIndex)
                assertEquals(TextQuality.OK, p1.quality)
                assertTrue(p1.text.contains("UniqueMarkerBeta"))
                assertFalse(p1.text.contains("UniqueMarkerAlpha"))

                val streamed = s.pages(0).toList()
                assertEquals(2, streamed.size)
                assertEquals(0, streamed[0].pageIndex)
                assertEquals(1, streamed[1].pageIndex)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun corruptFile() = runBlocking {
        val file = File(context.cacheDir, "corrupt_test.pdf").apply {
            writeText("not-a-valid-pdf-header-at-all")
        }
        try {
            val result = engine.open(Uri.fromFile(file))
            assertEquals(OpenResult.Error(OpenFailure.CORRUPT), result)
        } finally {
            file.delete()
        }
    }

    @Test
    fun closeIsIdempotent() = runBlocking {
        val file = createTwoPagePdf(
            filename = "idempotent_close_test.pdf",
            page1Text = "Page one sample",
            page2Text = "Page two sample",
        )
        try {
            val result = engine.open(Uri.fromFile(file))
            assertTrue(result is OpenResult.Ok)
            val session = (result as OpenResult.Ok).session
            session.close()
            session.close()
        } finally {
            file.delete()
        }
    }

    private fun createTwoPagePdf(
        filename: String,
        page1Text: String,
        page2Text: String,
    ): File {
        val outFile = File(context.cacheDir, filename)
        PDDocument().use { doc ->
            listOf(page1Text, page2Text).forEach { line ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText(line)
                    stream.endText()
                }
            }
            doc.save(outFile)
        }
        return outFile
    }
}
