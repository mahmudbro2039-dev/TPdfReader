package com.example

import com.example.data.model.FolderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderKeyTest {

    @Test
    fun distinctParentPathsWithSameLeafStayDistinct() {
        val mainDownloads = FolderKey.device("/storage/emulated/0/Download/")
        val telegramDownloads = FolderKey.device("/storage/emulated/0/Telegram/Download")
        assertEquals("/storage/emulated/0/Download", mainDownloads)
        assertEquals("/storage/emulated/0/Telegram/Download", telegramDownloads)
        assertNotEquals(mainDownloads, telegramDownloads)

        val labels = FolderKey.displayNames(listOf(mainDownloads, telegramDownloads))
        assertEquals("Download (Storage)", labels[mainDownloads])
        assertEquals("Download (Telegram)", labels[telegramDownloads])
    }

    @Test
    fun singleFolderUsesPlainLeafDisplayName() {
        val path = FolderKey.device("/storage/emulated/0/Books\\Research//")
        assertEquals("/storage/emulated/0/Books/Research", path)
        val labels = FolderKey.displayNames(listOf(path))
        assertEquals("Research", labels[path])
    }

    @Test
    fun blankAndRootMapToImportedVirtualFolder() {
        assertEquals(FolderKey.IMPORTED, FolderKey.canonical(""))
        assertEquals(FolderKey.IMPORTED, FolderKey.canonical("   "))
        assertEquals(FolderKey.IMPORTED, FolderKey.device("/"))
        assertEquals("Imported", FolderKey.displayName(FolderKey.IMPORTED))
        assertTrue(FolderKey.isVirtual(FolderKey.IMPORTED))
    }

    @Test
    fun virtualFoldersTrimPrefixAndPreserveLabel() {
        val created = FolderKey.virtual("  Exam Prep  ")
        assertEquals("vf:Exam Prep", created)
        assertTrue(FolderKey.isVirtual(created))
        assertEquals("Exam Prep", FolderKey.displayName(created))

        val doublePrefixed = FolderKey.virtual("vf:Work")
        assertEquals("vf:Work", doublePrefixed)
        assertFalse(FolderKey.isVirtual("/storage/emulated/0/Work"))
    }

    @Test
    fun virtualAndDeviceFolderWithSameDisplayNameDisambiguate() {
        val virtualBooks = FolderKey.virtual("Books")
        val deviceBooks = FolderKey.device("/storage/emulated/0/Books")
        val labels = FolderKey.displayNames(listOf(virtualBooks, deviceBooks))
        assertNotEquals(labels[virtualBooks], labels[deviceBooks])
        assertEquals("Books (custom)", labels[virtualBooks])
        assertEquals("Books (Storage)", labels[deviceBooks])
    }
}
