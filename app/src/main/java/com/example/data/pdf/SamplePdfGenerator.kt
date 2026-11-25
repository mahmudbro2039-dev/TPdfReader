package com.example.data.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import com.example.R
import com.example.ui.theme.ContentColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class TocEntry(
    val chapterLabel: String,
    val title: String,
    val pageIndex: Int
)

data class DocumentLinkEntry(
    val title: String,
    val urlOrTarget: String,
    val pageIndex: Int,
    val isInternalPageLink: Boolean = false,
    val targetPageIndex: Int = 0
)

/**
 * Generates realistic multi-page local PDF documents on first launch across folders
 * so every TeenaReader feature (Recent, Library 5 statuses, Folders with breadcrumbs,
 * Table of Contents, Live Search, Hide Margins, Highlights/Notes, Vector Signatures,
 * Share Quote Cards, and Text Mode) can be experienced immediately.
 */
object SamplePdfGenerator {

    private const val GUIDE_FILE_NAME = "TeenaReader_Quick_Guide.pdf"
    private const val ARCH_FILE_NAME = "Clean_Architecture_Handbook.pdf"
    const val GUIDE_TITLE = "TeenaReader Official Guide"
    const val ARCH_TITLE = "Android Architecture & Typography"

    private data class GuidePage(
        val chapter: String,
        val title: String,
        val bodyParagraphs: List<String>,
        val tipBox: String
    )

    private val guidePages = listOf(
        GuidePage(
            chapter = "CHAPTER 01  •  WELCOME TO TEENAREADER",
            title = "Clean, Fast & Private PDF Reader",
            bodyParagraphs = listOf(
                "Welcome to TeenaReader — a fast, minimalist, offline PDF reader and document library manager crafted for zero distractions, zero ads, and complete local privacy.",
                "The Home screen is organized into three dedicated tabs: Recent (with your Continue Reading hero card and reading history), Library (with cover grid/list views and 5 reading statuses), and Folders (with directory breadcrumb navigation).",
                "Tap anywhere in the center of a page to enter True Edge-to-Edge Full-Screen Mode. All toolbars vanish smoothly so your document occupies 100% of the display."
            ),
            tipBox = "Quick Tip: Open Settings from the top menu anytime to customize app themes, default reading modes, and full-screen shortcuts."
        ),
        GuidePage(
            chapter = "CHAPTER 02  •  EVERY TOOL, ONE TAP AWAY",
            title = "Unified Reading Modes & Page Fit Policies",
            bodyParagraphs = listOf(
                "Open the 'Every Tool, One Tap Away' menu from the reader toolbar to configure how pages are displayed and navigated:",
                "• 4 Reading Modes: Vertical Continuous Scroll, Horizontal Scroll, Single Page (Book Flip with left/right edge taps), and Dual-Page Book Spread.",
                "• 3 Page Fit Policies: Fit Width, Fit Height, or Whole Page view.",
                "• Hide Margins: Automatically crop outer whitespace borders around PDF pages so text fills your screen larger and clearer."
            ),
            tipBox = "Quick Tip: Enable Incognito Mode from the top bar or reader menu whenever you want to read without recording history."
        ),
        GuidePage(
            chapter = "CHAPTER 03  •  SEARCH, TOC & LINKS",
            title = "Blazingly Fast In-Document Search & Navigation",
            bodyParagraphs = listOf(
                "Need to locate a term across a long technical book? Tap Search in PDF on the top bar for real-time live results with wrapping Next/Previous navigation and match snippets.",
                "Open Bookmarks & Table of Contents to browse structured chapter headings, your custom named bookmarks, and your browser-like Jump History stack.",
                "All embedded URLs and cross-reference links (such as https://developer.android.com/jetpack/compose) are automatically extracted into the Document Links sheet."
            ),
            tipBox = "Quick Tip: Tap the bottom page counter pill at any time to jump directly to any page number."
        ),
        GuidePage(
            chapter = "CHAPTER 04  •  ANNOTATIONS & SIGNATURES",
            title = "My Highlights, Notes, Vector Signatures & Share Quote",
            bodyParagraphs = listOf(
                "TeenaReader includes a complete annotation suite:",
                "• Multi-Color Highlights & Notes: Save key passages with 5 vibrant color swatches (Yellow, Green, Blue, Pink, Orange) and attach personal study notes.",
                "• Handwritten Vector Signatures: Draw your signature on a smooth vector pad and stamp it directly onto any page.",
                "• Share Quote Cards: Turn any memorable passage into a high-resolution, styled quote card image in Paper Light, Obsidian Dark, Warm Sepia, or Dracula Purple themes."
            ),
            tipBox = "Quick Tip: Access all your saved highlights and notes across every PDF from 'My Notes & Highlights' on the Home screen."
        ),
        GuidePage(
            chapter = "CHAPTER 05  •  TEXT MODE & EYE COMFORT",
            title = "E-Reader Text Reflow & True PDF Dark Mode",
            bodyParagraphs = listOf(
                "Tap the Text Mode button in the top reader bar to transform any complex multi-column PDF into a clean, reflowable e-reader view with custom font families (Sans, Serif, Mono), font size scaling, and Paper, Sepia, Dark, or Dracula themes.",
                "In standard PDF view, switch between Original, Dark Mode (page color inversion), Warm Sepia, and High Contrast, or fine-tune the floating Brightness slider and Auto-Scroll speed (1x–10x)."
            ),
            tipBox = "See Chapter 01 (Page 1) or Chapter 04 (Page 4) to test internal cross-reference links and Jump History."
        )
    )

