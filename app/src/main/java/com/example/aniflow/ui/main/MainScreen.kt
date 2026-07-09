package com.example.aniflow.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.aniflow.Detail
import com.example.aniflow.Player
import com.example.aniflow.DeviceType
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.theme.*
import com.example.aniflow.ui.*
import com.example.aniflow.ui.phone.*
import com.example.aniflow.ui.tv.*
import com.example.aniflow.ui.tv.components.TvSideNavRail
import com.example.aniflow.ui.tv.components.TvTopNavBar
import com.example.aniflow.ui.redesign.*
import com.example.aniflow.ui.redesign.theme.glassSurface

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    deviceType: DeviceType,
    repository: AnimeRepository,
    modifier: Modifier = Modifier,
    watchlistStore: WatchlistStore? = null,
    watchHistoryStore: WatchHistoryStore? = null,
    settingsStore: SettingsStore? = null,
    viewModel: MainScreenViewModel = run {
        val context = LocalContext.current.applicationContext
        val watchList = watchlistStore ?: WatchlistStore(context)
        val watchHistory = watchHistoryStore ?: WatchHistoryStore(context)
        viewModel { MainScreenViewModel(repository, watchList, watchHistory, context) }
    }
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val trending by viewModel.trending.collectAsStateWithLifecycle()
    val popular by viewModel.popular.collectAsStateWithLifecycle()
    val seasonal by viewModel.seasonal.collectAsStateWithLifecycle()
    val airingToday by viewModel.airingToday.collectAsStateWithLifecycle()
    val topRated by viewModel.topRated.collectAsStateWithLifecycle()
    val upcoming by viewModel.upcoming.collectAsStateWithLifecycle()
    val recentlyUpdated by viewModel.recentlyUpdated.collectAsStateWithLifecycle()
    val actionAnime by viewModel.actionAnime.collectAsStateWithLifecycle()
    val romanceAnime by viewModel.romanceAnime.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val hasNextPage by viewModel.hasNextPage.collectAsStateWithLifecycle()
    val isSearchLoading by viewModel.isSearchLoading.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current.applicationContext
    val wStore = remember { watchlistStore ?: WatchlistStore(context) }
    val hStore = remember { watchHistoryStore ?: WatchHistoryStore(context) }
    val sStore = remember { settingsStore ?: SettingsStore(context) }
    val isRedesign = remember {
        context.packageName.endsWith(".redesign")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (updateInfo == null || !updateInfo!!.forceUpdate) {
            if (deviceType == DeviceType.TV) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .background(PrimaryDark)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TvTopNavBar(
                            selectedIndex = currentTab,
                            items = listOf(
                                Icons.Default.Home to "Home",
                                Icons.Default.Search to "Browse",
                                Icons.Default.Favorite to "Library",
                                Icons.Default.Settings to "Settings"
                            ),
                            onSelect = { viewModel.setTab(it) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 24.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryAccent)
                        } else {
                            if (isRedesign) {
                                when (currentTab) {
                                    0 -> RedesignTvHomeScreen(
                                        trending = trending,
                                        popular = popular,
                                        seasonal = seasonal,
                                        airing = airingToday,
                                        topRated = topRated,
                                        upcoming = upcoming,
                                        recentlyUpdated = recentlyUpdated,
                                        actionAnime = actionAnime,
                                        romanceAnime = romanceAnime,
                                        history = history,
                                        onAnimeClick = { onItemClick(Detail(it.id)) },
                                        onHistoryClick = { onItemClick(Player(it.animeId, it.episodeNumber)) }
                                    )
                                    1 -> RedesignTvBrowseScreen(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                        selectedGenre = selectedGenre,
                                        onGenreSelect = { viewModel.onGenreSelected(it) },
                                        results = searchResults,
                                        hasNextPage = hasNextPage,
                                        isSearchLoading = isSearchLoading,
                                        onLoadMore = { viewModel.loadNextSearchPage() },
                                        onAnimeClick = { onItemClick(Detail(it.id)) }
                                    )
                                    2 -> RedesignTvLibraryScreen(
                                        watchlist = watchlist,
                                        onAnimeClick = { onItemClick(Detail(it.id)) }
                                    )
                                    3 -> TvSettingsScreen(
                                        watchlistStore = wStore,
                                        watchHistoryStore = hStore,
                                        settingsStore = sStore,
                                        repository = repository
                                    )
                                }
                            } else {
                                when (currentTab) {
                                    0 -> TvHomeScreen(
                                        trending = trending,
                                        popular = popular,
                                        seasonal = seasonal,
                                        airing = airingToday,
                                        topRated = topRated,
                                        upcoming = upcoming,
                                        recentlyUpdated = recentlyUpdated,
                                        actionAnime = actionAnime,
                                        romanceAnime = romanceAnime,
                                        history = history,
                                        onAnimeClick = { onItemClick(Detail(it.id)) },
                                        onHistoryClick = { onItemClick(Player(it.animeId, it.episodeNumber)) }
                                    )
                                    1 -> TvBrowseScreen(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                        selectedGenre = selectedGenre,
                                        onGenreSelect = { viewModel.onGenreSelected(it) },
                                        results = searchResults,
                                        hasNextPage = hasNextPage,
                                        isSearchLoading = isSearchLoading,
                                        onLoadMore = { viewModel.loadNextSearchPage() },
                                        onAnimeClick = { onItemClick(Detail(it.id)) }
                                    )
                                    2 -> TvLibraryScreen(
                                        watchlist = watchlist,
                                        onAnimeClick = { onItemClick(Detail(it.id)) }
                                    )
                                    3 -> TvSettingsScreen(
                                        watchlistStore = wStore,
                                        watchHistoryStore = hStore,
                                        settingsStore = sStore,
                                        repository = repository
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Scaffold(
                    bottomBar = {
                        if (isRedesign) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(bottom = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .wrapContentSize()
                                        .glassSurface(shape = RoundedCornerShape(28.dp), borderWidth = 1.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val items = listOf(
                                            0 to (Icons.Rounded.Home to "Home"),
                                            1 to (Icons.Rounded.Search to "Browse"),
                                            2 to (Icons.Default.Favorite to "Library"),
                                            3 to (Icons.Rounded.Settings to "Settings")
                                        )
                                        items.forEach { (index, pair) ->
                                            val isSelected = currentTab == index
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .clickable { viewModel.setTab(index) }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = pair.first,
                                                        contentDescription = pair.second,
                                                        tint = if (isSelected) PrimaryAccentLight else TextSecondary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    if (isSelected) {
                                                        Text(
                                                            text = pair.second,
                                                            color = PrimaryAccentLight,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            NavigationBar(
                                containerColor = PrimaryDarker,
                                tonalElevation = 8.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { viewModel.setTab(0) },
                                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                                    label = { Text("Home", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryAccentLight,
                                        selectedTextColor = PrimaryAccentLight,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = PrimaryAccent.copy(alpha = 0.2f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { viewModel.setTab(1) },
                                    icon = { Icon(Icons.Rounded.Search, contentDescription = "Browse") },
                                    label = { Text("Browse", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryAccentLight,
                                        selectedTextColor = PrimaryAccentLight,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = PrimaryAccent.copy(alpha = 0.2f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == 2,
                                    onClick = { viewModel.setTab(2) },
                                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Library") },
                                    label = { Text("Library", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryAccentLight,
                                        selectedTextColor = PrimaryAccentLight,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = PrimaryAccent.copy(alpha = 0.2f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == 3,
                                    onClick = { viewModel.setTab(3) },
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryAccentLight,
                                        selectedTextColor = PrimaryAccentLight,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary,
                                        indicatorColor = PrimaryAccent.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    },
                    containerColor = PrimaryDark,
                    modifier = modifier
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryAccent)
                        } else {
                            if (isRedesign) {
                                when (currentTab) {
                                    0 -> RedesignPhoneHomeScreen(
                                        trending = trending,
                                        popular = popular,
                                        seasonal = seasonal,
                                        airing = airingToday,
                                        topRated = topRated,
                                        upcoming = upcoming,
                                        recentlyUpdated = recentlyUpdated,
                                        actionAnime = actionAnime,
                                        romanceAnime = romanceAnime,
                                        history = history,
                                        onAnimeClick = { onItemClick(Detail(it.id)) },
                                        onHistoryClick = { onItemClick(Player(it.animeId, it.episodeNumber)) }
                                    )
                                    1 -> RedesignPhoneBrowseScreen(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                        selectedGenre = selectedGenre,
                                        onGenreSelect = { viewModel.onGenreSelected(it) },
                                        results = searchResults,
                                        hasNextPage = hasNextPage,
                                        isSearchLoading = isSearchLoading,
                                        onLoadMore = { viewModel.loadNextSearchPage() },
                                        onAnimeClick = { onItemClick(Detail(it.id)) }
                                    )
                                    2 -> RedesignPhoneLibraryScreen(
                                        watchlist = watchlist,
                                        onAnimeClick = { onItemClick(Detail(it.id)) }
                                    )
                                    3 -> PhoneSettingsScreen(
                                        watchlistStore = wStore,
                                        watchHistoryStore = hStore,
                                        settingsStore = sStore,
                                        repository = repository
                                    )
                                }
                            } else {
                                when (currentTab) {
                                    0 -> PhoneHomeScreen(
                                        trending = trending,
                                        popular = popular,
                                        seasonal = seasonal,
                                        airing = airingToday,
                                        topRated = topRated,
                                        upcoming = upcoming,
                                        recentlyUpdated = recentlyUpdated,
                                        actionAnime = actionAnime,
                                        romanceAnime = romanceAnime,
                                        history = history,
                                        onAnimeClick = { onItemClick(Detail(it.id)) },
                                        onHistoryClick = { onItemClick(Player(it.animeId, it.episodeNumber)) }
                                    )
                                    1 -> PhoneBrowseScreen(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                        selectedGenre = selectedGenre,
                                        onGenreSelect = { viewModel.onGenreSelected(it) },
                                        results = searchResults,
                                        hasNextPage = hasNextPage,
                                        isSearchLoading = isSearchLoading,
                                        onLoadMore = { viewModel.loadNextSearchPage() },
                                        onAnimeClick = { onItemClick(Detail(it.id)) }
                                    )
                                    2 -> PhoneLibraryScreen(
                                        watchlist = watchlist,
                                        onAnimeClick = { onItemClick(Detail(it.id)) }
                                    )
                                    3 -> PhoneSettingsScreen(
                                        watchlistStore = wStore,
                                        watchHistoryStore = hStore,
                                        settingsStore = sStore,
                                        repository = repository
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (updateInfo != null) {
            UpdateTakeoverScreen(
                info = updateInfo!!,
                onDismiss = { viewModel.dismissUpdate() },
                onSkip = { viewModel.skipUpdate() }
            ) { onProgress ->
                com.example.aniflow.utils.AppUpdater.downloadAndInstall(context, updateInfo!!.updateUrl, updateInfo!!.versionName, onProgress)
            }
        }
    }
}


@Composable
fun UpdateTakeoverScreen(
    info: com.example.aniflow.data.model.AppUpdateInfo,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onDownload: ((Float) -> Unit) -> Unit
) {
    var downloadProgress by remember { mutableStateOf(-2.0f) }
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        visible = true
    }

    val executeWithAnimation: (() -> Unit) -> Unit = { callback ->
        scope.launch {
            visible = false
            delay(250L) // Wait for exit animation
            callback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark.copy(alpha = 0.85f))
            .clickable(enabled = !info.forceUpdate) {
                executeWithAnimation { onDismiss() }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + scaleIn(tween(300, easing = androidx.compose.animation.core.EaseInOutCubic), initialScale = 0.8f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.8f)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier
                    .width(420.dp)
                    .padding(24.dp)
                    .clickable(enabled = false) {}, // prevent click propagation
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon / Banner
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(PrimaryAccent.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = PrimaryAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Update Available",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "A new version of AniFlow (v${info.versionName}) is available. Please update to continue using the application.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    if (!info.updateNotes.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PrimaryDark.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "What's New:",
                                    color = PrimaryAccentLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = info.updateNotes,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    if (downloadProgress >= 0.0f) {
                        Text(
                            text = "Downloading Update: ${(downloadProgress * 100).toInt()}%",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            color = PrimaryAccent,
                            trackColor = PrimaryDark.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        if (downloadProgress == -1.0f) {
                            Text(
                                text = "Download failed. Please try again.",
                                color = androidx.compose.ui.graphics.Color.Red,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = {
                                    downloadProgress = 0.0f
                                    onDownload { progress ->
                                        downloadProgress = progress
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                    text = if (downloadProgress == -1.0f) "Retry Download" else "Download Now",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (!info.forceUpdate) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TextButton(
                                        onClick = {
                                            executeWithAnimation { onDismiss() }
                                        }
                                    ) {
                                        Text(
                                            text = "Remind Later",
                                            color = TextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            executeWithAnimation { onSkip() }
                                        }
                                    ) {
                                        Text(
                                            text = "Skip Version",
                                            color = TextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

