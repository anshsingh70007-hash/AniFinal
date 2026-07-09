package com.example.aniflow.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.model.AppUpdateInfo
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.theme.*
import kotlinx.coroutines.flow.flowOf
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

    var watchlistCleared by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }

    // Update check state
    var updateCheckState by remember { mutableStateOf<String?>(null) }
    var foundUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    val qualityPref by settingsStore.qualityPreference.collectAsState(initial = "auto")
    val languagePref by settingsStore.languagePreference.collectAsState(initial = "sub")
    val autoSkipIntroPref by settingsStore.autoSkipIntro.collectAsState(initial = false)
    val checkUpdatesPref by settingsStore.checkUpdatesStartup.collectAsState(initial = true)
    val themePref by settingsStore.themeMode.collectAsState(initial = "system")

    var qualityExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        
        // Preferred Quality
        ListItem(
            headlineContent = { Text("Quality Preference", color = TextPrimary) },
            supportingContent = { Text("Default quality when loading video sources", color = TextSecondary) },
            trailingContent = {
                Box {
                    TextButton(onClick = { qualityExpanded = true }) {
                        Text(qualityPref.uppercase(), color = SecondaryAccent)
                    }
                    DropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
                        listOf("auto", "1080p", "720p", "480p").forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(quality.uppercase()) },
                                onClick = {
                                    coroutineScope.launch {
                                        settingsStore.setQuality(quality)
                                        qualityExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
        Spacer(Modifier.height(8.dp))

        // Preferred Language
        ListItem(
            headlineContent = { Text("Preferred Language", color = TextPrimary) },
            supportingContent = { Text("Default audio and text preference", color = TextSecondary) },
            trailingContent = {
                Box {
                    TextButton(onClick = { languageExpanded = true }) {
                        Text(languagePref.uppercase(), color = SecondaryAccent)
                    }
                    DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                        listOf("sub", "dub").forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.uppercase()) },
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
            },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
        Spacer(Modifier.height(8.dp))



        // Theme Mode
        ListItem(
            headlineContent = { Text("Theme Mode", color = TextPrimary) },
            supportingContent = { Text("Change the app's visual style", color = TextSecondary) },
            trailingContent = {
                Box {
                    TextButton(onClick = { themeExpanded = true }) {
                        Text(themePref.uppercase(), color = SecondaryAccent)
                    }
                    DropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                        listOf("system", "dark", "amoled").forEach { theme ->
                            DropdownMenuItem(
                                text = { Text(theme.uppercase()) },
                                onClick = {
                                    coroutineScope.launch {
                                        settingsStore.setThemeMode(theme)
                                        themeExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
        Spacer(Modifier.height(8.dp))

        // Auto-Skip Intro
        ListItem(
            headlineContent = { Text("Auto-Skip Intro", color = TextPrimary) },
            supportingContent = { Text("Automatically skip anime openings when timeline data is available", color = TextSecondary) },
            trailingContent = {
                Switch(
                    checked = autoSkipIntroPref,
                    onCheckedChange = { value ->
                        coroutineScope.launch {
                            settingsStore.setAutoSkipIntro(value)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryAccent, checkedTrackColor = PrimaryAccentLight)
                )
            },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
        Spacer(Modifier.height(8.dp))

        // Check for updates automatically on startup
        ListItem(
            headlineContent = { Text("Check Updates on Startup", color = TextPrimary) },
            supportingContent = { Text("Automatically check for new versions when the app starts", color = TextSecondary) },
            trailingContent = {
                Switch(
                    checked = checkUpdatesPref,
                    onCheckedChange = { value ->
                        coroutineScope.launch {
                            settingsStore.setCheckUpdatesStartup(value)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryAccent, checkedTrackColor = PrimaryAccentLight)
                )
            },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
        Spacer(Modifier.height(16.dp))
        
        Text("Data Management", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        // Clear Watch History
        ListItem(
            headlineContent = { Text("Clear Watch History", color = TextPrimary) },
            supportingContent = { 
                Text(
                    if (historyCleared) "History cleared successfully!" else "Delete all recently watched progress", 
                    color = if (historyCleared) SuccessGreen else TextSecondary
                ) 
            },
            trailingContent = {
                Button(
                    onClick = {
                        watchHistoryStore.clearHistory()
                        historyCleared = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TertiaryAccent)
                ) {
                    Text("Clear", color = TextPrimary)
                }
            },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
        Spacer(Modifier.height(8.dp))
        
        // Clear Watchlist
        ListItem(
            headlineContent = { Text("Clear Watchlist", color = TextPrimary) },
            supportingContent = { 
                Text(
                    if (watchlistCleared) "Watchlist cleared successfully!" else "Delete all bookmarked anime", 
                    color = if (watchlistCleared) SuccessGreen else TextSecondary
                ) 
            },
            trailingContent = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            watchlistStore.clearWatchlist()
                            watchlistCleared = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TertiaryAccent)
                ) {
                    Text("Clear", color = TextPrimary)
                }
            },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
        Spacer(Modifier.height(16.dp))

        // --- Check for Updates ---
        Text("Updates", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("Check for Updates", color = TextPrimary) },
            supportingContent = {
                Text(
                    when (updateCheckState) {
                        "checking" -> "Checking for updates..."
                        "up_to_date" -> "You're on the latest version!"
                        "update_available" -> "Version ${foundUpdate?.versionName} available! ${foundUpdate?.updateNotes ?: ""}"
                        else -> "Tap to check if a new version is available"
                    },
                    color = when (updateCheckState) {
                        "up_to_date" -> SuccessGreen
                        "update_available" -> SecondaryAccent
                        "checking" -> PrimaryAccent
                        else -> TextSecondary
                    },
                    fontSize = 12.sp
                )
            },
            trailingContent = {
                if (updateCheckState == "update_available" && foundUpdate != null) {
                    Button(
                        onClick = { com.example.aniflow.utils.AppUpdater.downloadAndInstall(context, foundUpdate!!.updateUrl, foundUpdate!!.versionName) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                    ) {
                        Text("Download", color = TextPrimary)
                    }
                } else if (updateCheckState == "checking") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = PrimaryAccent,
                        strokeWidth = 2.dp
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
                                        android.util.Log.d("PhoneSettingsScreen", "info: $info, currentVersionCode: $currentVersionCode")
                                        android.widget.Toast.makeText(context, "Checked: Server=${info?.versionCode}, Current=$currentVersionCode", android.widget.Toast.LENGTH_SHORT).show()
                                        if (info != null && info.versionCode > currentVersionCode && !info.silentUpdate) {
                                            foundUpdate = info
                                            updateCheckState = "update_available"
                                        } else {
                                            updateCheckState = "up_to_date"
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("PhoneSettingsScreen", "Update check failed", e)
                                        android.widget.Toast.makeText(context, "Error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                                        updateCheckState = "up_to_date"
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                    ) {
                        Text("Check", color = TextPrimary)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
        Spacer(Modifier.height(8.dp))

        // App Version
        ListItem(
            headlineContent = { Text("App Version", color = TextPrimary) },
            supportingContent = { Text("v2.1.0 (Leanback Rebuilt)", color = TextSecondary) },
            trailingContent = { Text("Official Build", color = SuccessGreen) },
            colors = ListItemDefaults.colors(containerColor = SurfaceCard)
        )
    }
}
