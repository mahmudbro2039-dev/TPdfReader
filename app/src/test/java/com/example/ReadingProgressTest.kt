package com.example

import com.example.data.model.ReadingProgress
import com.example.data.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProgressTest {

    @Test
    fun `unopened document has zero fraction and zero percent`() {
        assertFalse(ReadingProgress.hasBeenOpened(0L))
        val fraction = ReadingProgress.fraction(
            currentPage = 0,
            pageCount = 10,
            lastOpenedAt = 0L,
            isCompleted = false
        )
        assertEquals(0f, fraction, 0.0001f)
        assertEquals(0, ReadingProgress.percent(fraction))
    }

    @Test
    fun `opened document on first page computes one-based fraction`() {
        assertTrue(ReadingProgress.hasBeenOpened(1_700_000_000_000L))
        val fraction = ReadingProgress.fraction(
            currentPage = 0,
            pageCount = 10,
            lastOpenedAt = 1_700_000_000_000L,
            isCompleted = false
        )
        assertEquals(0.1f, fraction, 0.0001f)
        assertEquals(10, ReadingProgress.percent(fraction))
    }

    @Test
    fun `unknown pageCount returns zero fraction unless completed`() {
        val fraction = ReadingProgress.fraction(
            currentPage = 2,
            pageCount = 0,
            lastOpenedAt = 1_700_000_000_000L,
            isCompleted = false
        )
        assertEquals(0f, fraction, 0.0001f)
        assertEquals(0, ReadingProgress.percent(fraction))
    }

    @Test
    fun `completed document always yields 1f and 100 percent`() {
        val fraction = ReadingProgress.fraction(
            currentPage = 0,
            pageCount = 10,
            lastOpenedAt = 0L,
            isCompleted = true
        )
        assertEquals(1f, fraction, 0.0001f)
        assertEquals(100, ReadingProgress.percent(fraction))
    }

    @Test
    fun `percent floors below 1f and only returns 100 at 1f`() {
        val almostDone = ReadingProgress.fraction(
            currentPage = 998,
            pageCount = 1000,
            lastOpenedAt = 1_000L,
            isCompleted = false
        )
        assertEquals(0.999f, almostDone, 0.0001f)
        assertEquals(99, ReadingProgress.percent(almostDone))
        assertEquals(100, ReadingProgress.percent(1f))
    }

    @Test
    fun `single-page pdf never auto-completes`() {
        assertEquals(
            ReadingStatus.TO_READ,
            ReadingProgress.nextStatus(ReadingStatus.TO_READ, currentPage = 0, pageCount = 1)
        )
        assertEquals(
            ReadingStatus.READING,
            ReadingProgress.nextStatus(ReadingStatus.READING, currentPage = 0, pageCount = 1)
        )
        val openedFraction = ReadingProgress.fraction(
            currentPage = 0,
            pageCount = 1,
            lastOpenedAt = 500L,
            isCompleted = false
        )
        assertEquals(1f, openedFraction, 0.0001f)
    }

    @Test
    fun `to_read transitions to reading only after page 0 and completes on last page`() {
        assertEquals(
            ReadingStatus.TO_READ,
            ReadingProgress.nextStatus(ReadingStatus.TO_READ, currentPage = 0, pageCount = 5)
        )
        assertEquals(
            ReadingStatus.READING,
            ReadingProgress.nextStatus(ReadingStatus.TO_READ, currentPage = 1, pageCount = 5)
        )
        assertEquals(
            ReadingStatus.COMPLETED,
            ReadingProgress.nextStatus(ReadingStatus.TO_READ, currentPage = 4, pageCount = 5)
        )
        assertEquals(
            ReadingStatus.COMPLETED,
            ReadingProgress.nextStatus(ReadingStatus.READING, currentPage = 4, pageCount = 5)
        )
    }

    @Test
    fun `navigating backward never lowers reading status`() {
        assertEquals(
            ReadingStatus.READING,
            ReadingProgress.nextStatus(ReadingStatus.READING, currentPage = 0, pageCount = 10)
        )
    }

    @Test
    fun `locked statuses never auto-change`() {
        for (locked in listOf(ReadingStatus.COMPLETED, ReadingStatus.ON_HOLD, ReadingStatus.ABANDONED)) {
            assertEquals(locked, ReadingProgress.nextStatus(locked, currentPage = 0, pageCount = 10))
            assertEquals(locked, ReadingProgress.nextStatus(locked, currentPage = 9, pageCount = 10))
            assertEquals(locked, ReadingProgress.nextStatus(locked, currentPage = 2, pageCount = 10))
        }
    }
}
