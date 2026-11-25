package com.example.data.text

import java.text.Normalizer

object TextNormalizer {

    private val lineEndingsRegex = Regex("\r\n|\r")
    private val invisibleStripRegex = Regex("[\u00AD\u200B\uFEFF\uFFFC]")
    private val horizontalWhitespaceRegex = Regex("[ \t\u00A0]+")
    private val multipleBlankLinesRegex = Regex("\n{3,}")
    private val trailingHyphenWordRegex = Regex("[A-Za-z]-$")
    private val lowercaseStartRegex = Regex("^[a-z].*")
    private val bulletOrNumberedLineRegex = Regex("^\\s*(?:[•‣▪◦\\-*]|(?:\\d+|[০-৯]+)[.)])\\s+.*")
    private val sentenceEndingRegex = Regex(".*[.?!।॥…][)\"'”’\\]]*$")

    fun clean(raw: String): String {
        if (raw.isEmpty()) return ""

        // Unify line endings first and remove BOM / ZWSP / soft-hyphen / object replacement.
        // Keep \u200C (ZWNJ) and \u200D (ZWJ) intact for Bengali conjuncts and vowel forms.
        val preCleaned = buildString(raw.length) {
            val normalizedNewlines = raw.replace(lineEndingsRegex, "\n")
            for (ch in normalizedNewlines) {
                if (ch == '\u00AD' || ch == '\u200B' || ch == '\uFEFF' || ch == '\uFFFC') {
                    continue
                }
                val code = ch.code
                val isDisallowedControl =
                    (code in 0x00..0x1F && ch != '\n' && ch != '\t') || code in 0x7F..0x9F
                if (!isDisallowedControl) {
                    append(ch)
                }
            }
        }

        val nfc = Normalizer.normalize(preCleaned, Normalizer.Form.NFC)
        val lines = nfc.split('\n').map { line ->
            line.replace(horizontalWhitespaceRegex, " ").trim()
        }
        return lines.joinToString("\n")
            .replace(multipleBlankLinesRegex, "\n\n")
            .trim()
    }

    fun forDisplay(raw: String): String {
        val cleaned = clean(raw)
        if (cleaned.isEmpty()) return ""
        return cleaned
            .replace("\uFB00", "ff")
            .replace("\uFB01", "fi")
            .replace("\uFB02", "fl")
            .replace("\uFB03", "ffi")
            .replace("\uFB04", "ffl")
    }

    fun reflow(text: String): String {
        val normalized = clean(text)
        if (normalized.isEmpty()) return ""

        val paragraphs = normalized.split(Regex("\n{2,}"))
        val reflowedParagraphs = paragraphs.mapNotNull { block ->
            val lines = block.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (lines.isEmpty()) return@mapNotNull null
            if (lines.size == 1) return@mapNotNull lines.first()

            val referenceWidth = lines.maxOf { it.length }
            val out = StringBuilder(block.length)
            var currentLine = lines.first()
            out.append(currentLine)

            for (i in 1 until lines.size) {
                val nextLine = lines[i]
                val endsWithSoftDehyphen =
                    trailingHyphenWordRegex.containsMatchIn(currentLine) &&
                        lowercaseStartRegex.matches(nextLine)
                val nextIsBullet = bulletOrNumberedLineRegex.matches(nextLine)
                val looksLikeParagraphBoundary =
                    referenceWidth >= 45 &&
                        currentLine.length < (referenceWidth * 0.75) &&
                        sentenceEndingRegex.matches(currentLine)

                when {
                    endsWithSoftDehyphen -> {
                        // Drop trailing hyphen from builder and attach next word fragment directly.
                        out.deleteCharAt(out.length - 1)
                        out.append(nextLine)
                        currentLine = nextLine
                    }

                    nextIsBullet -> {
                        out.append('\n').append(nextLine)
                        currentLine = nextLine
                    }

                    looksLikeParagraphBoundary -> {
                        out.append("\n\n").append(nextLine)
                        currentLine = nextLine
                    }

                    else -> {
                        out.append(' ').append(nextLine)
                        currentLine = nextLine
                    }
                }
            }
            out.toString()
        }

        return reflowedParagraphs
            .joinToString("\n\n")
            .replace(multipleBlankLinesRegex, "\n\n")
            .trim()
    }
}
