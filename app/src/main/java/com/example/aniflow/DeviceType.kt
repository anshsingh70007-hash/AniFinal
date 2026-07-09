package com.example.aniflow

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

enum class DeviceType {
    PHONE,
    TV
}

object DeviceDetector {
    @Volatile
    private var cachedResult: DeviceType? = null

    fun detect(context: Context): DeviceType {
        cachedResult?.let { return it }
        val result = detectInternal(context.applicationContext)
        cachedResult = result
        return result
    }

    private fun detectInternal(context: Context): DeviceType {
        // Signal 1: UiModeManager (primary, most reliable on real TVs)
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return DeviceType.TV
        }

        // Signal 2: Leanback feature (declared by Android TV devices)
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return DeviceType.TV
        }

        // Signal 3: No touchscreen hardware (TVs don't have touchscreens)
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            return DeviceType.TV
        }

        // Signal 4: Leanback-only check (some budget TV boxes)
        if (pm.hasSystemFeature("android.software.leanback_only")) {
            return DeviceType.TV
        }

        return DeviceType.PHONE
    }
}
