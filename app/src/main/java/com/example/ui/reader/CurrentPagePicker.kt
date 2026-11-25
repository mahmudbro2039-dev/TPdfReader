package com.example.ui.reader

data class PageSpan(
    val index: Int,
    val start: Int,
    val end: Int
)

object CurrentPagePicker {
    fun pick(
        spans: List<PageSpan>,
        viewportStart: Int,
        viewportEnd: Int,
        totalPages: Int
    ): Int? {
        if (spans.isEmpty() || totalPages <= 0) return null
        val first = spans.first()
        val last = spans.last()
        if (first.index == 0 && first.start >= viewportStart) return 0
        if (last.index == totalPages - 1 && last.end <= viewportEnd) return totalPages - 1
        val center = (viewportStart + viewportEnd) / 2
        spans.firstOrNull { center >= it.start && center < it.end }?.let { return it.index }
        return spans.maxByOrNull {
            (minOf(it.end, viewportEnd) - maxOf(it.start, viewportStart)).coerceAtLeast(0)
        }?.index
    }
}
