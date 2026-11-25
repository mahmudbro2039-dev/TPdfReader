package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.QuoteCardTheme
import com.example.data.model.ReadingStatus
import com.example.data.pdf.PdfStructureParser
import com.example.data.pdf.QuoteCardRenderer
import com.example.ui.model.PdfItem
import com.example.ui.model.toPdfItem
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
        val oneOfFive = PdfDocumentEntity(
            uriString = "file:///five.pdf",
            title = "Five Page Doc",
            folderName = "Downloads",
            pageCount = 5,
            currentPage = 0,
            fileSizeBytes = 1024L,
            lastOpenedAt = 1L,
            readingStatus = ReadingStatus.READING.name
        ).toPdfItem()
        assertEquals(0.2f, oneOfFive.progress, 0.0001f)
        assertEquals(20, oneOfFive.percent)

        val oneOfThree = PdfDocumentEntity(
            uriString = "file:///three.pdf",
            title = "Three Page Doc",
            folderName = "Downloads",
            pageCount = 3,
            currentPage = 0,
            fileSizeBytes = 1024L,
            lastOpenedAt = 1L,
            readingStatus = ReadingStatus.READING.name
        ).toPdfItem()
        assertEquals(1f / 3f, oneOfThree.progress, 0.0001f)
        assertEquals(33, oneOfThree.percent)

        val zeroPages = PdfDocumentEntity(
            uriString = "file:///zero.pdf",
            title = "Zero Page Doc",
            folderName = "Downloads",
            pageCount = 0,
            currentPage = 0,
            fileSizeBytes = 1024L,
            lastOpenedAt = 1L,
            readingStatus = ReadingStatus.READING.name
        ).toPdfItem()
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
                context = ApplicationProvider.getApplicationContext(),
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
        val pages = listOf(
            "Chapter 1: Welcome to TeenaReader\nVisit https://example.com for updates.",
            "Section 2: Reading Modes\nSee Page 1 for the overview."
        )
        val toc = PdfStructureParser.buildTableOfContents(context, pages, pages.size)
        val links = PdfStructureParser.extractDocumentLinks(context, pages, pages.size)
        assertEquals(2, toc.size)
        assertTrue(links.any { it.urlOrTarget.contains("example.com") })
        assertTrue(links.any { it.isInternalPageLink && it.targetPageIndex == 0 })
    }

    @Test
    fun `reader route uri encoding preserves percent-encoded content and file URIs`() {
        val safUri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2FMy%20Book.pdf"
        val encodedSaf = encodeReaderRouteUri(safUri)
        assertFalse(encodedSaf.contains("%"))
        assertFalse(encodedSaf.contains("/"))
        assertEquals(safUri, decodeReaderRouteUri(encodedSaf))

        val guideUri = "file:///data/user/0/com.example/files/Guides%20%26%20Manuals/TeenaReader_Quick_Guide.pdf"
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
