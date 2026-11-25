package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MjPdfDatabase
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.model.ReadingStatus
import com.example.data.pdf.PdfEngine
import com.example.data.repository.PdfLibraryRepository
import com.example.data.system.DeviceAccessStatus
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.TeenaApp
import com.example.ui.theme.TeenaTheme
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeUiScenarioTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    fun `TeenaApp UI renders Library Grid and Folders drilldown`() {
        runBlocking {
            db.pdfDao().registerOrRefresh(
                PdfDocumentEntity(
                    uriString = "file:///sample_ui_handbook.pdf",
                    title = "UI Engineering Handbook",
                    folderName = "Engineering",
                    fileSizeBytes = 10_240L,
                    pageCount = 10,
                    currentPage = 3,
                    readingStatus = ReadingStatus.READING.name,
                    lastOpenedAt = System.currentTimeMillis()
                )
            )
            preferencesRepository.setLibraryGridView(true)
        }

        val appViewModel = AppViewModel(repository, preferencesRepository)

        composeRule.setContent {
            TeenaTheme {
                TeenaApp(
                    viewModel = appViewModel,
                    onPickPdf = {},
                    onOpenDocument = { _, _ -> },
                    onOpenSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Navigate to Library tab and wait for grid item to render
        composeRule.onNodeWithTag("bottom_tab_library").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText("UI Engineering Handbook").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("UI Engineering Handbook").assertIsDisplayed()

        // Navigate to Folders tab, open Engineering folder, and inspect PDF
        composeRule.onNodeWithTag("bottom_tab_folders").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText("Engineering").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Engineering").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("UI Engineering Handbook").assertIsDisplayed()
    }

    @Test
    fun `ReaderScreen UI displays document title in TopBar`() {
        val readerViewModel = ReaderViewModel(repository, preferencesRepository, pdfEngine)
        val uri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2FPhysics_Notes.pdf"

        composeRule.setContent {
            TeenaTheme {
                ReaderScreen(
                    uriString = uri,
                    initialPage = null,
                    viewModel = readerViewModel,
                    onNavigateBack = {},
                    onOpenTextMode = {},
                    onOpenSettings = {},
                    onRotateScreen = {},
                    onSharePdf = { _, _ -> },
                    onPrintPdf = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("reader_document_title").assertIsDisplayed()
        composeRule.onNodeWithText("Physics_Notes").assertIsDisplayed()
    }

    @Test
    fun `TeenaApp displays permission banner when file access missing and dismisses cleanly`() {
        val appViewModel = AppViewModel(repository, preferencesRepository)
        appViewModel.onPermissionsRefreshed(
            DeviceAccessStatus(hasAllFilesAccess = false)
        )

        var filesRequested = false
        composeRule.setContent {
            TeenaTheme {
                TeenaApp(
                    viewModel = appViewModel,
                    onPickPdf = {},
                    onOpenDocument = { _, _ -> },
                    onOpenSettings = {},
                    onRequestAllFilesAccess = { filesRequested = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("open_pdf_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("top_search_field").assertIsDisplayed()
        composeRule.onNodeWithTag("search_submit_button").assertIsDisplayed()

        composeRule.onNodeWithTag("device_access_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("banner_grant_permission_button").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(filesRequested)

        composeRule.onNodeWithTag("banner_dismiss_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("device_access_banner").assertDoesNotExist()
    }

    @Test
    fun `TeenaApp top search accepts typing smoothly and switches tabs without lag`() {
        runBlocking {
            db.pdfDao().registerOrRefresh(
                PdfDocumentEntity(
                    uriString = "file:///kotlin_coroutines.pdf",
                    title = "Kotlin Coroutines Guide",
                    folderName = "Programming",
                    fileSizeBytes = 12_000L,
                    pageCount = 15,
                    currentPage = 2,
                    readingStatus = ReadingStatus.READING.name,
                    lastOpenedAt = System.currentTimeMillis()
                )
            )
            db.pdfDao().registerOrRefresh(
                PdfDocumentEntity(
                    uriString = "file:///calculus_notes.pdf",
                    title = "Calculus Notes",
                    folderName = "Math",
                    fileSizeBytes = 8_000L,
                    pageCount = 12,
                    currentPage = 0,
                    readingStatus = ReadingStatus.TO_READ.name,
                    lastOpenedAt = System.currentTimeMillis() - 1_000L
                )
            )
        }

        val appViewModel = AppViewModel(repository, preferencesRepository)

        composeRule.setContent {
            TeenaTheme {
                TeenaApp(
                    viewModel = appViewModel,
                    onPickPdf = {},
                    onOpenDocument = { _, _ -> },
                    onOpenSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("top_search_field").performClick()
        composeRule.onNodeWithTag("top_search_field").performTextInput("Kotlin")
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText("Kotlin Coroutines Guide").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Kotlin Coroutines Guide").assertIsDisplayed()
        composeRule.onNodeWithTag("search_clear_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("top_search_field").performTextInput("Calculus")
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText("Calculus Notes").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Calculus Notes").assertIsDisplayed()

        // Switching tab clears search and switches immediately
        composeRule.onNodeWithTag("bottom_tab_library").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText("Kotlin Coroutines Guide").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("bottom_tab_folders").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithText("2 FOLDERS").fetchSemanticsNodes().isNotEmpty() &&
                (composeRule.onAllNodesWithText("Math").fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText("Programming").fetchSemanticsNodes().isNotEmpty())
        }
        composeRule.onNodeWithTag("bottom_tab_recent").performClick()
        composeRule.waitForIdle()
    }
}
