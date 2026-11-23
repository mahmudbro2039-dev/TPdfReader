package com.example.data.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class DeviceAccessStatus(
    val hasAllFilesAccess: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val requiresManageAllFilesApi: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
) {
    val isFullyConfigured: Boolean
        get() = hasAllFilesAccess && isBatteryOptimizationIgnored
}

object DevicePermissionsHelper {

    fun getStatus(context: Context): DeviceAccessStatus {
        return DeviceAccessStatus(
            hasAllFilesAccess = hasFullFileAccess(context),
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored(context),
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

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return runCatching {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
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

    @SuppressLint("BatteryLife")
    fun buildBatteryOptimizationIntent(context: Context): Intent {
        val pm = context.packageManager
        val pkgUri = Uri.parse("package:${context.packageName}")
        val directRequest = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
        if (directRequest.resolveActivity(pm) != null) return directRequest

        val settingsFallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (settingsFallback.resolveActivity(pm) != null) return settingsFallback

        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
    }
}
