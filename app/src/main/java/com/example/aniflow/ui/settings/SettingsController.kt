package com.example.aniflow.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.model.AppUpdateInfo
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.ui.sound.Sfx
import com.example.aniflow.utils.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Speeds offered as the default. The player itself offers a wider range per session. */
internal val DefaultSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun speedText(s: Float): String =
    (if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0')) + "×"

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: AppUpdateInfo) : UpdateState
    data class Downloading(val info: AppUpdateInfo, val progress: Float) : UpdateState
    data object Failed : UpdateState
}

/**
 * Everything Settings shows and does, shared by the TV and phone screens so the two can never
 * disagree about what a setting means. Values are Compose state, fed by the stores' flows.
 */
@Stable
class SettingsController internal constructor(
    private val context: Context,
    private val settings: SettingsStore,
    private val watchlist: WatchlistStore,
    private val history: WatchHistoryStore,
    private val repository: AnimeRepository?,
    private val scope: CoroutineScope,
) {
    var audio by mutableStateOf("sub"); internal set
    var speed by mutableFloatStateOf(1f); internal set
    var autoplay by mutableStateOf(true); internal set
    var autoSkip by mutableStateOf(false); internal set
    var animeColor by mutableStateOf(false); internal set
    var navSounds by mutableStateOf(true); internal set
    var checkOnStart by mutableStateOf(true); internal set
    var heroTrailers by mutableStateOf(true); internal set

    var watchlistCount by mutableIntStateOf(0); internal set
    var titlesStarted by mutableIntStateOf(0); internal set
    var episodesWatched by mutableIntStateOf(0); internal set
    var minutesWatched by mutableLongStateOf(0L); internal set
    var cacheBytes by mutableLongStateOf(-1L); private set
    var update by mutableStateOf<UpdateState>(UpdateState.Idle); private set

    val versionName: String
    val versionCode: Long

    init {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        versionName = info?.versionName ?: "?"
        versionCode = when {
            info == null -> 0L
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode
            else -> @Suppress("DEPRECATION") info.versionCode.toLong()
        }
    }

    // ---- playback ----------------------------------------------------------------------------
    fun selectAudio(value: String) { audio = value; scope.launch { settings.setLanguage(value) } }
    fun selectSpeed(value: Float) { speed = value; scope.launch { settings.setDefaultPlaybackSpeed(value) } }
    fun toggleAutoplay() { val v = !autoplay; autoplay = v; scope.launch { settings.setAutoPlayNextEpisode(v) } }
    fun toggleAnimeColor() { val v = !animeColor; animeColor = v; scope.launch { settings.setAnimeColor(v) } }
    fun toggleAutoSkip() { val v = !autoSkip; autoSkip = v; scope.launch { settings.setAutoSkipIntro(v) } }

    // ---- interface ---------------------------------------------------------------------------
    fun toggleNavSounds() {
        val on = !navSounds
        navSounds = on
        // Play the toggle cue even when switching sounds off, so the change is audible.
        Sfx.enabled = true
        Sfx.toggle(on)
        Sfx.enabled = on
        scope.launch { settings.setNavSounds(on) }
    }

    fun toggleHeroTrailers() { val v = !heroTrailers; heroTrailers = v; scope.launch { settings.setHeroTrailers(v) } }

    // ---- library & storage -------------------------------------------------------------------
    fun clearHistory() = history.clearHistory()
    fun clearWatchlist() { scope.launch { watchlist.clearWatchlist() } }

    /** Posters, page caches and the metadata disk caches: all rebuilt on demand. Never user data. */
    fun clearCache() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val loader = coil3.SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
                cacheFiles().forEach { runCatching { it.deleteRecursively() } }
            }
            measureCache()
        }
    }

    fun measureCache() {
        scope.launch { cacheBytes = withContext(Dispatchers.IO) { cacheFiles().sumOf { sizeOf(it) } } }
    }

    private fun cacheFiles(): List<File> {
        val files = context.filesDir
        return (context.cacheDir.listFiles()?.toList().orEmpty()) +
            listOfNotNull(File(files, "detail_cache").takeIf { it.exists() }) +
            files.listFiles { f -> f.name.startsWith("feed_cache") }?.toList().orEmpty()
    }

    private fun sizeOf(f: File): Long = runCatching {
        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
    }.getOrDefault(0L)

    // ---- updates -----------------------------------------------------------------------------
    fun toggleCheckOnStart() { val v = !checkOnStart; checkOnStart = v; scope.launch { settings.setCheckUpdatesStartup(v) } }

    /** One button: check, then (if there is one) download, then nothing while downloading. */
    fun updateAction() {
        when (val u = update) {
            is UpdateState.Checking, is UpdateState.Downloading -> Unit
            is UpdateState.Available -> {
                update = UpdateState.Downloading(u.info, 0f)
                AppUpdater.downloadAndInstall(context, u.info) { p ->
                    update = if (p >= 1f) UpdateState.Available(u.info) else UpdateState.Downloading(u.info, p)
                }
            }
            else -> checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        val repo = repository ?: run { update = UpdateState.Failed; return }
        update = UpdateState.Checking
        scope.launch {
            val info = runCatching { repo.checkUpdates() }.getOrNull()
            // checkUpdates() returns null only when the manifest couldn't be fetched or parsed:
            // that is a failure to check, not "you're up to date".
            update = when {
                info == null -> UpdateState.Failed
                // A manual check offers the update even outside this install's rollout bucket.
                info.versionCode > versionCode && info.apkUrls.isNotEmpty() -> UpdateState.Available(info)
                else -> UpdateState.UpToDate
            }
        }
    }

    internal fun absorbHistory(list: List<WatchHistoryEntry>) {
        titlesStarted = list.size
        // History keeps the furthest episode per show, so earlier episodes count as watched.
        episodesWatched = list.sumOf { (it.episodeNumber - 1).coerceAtLeast(0) + if (it.durationMs > 0 && it.progressMs > it.durationMs * 0.85) 1 else 0 }
        minutesWatched = list.sumOf { h ->
            val ep = if (h.durationMs > 60_000) h.durationMs else 24 * 60_000L
            (h.episodeNumber - 1).coerceAtLeast(0) * ep + h.progressMs.coerceAtLeast(0)
        } / 60_000L
    }
}

