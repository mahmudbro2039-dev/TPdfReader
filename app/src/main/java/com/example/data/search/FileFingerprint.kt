package com.example.data.search

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

object FileFingerprint {
    /** "<size>:<lastModified>" or null if the file cannot be read. */
    fun of(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE, null -> {
                    val path = uri.path ?: return null
                    val file = File(path)
                    if (!file.exists() || !file.isFile || !file.canRead()) return null
                    "${file.length()}:${file.lastModified()}"
                }
                else -> {
                    var size = -1L
                    var lastModified = 0L
                    var querySucceeded = false

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        querySucceeded = true
                        if (cursor.moveToFirst()) {
                            val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeCol >= 0 && !cursor.isNull(sizeCol)) {
                                size = cursor.getLong(sizeCol)
                            }
                            val docModCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            if (docModCol >= 0 && !cursor.isNull(docModCol)) {
                                lastModified = cursor.getLong(docModCol)
                            } else {
                                val mediaModCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                                if (mediaModCol >= 0 && !cursor.isNull(mediaModCol)) {
                                    lastModified = cursor.getLong(mediaModCol)
                                }
                            }
                        }
                    }

                    if (size < 0L) {
                        val fdSize = runCatching {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                        }.getOrNull()
                        if (fdSize != null && fdSize >= 0L) {
                            size = fdSize
                        }
                    }

                    if (!querySucceeded && size < 0L) {
                        return null
                    }
                    if (size < 0L) {
                        size = 0L
                    }
                    "$size:$lastModified"
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
