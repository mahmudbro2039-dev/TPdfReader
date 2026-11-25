package com.example.data.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

data class DeviceAccessStatus(
    val hasAllFilesAccess: Boolean = false,
    val requiresManageAllFilesApi: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
)

object DevicePermissionsHelper {

    fun getStatus(context: Context): DeviceAccessStatus {
        return DeviceAccessStatus(
            hasAllFilesAccess = hasFullFileAccess(context),
            requiresManageAllFilesApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        )
    }

    fun hasFullFileAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasLegacyReadPermission(context: Context): Boolean {
        if (hasFullFileAccess(context)) return true
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    fun buildAllFilesAccessIntent(context: Context): Intent {
        val pm = context.packageManager
        val pkgUri = Uri.parse("package:${context.packageName}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appSpecific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)
            if (appSpecific.resolveActivity(pm) != null) return appSpecific

            val generalAllFiles = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            if (generalAllFiles.resolveActivity(pm) != null) return generalAllFiles
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
    }
}
