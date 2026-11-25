package com.example

import android.content.Context
import com.example.data.local.MjPdfDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.ocr.OcrEngine
import com.example.data.ocr.OcrRunner
import com.example.data.pdf.DevicePdfScanner
import com.example.data.pdf.PdfEngine
import com.example.data.repository.PdfLibraryRepository
import com.example.data.search.SearchIndexRepository
import com.example.data.search.SearchIndexer
import com.example.data.search.db.SearchIndexDatabase
import com.example.data.text.PdfBoxTextEngine
import com.example.data.text.PdfTextEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: MjPdfDatabase by lazy { MjPdfDatabase.getInstance(appContext) }
    val searchIndexDatabase: SearchIndexDatabase by lazy { SearchIndexDatabase.getInstance(appContext) }
    val searchIndexRepository: SearchIndexRepository by lazy { SearchIndexRepository(searchIndexDatabase) }
    val pdfEngine: PdfEngine by lazy { PdfEngine(appContext) }
    val textEngine: PdfTextEngine by lazy { PdfBoxTextEngine(appContext) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }
    val initialPreferences: UserPreferences by lazy {
        runBlocking { preferencesRepository.preferencesFlow.first() }
    }
    val devicePdfScanner: DevicePdfScanner by lazy {
        DevicePdfScanner(
            context = appContext,
            pdfDao = database.pdfDao(),
            pdfEngine = pdfEngine
        )
    }
    val libraryRepository: PdfLibraryRepository by lazy {
        PdfLibraryRepository(
            context = appContext,
            pdfDao = database.pdfDao(),
            pdfEngine = pdfEngine,
            searchIndexRepository = searchIndexRepository
        )
    }
    private val libraryCandidates: Flow<List<String>> by lazy {
        combine(
            database.pdfDao().observeIndexCandidates(),
            libraryRepository.missingUris
        ) { rows, missing ->
            rows.asSequence()
                .filter { it.uriString !in missing }
                .sortedByDescending { it.lastOpenedAt }
                .map { it.uriString }
                .toList()
        }.distinctUntilChanged { a, b -> a.toSet() == b.toSet() }
    }
    val searchIndexer: SearchIndexer by lazy {
        SearchIndexer(
            appContext = appContext,
            scope = appScope,
            repo = searchIndexRepository,
            textEngine = textEngine,
            libraryCandidates = libraryCandidates
        )
    }
    val ocrEngine: OcrEngine by lazy { OcrEngine(appContext) }
    val ocrRunner: OcrRunner by lazy {
        OcrRunner(
            appContext = appContext,
            appScope = appScope,
            repo = searchIndexRepository,
            ocrEngine = ocrEngine
        )
    }
}
