package com.example.data.search

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

enum class PauseReason {
    LOW_BATTERY,
    POWER_SAVER
}

object IndexConfig {
    const val MAX_PAGES_PER_DOC = 5000
    const val BATCH_PAGES = 10
    const val START_DELAY_MS = 3000L
    const val PAUSE_RECHECK_MS = 60_000L
}

object IndexPolicy {
    fun canRunNow(context: Context): PauseReason? {
        val batteryStatus = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        if (batteryStatus != null) {
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                plugged != 0

            if (!isCharging) {
                val batteryLowFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    batteryStatus.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)
                } else {
                    false
                }
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100f) / scale else 100f
                if (batteryLowFlag || pct <= 15f) {
                    return PauseReason.LOW_BATTERY
                }
            }
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isPowerSaveMode == true) {
            return PauseReason.POWER_SAVER
        }

        return null
    }
}
