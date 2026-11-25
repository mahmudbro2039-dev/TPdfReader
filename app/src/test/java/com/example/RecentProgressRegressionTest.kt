package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MjPdfDatabase
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.model.AnnotationType
import com.example.data.model.ReadingStatus
import com.example.data.pdf.DevicePdfScanner
import com.example.data.pdf.PdfEngine
import com.example.data.repository.PdfLibraryRepository
import com.example.ui.model.toPdfItem
import com.example.ui.viewmodel.AppUiState
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentProgressRegressionTest {

    private lateinit var context: Context
    private lateinit var db: MjPdfDatabase
    private lateinit var repository: PdfLibraryRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var pdfEngine: PdfEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MjPdfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pdfEngine = PdfEngine(context)
        preferencesRepository = PreferencesRepository(context)
        repository = PdfLibraryRepository(
            context = context,
            pdfDao = db.pdfDao(),
            pdfEngine = pdfEngine
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `bug7 unopened single-page and multi-page pdfs report zero progress`() {
        val unopenedOnePage = PdfDocumentEntity(
            uriString = "file:///one_page.pdf",
            title = "One Page",
            pageCount = 1,
            currentPage = 0,
            readingStatus = ReadingStatus.TO_READ.name,
            lastOpenedAt = 0L
        )
        assertEquals(0f, unopenedOnePage.progressFraction, 0.0001f)
        assertEquals(0, unopenedOnePage.progressPercent)
        assertFalse(unopenedOnePage.hasBeenOpened)

        val item = unopenedOnePage.toPdfItem()
        assertFalse(item.hasBeenOpened)
        assertEquals(0f, item.progress, 0.0001f)
        assertEquals(0, item.percent)
        assertEquals(0, item.currentPage)
    }

    @Test
    fun `bug8 scanner and starter guides leave lastOpenedAt zero and repair cleans only untouched rows`() = runBlocking {
        val guidesDir = File(context.filesDir, "Guides & Manuals").apply { mkdirs() }
        val booksDir = File(context.filesDir, "Books & Research").apply { mkdirs() }
        val guideFile = File(guidesDir, "TeenaReader_Quick_Guide.pdf")
        val archFile = File(booksDir, "Clean_Architecture_Handbook.pdf")
        val scanDir = File(context.cacheDir, "bug8_scan_${System.currentTimeMillis()}").apply { mkdirs() }
        val scannedPdf = File(scanDir, "Scanned_Unopened.pdf")
        val dummyPdf = ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
            "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n" +
            "3 0 obj<</Type/Page/MediaBox[0 0 595 842]>>endobj\n%%EOF\n" +
            "%".repeat(1100)).toByteArray()

        try {
            guideFile.writeBytes(dummyPdf)
            archFile.writeBytes(dummyPdf)
            scannedPdf.writeBytes(dummyPdf)

            repository.ensureStarterGuideExists()
            val scanner = DevicePdfScanner(context, db.pdfDao(), pdfEngine)
            scanner.scanDevicePdfs(includeMediaStore = false, customRoots = listOf(scanDir))

            val initialDocs = db.pdfDao().observeAllDocuments().first()
            assertTrue(initialDocs.isNotEmpty())
            assertTrue(initialDocs.any { it.uriString == Uri.fromFile(scannedPdf).toString() })
            assertTrue(initialDocs.all { it.lastOpenedAt == 0L && it.addedAt > 0L })
        } finally {
            guideFile.delete()
            archFile.delete()
            scanDir.deleteRecursively()
        }

        val dao = db.pdfDao()
        // Fake lastOpenedAt on untouched scanned doc -> should be repaired to 0
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = "file:///fake_recent.pdf",
                title = "Fake Recent",
                pageCount = 5,
                currentPage = 0,
                readingStatus = ReadingStatus.TO_READ.name,
                isFavorite = false,
                lastOpenedAt = 123_456L
            )
        )
        // Truly read / pinned / status-changed docs -> must NOT be cleared
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = "file:///pinned_unread.pdf",
                title = "Pinned Unread",
                pageCount = 5,
                currentPage = 0,
                readingStatus = ReadingStatus.TO_READ.name,
                isFavorite = true,
                lastOpenedAt = 234_567L
            )
        )
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = "file:///real_reading.pdf",
                title = "Real Reading",
                pageCount = 5,
                currentPage = 2,
                readingStatus = ReadingStatus.READING.name,
                isFavorite = false,
                lastOpenedAt = 345_678L
            )
        )

        dao.repairFalseLastOpenedTimestamps()

        assertEquals(0L, dao.getDocument("file:///fake_recent.pdf")!!.lastOpenedAt)
        assertEquals(234_567L, dao.getDocument("file:///pinned_unread.pdf")!!.lastOpenedAt)
        assertEquals(345_678L, dao.getDocument("file:///real_reading.pdf")!!.lastOpenedAt)
    }

    @Test
    fun `bug9 transactional updateProgress enforces status transitions and preserves concurrent edits`() = runBlocking {
        val dao = db.pdfDao()
        val uri = "file:///status_rules.pdf"
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Status Rules",
                pageCount = 0,
                currentPage = 0,
                readingStatus = ReadingStatus.TO_READ.name,
                isFavorite = false,
                lastOpenedAt = 0L
            )
        )

        repository.markOpened(uri, now = 1_000L, pageCount = 5)
        var doc = dao.getDocument(uri)!!
        assertEquals(1_000L, doc.lastOpenedAt)
        assertEquals(5, doc.pageCount)
        assertEquals(ReadingStatus.TO_READ, doc.status)

        // Page 0 keeps TO_READ
        repository.updateProgress(uri, currentPage = 0, pageCount = 5, zoomScale = 1f)
        doc = dao.getDocument(uri)!!
        assertEquals(ReadingStatus.TO_READ, doc.status)

        // Toggle favorite separately and advance to page 1 -> READING, favorite preserved
        repository.toggleFavorite(uri, currentFavorite = false)
        repository.updateProgress(uri, currentPage = 1, pageCount = 5, zoomScale = 1.2f)
        doc = dao.getDocument(uri)!!
        assertEquals(ReadingStatus.READING, doc.status)
        assertTrue(doc.isFavorite)

        // Reach last page -> COMPLETED
        repository.updateProgress(uri, currentPage = 4, pageCount = 5, zoomScale = 1f)
        doc = dao.getDocument(uri)!!
        assertEquals(ReadingStatus.COMPLETED, doc.status)

        // Go back to page 1 -> stays COMPLETED (never lowers)
        repository.updateProgress(uri, currentPage = 1, pageCount = 5, zoomScale = 1f)
        doc = dao.getDocument(uri)!!
        assertEquals(ReadingStatus.COMPLETED, doc.status)

        // ON_HOLD stays locked even on last page
        repository.updateReadingStatus(uri, ReadingStatus.ON_HOLD)
        repository.updateProgress(uri, currentPage = 4, pageCount = 5, zoomScale = 1f)
        assertEquals(ReadingStatus.ON_HOLD, dao.getDocument(uri)!!.status)

        // 1-page PDF never auto-completes
        val singleUri = "file:///single_page.pdf"
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = singleUri,
                title = "Single",
                pageCount = 1,
                currentPage = 0,
                readingStatus = ReadingStatus.TO_READ.name,
                lastOpenedAt = 0L
            )
        )
        repository.updateProgress(singleUri, currentPage = 0, pageCount = 1, zoomScale = 1f)
        assertEquals(ReadingStatus.TO_READ, dao.getDocument(singleUri)!!.status)
    }

    @Test
    fun `bug10 incognito leaves zero trace on picked docs or progress while keeping bookmarks working`() = runBlocking {
        val tempPdf = File(context.cacheDir, "incognito_pick.pdf").apply {
            writeText("%PDF-1.4\n%%EOF")
        }
        try {
            val uri = Uri.fromFile(tempPdf)
            val entity = repository.registerPickedDocument(uri, incognitoMode = true)
            assertEquals(0L, entity.lastOpenedAt)

            // Also verify updating progress in incognito writes nothing
            repository.updateProgress(
                uriString = entity.uriString,
                currentPage = 3,
                pageCount = 10,
                zoomScale = 2f,
                incognitoMode = true
            )
            val afterProgress = db.pdfDao().getDocument(entity.uriString)!!
            assertEquals(0L, afterProgress.lastOpenedAt)
            assertEquals(0, afterProgress.currentPage)
            assertEquals(ReadingStatus.TO_READ, afterProgress.status)

            // Bookmarks & annotations continue working in incognito
            val bookmarkId = repository.addBookmark(entity.uriString, entity.title, 2, "Secret Page")
            val annotationId = repository.addAnnotation(
                entity.uriString,
                entity.title,
                2,
                AnnotationType.NOTE,
                "Quote",
                "Note",
                "#FACC15"
            )
            assertTrue(bookmarkId > 0L)
            assertTrue(annotationId > 0L)
        } finally {
            tempPdf.delete()
        }
    }

    @Test
    fun `appViewModel hero selects latest non-missing READING incomplete doc and recent hides unopened`() = runBlocking {
        val dao = db.pdfDao()
        preferencesRepository.setLastOpenedRepairDone(true)

        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = "file:///unopened.pdf",
                title = "Unopened",
                pageCount = 10,
                readingStatus = ReadingStatus.TO_READ.name,
                lastOpenedAt = 0L,
                addedAt = 5_000L
            )
        )
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = "file:///completed_recent.pdf",
                title = "Completed Recent",
                pageCount = 10,
                currentPage = 9,
                readingStatus = ReadingStatus.COMPLETED.name,
                lastOpenedAt = 9_000L
            )
        )
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = "file:///active_reading.pdf",
                title = "Active Reading",
                pageCount = 10,
                currentPage = 2,
                readingStatus = ReadingStatus.READING.name,
                lastOpenedAt = 7_000L
            )
        )

        val vm = AppViewModel(repository, preferencesRepository)
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }
        try {
            val state = awaitState(vm) { !it.isLoading && it.totalCount >= 3 }
            // Even though Completed Recent has higher lastOpenedAt (9000), hero picks Active Reading (7000)
            assertEquals("Active Reading", state.heroItem?.title)
            // Recent remaining has Completed Recent (opened) but NOT Unopened
            assertTrue(state.recentRemainingItems.any { it.title == "Completed Recent" })
            assertFalse(state.recentRemainingItems.any { it.title == "Unopened" })

            // If all opened docs are Completed (no READING candidate), hero becomes null while recent keeps opened
            repository.updateReadingStatus("file:///active_reading.pdf", ReadingStatus.COMPLETED)
            val noHeroState = awaitState(vm) { it.heroItem == null && it.recentRemainingItems.isNotEmpty() }
            assertNull(noHeroState.heroItem)
            assertEquals(
                listOf("Completed Recent", "Active Reading"),
                noHeroState.recentRemainingItems.map { it.title }
            )
        } finally {
            collectJob.cancel()
        }
    }

    private suspend fun awaitState(
        viewModel: AppViewModel,
        timeoutMs: Long = 3_000L,
        predicate: (AppUiState) -> Boolean
    ): AppUiState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val current = viewModel.uiState.value
            if (predicate(current)) return current
            withContext(Dispatchers.Default) { delay(20L) }
        }
        return viewModel.uiState.value
    }
}
