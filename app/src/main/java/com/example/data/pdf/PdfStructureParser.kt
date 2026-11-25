package com.example.data.pdf

import android.content.Context
import android.net.Uri
import com.example.R

object PdfStructureParser {

    private val urlRegex = Regex("""https?://[^\s)\]"'<>]+""", RegexOption.IGNORE_CASE)
    private val pageRefRegex = Regex("""(?:Page|Chapter)\s+0?(\d{1,4})""", RegexOption.IGNORE_CASE)

    fun buildTableOfContents(
        context: Context,
        pagesText: List<String>,
        pageCount: Int
    ): List<TocEntry> {
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
                            chapterLabel = context.getString(R.string.page_badge_short, pageIndex + 1),
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
                        chapterLabel = context.getString(R.string.toc_section_label, i + 1),
                        title = context.getString(R.string.page_badge_short, i + 1),
                        pageIndex = i
                    )
                )
            }
        }
        return entries
    }

    fun extractDocumentLinks(
        context: Context,
        pagesText: List<String>,
        pageCount: Int
    ): List<DocumentLinkEntry> {
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
                            title = context.getString(R.string.link_external_web, host),
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
                                title = context.getString(R.string.link_jump_to, match.value),
                                urlOrTarget = context.getString(R.string.link_internal_page_ref, targetIndex + 1),
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
}
