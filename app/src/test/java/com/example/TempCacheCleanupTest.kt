package com.example

import com.example.data.system.TempCacheCleanup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class TempCacheCleanupTest {

    @Test
    fun `deletes files older than 24h and keeps recent files`() {
        val cacheDir = createTempDirectory("teena_temp_cleanup_test").toFile()
        try {
            val nowMs = 1_700_000_000_000L
            val expiredTime = nowMs - TempCacheCleanup.MAX_AGE_MS - 1_000L
            val freshTime = nowMs - TempCacheCleanup.MAX_AGE_MS + 60_000L

            val sharedOld = File(cacheDir, "shared/old.pdf").apply {
                parentFile?.mkdirs()
                writeText("old")
                setLastModified(expiredTime)
            }
            val sharedFresh = File(cacheDir, "shared/fresh.pdf").apply {
                writeText("fresh")
                setLastModified(freshTime)
            }
            val quotesOld = File(cacheDir, "shared_quotes/old.png").apply {
                parentFile?.mkdirs()
                writeText("old")
                setLastModified(expiredTime)
            }
            val ocrOld = File(cacheDir, "ocr_tmp/old.pdf").apply {
                parentFile?.mkdirs()
                writeText("old")
                setLastModified(expiredTime)
            }
            val pdfBoxOld = File(cacheDir, "pdfbox_tmp/old.tmp").apply {
                parentFile?.mkdirs()
                writeText("old")
                setLastModified(expiredTime)
            }

            // When PdfBox has an active session, pdfbox_tmp must NOT be touched.
            TempCacheCleanup.cleanExpiredTempFiles(
                cacheDir = cacheDir,
                includePdfBoxTmp = false,
                nowMs = nowMs
            )
            assertFalse(sharedOld.exists())
            assertTrue(sharedFresh.exists())
            assertFalse(quotesOld.exists())
            assertFalse(ocrOld.exists())
            assertTrue(pdfBoxOld.exists())

            // When PdfBox has no active session, expired pdfbox_tmp files are pruned too.
            TempCacheCleanup.cleanExpiredTempFiles(
                cacheDir = cacheDir,
                includePdfBoxTmp = true,
                nowMs = nowMs
            )
            assertFalse(pdfBoxOld.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