internal fun watchTimeText(minutes: Long): String = when {
    minutes < 60 -> "${minutes}m"
    minutes < 60 * 48 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes / 60}h"
}

internal fun bytesText(bytes: Long): String = when {
    bytes < 0 -> "Measuring…"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

@Composable
fun rememberSettingsController(
    settingsStore: SettingsStore,
    watchlistStore: WatchlistStore,
    watchHistoryStore: WatchHistoryStore,
    repository: AnimeRepository?,
): SettingsController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val c = remember(settingsStore, watchlistStore, watchHistoryStore, repository) {
        SettingsController(context, settingsStore, watchlistStore, watchHistoryStore, repository, scope)
    }
    LaunchedEffect(c) {
        launch { settingsStore.languagePreference.collect { c.audio = it } }
        launch { settingsStore.defaultPlaybackSpeed.collect { c.speed = it } }
        launch { settingsStore.autoPlayNextEpisode.collect { c.autoplay = it } }
        launch { settingsStore.autoSkipIntro.collect { c.autoSkip = it } }
        launch { settingsStore.animeColor(animeColorDefault(context)).collect { c.animeColor = it } }
        launch { settingsStore.navSounds.collect { c.navSounds = it } }
        launch { settingsStore.checkUpdatesStartup.collect { c.checkOnStart = it } }
        launch { settingsStore.heroTrailers.collect { c.heroTrailers = it } }
        launch { watchlistStore.watchlistFlow.collect { c.watchlistCount = it.size } }
        launch { watchHistoryStore.historyFlow.collect { c.absorbHistory(it) } }
        c.measureCache()
    }
    return c
}

/** Anime colour is on by default on phones; on TVs it would bypass the TV's own picture engine. */
fun animeColorDefault(context: android.content.Context): Boolean =
    com.example.aniflow.DeviceDetector.detect(context) != com.example.aniflow.DeviceType.TV
