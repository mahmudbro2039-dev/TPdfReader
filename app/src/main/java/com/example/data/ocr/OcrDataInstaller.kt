package com.example.data.ocr

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrData(
    val dataPath: String,
    val languages: String
)

object OcrDataInstaller {
    private const val TESSDATA_DIR = "tessdata"
    private const val BEN_FILE = "ben.traineddata"
    private const val ENG_FILE = "eng.traineddata"

    suspend fun ensureInstalled(context: Context): OcrData? = withContext(Dispatchers.IO) {
        val dataPath = File(context.filesDir, OcrConfig.DATA_DIR_NAME)
        val tessDir = File(dataPath, TESSDATA_DIR)
        if (!tessDir.exists() && !tessDir.mkdirs()) {
            return@withContext null
        }

        val assetNames = runCatching {
            context.assets.list(TESSDATA_DIR)?.toSet().orEmpty()
        }.getOrDefault(emptySet())

        copyIfMissing(context, assetNames, tessDir, BEN_FILE)
        copyIfMissing(context, assetNames, tessDir, ENG_FILE)

        val hasBen = File(tessDir, BEN_FILE).let { it.isFile && it.length() > 0L }
        val hasEng = File(tessDir, ENG_FILE).let { it.isFile && it.length() > 0L }

        val languages = when {
            hasBen && hasEng -> OcrConfig.LANGUAGES
            hasEng -> OcrConfig.FALLBACK_LANGUAGES
            hasBen -> "ben"
            else -> return@withContext null
        }

        OcrData(
            dataPath = dataPath.absolutePath,
            languages = languages
        )
    }

    private fun copyIfMissing(
        context: Context,
        assetNames: Set<String>,
        tessDir: File,
        fileName: String
    ) {
        val target = File(tessDir, fileName)
        if (target.isFile && target.length() > 0L) return
        if (fileName !in assetNames) return

        val temp = File(tessDir, "$fileName.tmp")
        runCatching {
            context.assets.open("$TESSDATA_DIR/$fileName").use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }.onFailure {
            runCatching { temp.delete() }
        }
    }
}
