package com.example.data.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.search.db.SearchIndexDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchIndexDbRobolectricTest {

    private lateinit var db: SearchIndexDatabase
    private lateinit var repo: SearchIndexRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SearchIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SearchIndexRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun bengaliPrefixSearch() = runBlocking {
        val uri = "file:///bengali_poem.pdf"
        val doc = repo.getOrCreate(uri, "fp-bn-1")
        repo.writeTextBatch(
            docId = doc.docId,
            pages = listOf(0 to SearchTextNormalizer.forIndex("আমার সোনার বাংলা আমি তোমায় ভালোবাসি।")),
            nextPage = 1
        )

        assertEquals(listOf(0), repo.searchAll("সোনার").map { it.pageIndex })
        assertEquals(listOf(0), repo.searchAll("সোন").map { it.pageIndex })
        assertEquals(listOf(0), repo.searchAll("বাংলা").map { it.pageIndex })
        assertTrue(repo.searchAll("নার").isEmpty())
    }

    @Test
    fun punctuationAndDanda() = runBlocking {
        val uri = "file:///danda.pdf"
        val doc = repo.getOrCreate(uri, "fp-danda-1")
        repo.writeTextBatch(
            docId = doc.docId,
            pages = listOf(0 to SearchTextNormalizer.forIndex("খাই।")),
            nextPage = 1
        )

        val hits = repo.searchAll("খাই")
        assertEquals(1, hits.size)
        assertEquals(0, hits.first().pageIndex)
    }

    @Test
    fun englishCaseInsensitive() = runBlocking {
        val uri = "file:///hello.pdf"
        val doc = repo.getOrCreate(uri, "fp-en-1")
        repo.writeTextBatch(
            docId = doc.docId,
            pages = listOf(2 to SearchTextNormalizer.forIndex("Hello World")),
            nextPage = 3
        )

        val hits = repo.searchAll("hello wor")
        assertEquals(1, hits.size)
        assertEquals(2, hits.first().pageIndex)
    }

    @Test
    fun searchInDocIsolated() = runBlocking {
        val uriA = "file:///doc_a.pdf"
        val uriB = "file:///doc_b.pdf"
        val docA = repo.getOrCreate(uriA, "fp-a")
        val docB = repo.getOrCreate(uriB, "fp-b")

        repo.writeTextBatch(docA.docId, listOf(1 to SearchTextNormalizer.forIndex("shared keyword alpha")), nextPage = 2)
        repo.writeTextBatch(docB.docId, listOf(4 to SearchTextNormalizer.forIndex("shared keyword beta")), nextPage = 5)

        val hitsA = repo.searchInDocument(uriA, "shared")
        assertEquals(1, hitsA.size)
        assertEquals(uriA, hitsA.first().uriString)
        assertEquals(1, hitsA.first().pageIndex)
    }

    @Test
    fun removeDeletesPages() = runBlocking {
        val uri = "file:///removable.pdf"
        val doc = repo.getOrCreate(uri, "fp-rm")
        repo.writeTextBatch(doc.docId, listOf(0 to SearchTextNormalizer.forIndex("vanishing phrase")), nextPage = 1)
        assertEquals(1, repo.searchAll("vanishing").size)

        repo.remove(uri)

        assertTrue(repo.searchAll("vanishing").isEmpty())
    }

    @Test
    fun resumePointer() = runBlocking {
        val uri = "file:///resume.pdf"
        val doc = repo.getOrCreate(uri, "fp-resume")
        repo.writeTextBatch(doc.docId, listOf(0 to SearchTextNormalizer.forIndex("page zero")), nextPage = 10)

        val reopenedRepo = SearchIndexRepository(db)
        assertEquals(10, reopenedRepo.getDoc(uri)?.nextPage)
    }

    @Test
    fun reindexOnFingerprintChange() = runBlocking {
        val uri = "file:///modified.pdf"
        val firstDoc = repo.getOrCreate(uri, "fp-v1")
        repo.writeTextBatch(firstDoc.docId, listOf(0 to SearchTextNormalizer.forIndex("obsolete token")), nextPage = 4)
        assertEquals(1, repo.searchAll("obsolete").size)

        val resetDoc = repo.getOrCreate(uri, "fp-v2")
        assertEquals(0, resetDoc.nextPage)
        assertTrue(repo.searchAll("obsolete").isEmpty())
    }
}
