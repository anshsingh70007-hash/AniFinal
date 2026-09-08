package com.example.aniflow.ui.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.darkGlassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.GlassTokens
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import com.example.aniflow.data.UserFeedbackStore
import com.example.aniflow.data.UserFeedback
import androidx.compose.foundation.shape.CircleShape

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
    userFeedbackStore: UserFeedbackStore? = null,
    // False while another destination sits on top of this one, so BACK there pops the back stack
    // instead of being swallowed by the tab/exit handling below.
    isTopDestination: Boolean = true,
    viewModel: MainScreenViewModel = run {
        val context = LocalContext.current.applicationContext
        val watchList = watchlistStore ?: WatchlistStore(context)
        val watchHistory = watchHistoryStore ?: WatchHistoryStore(context)
        val feedbackStore = userFeedbackStore ?: UserFeedbackStore(context)
        viewModel { MainScreenViewModel(repository, watchList, watchHistory, feedbackStore, context) }
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
    val userFeedbackList by viewModel.userFeedbackList.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current.applicationContext
    val wStore = remember { watchlistStore ?: WatchlistStore(context) }
    val hStore = remember { watchHistoryStore ?: WatchHistoryStore(context) }
    val sStore = remember { settingsStore ?: SettingsStore(context) }
    val feedbackOnboardingShown by sStore.feedbackOnboardingShown.collectAsStateWithLifecycle(initialValue = true)
    val showOnboarding = !feedbackOnboardingShown && updateInfo == null
    val isRedesign = remember {
        context.packageName.endsWith(".redesign")
    }

    // BACK handling. The app previously had exactly one BackHandler (the player), so BACK from any
    // sub-tab closed the app instead of returning to Home.
    val activity = LocalActivity.current
    val navBarFocusRequester = remember { FocusRequester() }
    var navBarFocused by remember { mutableStateOf(false) }
    var exitArmed by remember { mutableStateOf(false) }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(2_000)
            exitArmed = false
        }
    }
    BackHandler(enabled = isTopDestination) {
        when {
            // On TV, BACK from the content grid should land on the nav bar, not quit.
            deviceType == DeviceType.TV && !navBarFocused ->
                runCatching { navBarFocusRequester.requestFocus() }
            currentTab != 0 -> viewModel.setTab(0)
            exitArmed -> activity?.finish()
            else -> {
                exitArmed = true
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (updateInfo == null || !updateInfo!!.forceUpdate) {
            val contentModifier = if (updateInfo != null || showOnboarding) {
                Modifier
                    .focusProperties { canFocus = false }
                    .let {
                        if (isRedesign) it.blur(20.dp) else it
                    }
            } else {
                Modifier
            }
            Box(modifier = contentModifier.fillMaxSize()) {
                if (deviceType == DeviceType.TV) {
                    val tvContent = @Composable {
                        Column(
                            modifier = modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp, bottom = 12.dp)
                                    .onFocusChanged { navBarFocused = it.hasFocus },
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
                                    onSelect = { viewModel.setTab(it) },
                                    selectedItemFocusRequester = navBarFocusRequester
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
                                                userFeedbackList = userFeedbackList,
                                                onAnimeClick = { onItemClick(Detail(it.id)) },
                                                onHistoryClick = { onItemClick(Detail(it.animeId)) }
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
                                                userFeedbackList = userFeedbackList,
                                                onAnimeClick = { onItemClick(Detail(it.id)) },
                                                onHistoryClick = { onItemClick(Detail(it.animeId)) }
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
                    }

                    Box(modifier = modifier.fillMaxSize().background(PrimaryDark)) {
                        tvContent()
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
                                var lastTab by remember { mutableStateOf(currentTab) }
                                val isMovingRight = currentTab > lastTab
                                val isMovingLeft = currentTab < lastTab

                                val leftStiffness = if (isMovingLeft) Spring.StiffnessMedium else Spring.StiffnessMediumLow
                                val rightStiffness = if (isMovingRight) Spring.StiffnessMedium else Spring.StiffnessMediumLow

                                LaunchedEffect(currentTab) {
                                    delay(280) // Wait slightly less than spring settling time to update state
                                    lastTab = currentTab
                                }

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .width(340.dp)
                                        .height(68.dp)
                                        .darkGlassSurface(shape = CircleShape, borderWidth = 1.dp)
                                ) {
                                    val tabWidth = maxWidth / 4

                                    val leftEdge by animateDpAsState(
                                        targetValue = tabWidth * currentTab + 6.dp,
                                        animationSpec = spring(
                                            dampingRatio = 0.75f,
                                            stiffness = leftStiffness
                                        ),
                                        label = "leftEdge"
                                    )
                                    val rightEdge by animateDpAsState(
                                        targetValue = tabWidth * (currentTab + 1) - 6.dp,
                                        animationSpec = spring(
                                            dampingRatio = 0.75f,
                                            stiffness = rightStiffness
                                        ),
                                        label = "rightEdge"
                                    )

                                    // Liquid Sliding Indicator Capsule
                                    Box(
                                        modifier = Modifier
                                            .offset(x = leftEdge)
                                            .width(rightEdge - leftEdge)
                                            .fillMaxHeight()
                                            .padding(vertical = 8.dp)
                                            .background(
                                                color = Color.White.copy(alpha = 0.12f),
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.08f),
                                                shape = CircleShape
                                            )
                                    )

                                    // Tab Icons Row
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
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

                                            // Animate size/scale of icon on click
                                            val iconScale by animateFloatAsState(
                                                targetValue = if (isSelected) 1.25f else 1.0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                ),
                                                label = "iconScale"
                                            )

                                            // Animate color/tint of icon
                                            val iconColor by animateColorAsState(
                                                targetValue = if (isSelected) GlassTokens.GlowCyan else GlassTokens.TextMuted.copy(alpha = 0.7f),
                                                animationSpec = tween(durationMillis = 250),
                                                label = "iconColor"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        viewModel.setTab(index)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = pair.first,
                                                        contentDescription = pair.second,
                                                        tint = iconColor,
                                                        modifier = Modifier
                                                            .graphicsLayer(
                                                                scaleX = iconScale,
                                                                scaleY = iconScale
                                                            )
                                                            .size(24.dp)
                                                    )
                                                    
                                                    // Active tab indicator dot
                                                    AnimatedVisibility(
                                                        visible = isSelected,
                                                        enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                                                        exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
                                                    ) {
                                                        Spacer(Modifier.height(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .background(
                                                                    color = GlassTokens.GlowCyan,
                                                                    shape = CircleShape
                                                                )
                                                                .drawBehind {
                                                                    drawCircle(
                                                                        color = GlassTokens.GlowCyan.copy(alpha = 0.4f),
                                                                        radius = 6.dp.toPx()
                                                                    )
                                                                }
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
                                        userFeedbackList = userFeedbackList,
                                        onAnimeClick = { onItemClick(Detail(it.id)) },
                                        onHistoryClick = { onItemClick(Detail(it.animeId)) }
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
                                        userFeedbackList = userFeedbackList,
                                        onAnimeClick = { onItemClick(Detail(it.id)) },
                                        onHistoryClick = { onItemClick(Detail(it.animeId)) }
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

        if (showOnboarding) {
            val scope = rememberCoroutineScope()
            FeedbackOnboardingScreen(
                onDismiss = {
                    scope.launch {
                        sStore.setFeedbackOnboardingShown(true)
                    }
                }
            )
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
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }
    val deviceType = com.example.aniflow.LocalDeviceType.current
    val focusRequester = remember { FocusRequester() }
    val githubReleaseUrl = "https://github.com/anshsingh70007-hash/AniFinal/releases/latest"

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(visible) {
        if (visible && deviceType == com.example.aniflow.DeviceType.TV) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val executeWithAnimation: (() -> Unit) -> Unit = { callback ->
        scope.launch {
            visible = false
            delay(250L)
            callback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isRedesign) {
                    Color.Black.copy(alpha = 0.85f)
                } else {
                    PrimaryDark.copy(alpha = 0.9f)
                }
            )
            .let {
                if (deviceType != com.example.aniflow.DeviceType.TV) {
                    it.clickable(enabled = !info.forceUpdate && downloadProgress < 0f) {
                        executeWithAnimation { onDismiss() }
                    }
                } else {
                    it
                }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + scaleIn(tween(300, easing = androidx.compose.animation.core.EaseInOutCubic), initialScale = 0.8f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.8f)
        ) {
            val cardModifier = if (isRedesign) {
                Modifier
                    .widthIn(max = 440.dp)
                    .padding(24.dp)
                    .glassSurface(shape = RoundedCornerShape(20.dp))
                    .let {
                        if (deviceType != com.example.aniflow.DeviceType.TV) {
                            it.clickable(enabled = false) {}
                        } else {
                            it
                        }
                    }
            } else {
                Modifier
                    .widthIn(max = 440.dp)
                    .padding(24.dp)
                    .let {
                        if (deviceType != com.example.aniflow.DeviceType.TV) {
                            it.clickable(enabled = false) {}
                        } else {
                            it
                        }
                    }
            }

            Surface(
                modifier = cardModifier,
                shape = RoundedCornerShape(20.dp),
                color = if (isRedesign) Color(0xFF0F0E17).copy(alpha = 0.98f) else SurfaceCard,
                tonalElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon
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
                        val notesModifier = if (isRedesign) {
                            Modifier
                                .fillMaxWidth()
                                .glassSurface(shape = RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .background(PrimaryDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        }
                        Column(modifier = notesModifier) {
                            Text(
                                text = "What's New:",
                                color = PrimaryAccentLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val urlRegex = remember { Regex("""(https?://[^\s]+)""") }
                            val notes = info.updateNotes
                            val annotatedNotes = remember(notes) {
                                buildAnnotatedString {
                                    val matches = urlRegex.findAll(notes)
                                    var lastEnd = 0
                                    for (match in matches) {
                                        val start = match.range.first
                                        val end = match.range.last + 1
                                        if (start > lastEnd) {
                                            append(notes.substring(lastEnd, start))
                                        }
                                        val url = match.value
                                        val linkStart = length
                                        append(url)
                                        val linkEnd = length
                                        try {
                                            addLink(
                                                LinkAnnotation.Url(
                                                    url = url,
                                                    styles = TextLinkStyles(
                                                        style = SpanStyle(
                                                            color = PrimaryAccentLight,
                                                            textDecoration = TextDecoration.Underline,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    )
                                                ),
                                                linkStart,
                                                linkEnd
                                            )
                                        } catch (t: Throwable) {
                                            addStyle(
                                                SpanStyle(
                                                    color = PrimaryAccentLight,
                                                    textDecoration = TextDecoration.Underline,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                linkStart,
                                                linkEnd
                                            )
                                            addStringAnnotation("URL", url, linkStart, linkEnd)
                                        }
                                        lastEnd = end
                                    }
                                    if (lastEnd < notes.length) {
                                        append(notes.substring(lastEnd))
                                    }
                                }
                            }
                            Text(
                                text = annotatedNotes,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (downloadProgress >= 0.0f) {
                        // Downloading state
                        Text(
                            text = "Downloading Update: ${(downloadProgress * 100).toInt()}%",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            color = PrimaryAccent,
                            trackColor = if (isRedesign) Color.White.copy(alpha = 0.1f) else PrimaryDark.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (downloadProgress >= 0.99f) "Installing..." else "Please wait...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        // Action buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (downloadProgress == -1.0f) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE53935).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFFE53935).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "⚠️ One-Time Reinstall Required",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Due to our new permanent security key, older apps cannot update in-place. Please uninstall your older app and install from GitHub.",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            // GitHub Install Button (Primary, clickable, D-pad focusable on TV)
                            UpdateButton(
                                text = "Install from GitHub",
                                isPrimary = true,
                                isRedesign = isRedesign,
                                deviceType = deviceType,
                                modifier = Modifier.focusRequester(focusRequester),
                                onClick = {
                                    try {
                                        uriHandler.openUri(githubReleaseUrl)
                                    } catch (e: Exception) {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(githubReleaseUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(browserIntent)
                                        } catch (err: Exception) {
                                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            // Direct In-App APK Download
                            UpdateButton(
                                text = if (downloadProgress == -1.0f) "Retry In-App Download" else "Direct APK Download",
                                isPrimary = false,
                                isRedesign = isRedesign,
                                deviceType = deviceType,
                                onClick = {
                                    downloadProgress = 0.0f
                                    onDownload { progress ->
                                        downloadProgress = progress
                                    }
                                }
                            )

                            if (!info.forceUpdate) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    UpdateButton(
                                        text = "Remind Later",
                                        isPrimary = false,
                                        isRedesign = isRedesign,
                                        deviceType = deviceType,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            executeWithAnimation { onDismiss() }
                                        }
                                    )
                                    UpdateButton(
                                        text = "Skip Version",
                                        isPrimary = false,
                                        isRedesign = isRedesign,
                                        deviceType = deviceType,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            executeWithAnimation { onSkip() }
                                        }
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

@Composable
private fun UpdateButton(
    text: String,
    isPrimary: Boolean,
    isRedesign: Boolean,
    deviceType: com.example.aniflow.DeviceType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val buttonModifier = if (isRedesign) {
        modifier
            .let { if (isPrimary) it.fillMaxWidth() else it }
            .onFocusChanged { isFocused = it.isFocused }
            .let {
                if (deviceType == com.example.aniflow.DeviceType.TV) {
                    it.focusGlow(isFocused, shape = RoundedCornerShape(12.dp))
                } else {
                    it
                }
            }
            .glassSurface(
                shape = RoundedCornerShape(12.dp),
                borderWidth = if (isPrimary) 2.dp else 1.dp,
                isFocused = isFocused
            )
            .let {
                if (isPrimary && !isFocused) {
                    it.background(PrimaryAccent.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
                } else {
                    it
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = if (isPrimary) 14.dp else 10.dp)
    } else {
        modifier
            .let { if (isPrimary) it.fillMaxWidth() else it }
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isPrimary -> PrimaryAccent
                    isFocused -> PrimaryAccent.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            )
            .let {
                if (deviceType == com.example.aniflow.DeviceType.TV && isFocused) {
                    it.border(2.dp, SecondaryAccent, RoundedCornerShape(12.dp))
                } else {
                    it
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = if (isPrimary) 14.dp else 10.dp)
    }

    Box(
        modifier = buttonModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) {
                PrimaryAccentLight
            } else if (isPrimary) {
                TextPrimary
            } else {
                TextSecondary
            },
            fontSize = if (isPrimary) 16.sp else 14.sp,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun FeedbackOnboardingScreen(
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }
    val deviceType = com.example.aniflow.LocalDeviceType.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(visible) {
        if (visible && deviceType == com.example.aniflow.DeviceType.TV) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val executeWithAnimation: (() -> Unit) -> Unit = { callback ->
        scope.launch {
            visible = false
            delay(250L)
            callback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isRedesign) {
                    Color.Black.copy(alpha = 0.85f)
                } else {
                    PrimaryDark.copy(alpha = 0.9f)
                }
            )
            .let {
                if (deviceType != com.example.aniflow.DeviceType.TV) {
                    it.clickable {
                        executeWithAnimation { onDismiss() }
                    }
                } else {
                    it
                }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + scaleIn(tween(300, easing = androidx.compose.animation.core.EaseInOutCubic), initialScale = 0.8f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.8f)
        ) {
            val cardModifier = if (isRedesign) {
                Modifier
                    .widthIn(max = 440.dp)
                    .padding(24.dp)
                    .glassSurface(shape = RoundedCornerShape(20.dp))
                    .let {
                        if (deviceType != com.example.aniflow.DeviceType.TV) {
                            it.clickable(enabled = false) {}
                        } else {
                            it
                        }
                    }
            } else {
                Modifier
                    .widthIn(max = 440.dp)
                    .padding(24.dp)
                    .let {
                        if (deviceType != com.example.aniflow.DeviceType.TV) {
                            it.clickable(enabled = false) {}
                        } else {
                            it
                        }
                    }
            }

            Surface(
                modifier = cardModifier,
                shape = RoundedCornerShape(20.dp),
                color = if (isRedesign) Color(0xFF0F0E17).copy(alpha = 0.98f) else SurfaceCard,
                tonalElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(PrimaryAccent.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = PrimaryAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Introducing User's Choice!",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Share why you love your favorite anime! Open any anime details page, write your reasons, and submit to see them instantly displayed on the global \"User's Choice\" section on the home screen.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    UpdateButton(
                        text = "Got it!",
                        isPrimary = true,
                        isRedesign = isRedesign,
                        deviceType = deviceType,
                        modifier = Modifier.focusRequester(focusRequester),
                        onClick = {
                            executeWithAnimation { onDismiss() }
                        }
                    )
                }
            }
        }
    }
}

