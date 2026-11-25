package com.example.data.search

import java.text.Normalizer

object SearchTextNormalizer {
    const val MIN_QUERY_CHARS = 2
    const val MAX_QUERY_TOKENS = 8

    /** Used for BOTH indexing and querying. Result: lowercase words separated by single spaces. */
    fun forIndex(raw: String): String {
        val nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length)
        var lastWasSpace = true
        for (ch in nfc) {
            when {
                ch == '\u200D' || ch == '\u200C' -> Unit // drop ZWJ/ZWNJ: keeps the word in one piece
                ch.isLetterOrDigit() || isMark(ch) -> {
                    sb.append(ch.lowercaseChar())
                    lastWasSpace = false
                }
                else -> if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return sb.toString().trimEnd()
    }

    private fun isMark(ch: Char): Boolean {
        val t = Character.getType(ch)
        return t == Character.NON_SPACING_MARK.toInt() ||
            t == Character.COMBINING_SPACING_MARK.toInt() ||
            t == Character.ENCLOSING_MARK.toInt()
    }

    /** FTS MATCH string, or null if the input is too short. Last token is a prefix search. */
    fun buildMatchQuery(userInput: String): String? {
        val norm = forIndex(userInput)
        if (norm.length < MIN_QUERY_CHARS) return null
        val tokens = norm.split(' ').filter { it.isNotEmpty() }.take(MAX_QUERY_TOKENS)
        if (tokens.isEmpty()) return null
        return tokens.mapIndexed { i, t -> if (i == tokens.lastIndex) "$t*" else t }.joinToString(" ")
    }
}

object PageRowId {
    const val PAGES_PER_DOC = 1_000_000L
    fun of(docId: Long, pageIndex: Int): Long = docId * PAGES_PER_DOC + pageIndex
    fun docOf(rowId: Long): Long = rowId / PAGES_PER_DOC
    fun pageOf(rowId: Long): Int = (rowId % PAGES_PER_DOC).toInt()
    fun firstOf(docId: Long): Long = docId * PAGES_PER_DOC
    fun lastOf(docId: Long): Long = docId * PAGES_PER_DOC + PAGES_PER_DOC - 1
}
