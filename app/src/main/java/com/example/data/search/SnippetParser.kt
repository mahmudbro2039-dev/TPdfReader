package com.example.data.search

/**
 * Splits an FTS4 snippet containing [SNIPPET_OPEN] / [SNIPPET_CLOSE] markers
 * into ordered `(text, isHighlighted)` chunks. Never throws on unbalanced markers.
 */
fun parseSnippet(raw: String): List<Pair<String, Boolean>> {
    if (raw.isEmpty()) return emptyList()
    val chunks = ArrayList<Pair<String, Boolean>>()
    val current = StringBuilder()
    var highlighted = false
    val openChar = SNIPPET_OPEN[0]
    val closeChar = SNIPPET_CLOSE[0]

    for (ch in raw) {
        when (ch) {
            openChar -> {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString() to highlighted)
                    current.setLength(0)
                }
                highlighted = true
            }
            closeChar -> {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString() to highlighted)
                    current.setLength(0)
                }
                highlighted = false
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) {
        chunks.add(current.toString() to highlighted)
    }
    return chunks
}
