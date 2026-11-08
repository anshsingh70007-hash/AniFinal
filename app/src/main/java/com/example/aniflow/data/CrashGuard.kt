package com.example.aniflow.data

import android.content.Context
import java.io.File

/**
 * Crash-loop breaker. Each crash is stamped synchronously (the process is dying), and if the app
 * has crashed 3 times within 10 minutes, the next start wipes everything that can be rebuilt —
 * HTTP/image/WebView caches, cached feeds and details, provider title mappings — so a poisoned
 * cache can't crash the app on every launch. The watchlist, history and settings are never touched.
 */
object CrashGuard {
    private const val PREFS = "crash_guard"
    private const val KEY = "crash_times"
    private const val WINDOW_MS = 10 * 60_000L
    private const val LOOP_THRESHOLD = 3

    @Volatile private var installed = false

    /** Call first thing in onCreate. Returns true if a crash loop was detected and caches were reset. */
    fun install(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        fun recent(): List<Long> = prefs.getString(KEY, "").orEmpty().split(',')
            .mapNotNull { it.toLongOrNull() }.filter { now - it in 0..WINDOW_MS }

        if (!installed) {
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching {
                    val t = System.currentTimeMillis()
                    val times = prefs.getString(KEY, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
                        .filter { t - it in 0..WINDOW_MS } + t
                    prefs.edit().putString(KEY, times.takeLast(LOOP_THRESHOLD + 2).joinToString(",")).commit()
                }
                previous?.uncaughtException(thread, error)
            }
        }

        if (recent().size < LOOP_THRESHOLD) return false
        android.util.Log.w("CrashGuard", "Crash loop detected: resetting rebuildable caches")
        runCatching { app.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching { File(app.filesDir, "detail_cache").deleteRecursively() }
        runCatching { app.filesDir.listFiles { f -> f.name.startsWith("feed_cache") }?.forEach { it.delete() } }
        // DataStore isn't open yet at this point in onCreate, so its file can go.
        runCatching { File(app.filesDir, "datastore/provider_mapping_preferences.preferences_pb").delete() }
        prefs.edit().remove(KEY).commit()
        return true
    }
}
