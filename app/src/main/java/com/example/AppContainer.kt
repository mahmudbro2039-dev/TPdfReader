package com.example

import android.content.Context
import com.example.data.local.MjPdfDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.pdf.DevicePdfScanner
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.PdfTextExtractor
import com.example.data.repository.PdfLibraryRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: MjPdfDatabase = MjPdfDatabase.getInstance(appContext)
    val pdfEngine: PdfEngine = PdfEngine(appContext)
    val pdfTextExtractor: PdfTextExtractor = PdfTextExtractor(appContext)
    val preferencesRepository: PreferencesRepository = PreferencesRepository(appContext)
    val devicePdfScanner: DevicePdfScanner = DevicePdfScanner(
        context = appContext,
        pdfDao = database.pdfDao(),
        pdfEngine = pdfEngine
    )
    val libraryRepository: PdfLibraryRepository = PdfLibraryRepository(
        context = appContext,
        pdfDao = database.pdfDao(),
        pdfEngine = pdfEngine
    )
}
