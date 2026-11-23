package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.QuoteCardTheme
import com.example.data.model.ReadingStatus
import com.example.data.pdf.PdfTextExtractor
import com.example.data.pdf.QuoteCardRenderer
import com.example.ui.model.PdfItem
import com.example.ui.model.percent
import com.example.ui.model.progress
import com.example.ui.theme.ReadingStatus as UiReadingStatus
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
    fun `app name matches TeenaReader branding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TeenaReader", appName)
    }

    @Test
    fun `PdfItem progress formula matches spec across edge cases`() {
        val oneOfFive = PdfItem(
            id = "1",
            title = "Five Page Doc",
            folderName = "Downloads",
            uri = "file:///five.pdf",
            currentPage = 1,
            totalPages = 5,
            sizeBytes = 1024L,
            lastOpenedAt = 1L,
            status = UiReadingStatus.Reading
        )
        assertEquals(0.2f, oneOfFive.progress, 0.0001f)
        assertEquals(20, oneOfFive.percent)

        val oneOfThree = oneOfFive.copy(currentPage = 1, totalPages = 3)
        assertEquals(1f / 3f, oneOfThree.progress, 0.0001f)
        assertEquals(33, oneOfThree.percent)

        val zeroPages = oneOfFive.copy(currentPage = 0, totalPages = 0)
        assertEquals(0f, zeroPages.progress, 0.0001f)
        assertEquals(0, zeroPages.percent)
    }

    @Test
    fun `document progress fraction and reading statuses work accurately`() {
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
                quoteText = "Clean, distraction-free reading puts your document first.",
                bookTitle = "TeenaReader Quick Guide",
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
            "Chapter 1: Welcome to TeenaReader\nVisit https://example.com for updates.",
            "Section 2: Reading Modes\nSee Page 1 for the overview."
        )
        val toc = extractor.buildTableOfContents(pages, pages.size)
        val links = extractor.extractDocumentLinks(pages, pages.size)
        assertEquals(2, toc.size)
        assertTrue(links.any { it.urlOrTarget.contains("example.com") })
        assertTrue(links.any { it.isInternalPageLink && it.targetPageIndex == 0 })
    }
}
