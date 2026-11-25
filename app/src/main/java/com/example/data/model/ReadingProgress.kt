package com.example.data.model

import kotlin.math.floor

object ReadingProgress {
    private val LOCKED_STATUSES = setOf(
        ReadingStatus.COMPLETED,
        ReadingStatus.ON_HOLD,
        ReadingStatus.ABANDONED
    )

    fun hasBeenOpened(lastOpenedAt: Long): Boolean = lastOpenedAt > 0L

    fun fraction(
        currentPage: Int,
        pageCount: Int,
        lastOpenedAt: Long,
        isCompleted: Boolean
    ): Float {
        if (isCompleted) return 1f
        if (!hasBeenOpened(lastOpenedAt) || pageCount <= 0) return 0f
        return ((currentPage + 1).toFloat() / pageCount).coerceIn(0f, 1f)
    }

    fun percent(fraction: Float): Int =
        if (fraction >= 1f) 100 else floor(fraction * 100f).toInt().coerceIn(0, 99)

    fun nextStatus(
        current: ReadingStatus,
        currentPage: Int,
        pageCount: Int
    ): ReadingStatus = nextStatus(
        current = current,
        currentPage = currentPage,
        pageCount = pageCount,
        toRead = ReadingStatus.TO_READ,
        reading = ReadingStatus.READING,
        completed = ReadingStatus.COMPLETED,
        locked = LOCKED_STATUSES
    )

    fun <S> nextStatus(
        current: S,
        currentPage: Int,
        pageCount: Int,
        toRead: S,
        reading: S,
        completed: S,
        locked: Set<S>
    ): S {
        if (current in locked) return current
        if (pageCount >= 2 && currentPage == pageCount - 1) return completed
        if (current == toRead && currentPage > 0) return reading
        return current
    }
}
