package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.MjPdfDatabase
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.model.AnnotationType
import com.example.data.model.ReadingStatus
import com.example.data.model.ScrollLayoutMode
import com.example.data.pdf.DevicePdfScanner
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.PdfTextExtractor
import com.example.data.repository.PdfLibraryRepository
import com.example.data.system.DeviceAccessStatus
import com.example.data.system.DevicePermissionsHelper
import com.example.ui.model.SortOrder
import com.example.ui.theme.ReadingStatus as UiReadingStatus
import com.example.ui.viewmodel.AppUiState
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.ReaderLoadState
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScenarioAndPerformanceTest {

    private lateinit var context: Context
    private lateinit var db: MjPdfDatabase
    private lateinit var repository: PdfLibraryRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var pdfEngine: PdfEngine
    private lateinit var pdfTextExtractor: PdfTextExtractor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MjPdfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pdfEngine = PdfEngine(context)
        pdfTextExtractor = PdfTextExtractor(context)
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
    fun `clearAllLibraryData atomically clears documents bookmarks and annotations`() = runBlocking {
        val dao = db.pdfDao()
        val uri = "file:///test_atomic_clear.pdf"
        dao.upsertDocument(
            PdfDocumentEntity(
                uriString = uri,
                title = "Atomic Book",
                folderName = "Books",
                pageCount = 12,
                currentPage = 2
            )
        )
        dao.insertBookmark(BookmarkEntity(documentUri = uri, documentTitle = "Atomic Book", pageIndex = 1, title = "Intro"))
        dao.insertAnnotation(
            AnnotationEntity(
                documentUri = uri,
                documentTitle = "Atomic Book",
                pageIndex = 2,
                type = AnnotationType.NOTE.name,
                quotedText = "Important theorem",
                noteText = "Review again",
                colorHex = "#FACC15"
            )
        )

        assertEquals(1, dao.observeAllDocuments().first().size)
        assertEquals(1, dao.observeAllBookmarks().first().size)
        assertEquals(1, dao.observeAllAnnotations().first().size)

        repository.clearAllHistory()

        assertTrue(dao.observeAllDocuments().first().isEmpty())
        assertTrue(dao.observeAllBookmarks().first().isEmpty())
        assertTrue(dao.observeAllAnnotations().first().isEmpty())
    }

    @Test
    fun `AppViewModel normalizes blank folder to Storage and populates currentFolderItems`() = runBlocking {
        val dao = db.pdfDao()
        dao.upsertDocument(
            PdfDocumentEntity(
                uriString = "file:///unfoldered_doc.pdf",
                title = "Unfoldered Manual",
                folderName = "",
                fileSizeBytes = 2048L,
                pageCount = 5,
                currentPage = 1,
                lastOpenedAt = System.currentTimeMillis()
            )
        )
        dao.upsertDocument(
            PdfDocumentEntity(
                uriString = "file:///research_doc.pdf",
                title = "Research Paper",
                folderName = "Research",
                fileSizeBytes = 4096L,
                pageCount = 8,
                currentPage = 7,
                readingStatus = ReadingStatus.COMPLETED.name,
                lastOpenedAt = System.currentTimeMillis() - 5_000L
            )
        )

        val viewModel = AppViewModel(repository, preferencesRepository, pdfTextExtractor)
        val collectJob = launch(Dispatchers.Default) { viewModel.uiState.collect {} }
        try {
            val loadedState = awaitState(viewModel) { !it.isLoading && it.totalCount >= 2 }
            assertTrue(loadedState.folderItems.any { it.name == "Storage" && it.pdfCount >= 1 })

            viewModel.openFolder("Storage")
            val folderState = awaitState(viewModel) {
                it.selectedFolderName == "Storage" &&
                    it.currentFolderItems.any { item -> item.title == "Unfoldered Manual" }
            }
            assertEquals("Unfoldered Manual", folderState.currentFolderItems.first().title)

            viewModel.selectStatusFilter(UiReadingStatus.Completed)
            val filteredState = awaitState(viewModel) { it.selectedStatusFilter == UiReadingStatus.Completed }
            assertTrue(filteredState.libraryItems.all { it.status == UiReadingStatus.Completed })
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `AppViewModel deep search runs in parallel across FlateDecode PDFs and hits extractor cache`() = runBlocking {
        val pdfA = File(context.cacheDir, "deep_search_flate_a.pdf")
        val pdfB = File(context.cacheDir, "deep_search_flate_b.pdf")
        try {
            writeFlatePdf(pdfA, "BT (Quantum Entanglement in Kotlin Coroutines) Tj ET")
            writeFlatePdf(pdfB, "BT (Classical Architecture Principles) Tj ET")

            val uriA = Uri.fromFile(pdfA).toString()
            val uriB = Uri.fromFile(pdfB).toString()

            db.pdfDao().upsertDocument(
                PdfDocumentEntity(uriString = uriA, title = "Doc Alpha", folderName = "Science", pageCount = 1, lastOpenedAt = 10L)
            )
            db.pdfDao().upsertDocument(
                PdfDocumentEntity(uriString = uriB, title = "Doc Beta", folderName = "Design", pageCount = 1, lastOpenedAt = 5L)
            )

            // Verify FlateDecode + cache hit
            val pagesFirst = pdfTextExtractor.extractPagesText(uriA, 1)
            assertTrue(PdfTextExtractor.containsNormalized(pagesFirst.first(), "quantum entanglement"))
            val pagesCached = pdfTextExtractor.extractPagesText(uriA, 1)
            assertEquals(pagesFirst, pagesCached)

            preferencesRepository.setDeepSearch(true)
            val viewModel = AppViewModel(repository, preferencesRepository, pdfTextExtractor)
            val collectJob = launch(Dispatchers.Default) { viewModel.uiState.collect {} }
            try {
                awaitState(viewModel) { it.deepSearch && it.totalCount >= 2 }

                viewModel.onSubmitSearch("quantum entanglement")
                val searchState = awaitState(viewModel) {
                    !it.isSearching &&
                        it.searchResults.any { item -> item.uri == uriA }
                }
                assertTrue(searchState.searchResults.any { it.title == "Doc Alpha" })
                assertFalse(searchState.searchResults.any { it.title == "Doc Beta" })

                viewModel.setSortOrder(SortOrder.Name)
                val sortedState = awaitState(viewModel) { it.sort == SortOrder.Name }
                assertEquals(SortOrder.Name, sortedState.sort)
            } finally {
                collectJob.cancel()
            }
        } finally {
            pdfA.delete()
            pdfB.delete()
        }
    }

    @Test
    fun `PdfEngine fails cleanly on missing file and rejects invalid page peek`() = runBlocking {
        val openMissing = pdfEngine.openDocument("file:///non_existent_file_12345.pdf")
        assertTrue(openMissing.isFailure)
        assertEquals(null, pdfEngine.peekCachedPage("file:///non_existent_file_12345.pdf", 0, 0, 1.0f))
    }

    @Test
    fun `ReaderViewModel stepSinglePage navigates 1 page in DualPage mode while stepPage steps spreads`() = runBlocking {
        val uri = "file:///reader_step_test.pdf"
        db.pdfDao().upsertDocument(
            PdfDocumentEntity(uriString = uri, title = "Dual Page Guide", folderName = "Docs", pageCount = 6, currentPage = 0)
        )

        val readerVm = ReaderViewModel(repository, preferencesRepository, pdfEngine, pdfTextExtractor)
        readerVm.setLayoutMode(ScrollLayoutMode.DUAL_PAGE)

        val field = ReaderViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(readerVm) as kotlinx.coroutines.flow.MutableStateFlow<com.example.ui.viewmodel.ReaderUiState>
        stateFlow.value = stateFlow.value.copy(
            loadState = ReaderLoadState.Ready,
            uriString = uri,
            documentTitle = "Dual Page Guide",
            pageCount = 6,
            currentPage = 0,
            layoutMode = ScrollLayoutMode.DUAL_PAGE
        )

        readerVm.stepSinglePage(1)
        assertEquals(1, readerVm.uiState.value.currentPage)

        readerVm.stepPage(1)
        assertEquals(3, readerVm.uiState.value.currentPage)

        readerVm.jumpToPage(5, recordHistory = true)
        assertEquals(5, readerVm.uiState.value.currentPage)
        assertTrue(readerVm.uiState.value.canJumpBack)

        readerVm.jumpBackInHistory()
        assertEquals(3, readerVm.uiState.value.currentPage)
        assertTrue(readerVm.uiState.value.canJumpForward)

        readerVm.jumpForwardInHistory()
        assertEquals(5, readerVm.uiState.value.currentPage)

        readerVm.setZoomScale(2.5f)
        assertEquals(2.5f, readerVm.uiState.value.zoomScale, 0.001f)
        readerVm.toggleZoomLock()
        readerVm.setZoomScale(1.0f)
        assertEquals(2.5f, readerVm.uiState.value.zoomScale, 0.001f)
    }

    @Test
    fun `DevicePdfScanner scans nested directories in parallel, prunes system dirs, and preserves existing progress`() = runBlocking {
        val tempRoot = File(context.cacheDir, "scanner_test_root_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val booksDir = File(tempRoot, "Books").apply { mkdirs() }
            val reportsDir = File(tempRoot, "Downloads/Reports").apply { mkdirs() }
            val hiddenDir = File(tempRoot, ".thumbnails").apply { mkdirs() }
            val androidDataDir = File(tempRoot, "Android/data/com.other").apply { mkdirs() }
            val cacheDir = File(tempRoot, "cache").apply { mkdirs() }

            val validPdf1 = File(booksDir, "Kotlin Deep Dive.pdf").also { writeFlatePdf(it, "BT (Kotlin) Tj ET") }
            val validPdf2 = File(reportsDir, "Annual_Report.pdf").also { writeFlatePdf(it, "BT (Report) Tj ET") }
            val fakePdf = File(booksDir, "corrupt.pdf").also { it.writeText("not really a pdf document content") }
            File(hiddenDir, "hidden_secret.pdf").also { writeFlatePdf(it, "BT (Hidden) Tj ET") }
            File(androidDataDir, "sandbox.pdf").also { writeFlatePdf(it, "BT (Sandbox) Tj ET") }
            File(cacheDir, "temp_preview.pdf").also { writeFlatePdf(it, "BT (Cache) Tj ET") }

            // Pre-seed validPdf1 with existing user progress & favorite flag that MUST NOT be overwritten
            val preSeededUri = Uri.fromFile(validPdf1).toString()
            db.pdfDao().upsertDocument(
                PdfDocumentEntity(
                    uriString = preSeededUri,
                    title = "Kotlin Deep Dive",
                    folderName = "Books",
                    fileSizeBytes = validPdf1.length(),
                    pageCount = 42,
                    currentPage = 17,
                    readingStatus = ReadingStatus.READING.name,
                    isFavorite = true
                )
            )

            val scanner = DevicePdfScanner(context, db.pdfDao(), pdfEngine)
            val firstPass = scanner.scanDevicePdfs(includeMediaStore = false, customRoots = listOf(tempRoot))

            assertFalse(firstPass.isScanning)
            assertEquals(1, firstPass.newlyAddedCount)
            assertEquals(1, firstPass.totalFoundCount)

            val allDocs = db.pdfDao().observeAllDocuments().first()
            val seededDoc = allDocs.first { it.uriString == preSeededUri }
            assertEquals(17, seededDoc.currentPage)
            assertTrue(seededDoc.isFavorite)
            assertEquals(42, seededDoc.pageCount)

            val newlyScannedUri = Uri.fromFile(validPdf2).toString()
            val newlyScanned = allDocs.firstOrNull { it.uriString == newlyScannedUri }
            assertTrue(newlyScanned != null)
            assertEquals("Annual_Report", newlyScanned?.title)
            assertEquals("Reports", newlyScanned?.folderName)

            assertFalse(allDocs.any { it.uriString == Uri.fromFile(fakePdf).toString() })
            assertFalse(allDocs.any { it.title == "hidden_secret" })
            assertFalse(allDocs.any { it.title == "sandbox" })
            assertFalse(allDocs.any { it.title == "temp_preview" })

            // Second scan must deduplicate cleanly and insert 0 duplicates
            val secondPass = scanner.scanDevicePdfs(includeMediaStore = false, customRoots = listOf(tempRoot))
            assertEquals(0, secondPass.newlyAddedCount)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `DevicePermissionsHelper builds valid non-null intents for All Files and Battery Optimization`() = runBlocking {
        val allFilesIntent = DevicePermissionsHelper.buildAllFilesAccessIntent(context)
        val batteryIntent = DevicePermissionsHelper.buildBatteryOptimizationIntent(context)
        assertTrue(!allFilesIntent.action.isNullOrBlank())
        assertTrue(!batteryIntent.action.isNullOrBlank())

        val vm = AppViewModel(repository, preferencesRepository, pdfTextExtractor)
        val collectJob = launch(Dispatchers.Default) { vm.uiState.collect {} }
        try {
            vm.onPermissionsRefreshed(
                DeviceAccessStatus(
                    hasAllFilesAccess = false,
                    isBatteryOptimizationIgnored = false
                )
            )
            val updated = awaitState(vm) {
                !it.hasAllFilesAccess && !it.isBatteryOptimizationIgnored
            }
            assertFalse(updated.hasAllFilesAccess)
            assertFalse(updated.isBatteryOptimizationIgnored)
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

    private fun writeFlatePdf(target: File, content: String) {
        val compressed = ByteArrayOutputStream().apply {
            val deflater = Deflater()
            deflater.setInput(content.toByteArray(Charsets.ISO_8859_1))
            deflater.finish()
            val buf = ByteArray(1024)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                write(buf, 0, n)
            }
            deflater.end()
        }.toByteArray()

        val header = "%PDF-1.4\n1 0 obj\n<< /Filter /FlateDecode /Length ${compressed.size} >>\nstream\n"
            .toByteArray(Charsets.ISO_8859_1)
        val footer = "\nendstream\nendobj\n%%EOF".toByteArray(Charsets.ISO_8859_1)
        target.writeBytes(header + compressed + footer)
    }
}
