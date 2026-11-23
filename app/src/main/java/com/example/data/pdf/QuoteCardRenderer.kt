package com.example.data.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.data.model.QuoteCardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Generates high-resolution styled Quote Cards from selected PDF passages
 * (Paper Light, Obsidian Dark, Warm Sepia, or Dracula Purple) and shares them via FileProvider,
 * matching MJ PDF v3.0/v3.1's signature "Share Quote" feature.
 */
object QuoteCardRenderer {

    suspend fun renderQuoteBitmap(
        quoteText: String,
        bookTitle: String,
        pageNumber: Int,
        theme: QuoteCardTheme
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = 1080
        val marginX = 88f
        val innerPad = 64f
        val contentWidth = width - (marginX + innerPad) * 2

        val (outerBg, cardBg, primaryText, secondaryText, accentColor) = when (theme) {
            QuoteCardTheme.LIGHT -> QuotePalette(
                outerBg = "#EDEAE4",
                cardBg = "#FCFBF8",
                primaryText = "#18181B",
                secondaryText = "#52525B",
                accent = "#B91C1C"
            )
            QuoteCardTheme.DARK -> QuotePalette(
                outerBg = "#09090B",
                cardBg = "#17171C",
                primaryText = "#F4F4F5",
                secondaryText = "#A1A1AA",
                accent = "#F87171"
            )
            QuoteCardTheme.SEPIA -> QuotePalette(
                outerBg = "#E5D9BF",
                cardBg = "#F5ECD7",
                primaryText = "#3E2F1C",
                secondaryText = "#6E563A",
                accent = "#B45309"
            )
            QuoteCardTheme.DRACULA -> QuotePalette(
                outerBg = "#1E1F29",
                cardBg = "#282A36",
                primaryText = "#F8F8F2",
                secondaryText = "#94A3B8",
                accent = "#BD93F9"
            )
        }

        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(primaryText)
            textSize = 36f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val quoteMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(accentColor)
            textSize = 96f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            alpha = 210
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(primaryText)
            textSize = 27f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(secondaryText)
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(accentColor)
            textSize = 21f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }

        val cleanQuote = quoteText.trim().take(620)
        val wrappedLines = wrapQuoteLines(cleanQuote, contentWidth, quotePaint)
        val lineHeight = 54f
        val textBlockHeight = wrappedLines.size * lineHeight

        val cardTop = 88f
        val cardHeight = (260f + textBlockHeight + 170f).coerceAtLeast(520f)
        val totalHeight = (cardTop * 2 + cardHeight).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Outer canvas backdrop
        canvas.drawColor(Color.parseColor(outerBg))

        // Main elevated quote card
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(cardBg)
            style = Paint.Style.FILL
        }
        val cardRect = RectF(marginX, cardTop, width - marginX, cardTop + cardHeight)
        canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)

        // Top accent bar inside card
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(accentColor)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(marginX + innerPad, cardTop + 56f, marginX + innerPad + 84f, cardTop + 66f),
            6f,
            6f,
            accentPaint
        )

        // Opening decorative quotation glyph
        canvas.drawText("“", marginX + innerPad - 8f, cardTop + 154f, quoteMarkPaint)

        // Quote body lines
        var y = cardTop + 190f
        wrappedLines.forEach { line ->
            canvas.drawText(line, marginX + innerPad, y, quotePaint)
            y += lineHeight
        }

        // Bottom divider and book attribution
        val footerTop = cardTop + cardHeight - 135f
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(secondaryText)
            alpha = 65
            strokeWidth = 2f
        }
        canvas.drawLine(
            marginX + innerPad,
            footerTop,
            width - marginX - innerPad,
            footerTop,
            dividerPaint
        )

        val cleanTitle = bookTitle.ifBlank { "PDF Document" }.take(44)
        canvas.drawText(cleanTitle, marginX + innerPad, footerTop + 52f, titlePaint)
        canvas.drawText("Page $pageNumber", marginX + innerPad, footerTop + 88f, metaPaint)

        val watermark = "MJ PDF"
        val wmWidth = brandPaint.measureText(watermark)
        canvas.drawText(
            watermark,
            width - marginX - innerPad - wmWidth,
            footerTop + 88f,
            brandPaint
        )

        bitmap
    }

    suspend fun shareQuoteBitmap(
        context: Context,
        bitmap: Bitmap,
        bookTitle: String
    ): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "shared_quotes").apply { if (!exists()) mkdirs() }
            val file = File(dir, "mj_pdf_quote_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Quote from $bookTitle")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }.getOrNull()
    }

    private fun wrapQuoteLines(text: String, maxWidth: Float, paint: Paint): List<String> {
        val result = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                if (result.isNotEmpty() && result.last().isNotEmpty()) result.add("")
            } else {
                var current = StringBuilder()
                for (word in words) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        current = StringBuilder(candidate)
                    } else {
                        if (current.isNotEmpty()) result.add(current.toString())
                        current = StringBuilder(word)
                    }
                }
                if (current.isNotEmpty()) result.add(current.toString())
            }
        }
        return result.take(14)
    }

    private data class QuotePalette(
        val outerBg: String,
        val cardBg: String,
        val primaryText: String,
        val secondaryText: String,
        val accent: String
    )
}
