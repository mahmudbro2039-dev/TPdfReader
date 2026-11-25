package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

object UriAccess {
    /** true if we can read this uri after a restart */
    fun persistRead(context: Context, uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return true
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (e: SecurityException) {
            Log.w("UriAccess", "Persist failed for $uri", e)
            context.contentResolver.persistedUriPermissions
                .any { it.uri == uri && it.isReadPermission }
        }
    }

    /** true if the file can really be opened right now */
    fun isReadable(context: Context, uri: Uri): Boolean = try {
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> {
                val path = uri.path ?: return false
                File(path).canRead()
            }
            else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }
    } catch (_: Exception) {
        false
    }

    /** free old permissions that are no longer in our library (limit protection) */
    fun trimPersisted(context: Context, knownUris: Set<String>, keepBelow: Int = 100) {
        val perms = context.contentResolver.persistedUriPermissions
        if (perms.size < keepBelow) return
        perms.filter { it.uri.toString() !in knownUris }.forEach {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    it.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
        }
    }
}
