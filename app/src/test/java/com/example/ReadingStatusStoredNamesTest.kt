package com.example

import com.example.data.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatusStoredNamesTest {

    @Test
    fun readingStatusStoredNamesAndOrderRemainStable() {
        // Stored by enum .name string in Room PdfDocumentEntity.readingStatus;
        // never rename or reorder existing entries.
        val expected = listOf(
            "TO_READ",
            "READING",
            "ON_HOLD",
            "COMPLETED",
            "ABANDONED"
        )
        assertEquals(expected, ReadingStatus.entries.map { it.name })
    }
}
