package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.MjPdfDatabase
import com.example.data.local.PdfDao
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.AnnotationType
import com.example.data.model.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfDaoRegressionTest {

    private lateinit var db: MjPdfDatabase
    private lateinit var dao: PdfDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MjPdfDatabase::class.java
        ).build()
        dao = db.pdfDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun bookmarkSurvivesReRegister() = runBlocking {
        val uri = "content://docs/bookmark_survives.pdf"
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Bookmark Book",
                folderName = "Books",
                fileSizeBytes = 1024L,
                pageCount = 10
            )
        )
        dao.insertBookmark(
            BookmarkEntity(
                documentUri = uri,
                documentTitle = "Bookmark Book",
                pageIndex = 3,
                title = "Important Chapter"
            )
        )

        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Bookmark Book",
                folderName = "Downloads",
                fileSizeBytes = 2048L,
                pageCount = 12
            )
        )

        val bookmarks = dao.observeBookmarksForDocument(uri).first()
        assertEquals(1, bookmarks.size)
        assertEquals("Important Chapter", bookmarks.first().title)
    }

    @Test
    fun annotationSurvivesReRegister() = runBlocking {
        val uri = "content://docs/annotation_survives.pdf"
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Notes Book",
                folderName = "Books",
                fileSizeBytes = 1024L,
                pageCount = 10
            )
        )
        dao.insertAnnotation(
            AnnotationEntity(
                documentUri = uri,
                documentTitle = "Notes Book",
                pageIndex = 2,
                type = AnnotationType.NOTE.name,
                quotedText = "Key passage",
                noteText = "Keep this note",
                colorHex = "#FACC15"
            )
        )

        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Notes Book",
                folderName = "Other",
                fileSizeBytes = 2048L,
                pageCount = 10
            )
        )

        val annotations = dao.observeAnnotationsForDocument(uri).first()
        assertEquals(1, annotations.size)
        assertEquals("Keep this note", annotations.first().noteText)
    }

    @Test
    fun progressSurvivesReRegister() = runBlocking {
        val uri = "content://docs/progress_survives.pdf"
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Progress Book",
                folderName = "Docs",
                fileSizeBytes = 1024L,
                pageCount = 25,
                currentPage = 5,
                isFavorite = true,
                readingStatus = ReadingStatus.READING.name
            )
        )

        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Progress Book",
                folderName = "NewFolder",
                fileSizeBytes = 2048L,
                pageCount = 25,
                currentPage = 0,
                isFavorite = false,
                readingStatus = ReadingStatus.TO_READ.name
            )
        )

        val stored = dao.getDocument(uri)!!
        assertEquals(5, stored.currentPage)
        assertTrue(stored.isFavorite)
        assertEquals(ReadingStatus.READING.name, stored.readingStatus)
    }

    @Test
    fun folderLabelSurvivesReRegister() = runBlocking {
        val uri = "content://docs/folder_survives.pdf"
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Folder Book",
                folderName = "Downloads",
                fileSizeBytes = 1024L,
                pageCount = 8
            )
        )
        dao.updateDocumentFolder(uri, "Research Archive")

        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Folder Book",
                folderName = "Downloads",
                fileSizeBytes = 2048L,
                pageCount = 8
            )
        )

        val stored = dao.getDocument(uri)!!
        assertEquals("Research Archive", stored.folderName)
    }

    @Test
    fun explicitDeleteStillCascades() = runBlocking {
        val uri = "content://docs/delete_cascades.pdf"
        dao.registerOrRefresh(
            PdfDocumentEntity(
                uriString = uri,
                title = "Delete Book",
                folderName = "Books",
                pageCount = 6
            )
        )
        dao.insertBookmark(
            BookmarkEntity(
                documentUri = uri,
                documentTitle = "Delete Book",
                pageIndex = 1,
                title = "Page 2"
            )
        )
        assertEquals(1, dao.observeBookmarksForDocument(uri).first().size)

        dao.deleteDocument(uri)

        assertTrue(dao.observeBookmarksForDocument(uri).first().isEmpty())
    }
}
