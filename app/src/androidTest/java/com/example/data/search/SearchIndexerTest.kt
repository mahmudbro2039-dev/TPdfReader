package com.example.data.search

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.search.db.SearchIndexDatabase
import com.example.data.text.PdfBoxTextEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchIndexerTest {

    private lateinit var context: Context
    private lateinit var db: SearchIndexDatabase
    private lateinit var repo: SearchIndexRepository
    private lateinit var indexerScope: CoroutineScope
    private lateinit var indexer: SearchIndexer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SearchIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SearchIndexRepository(db)
        indexerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        indexer = SearchIndexer(
            appContext = context,
            scope = indexerScope,
            repo = repo,
            textEngine = PdfBoxTextEngine(context),
            libraryCandidates = emptyFlow()
        )
    }

    @After
    fun tearDown() {
        indexerScope.cancel()
        db.close()
    }

    @Test
    fun indexesThreePagePdfAndFindsBetaOnPageOneOnly() = runBlocking {
        val pdfFile = File(context.cacheDir, "search_indexer_test.pdf")
        val document = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
        }
        try {
            listOf("TEENA-ALPHA", "TEENA-BETA", "TEENA-GAMMA").forEachIndexed { index, marker ->
                val pageInfo = PdfDocument.PageInfo.Builder(400, 600, index + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText(marker, 40f, 120f, paint)
                document.finishPage(page)
            }
            pdfFile.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }

        try {
            val uri = Uri.fromFile(pdfFile).toString()
            indexer.indexSingleDocument(uri)

            val hits = repo.searchInDocument(uri, "beta")
            assertEquals(listOf(1), hits.map { it.pageIndex })
        } finally {
            pdfFile.delete()
        }
    }
}
