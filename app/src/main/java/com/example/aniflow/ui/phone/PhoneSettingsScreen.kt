package com.example.aniflow.ui.phone

import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch

@Composable
fun PhoneSettingsScreen(
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

    var languageExpanded by remember { mutableStateOf(false) }
    var speedExpanded by remember { mutableStateOf(false) }

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
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
        )

        // --- PLAYBACK SETTINGS SECTION ---
        Text(
            text = "Playback",
            color = if (isRedesign) PrimaryAccentLight else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Preferred Language
        SettingsCard(isRedesign = isRedesign) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preferred Language", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Default audio preference for episodes", color = TextSecondary, fontSize = 12.sp)
                }
                Box {
                    TextButton(onClick = { languageExpanded = true }) {
                        Text(languagePref.uppercase(), color = SecondaryAccent, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false },
                        modifier = Modifier.background(SurfaceCard)
                    ) {
                        listOf("sub", "dub").forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.uppercase(), color = TextPrimary) },
                                onClick = {
                                    coroutineScope.launch {
                                        settingsStore.setLanguage(lang)
                                        languageExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Default Playback Speed
        SettingsCard(isRedesign = isRedesign) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Default Playback Speed", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Initial speed for newly loaded episodes", color = TextSecondary, fontSize = 12.sp)
                }
                Box {
                    TextButton(onClick = { speedExpanded = true }) {
                        Text("${defaultSpeedPref}x", color = SecondaryAccent, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(
                        expanded = speedExpanded,
                        onDismissRequest = { speedExpanded = false },
                        modifier = Modifier.background(SurfaceCard)
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x", color = TextPrimary) },
                                onClick = {
                                    coroutineScope.launch {
                                        settingsStore.setDefaultPlaybackSpeed(speed)
                                        speedExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Auto-Play Next Episode
        SettingsCard(isRedesign = isRedesign) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Play Next Episode", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Load the next episode automatically when finished", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = autoPlayNextPref,
                    onCheckedChange = { value ->
                        coroutineScope.launch {
                            settingsStore.setAutoPlayNextEpisode(value)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryAccent,
                        checkedTrackColor = PrimaryAccentLight.copy(alpha = 0.5f)
                    )
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // --- DATA MANAGEMENT SECTION ---
        Text(
            text = "Data Management",
            color = if (isRedesign) PrimaryAccentLight else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Clear History
        SettingsCard(isRedesign = isRedesign) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clear Watch History", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (historyCleared) "History cleared successfully!" else "Delete all recently watched progress",
                        color = if (historyCleared) SuccessGreen else TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = {
                        watchHistoryStore.clearHistory()
                        historyCleared = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TertiaryAccent)
                ) {
                    Text("Clear", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Clear Watchlist
        SettingsCard(isRedesign = isRedesign) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clear Watchlist", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (watchlistCleared) "Watchlist cleared successfully!" else "Delete all bookmarked anime",
                        color = if (watchlistCleared) SuccessGreen else TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            watchlistStore.clearWatchlist()
                            watchlistCleared = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TertiaryAccent)
                ) {
                    Text("Clear", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // --- UPDATES SECTION ---
        Text(
            text = "Updates & Version",
            color = if (isRedesign) PrimaryAccentLight else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Check updates on startup
        SettingsCard(isRedesign = isRedesign) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check Updates on Startup", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Check for new versions automatically when opening", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = checkUpdatesPref,
                    onCheckedChange = { value ->
                        coroutineScope.launch {
                            settingsStore.setCheckUpdatesStartup(value)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryAccent,
                        checkedTrackColor = PrimaryAccentLight.copy(alpha = 0.5f)
                    )
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Check for updates button
        SettingsCard(isRedesign = isRedesign) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check for Updates", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when (updateCheckState) {
                            "checking" -> "Checking server for new builds..."
                            "up_to_date" -> "You're running the latest build!"
                            "update_available" -> "Update v${foundUpdate?.versionName} is available!"
                            else -> "Check if a new version is available"
                        },
                        color = when (updateCheckState) {
                            "checking" -> PrimaryAccentLight
                            "up_to_date" -> SuccessGreen
                            "update_available" -> SecondaryAccent
                            else -> TextSecondary
                        },
                        fontSize = 12.sp
                    )
                }
                if (updateCheckState == "update_available" && foundUpdate != null) {
                    Button(
                        onClick = {
                            com.example.aniflow.utils.AppUpdater.downloadAndInstall(
                                context,
                                foundUpdate!!.updateUrl,
                                foundUpdate!!.versionName
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                    ) {
                        Text("Download", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                } else if (updateCheckState == "checking") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = PrimaryAccent,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Button(
                        onClick = {
                            if (repository != null) {
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
                                        android.util.Log.e("PhoneSettingsScreen", "Update check failed", e)
                                        updateCheckState = "up_to_date"
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                    ) {
                        Text("Check", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // App Version
        SettingsCard(isRedesign = isRedesign) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("App Version", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("v$appVersionName", color = TextSecondary, fontSize = 12.sp)
                }
                Text(
                    text = "Official Build",
                    color = SuccessGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsCard(
    isRedesign: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (isRedesign) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .glassSurface(shape = RoundedCornerShape(16.dp), borderWidth = 1.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(12.dp),
            content = content
        )
    }
}
