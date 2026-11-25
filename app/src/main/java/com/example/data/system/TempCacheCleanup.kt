package com.example.data.system

import java.io.File

object TempCacheCleanup {

    const val MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

    private val ALWAYS_CLEAN_DIRS = listOf(
        "shared",
        "shared_quotes",
        "ocr_tmp"
    )

    private const val PDFBOX_TMP_DIR = "pdfbox_tmp"

    fun cleanExpiredTempFiles(
        cacheDir: File,
        includePdfBoxTmp: Boolean,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = MAX_AGE_MS
    ) {
        val cutoffMs = nowMs - maxAgeMs
        for (dirName in ALWAYS_CLEAN_DIRS) {
            pruneOldFiles(File(cacheDir, dirName), cutoffMs)
        }
        if (includePdfBoxTmp) {
            pruneOldFiles(File(cacheDir, PDFBOX_TMP_DIR), cutoffMs)
        }
    }

    private fun pruneOldFiles(root: File, cutoffMs: Long) {
        runCatching {
            if (!root.exists() || !root.isDirectory) return
            val children = root.listFiles() ?: return
            for (child in children) {
                pruneEntry(child, cutoffMs)
            }
        }
    }

    private fun pruneEntry(entry: File, cutoffMs: Long) {
        runCatching {
            if (entry.isDirectory) {
                entry.listFiles()?.forEach { nested ->
                    pruneEntry(nested, cutoffMs)
                }
                if (entry.listFiles()?.isEmpty() == true) {
                    entry.delete()
                }
            } else if (entry.isFile) {
                val modified = entry.lastModified()
                if (modified in 1L until cutoffMs) {
                    entry.delete()
                }
            }
        }
    }
}