    private val architecturePages = listOf(
        GuidePage(
            chapter = "PART I  •  EDITORIAL TYPOGRAPHY",
            title = "Designing High-Contrast Reading Interfaces",
            bodyParagraphs = listOf(
                "Great reading software disappears behind the content. By pairing geometric sans-serif UI labels (Plus Jakarta Sans) with high-legibility editorial serif prose (Newsreader), visual fatigue is dramatically reduced.",
                "Warm archival paper backgrounds eliminate harsh digital glare in daylight, while obsidian charcoal and AMOLED black preserve night vision."
            ),
            tipBox = "Reference: https://material.io/design/typography for modern type scale guidelines."
        ),
        GuidePage(
            chapter = "PART II  •  MODULAR KOTLIN ARCHITECTURE",
            title = "Reactive State & Zero-Bloat PDF Rendering",
            bodyParagraphs = listOf(
                "By combining Android's native PdfRenderer with an LRU memory bitmap cache, mutex-guarded render coroutines, and GPU ColorMatrix shaders, pages render at 60fps without bundling 25MB of redundant C++ binaries.",
                "All reading progress, zoom levels, bookmarks, and multi-color annotations are persisted reactively in Room SQLite and Jetpack DataStore."
            ),
            tipBox = "Try testing Dual-Page Book Spread or Single-Page Book Flip mode on this handbook!"
        ),
        GuidePage(
            chapter = "PART III  •  ANNOTATION WORKFLOWS",
            title = "Capturing Knowledge While You Read",
            bodyParagraphs = listOf(
                "Active reading requires frictionless capture. Highlighting a paragraph, jotting down a margin note, or exporting a styled Quote Card image takes only a single tap from the reader toolbar.",
                "Your personal library stays 100% offline on your device with zero telemetry."
            ),
            tipBox = "Tap the Share Quote icon in the reader to export a styled quote card from this page."
        )
    )

    suspend fun ensureGuidePdfFile(context: Context): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "Guides & Manuals").apply { if (!exists()) mkdirs() }
        val target = File(dir, GUIDE_FILE_NAME)
        if (!target.exists() || target.length() <= 1024L) {
            writePdfPages(context, target, guidePages, "TeenaReader  •  Official Manual")
        }
        target
    }

    suspend fun ensureArchitecturePdfFile(context: Context): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "Books & Research").apply { if (!exists()) mkdirs() }
        val target = File(dir, ARCH_FILE_NAME)
        if (!target.exists() || target.length() <= 1024L) {
            writePdfPages(context, target, architecturePages, "TeenaReader  •  Books & Research")
        }
        target
    }

    private fun writePdfPages(
        context: Context,
        target: File,
        pages: List<GuidePage>,
        footerLabel: String
    ) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val marginX = 52f
        val contentWidth = pageWidth - marginX * 2

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContentColors.sampleGuideAccent.toArgb()
            style = Paint.Style.FILL
        }
        val chapterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContentColors.sampleGuideAccent.toArgb()
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContentColors.sampleGuideHeading.toArgb()
            textSize = 23f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContentColors.sampleGuideBody.toArgb()
            textSize = 13.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val tipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContentColors.sampleGuideTipBg.toArgb()
            style = Paint.Style.FILL
        }
        val tipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContentColors.sampleGuideTipText.toArgb()
            textSize = 11.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContentColors.sampleGuideMuted.toArgb()
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContentColors.sampleGuideDivider.toArgb()
            strokeWidth = 1f
        }

        try {
            pages.forEachIndexed { index, spec ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(ContentColors.pagePaper.toArgb())
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 9f, accentPaint)

                var y = 72f
                canvas.drawText(spec.chapter, marginX, y, chapterPaint)
                y += 30f

                y = drawWrappedText(canvas, spec.title, marginX, y, contentWidth, titlePaint, 30f)
                y += 10f
                canvas.drawLine(marginX, y, pageWidth - marginX, y, dividerPaint)
                y += 28f

                spec.bodyParagraphs.forEach { paragraph ->
                    y = drawWrappedText(canvas, paragraph, marginX, y, contentWidth, bodyPaint, 21f)
                    y += 15f
                }

                val tipTop = (y + 16f).coerceAtMost(pageHeight - 170f)
                val tipLines = wrapLines(spec.tipBox, contentWidth - 36f, tipTextPaint)
                val tipHeight = 32f + tipLines.size * 18f
                val tipRect = RectF(marginX, tipTop, pageWidth - marginX, tipTop + tipHeight)
                canvas.drawRoundRect(tipRect, 10f, 10f, tipBgPaint)
                canvas.drawRect(marginX, tipTop + 8f, marginX + 4f, tipTop + tipHeight - 8f, accentPaint)

                var tipY = tipTop + 25f
                tipLines.forEach { line ->
                    canvas.drawText(line, marginX + 18f, tipY, tipTextPaint)
                    tipY += 18f
                }

                canvas.drawLine(marginX, pageHeight - 54f, pageWidth - marginX, pageHeight - 54f, dividerPaint)
                canvas.drawText(footerLabel, marginX, pageHeight - 34f, footerPaint)
                val pageLabel = context.getString(R.string.page_of, index + 1, pages.size)
                val labelWidth = footerPaint.measureText(pageLabel)
                canvas.drawText(pageLabel, pageWidth - marginX - labelWidth, pageHeight - 34f, footerPaint)

                document.finishPage(page)
            }

            FileOutputStream(target).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }
    }

    private fun drawWrappedText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ): Float {
        var y = startY
        wrapLines(text, maxWidth, paint).forEach { line ->
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun wrapLines(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
