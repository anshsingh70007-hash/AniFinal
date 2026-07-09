package com.example.aniflow.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.model.AppUpdateInfo
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow
import kotlinx.coroutines.launch

@Composable
fun TvSettingsScreen(
    watchlistStore: WatchlistStore,
    watchHistoryStore: WatchHistoryStore,
    settingsStore: SettingsStore,
    repository: AnimeRepository? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }

    var watchlistCleared by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }

    // Update check state
    var updateCheckState by remember { mutableStateOf<String?>(null) }
    var foundUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    val languagePref by settingsStore.languagePreference.collectAsState(initial = "sub")
    val defaultSpeedPref by settingsStore.defaultPlaybackSpeed.collectAsState(initial = 1.0f)
    val autoPlayNextPref by settingsStore.autoPlayNextEpisode.collectAsState(initial = true)
    val checkUpdatesPref by settingsStore.checkUpdatesStartup.collectAsState(initial = true)

    val appVersionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "1.7.15"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // Preferred Language
        TvSettingsRow(
            title = "Preferred Language",
            subtitle = "Default audio preference for episodes: ${languagePref.uppercase()}",
            isRedesign = isRedesign,
            onClick = {
                coroutineScope.launch {
                    val next = if (languagePref == "sub") "dub" else "sub"
                    settingsStore.setLanguage(next)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // Default Playback Speed
        TvSettingsRow(
            title = "Default Playback Speed",
            subtitle = "Initial speed for newly loaded episodes: ${defaultSpeedPref}x",
            isRedesign = isRedesign,
            onClick = {
                coroutineScope.launch {
                    val next = when (defaultSpeedPref) {
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2.0f
                        2.0f -> 0.5f
                        0.5f -> 0.75f
                        else -> 1.0f
                    }
                    settingsStore.setDefaultPlaybackSpeed(next)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // Auto-Play Next Episode
        TvSettingsRow(
            title = "Auto-Play Next Episode",
            subtitle = "Automatically load the next episode: ${if (autoPlayNextPref) "ENABLED" else "DISABLED"}",
            isRedesign = isRedesign,
            onClick = {
                coroutineScope.launch {
                    settingsStore.setAutoPlayNextEpisode(!autoPlayNextPref)
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Text("Data Management", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // Clear History row
        TvSettingsRow(
            title = "Clear Watch History",
            subtitle = if (historyCleared) "History cleared successfully!" else "Delete all recently watched progress",
            isRedesign = isRedesign,
            onClick = {
                watchHistoryStore.clearHistory()
                historyCleared = true
            }
        )

        Spacer(Modifier.height(12.dp))

        // Clear Watchlist row
        TvSettingsRow(
            title = "Clear Watchlist",
            subtitle = if (watchlistCleared) "Watchlist cleared successfully!" else "Delete all bookmarked anime",
            isRedesign = isRedesign,
            onClick = {
                coroutineScope.launch {
                    watchlistStore.clearWatchlist()
                    watchlistCleared = true
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Text("Updates & Version", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // Check updates on startup
        TvSettingsRow(
            title = "Check Updates on Startup",
            subtitle = "Automatically check for updates on startup: ${if (checkUpdatesPref) "ENABLED" else "DISABLED"}",
            isRedesign = isRedesign,
            onClick = {
                coroutineScope.launch {
                    settingsStore.setCheckUpdatesStartup(!checkUpdatesPref)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // Check for Updates row
        TvSettingsRow(
            title = "Check for Updates",
            subtitle = when (updateCheckState) {
                "checking" -> "Checking for updates..."
                "up_to_date" -> "You're on the latest version!"
                "update_available" -> "Version ${foundUpdate?.versionName} available! Click to download."
                else -> "Click to check if a new version is available"
            },
            isRedesign = isRedesign,
            onClick = {
                if (updateCheckState == "update_available" && foundUpdate != null) {
                    com.example.aniflow.utils.AppUpdater.downloadAndInstall(context, foundUpdate!!.updateUrl, foundUpdate!!.versionName)
                } else if (repository != null && updateCheckState != "checking") {
                    updateCheckState = "checking"
                    coroutineScope.launch {
                        try {
                            val info = repository.checkUpdates()
                            val currentVersionCode = try {
                                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    packageInfo.longVersionCode.toInt()
                                } else {
                                    @Suppress("DEPRECATION")
                                    packageInfo.versionCode
                                }
                            } catch (e: Exception) {
                                1
                            }
                            if (info != null && info.versionCode > currentVersionCode && !info.silentUpdate) {
                                foundUpdate = info
                                updateCheckState = "update_available"
                            } else {
                                updateCheckState = "up_to_date"
                            }
                        } catch (e: Exception) {
                            updateCheckState = "up_to_date"
                        }
                    }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // App Version
        TvSettingsRow(
            title = "App Version",
            subtitle = "v$appVersionName (Official Build)",
            isRedesign = isRedesign,
            onClick = {}
        )
    }
}

@Composable
fun TvSettingsRow(
    title: String,
    subtitle: String,
    isRedesign: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val baseModifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { isFocused = it.isFocused }
        .clickable { onClick() }

    val containerModifier = if (isRedesign) {
        baseModifier
            .focusGlow(isFocused, shape = RoundedCornerShape(12.dp))
            .glassSurface(shape = RoundedCornerShape(12.dp), borderWidth = 1.dp, isFocused = isFocused)
            .padding(16.dp)
    } else {
        baseModifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) PrimaryAccent else SurfaceCard)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SecondaryAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(16.dp)
    }

    Row(
        modifier = containerModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = if (isFocused && !isRedesign) TextPrimary.copy(alpha = 0.8f) else TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
