package com.example.aniflow.ui.main

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.aniflow.data.model.finished
import com.example.aniflow.ui.phone.*
import com.example.aniflow.ui.tv.*
import com.example.aniflow.ui.tv.components.TvSideNavRail
import com.example.aniflow.ui.tv.components.TvTopNavBar
import com.example.aniflow.ui.tv.home.TvHome
import com.example.aniflow.ui.tv.home.TvShell
import com.example.aniflow.ui.tv.home.TvTokens
import com.example.aniflow.ui.redesign.*
import com.example.aniflow.ui.redesign.theme.glassSurface

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val latestAired by viewModel.latestAired.collectAsStateWithLifecycle()
    val topPicks by viewModel.topPicks.collectAsStateWithLifecycle()
    val becauseYouWatched by viewModel.becauseYouWatched.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val weekSchedule by viewModel.weekSchedule.collectAsStateWithLifecycle()
    val weekScheduleFailed by viewModel.weekScheduleFailed.collectAsStateWithLifecycle()
    // TVs keep the app alive for days: coming back to it refreshes rows older than half an hour.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshIfStale()
        onPauseOrDispose { }
    }
    val userFeedbackList by viewModel.userFeedbackList.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current.applicationContext
    val wStore = remember { watchlistStore ?: WatchlistStore(context) }
    val hStore = remember { watchHistoryStore ?: WatchHistoryStore(context) }
    val sStore = remember { settingsStore ?: SettingsStore(context) }
    val heroTrailers by sStore.heroTrailers.collectAsStateWithLifecycle(initialValue = true)
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
            val contentModifier = if (updateInfo != null) {
                // B16: canFocus=false on a plain Box never blocked its children, so the D-pad walked
                // behind the modal (and could press Play under it). Cancel entry into the group.
                Modifier
                    .focusProperties {
                        canFocus = false
                        @Suppress("DEPRECATION")
                        enter = { FocusRequester.Cancel }
                    }
                    .focusGroup()
                    .let {
                        if (isRedesign) it.blur(20.dp) else it
                    }
            } else {
                Modifier
            }
            var searchKeySignal by remember { mutableIntStateOf(0) }
            Box(
                modifier = contentModifier.fillMaxSize().onPreviewKeyEvent { e ->
                    // The remote's Search key jumps straight to Search instead of leaving the app
                    // for the system search screen.
                    if (deviceType != DeviceType.TV || e.key != androidx.compose.ui.input.key.Key.Search) return@onPreviewKeyEvent false
                    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) { viewModel.setTab(1); searchKeySignal++ }
                    true
                }
            ) {
                if (deviceType == DeviceType.TV) {
                    // One TV path regardless of package flavour: rail + Netflix-style home.
                    // Browse/Library/Settings reuse the existing TV screens, inset past the rail.
                    TvShell(
                        selectedTab = currentTab,
                        onSelectTab = { viewModel.setTab(it) },
                        railFocusRequester = navBarFocusRequester,
                        focusContentSignal = searchKeySignal,
                        onRailFocusChanged = { navBarFocused = it },
                    ) { _ ->
                        if (currentTab == 0) {
                            TvHome(
                                trending = trending,
                                popular = popular,
                                seasonal = seasonal,
                                topRated = topRated,
                                upcoming = upcoming,
                                recentlyUpdated = recentlyUpdated,
                                actionAnime = actionAnime,
                                romanceAnime = romanceAnime,
                                history = history,
                                latestAired = latestAired,
                                topPicks = topPicks,
                                becauseYouWatched = becauseYouWatched,
                                airing = airingToday,
                                userFeedbackList = userFeedbackList,
                                shelves = shelves,
                                trailersEnabled = heroTrailers,
                                isLoading = isLoading,
                                blocked = updateInfo != null,
                                // Off while the rail has focus, so BACK there reaches MainScreen's Home/exit logic.
                                backEnabled = isTopDestination && updateInfo == null && !navBarFocused,
                                onOpenDetail = { onItemClick(Detail(it)) },
                                onPlay = { id, ep -> onItemClick(Player(id, ep)) },
                                onBackToRail = { runCatching { navBarFocusRequester.requestFocus() } },
                            )
                        } else if (currentTab == 3) {
                            // Full-bleed screens that inset themselves past the rail.
                            TvSettingsScreen(
                                watchlistStore = wStore,
                                watchHistoryStore = hStore,
                                settingsStore = sStore,
                                repository = repository
                            )
                        } else if (currentTab == 4) {
                            TvScheduleScreen(
                                schedule = weekSchedule,
                                failed = weekScheduleFailed,
                                onRetry = { viewModel.retryWeekSchedule() },
                                onOpenDetail = { onItemClick(Detail(it)) },
                            )
                        } else {
                            Box(
                                modifier = modifier
                                    .fillMaxSize()
                                    .padding(start = TvTokens.ContentStart, top = 28.dp, end = 24.dp)
                            ) {
                                when (currentTab) {
                                    1 -> TvBrowseScreen(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                                        selectedGenre = selectedGenre,
                                        onGenreSelect = { viewModel.onGenreSelected(it) },
                                        results = searchResults,
                                        hasNextPage = hasNextPage,
                                        isSearchLoading = isSearchLoading,
                                        onLoadMore = { viewModel.loadNextSearchPage() },
                                        onAnimeClick = { viewModel.rememberSearch(); onItemClick(Detail(it.id)) },
                                        recent = recentSearches,
                                        onClearRecent = { viewModel.clearRecentSearches() },
                                    )
                                    2 -> TvLibraryScreen(
                                        watchlist = watchlist,
                                        onAnimeClick = { onItemClick(Detail(it.id)) },
                                        history = history,
                                        onHistoryClick = { onItemClick(Detail(it.animeId)) }
                                    )
                                }
                            }
                        }
                    }
            } else if (!isRedesign) {
                // The phone frame: top bar + floating capsule tab bar + resume bar (ui/phone/PhoneShell).
                var segment by rememberSaveable { mutableIntStateOf(SEGMENT_FOR_YOU) }
                var searchFocus by rememberSaveable { mutableIntStateOf(0) }
                val resume = remember(history) {
                    history.maxByOrNull { it.lastWatchedTime }?.takeIf { !it.finished }
                }
                PhoneShell(
                    currentTab = currentTab,
                    onSelectTab = { viewModel.setTab(it) },
                    segment = segment,
                    onSegment = { segment = it },
                    onSearch = { viewModel.setTab(1); searchFocus++ },
                    resume = resume,
                    onResume = { onItemClick(Player(it.animeId, it.episodeNumber)) },
                ) { tab, inset ->
                    CompositionLocalProvider(LocalPhoneBottomInset provides inset) {
                        when (tab) {
                            0 -> if (isLoading) {
                                Box(Modifier.fillMaxSize()) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryAccent)
                                }
                            } else PhoneHomeScreen(
                                onOpenSchedule = { viewModel.setTab(4) },
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
                                latestAired = latestAired,
                                topPicks = topPicks,
                                becauseYouWatched = becauseYouWatched,
                                shelves = shelves,
                                userFeedbackList = userFeedbackList,
                                onAnimeClick = { onItemClick(Detail(it.id)) },
                                onHistoryClick = { onItemClick(Detail(it.animeId)) },
                                onPlay = { id, ep -> onItemClick(Player(id, ep)) },
                                settingsStore = sStore,
                                segment = segment,
                                bottomInset = inset,
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
                                onAnimeClick = { viewModel.rememberSearch(); onItemClick(Detail(it.id)) },
                                recent = recentSearches,
                                onClearRecent = { viewModel.clearRecentSearches() },
                                focusSearch = searchFocus,
                            )
                            2 -> PhoneLibraryScreen(
                                watchlist = watchlist,
                                onAnimeClick = { onItemClick(Detail(it.id)) },
                                history = history,
                                onHistoryClick = { onItemClick(Detail(it.animeId)) }
                            )
                            3 -> PhoneSettingsScreen(
                                watchlistStore = wStore,
                                watchHistoryStore = hStore,
                                settingsStore = sStore,
                                repository = repository
                            )
                            4 -> PhoneScheduleScreen(
                                schedule = weekSchedule,
                                failed = weekScheduleFailed,
                                onRetry = { viewModel.retryWeekSchedule() },
                                onOpenDetail = { onItemClick(Detail(it)) }
                            )
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
                                        .width(360.dp)
                                        .height(68.dp)
                                        .darkGlassSurface(shape = CircleShape, borderWidth = 1.dp)
                                ) {
                                    val tabWidth = maxWidth / 5
                                    // Tab index -> slot in the capsule (Schedule sits in the middle).
                                    val slot = listOf(0, 1, 3, 4, 2).getOrElse(currentTab) { 0 }

                                    val leftEdge by animateDpAsState(
                                        targetValue = tabWidth * slot + 6.dp,
                                        animationSpec = spring(
                                            dampingRatio = 0.75f,
                                            stiffness = leftStiffness
                                        ),
                                        label = "leftEdge"
                                    )
                                    val rightEdge by animateDpAsState(
                                        targetValue = tabWidth * (slot + 1) - 6.dp,
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
                                            4 to (Icons.Default.DateRange to "Schedule"),
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
                                    selected = currentTab == 4,
                                    onClick = { viewModel.setTab(4) },
                                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Schedule") },
                                    label = { Text("Schedule", fontSize = 10.sp) },
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
                                        onAnimeClick = { viewModel.rememberSearch(); onItemClick(Detail(it.id)) },
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
                                    4 -> PhoneScheduleScreen(
                                        schedule = weekSchedule,
                                        failed = weekScheduleFailed,
                                        onRetry = { viewModel.retryWeekSchedule() },
                                        onOpenDetail = { onItemClick(Detail(it)) }
                                    )
                                }
                            } else {
                                when (currentTab) {
                                    0 -> PhoneHomeScreen(
                                        onOpenSchedule = { viewModel.setTab(4) },
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
                                        latestAired = latestAired,
                                        topPicks = topPicks,
                                        becauseYouWatched = becauseYouWatched,
                                        shelves = shelves,
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
                                        onAnimeClick = { viewModel.rememberSearch(); onItemClick(Detail(it.id)) },
                                        recent = recentSearches,
                                        onClearRecent = { viewModel.clearRecentSearches() },
                                    )
                                    2 -> PhoneLibraryScreen(
                                        watchlist = watchlist,
                                        onAnimeClick = { onItemClick(Detail(it.id)) },
                                        history = history,
                                        onHistoryClick = { onItemClick(Detail(it.animeId)) }
                                    )
                                    3 -> PhoneSettingsScreen(
                                        watchlistStore = wStore,
                                        watchHistoryStore = hStore,
                                        settingsStore = sStore,
                                        repository = repository
                                    )
                                    4 -> PhoneScheduleScreen(
                                        schedule = weekSchedule,
                                        failed = weekScheduleFailed,
                                        onRetry = { viewModel.retryWeekSchedule() },
                                        onOpenDetail = { onItemClick(Detail(it)) }
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
            // Maintenance comes only from the signed manifest, and carries its own end time.
            val isMaintenance = updateInfo!!.maintenance
            if (isMaintenance) {
                com.example.aniflow.ui.maintenance.MaintenanceScreen(
                    deviceType = deviceType,
                    onCheckStatus = {
                        viewModel.checkForUpdates(force = true)
                    }
                )
            } else {
                UpdateTakeoverScreen(
                    info = updateInfo!!,
                    onDismiss = { viewModel.dismissUpdate() },
                    onSkip = { viewModel.skipUpdate() }
                ) { onProgress ->
                    com.example.aniflow.utils.AppUpdater.downloadAndInstall(context, updateInfo!!, onProgress)
                }
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
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isRedesign = remember { context.packageName.endsWith(".redesign") }
    val deviceType = com.example.aniflow.LocalDeviceType.current
    val focusRequester = remember { FocusRequester() }
    val githubReleaseUrl = "https://github.com/anshsingh70007-hash/AniFinal/releases/latest"

    LaunchedEffect(Unit) {
        visible = true
    }

    // TV: the button sits inside AnimatedVisibility, so it isn't attached on the first frame and a
    // single requestFocus() silently missed. Focus then stayed on the home screen behind, and OK
    // opened a show instead of installing. Retry until the dialog really holds focus.
    var dialogHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible && deviceType == com.example.aniflow.DeviceType.TV) {
            repeat(40) {
                if (dialogHasFocus) return@LaunchedEffect
                runCatching { focusRequester.requestFocus() }
                delay(50)
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
                color = if (isRedesign) Color(0xFF110C1C).copy(alpha = 0.98f) else SurfaceCard,
                tonalElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Keep remote focus inside the dialog, and scroll so the buttons are
                        // reachable on a 720p TV (they were cut off below the screen).
                        .onFocusChanged { dialogHasFocus = it.hasFocus }
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
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

                    val pInfo = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        } catch (e: Exception) { null }
                    }
                    val currentVersionCode = remember {
                        if (pInfo != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pInfo.longVersionCode.toInt()
                        } else {
                            @Suppress("DEPRECATION")
                            pInfo?.versionCode ?: 53
                        }
                    }
                    // Was `!isPreviousVersionUser || …` (left over from the September lockdown): for
                    // anyone on versionCode >= 53 every update rendered as "Maintenance" with only
                    // "Check Server Status"/"Exit App" — no install button, so 1.8.7 could never
                    // update in-app. Maintenance is now driven only by the remote JSON.
                    val isMaintenance = info.maintenance

                    Text(
                        text = if (isMaintenance) "Maintenance" else "Update Available",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isMaintenance)
                            "This app is under maintenance we will be back soon.\n\nWe're fine-tuning our video player engine and streaming infrastructure to ensure seamless, stutter-free playback for you. Grab a coffee or your favorite snack — we'll be back shortly! ☕❤️"
                        else
                            if (info.forceUpdate) "AniFlow ${info.versionName} is required to keep using the app."
                            else "AniFlow ${info.versionName} is ready to install.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    if (!isMaintenance && !info.updateNotes.isNullOrEmpty()) {
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
                            if (isMaintenance) {
                                UpdateButton(
                                    text = "Check Server Status",
                                    isPrimary = true,
                                    isRedesign = isRedesign,
                                    deviceType = deviceType,
                                    modifier = Modifier.focusRequester(focusRequester),
                                    onClick = {
                                        Toast.makeText(context, "Checking server status...", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                )
                                UpdateButton(
                                    text = "Exit App",
                                    isPrimary = false,
                                    isRedesign = isRedesign,
                                    deviceType = deviceType,
                                    onClick = {
                                        val activity = (context as? android.app.Activity)
                                        activity?.finishAffinity()
                                    }
                                )
                            } else {
                                if (downloadProgress == -1.0f) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFF4D6D).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFFF4D6D).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "The update didn't install",
                                            color = Color(0xFFFF8FA3),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Nothing was changed. Check your connection and try again.",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }

                                // Primary: the in-app install (signed, verified download, then the
                                // system installer). This used to be a small secondary link, while
                                // the big button opened GitHub in a browser, which TVs don't have:
                                // "You don't have an app that can do this".
                                UpdateButton(
                                    text = if (downloadProgress == -1.0f) "Try again" else "Update now",
                                    isPrimary = true,
                                    isRedesign = isRedesign,
                                    deviceType = deviceType,
                                    modifier = Modifier.focusRequester(focusRequester),
                                    onClick = {
                                        downloadProgress = 0.0f
                                        onDownload { progress ->
                                            downloadProgress = progress
                                        }
                                    }
                                )

                                // Phones only: the download page, for anyone who'd rather install by hand.
                                if (deviceType != com.example.aniflow.DeviceType.TV) {
                                    UpdateButton(
                                        text = "Open download page",
                                        isPrimary = false,
                                        isRedesign = isRedesign,
                                        deviceType = deviceType,
                                        onClick = {
                                            try {
                                                uriHandler.openUri(githubReleaseUrl)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No browser found to open the download page.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }

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


