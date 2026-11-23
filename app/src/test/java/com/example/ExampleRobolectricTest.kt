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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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

    @Test
    fun `normalized search matches across line breaks and collapses whitespace`() {
        val multiline = "Clean\n   Architecture\tPrinciples"
        assertTrue(PdfTextExtractor.containsNormalized(multiline, "clean architecture"))
        assertTrue(PdfTextExtractor.containsNormalized(multiline, "  architecture   principles "))
        assertFalse(PdfTextExtractor.containsNormalized(multiline, "   "))
        assertFalse(PdfTextExtractor.containsNormalized(multiline, "monolith"))
    }

    @Test
    fun `pdf extractor decodes hex strings and skips stream substrings in keys`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)
        val tempPdf = File(context.cacheDir, "search_hex_test.pdf")
        try {
            // Includes '/Producer (stream-writer)' before the real stream keyword plus 1-byte and UTF-16BE hex strings.
            tempPdf.writeText(
                """
                %PDF-1.4
                1 0 obj
                << /Producer (stream-writer) /Length 96 >>
                stream
                BT
                <48656C6C6F> Tj
                <FEFF0057006F0072006C0064> Tj
                ET
                endstream
                endobj
                """.trimIndent()
            )
            val pages = kotlinx.coroutines.runBlocking {
                extractor.extractPagesText("file://${tempPdf.absolutePath}", 1)
            }
            assertEquals(1, pages.size)
            assertTrue(PdfTextExtractor.containsNormalized(pages[0], "Hello World"))
        } finally {
            tempPdf.delete()
        }
    }

    @Test
    fun `reader route uri encoding preserves percent-encoded content and file URIs`() {
        val safUri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2FMy%20Book.pdf"
        val encodedSaf = encodeReaderRouteUri(safUri)
        assertFalse(encodedSaf.contains("%"))
        assertFalse(encodedSaf.contains("/"))
        assertEquals(safUri, decodeReaderRouteUri(encodedSaf))

        val guideUri = "file:///data/user/0/com.example/files/Guides%20%26%20Manuals/MJ_PDF_Quick_Guide.pdf"
        assertEquals(guideUri, decodeReaderRouteUri(encodeReaderRouteUri(guideUri)))

        // Raw URI passthrough fallback
        assertEquals(safUri, decodeReaderRouteUri(safUri))
    }

    @Test
    fun `fallbackTitleFromUri extracts clean document title without pdf extension`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppContainer(context).libraryRepository

        val safUri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2FPhysics_Notes.PDF"
        assertEquals("Physics_Notes", repo.fallbackTitleFromUri(safUri))

        val colonOnlyUri = "content://com.android.externalstorage.documents/document/primary%3ACleanCode.pdf"
        assertEquals("CleanCode", repo.fallbackTitleFromUri(colonOnlyUri))

        val fileUri = "file:///data/user/0/com.example/files/Books%20%26%20Research/Clean_Architecture_Handbook.pdf"
        assertEquals("Clean_Architecture_Handbook", repo.fallbackTitleFromUri(fileUri))
    }
}
