package com.example.aniflow.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import kotlinx.coroutines.launch

@Composable
fun TvSettingsScreen(
    watchlistStore: WatchlistStore,
    watchHistoryStore: WatchHistoryStore,
    settingsStore: SettingsStore,
    repository: AnimeRepository? = null
) {
    val coroutineScope = rememberCoroutineScope()

    var watchlistCleared by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }

    // Update check state
    var updateCheckState by remember { mutableStateOf<String?>(null) }
    var foundUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    val qualityPref by settingsStore.qualityPreference.collectAsState(initial = "auto")
    val languagePref by settingsStore.languagePreference.collectAsState(initial = "sub")
    val autoSkipIntroPref by settingsStore.autoSkipIntro.collectAsState(initial = false)
    val themePref by settingsStore.themeMode.collectAsState(initial = "system")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        
        // Quality preference row
        TvSettingsRow(
            title = "Quality Preference",
            subtitle = "Default quality when loading video sources: ${qualityPref.uppercase()}",
            onClick = {
                coroutineScope.launch {
                    val next = when (qualityPref) {
                        "auto" -> "1080p"
                        "1080p" -> "720p"
                        "720p" -> "480p"
                        else -> "auto"
                    }
                    settingsStore.setQuality(next)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // Language preference row
        TvSettingsRow(
            title = "Preferred Language",
            subtitle = "Default audio and text preference: ${languagePref.uppercase()}",
            onClick = {
                coroutineScope.launch {
                    val next = if (languagePref == "sub") "dub" else "sub"
                    settingsStore.setLanguage(next)
                }
            }
        )

        Spacer(Modifier.height(12.dp))



        // Auto-skip intro toggle row
        TvSettingsRow(
            title = "Auto-Skip Intro",
            subtitle = "Automatically skip anime openings when available: ${if (autoSkipIntroPref) "ENABLED" else "DISABLED"}",
            onClick = {
                coroutineScope.launch {
                    settingsStore.setAutoSkipIntro(!autoSkipIntroPref)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        // Theme mode preference row
        TvSettingsRow(
            title = "Theme Mode",
            subtitle = "App visual style: ${themePref.uppercase()}",
            onClick = {
                coroutineScope.launch {
                    val next = when (themePref) {
                        "system" -> "dark"
                        "dark" -> "amoled"
                        else -> "system"
                    }
                    settingsStore.setThemeMode(next)
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
            onClick = {
                coroutineScope.launch {
                    watchlistStore.clearWatchlist()
                    watchlistCleared = true
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Text("Updates", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // Check for Updates row
        val context = androidx.compose.ui.platform.LocalContext.current
        TvSettingsRow(
            title = "Check for Updates",
            subtitle = when (updateCheckState) {
                "checking" -> "Checking for updates..."
                "up_to_date" -> "You're on the latest version!"
                "update_available" -> "Version ${foundUpdate?.versionName} available! Press to download."
                else -> "Press to check if a new version is available"
            },
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
    }
}

@Composable
fun TvSettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) PrimaryAccent else SurfaceCard)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SecondaryAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = if (isFocused) TextPrimary.copy(alpha = 0.8f) else TextSecondary, fontSize = 12.sp)
        }
    }
}
