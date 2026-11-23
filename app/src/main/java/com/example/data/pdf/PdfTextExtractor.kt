package com.example.data.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

/**
 * Lightweight pure-Kotlin PDF text extractor for MJ PDF's Text Mode,
 * Live Search, Table of Contents parsing, and Hyperlink detection.
 */
class PdfTextExtractor(private val context: Context) {

    private val urlRegex = Regex("""https?://[^\s)\]"'<>]+""", RegexOption.IGNORE_CASE)
    private val pageRefRegex = Regex("""(?:Page|Chapter)\s+0?(\d{1,4})""", RegexOption.IGNORE_CASE)

    suspend fun extractPagesText(uriString: String, totalPages: Int): List<String> =
        withContext(Dispatchers.IO) {
            SamplePdfGenerator.getCompanionTextForUri(context, uriString)?.let { companionPages ->
                return@withContext companionPages
            }

            val rawBytes = readBytes(uriString) ?: return@withContext List(totalPages.coerceAtLeast(1)) { "" }
            val pdfContent = String(rawBytes, StandardCharsets.ISO_8859_1)

            val extractedBlocks = extractAllTextStreams(rawBytes, pdfContent)
            if (extractedBlocks.isEmpty()) {
                return@withContext List(totalPages.coerceAtLeast(1)) { "" }
            }

            if (totalPages <= 1) {
                return@withContext listOf(extractedBlocks.joinToString("\n\n"))
            }

            val result = MutableList(totalPages) { "" }
            if (extractedBlocks.size >= totalPages) {
                for (i in 0 until totalPages) {
                    result[i] = extractedBlocks[i]
                }
                if (extractedBlocks.size > totalPages) {
                    val remainder = extractedBlocks.drop(totalPages).joinToString("\n\n")
                    if (remainder.isNotBlank()) {
                        result[totalPages - 1] = (result[totalPages - 1] + "\n\n" + remainder).trim()
                    }
                }
            } else {
                val combined = extractedBlocks.joinToString("\n\n")
                val paragraphs = combined.split("\n\n").filter { it.isNotBlank() }
                if (paragraphs.size >= totalPages) {
                    val chunkSize = (paragraphs.size + totalPages - 1) / totalPages
                    paragraphs.chunked(chunkSize).forEachIndexed { idx, chunk ->
                        if (idx < totalPages) {
                            result[idx] = chunk.joinToString("\n\n")
                        }
                    }
                } else {
                    extractedBlocks.forEachIndexed { idx, block ->
                        if (idx < totalPages) result[idx] = block
                    }
                }
            }
            result
        }

    fun buildTableOfContents(pagesText: List<String>, pageCount: Int): List<TocEntry> {
        val entries = mutableListOf<TocEntry>()
        pagesText.forEachIndexed { pageIndex, pageText ->
            val lines = pageText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                val firstLine = lines.first()
                val secondLine = lines.getOrNull(1) ?: ""
                val isChapterHeader = firstLine.startsWith("CHAPTER", ignoreCase = true) ||
                    firstLine.startsWith("PART ", ignoreCase = true) ||
                    firstLine.startsWith("SECTION", ignoreCase = true) ||
                    (firstLine.length in 5..64 && firstLine == firstLine.uppercase() && firstLine.any { it.isLetter() })

                if (isChapterHeader) {
                    val chapterTitle = if (secondLine.isNotBlank() && secondLine.length <= 80) {
                        secondLine
                    } else {
                        firstLine
                    }
                    entries.add(
                        TocEntry(
                            chapterLabel = firstLine.take(42),
                            title = chapterTitle,
                            pageIndex = pageIndex
                        )
                    )
                } else if (firstLine.length in 6..72) {
                    entries.add(
                        TocEntry(
                            chapterLabel = "Page ${pageIndex + 1}",
                            title = firstLine,
                            pageIndex = pageIndex
                        )
                    )
                }
            }
        }

