package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.QuoteCardTheme
import com.example.data.model.ReadingStatus
import com.example.data.pdf.PdfTextExtractor
import com.example.data.pdf.QuoteCardRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `app name matches MJ PDF branding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MJ PDF", appName)
    }

    @Test
    fun `document progress fraction and 5 reading statuses work accurately`() {
        val doc = PdfDocumentEntity(
            uriString = "file:///sample.pdf",
            title = "Sample",
            folderName = "Books & Research",
            pageCount = 10,
            currentPage = 4,
            readingStatus = ReadingStatus.ABANDONED.name
        )
        assertEquals(0.5f, doc.progressFraction, 0.001f)
        assertEquals(50, doc.progressPercent)
        assertEquals(ReadingStatus.ABANDONED, doc.status)
    }

    @Test
    fun `quote card renderer produces high resolution bitmap`() {
        val bitmap = kotlinx.coroutines.runBlocking {
            QuoteCardRenderer.renderQuoteBitmap(
                quoteText = "Every Tool, One Tap Away — fast, lightweight, and privacy-first.",
                bookTitle = "MJ PDF Quick Guide",
                pageNumber = 1,
                theme = QuoteCardTheme.DARK
            )
        }
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertTrue(bitmap.height >= 600)
    }

    @Test
    fun `toc and link extractor detects chapters and references`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)
        val pages = listOf(
            "Chapter 1: Welcome to MJ PDF\nVisit https://mjpdf.site for updates.",
            "Section 2: Reading Modes\nSee Page 1 for the overview."
        )
        val toc = extractor.buildTableOfContents(pages, pages.size)
        val links = extractor.extractDocumentLinks(pages, pages.size)
        assertEquals(2, toc.size)
        assertTrue(links.any { it.urlOrTarget.contains("mjpdf.site") })
        assertTrue(links.any { it.isInternalPageLink && it.targetPageIndex == 0 })
    }
}

