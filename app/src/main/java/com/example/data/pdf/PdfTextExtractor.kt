package com.example.data.pdf

import android.content.Context
import android.net.Uri
import android.util.LruCache
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
    private val extractedPagesCache = LruCache<String, List<String>>(16)

    fun invalidateCache(uriString: String) {
        synchronized(extractedPagesCache) {
            extractedPagesCache.remove(uriString)
        }
    }

    suspend fun extractPagesText(uriString: String, totalPages: Int): List<String> =
        withContext(Dispatchers.IO) {
            SamplePdfGenerator.getCompanionTextForUri(context, uriString)?.let { companionPages ->
                return@withContext companionPages
            }

            synchronized(extractedPagesCache) {
                extractedPagesCache.get(uriString)?.let { cached ->
                    if (totalPages <= 0 || cached.size == totalPages) return@withContext cached
                }
            }

            val rawBytes = readBytes(uriString) ?: return@withContext List(totalPages.coerceAtLeast(1)) { "" }
            val pdfContent = String(rawBytes, StandardCharsets.ISO_8859_1)

            val extractedBlocks = extractAllTextStreams(rawBytes, pdfContent)
            if (extractedBlocks.isEmpty()) {
                return@withContext List(totalPages.coerceAtLeast(1)) { "" }
            }

            if (totalPages <= 1) {
                val single = listOf(extractedBlocks.joinToString("\n\n"))
                synchronized(extractedPagesCache) { extractedPagesCache.put(uriString, single) }
                return@withContext single
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
            synchronized(extractedPagesCache) { extractedPagesCache.put(uriString, result) }
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

            if (!isStreamKeyword(latin1, streamMarker)) {
                searchIndex = streamMarker + 6
                continue
            }

            val headerStart = (streamMarker - 512).coerceAtLeast(0)
            val headerSnippet = latin1.substring(headerStart, streamMarker)
            val isFlate = headerSnippet.contains("/FlateDecode")
            val isSkipStream = headerSnippet.contains("/Subtype /Image") ||
                headerSnippet.contains("/Subtype/Image") ||
                headerSnippet.contains("/FontFile") ||
                headerSnippet.contains("/Metadata")

            var dataStart = streamMarker + 6
            if (dataStart < rawBytes.size && rawBytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < rawBytes.size && rawBytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endStreamMarker = latin1.indexOf("endstream", dataStart)
            if (endStreamMarker < 0) break

            searchIndex = endStreamMarker + 9

            if (isSkipStream) continue

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
            if (parsed.isNotBlank()) {
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
                val output = ByteArrayOutputStream((compressed.size * 3).coerceIn(1024, 131_072))
                val buffer = ByteArray(8192)
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
                '<' -> {
                    if (i + 1 < len && stream[i + 1] == '<') {
                        i += 2
                    } else {
                        val (hexText, nextIndex) = readPdfHexString(stream, i)
                        if (hexText.isNotEmpty()) sb.append(hexText)
                        i = nextIndex
                    }
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
                    } else if (i + 1 < len && (stream[i + 1] == 'j' || stream[i + 1] == 'J')) {
                        if (sb.isNotEmpty() && !sb.last().isWhitespace()) sb.append(' ')
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
                            var code = 0
                            var digits = 0
                            var k = i + 1
                            while (k < stream.length && digits < 3 && stream[k] in '0'..'7') {
                                code = (code shl 3) or (stream[k] - '0')
                                digits++
                                k++
                            }
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
                stream[i] == '<' -> {
                    if (i + 1 < stream.length && stream[i + 1] == '<') {
                        i += 2
                    } else {
                        val (piece, next) = readPdfHexString(stream, i)
                        out.append(piece)
                        i = next
                    }
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

    private fun readPdfHexString(stream: String, startIndex: Int): Pair<String, Int> {
        val hex = StringBuilder()
        var i = startIndex + 1
        while (i < stream.length && stream[i] != '>') {
            val ch = stream[i]
            if (ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F') {
                hex.append(ch)
            }
            i++
        }
        val nextIndex = if (i < stream.length && stream[i] == '>') i + 1 else i
        if (hex.length < 2) return "" to nextIndex
        if (hex.length % 2 != 0) hex.append('0')

        val byteCount = hex.length / 2
        val bytes = ByteArray(byteCount)
        for (b in 0 until byteCount) {
            val hi = hexNibble(hex[b * 2])
            val lo = hexNibble(hex[b * 2 + 1])
            bytes[b] = ((hi shl 4) or lo).toByte()
        }

        // Check for UTF-16BE (either BOM FEFF or 00xx ASCII-in-UTF16 pattern)
        val looksUtf16Be = (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
            (bytes.size >= 4 && bytes.size % 2 == 0 && (0 until bytes.size step 2).count { bytes[it] == 0.toByte() } * 2 >= bytes.size / 2)

        val decoded = if (looksUtf16Be) {
            val offset = if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) 2 else 0
            val sb = StringBuilder(bytes.size / 2)
            var idx = offset
            while (idx + 1 < bytes.size) {
                val code = ((bytes[idx].toInt() and 0xFF) shl 8) or (bytes[idx + 1].toInt() and 0xFF)
                if (code in 32..126 || code >= 160) sb.append(code.toChar())
                idx += 2
            }
            sb.toString()
        } else {
            val sb = StringBuilder(bytes.size)
            for (b in bytes) {
                val code = b.toInt() and 0xFF
                if (code in 32..126 || code >= 160) sb.append(code.toChar())
            }
            sb.toString()
        }
        return decoded to nextIndex
    }

    private fun hexNibble(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        in 'A'..'F' -> ch - 'A' + 10
        else -> 0
    }

    private fun isStreamKeyword(text: String, index: Int): Boolean {
        if (index > 0) {
            val prev = text[index - 1]
            if (!prev.isWhitespace() && prev != '>' && prev != ']') return false
        }
        val afterIndex = index + 6
        if (afterIndex >= text.length) return false
        val after = text[afterIndex]
        return after == '\r' || after == '\n'
    }

    private fun cleanExtractedText(raw: String): String {
        return raw
            .replace(horizontalSpaceRegex, " ")
            .replace(multiNewlineRegex, "\n\n")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    private fun isBoundary(text: String, index: Int): Boolean {
        if (index < 0 || index >= text.length) return true
        return text[index].isWhitespace() || text[index] in "[]()<>/%"
    }

    companion object {
        private val whitespaceRegex = Regex("\\s+")
        private val horizontalSpaceRegex = Regex("[ \\t]+")
        private val multiNewlineRegex = Regex("\\n{3,}")

        fun containsNormalized(haystack: String, query: String): Boolean {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return false
            if (haystack.contains(trimmed, ignoreCase = true)) return true
            val needle = trimmed.replace(whitespaceRegex, " ")
            if (needle != trimmed && haystack.contains(needle, ignoreCase = true)) return true
            val normalizedHaystack = haystack.replace(whitespaceRegex, " ")
            return normalizedHaystack.contains(needle, ignoreCase = true)
        }
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