        if (entries.isEmpty() && pageCount > 0) {
            for (i in 0 until pageCount) {
                entries.add(
                    TocEntry(
                        chapterLabel = "Section ${i + 1}",
                        title = "Page ${i + 1}",
                        pageIndex = i
                    )
                )
            }
        }
        return entries
    }

    fun extractDocumentLinks(pagesText: List<String>, pageCount: Int): List<DocumentLinkEntry> {
        val links = mutableListOf<DocumentLinkEntry>()
        val seen = mutableSetOf<String>()

        pagesText.forEachIndexed { pageIndex, pageText ->
            urlRegex.findAll(pageText).forEach { match ->
                val url = match.value.trimEnd('.', ',', ';', ')')
                val key = "$pageIndex|$url"
                if (seen.add(key)) {
                    val host = runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)
                    links.add(
                        DocumentLinkEntry(
                            title = "External Web Link ($host)",
                            urlOrTarget = url,
                            pageIndex = pageIndex,
                            isInternalPageLink = false
                        )
                    )
                }
            }

            pageRefRegex.findAll(pageText).forEach { match ->
                val num = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val targetIndex = (num - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                if (targetIndex != pageIndex) {
                    val key = "$pageIndex->p$targetIndex"
                    if (seen.add(key)) {
                        links.add(
                            DocumentLinkEntry(
                                title = "Jump to ${match.value}",
                                urlOrTarget = "Internal reference to Page ${targetIndex + 1}",
                                pageIndex = pageIndex,
                                isInternalPageLink = true,
                                targetPageIndex = targetIndex
                            )
                        )
                    }
                }
            }
        }
        return links
    }

    private fun extractAllTextStreams(rawBytes: ByteArray, latin1: String): List<String> {
        val blocks = mutableListOf<String>()
        var searchIndex = 0
        val maxStreams = 200

        while (searchIndex < latin1.length && blocks.size < maxStreams) {
            val streamMarker = latin1.indexOf("stream", searchIndex)
            if (streamMarker < 0) break

            val headerStart = (streamMarker - 320).coerceAtLeast(0)
            val headerSnippet = latin1.substring(headerStart, streamMarker)
            val isFlate = headerSnippet.contains("/FlateDecode")
            val isImage = headerSnippet.contains("/Subtype /Image") || headerSnippet.contains("/Subtype/Image")

            var dataStart = streamMarker + 6
            if (dataStart < rawBytes.size && rawBytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < rawBytes.size && rawBytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endStreamMarker = latin1.indexOf("endstream", dataStart)
            if (endStreamMarker < 0) break

            searchIndex = endStreamMarker + 9

            if (isImage) continue

            val dataEnd = endStreamMarker.coerceAtMost(rawBytes.size)
            if (dataEnd <= dataStart) continue

            val slice = rawBytes.copyOfRange(dataStart, dataEnd)
            val decodedBytes = if (isFlate) inflateSafely(slice) else slice
            if (decodedBytes == null || decodedBytes.isEmpty()) continue

            val contentStream = String(decodedBytes, StandardCharsets.ISO_8859_1)
            if (!contentStream.contains("BT") && !contentStream.contains("Tj") && !contentStream.contains("TJ")) {
                continue
            }

            val parsed = parseTextOperators(contentStream).trim()
            if (parsed.length >= 12) {
                blocks.add(parsed)
            }
        }
        return blocks
    }

    private fun inflateSafely(compressed: ByteArray): ByteArray? {
        return runCatching {
            val inflater = Inflater()
            try {
                inflater.setInput(compressed)
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var guard = 0
                while (!inflater.finished() && guard < 1500) {
                    val count = inflater.inflate(buffer)
                    if (count == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) break
                    } else {
                        output.write(buffer, 0, count)
                    }
                    guard++
                }
                output.toByteArray()
            } finally {
                inflater.end()
            }
        }.getOrNull()
    }

    private fun parseTextOperators(stream: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = stream.length
        var inTextObject = false

        while (i < len) {
            if (stream.startsWith("BT", i) && isBoundary(stream, i - 1) && isBoundary(stream, i + 2)) {
                inTextObject = true
                i += 2
                continue
            }
            if (stream.startsWith("ET", i) && isBoundary(stream, i - 1) && isBoundary(stream, i + 2)) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                inTextObject = false
                i += 2
                continue
            }
            if (!inTextObject) {
                i++
                continue
            }

            when (stream[i]) {
                '(' -> {
                    val (literal, nextIndex) = readPdfLiteralString(stream, i)
                    if (literal.isNotEmpty()) sb.append(literal)
                    i = nextIndex
                }
                '[' -> {
                    val (arrayText, nextIndex) = readPdfArrayText(stream, i)
                    if (arrayText.isNotEmpty()) sb.append(arrayText)
                    i = nextIndex
                }
                'T' -> {
                    if (i + 1 < len && (stream[i + 1] == 'd' || stream[i + 1] == 'D' || stream[i + 1] == '*')) {
                        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                        i += 2
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }

        return cleanExtractedText(sb.toString())
    }

    private fun readPdfLiteralString(stream: String, startIndex: Int): Pair<String, Int> {
        val out = StringBuilder()
        var i = startIndex + 1
        var depth = 1
        while (i < stream.length && depth > 0) {
            val ch = stream[i]
            when {
                ch == '\\' && i + 1 < stream.length -> {
                    val next = stream[i + 1]
                    when (next) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\n')
                        't' -> out.append(' ')
                        '(', ')', '\\' -> out.append(next)
                        else -> if (next in '0'..'7') {
                            var oct = ""
                            var k = i + 1
                            while (k < stream.length && oct.length < 3 && stream[k] in '0'..'7') {
                                oct += stream[k]
                                k++
                            }
                            val code = oct.toIntOrNull(8) ?: 32
                            if (code in 32..126) out.append(code.toChar())
                            i = k - 2
                        } else {
                            out.append(next)
                        }
                    }
                    i += 2
                }
                ch == '(' -> {
                    depth++
                    out.append(ch)
                    i++
                }
                ch == ')' -> {
                    depth--
                    if (depth > 0) out.append(ch)
                    i++
                }
                else -> {
                    if (ch.code in 32..126 || ch.code >= 160) out.append(ch)
                    i++
                }
            }
        }
        return out.toString() to i
    }

    private fun readPdfArrayText(stream: String, startIndex: Int): Pair<String, Int> {
        val out = StringBuilder()
        var i = startIndex + 1
        while (i < stream.length && stream[i] != ']') {
            when {
                stream[i] == '(' -> {
                    val (piece, next) = readPdfLiteralString(stream, i)
                    out.append(piece)
                    i = next
                }
                stream[i] == '-' || stream[i].isDigit() -> {
                    val numStart = i
                    i++
                    while (i < stream.length && (stream[i].isDigit() || stream[i] == '.')) i++
                    val adjustment = stream.substring(numStart, i).toFloatOrNull() ?: 0f
                    if (adjustment < -160f && out.isNotEmpty() && !out.endsWith(" ")) {
                        out.append(' ')
                    }
                }
                else -> i++
            }
        }
        return out.toString() to (i + 1).coerceAtMost(stream.length)
    }

    private fun cleanExtractedText(raw: String): String {
        return raw
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    private fun isBoundary(text: String, index: Int): Boolean {
        if (index < 0 || index >= text.length) return true
        return text[index].isWhitespace() || text[index] in "[]()<>/%"
    }

    private fun readBytes(uriString: String): ByteArray? {
        return runCatching {
            val uri = Uri.parse(uriString)
            val stream: InputStream? = when (uri.scheme) {
                "file" -> File(uri.path ?: return null).takeIf { it.exists() }?.inputStream()
                null -> File(uriString).takeIf { it.exists() }?.inputStream()
                else -> context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16_384)
                var total = 0
                val maxBytes = 18 * 1024 * 1024
                while (total < maxBytes) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    buffer.write(chunk, 0, read)
                    total += read
                }
                buffer.toByteArray()
            }
        }.getOrNull()
    }
}
