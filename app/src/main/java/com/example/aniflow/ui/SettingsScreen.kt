package com.example.aniflow.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.DeviceType
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.model.AppUpdateInfo
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    watchlistStore: WatchlistStore,
    watchHistoryStore: WatchHistoryStore,
    settingsStore: SettingsStore,
    deviceType: DeviceType,
    repository: AnimeRepository? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var watchlistCleared by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }

    var updateCheckState by remember { mutableStateOf<String?>(null) }
    var foundUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    val qualityPref by settingsStore.qualityPreference.collectAsState(initial = "auto")
    val languagePref by settingsStore.languagePreference.collectAsState(initial = "sub")
    val autoSkipIntroPref by settingsStore.autoSkipIntro.collectAsState(initial = false)
    val checkUpdatesPref by settingsStore.checkUpdatesStartup.collectAsState(initial = true)
    val themePref by settingsStore.themeMode.collectAsState(initial = "system")

    val outerPadding = if (deviceType == DeviceType.TV) 24.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
            .padding(horizontal = outerPadding, vertical = outerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", color = TextPrimary, fontSize = if (deviceType == DeviceType.TV) 24.sp else 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Text("Preferences", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        AdaptiveSettingsRow(
            title = "Quality Preference",
            subtitle = "Default quality when loading video sources: ${qualityPref.uppercase()}",
            deviceType = deviceType,
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

        AdaptiveSettingsRow(
            title = "Preferred Language",
            subtitle = "Default audio and text preference: ${languagePref.uppercase()}",
            deviceType = deviceType,
            onClick = {
                coroutineScope.launch {
                    val next = if (languagePref == "sub") "dub" else "sub"
                    settingsStore.setLanguage(next)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        AdaptiveSettingsRow(
            title = "Auto-Skip Intro",
            subtitle = "Automatically skip openings: ${if (autoSkipIntroPref) "ENABLED" else "DISABLED"}",
            deviceType = deviceType,
            onClick = {
                coroutineScope.launch {
                    settingsStore.setAutoSkipIntro(!autoSkipIntroPref)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        AdaptiveSettingsRow(
            title = "Theme Mode",
            subtitle = "App visual style: ${themePref.uppercase()}",
            deviceType = deviceType,
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

        Spacer(Modifier.height(12.dp))

        AdaptiveSettingsRow(
            title = "Check Updates on Startup",
            subtitle = "Auto-check for updates when app launches: ${if (checkUpdatesPref) "ENABLED" else "DISABLED"}",
            deviceType = deviceType,
            onClick = {
                coroutineScope.launch {
                    settingsStore.setCheckUpdatesStartup(!checkUpdatesPref)
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Text("Data Management", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        AdaptiveSettingsRow(
            title = "Clear Watch History",
            subtitle = if (historyCleared) "History cleared successfully!" else "Delete all recently watched progress",
            deviceType = deviceType,
            onClick = {
                watchHistoryStore.clearHistory()
                historyCleared = true
            }
        )

        Spacer(Modifier.height(12.dp))

        AdaptiveSettingsRow(
            title = "Clear Watchlist",
            subtitle = if (watchlistCleared) "Watchlist cleared successfully!" else "Delete all bookmarked anime",
            deviceType = deviceType,
            onClick = {
                coroutineScope.launch {
                    watchlistStore.clearWatchlist()
                    watchlistCleared = true
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Text("Updates", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        AdaptiveSettingsRow(
            title = "Check for Updates",
            subtitle = when (updateCheckState) {
                "checking" -> "Checking for updates..."
                "up_to_date" -> "You're on the latest version!"
                "update_available" -> "Version ${foundUpdate?.versionName} available! Click to download."
                else -> "Click to check if a new version is available"
            },
            deviceType = deviceType,
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
fun AdaptiveSettingsRow(
    title: String,
    subtitle: String,
    deviceType: DeviceType,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        label = "settingsScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) PrimaryAccent else SurfaceCard)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) SecondaryAccent else SurfaceBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .run {
                if (deviceType == DeviceType.TV) {
                    focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onClick() }
                } else {
                    clickable { onClick() }
                }
            }
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
