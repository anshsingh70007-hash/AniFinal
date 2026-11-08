package com.example.aniflow.ui.tv.home

import com.example.aniflow.data.remote.MediaExtras
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.example.aniflow.data.model.TrailerClip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import com.example.aniflow.ui.focus.primaryFocus
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.model.continueEpisode
import com.example.aniflow.data.model.finished
import com.example.aniflow.data.model.newEpisodes
import com.example.aniflow.data.model.newEpisodesLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One rail on the home screen. `progress` is only set for Continue Watching. */
@Immutable
private data class HomeRow(
    val key: String,
    val title: String,
    val items: List<Anime>,
    val ranked: Boolean = false,
    val history: Map<Int, WatchHistoryEntry> = emptyMap(),
    /** Newest aired episode per still-airing show (Continue Watching only). */
    val latest: Map<Int, Int> = emptyMap(),
)

/**
 * The TV home. Billboard on top follows focus; rows below; the focused card springs forward and,
 * if you stay on it, opens into a wide info card while its trailer fades in behind the billboard.
 * Move away and everything settles back. One trailer surface, one image decode size per role,
 * and every focus animation is a graphicsLayer change — nothing relayouts per frame except the
 * single card that is expanding.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TvHome(
    trending: List<Anime>,
    popular: List<Anime>,
    seasonal: List<Anime>,
    topRated: List<Anime>,
    upcoming: List<Anime>,
    recentlyUpdated: List<Anime>,
    actionAnime: List<Anime>,
    romanceAnime: List<Anime>,
    history: List<WatchHistoryEntry>,
    /** AniList id -> newest episode out, for shows still airing. */
    latestAired: Map<Int, Int> = emptyMap(),
    topPicks: List<Anime>,
    becauseYouWatched: Pair<String, List<Anime>>?,
    shelves: List<com.example.aniflow.data.repository.HomeShelf> = emptyList(),
    /** Settings > Trailer previews. */
    trailersEnabled: Boolean = true,
    isLoading: Boolean,
    backEnabled: Boolean,
    /** A modal (update / onboarding) is on top: don't grab focus until it closes. */
    blocked: Boolean,
    onOpenDetail: (Int) -> Unit,
    onPlay: (animeId: Int, episode: Int) -> Unit,
    onBackToRail: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- data ---------------------------------------------------------------------------------
    val byId = remember(trending, popular, seasonal, topRated, upcoming, recentlyUpdated, actionAnime, romanceAnime, shelves) {
        (trending + popular + seasonal + topRated + recentlyUpdated + upcoming + actionAnime + romanceAnime + shelves.flatMap { it.items })
            .associateBy { it.id }
    }
    val rows = remember(byId, history, latestAired, topPicks, becauseYouWatched, shelves) {
        val mid = shelves.filter { !it.late }.map { HomeRow("shelf_" + it.key, it.title, it.items) }
        val late = shelves.filter { it.late }.map { HomeRow("shelf_" + it.key, it.title, it.items) }
        val historyById = history.associateBy { it.animeId }
        val continueItems = history.sortedByDescending { it.lastWatchedTime }.map { h ->
            byId[h.animeId] ?: Anime(id = h.animeId, title = h.title, coverImage = h.coverImage)
        }
        (listOf(
            HomeRow("continue", "Continue Watching", continueItems, history = historyById, latest = latestAired),
            HomeRow("picks", "Top Picks for You", topPicks),
            HomeRow("because", "Because You Watched ${becauseYouWatched?.first.orEmpty()}", becauseYouWatched?.second.orEmpty()),
            HomeRow("trending", "Trending Now", trending),
            HomeRow("top10", "Top 10 Highest Rated", topRated.take(10), ranked = true),
            HomeRow("seasonal", "Airing This Season", seasonal),
            HomeRow("fresh", "Fresh Episodes", recentlyUpdated),
        ) + mid + listOf(
            HomeRow("popular", "All-Time Popular", popular),
            HomeRow("action", "Adrenaline: Action", actionAnime),
            HomeRow("romance", "Heart-Racing Romance", romanceAnime),
        ) + late + listOf(
            HomeRow("upcoming", "Coming Soon", upcoming),
        )).filter { it.items.isNotEmpty() }.distinctBy { it.key }
    }
    // Spotlight: the hero rotates through a few trending titles with wide art while you sit on it.
    val spotlight = remember(trending, rows) {
        trending.filter { it.bannerImage != null }.take(5).ifEmpty {
            listOfNotNull(trending.firstOrNull() ?: rows.firstOrNull()?.items?.firstOrNull())
        }
    }
    var spotIndex by rememberSaveable { mutableStateOf(0) }
    val featured = spotlight.getOrNull(spotIndex % spotlight.size.coerceAtLeast(1))

    // ---- focus / hero state --------------------------------------------------------------------
    var heroMode by rememberSaveable { mutableStateOf(true) }     // focus is on the billboard buttons
    var heroInfoBottom by remember { mutableIntStateOf(0) }        // px, measured by the Billboard
    var heroBlockBottom by remember { mutableIntStateOf(0) }
    var focusCandidate by remember { mutableStateOf<Anime?>(null) }
    var heroItem by remember { mutableStateOf<Anime?>(null) }
    var savedRow by rememberSaveable { mutableStateOf(-1) }
    var savedCol by rememberSaveable { mutableStateOf(-1) }
    val playFocus = remember { FocusRequester() }
    val trailerCardFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var lastKey by remember { mutableStateOf(0) }

    // Hero changes are debounced only when they follow row focus; spotlight steps land immediately.
    LaunchedEffect(heroMode, focusCandidate, featured) {
        val target = if (heroMode) featured else focusCandidate
        if (target != null && target.id != heroItem?.id) {
            if (heroItem != null && !heroMode) delay(TvTokens.HeroDebounceMs)
            heroItem = target
        }
    }

    // ---- trailer: never autoplays; the side card opens it full screen --------------------------
    var viewer by remember { mutableStateOf<Pair<Anime, String>?>(null) }
    val chrome = LocalTvChrome.current
    LaunchedEffect(heroMode) { chrome.browsing = !heroMode }
    LaunchedEffect(viewer) { chrome.fullscreen = viewer != null }
    DisposableEffect(Unit) { onDispose { chrome.browsing = false; chrome.fullscreen = false } }
    // Advance the spotlight every 9 s, but only while nobody is pressing keys on the hero.
    LaunchedEffect(heroMode, spotIndex, lastKey, viewer, spotlight.size, blocked) {
        if (!heroMode || viewer != null || blocked || spotlight.size < 2) return@LaunchedEffect
        delay(TvTokens.SpotlightMs)
        spotIndex = (spotIndex + 1) % spotlight.size
    }

    // ---- back: rows -> billboard -> rail ------------------------------------------------------
    BackHandler(enabled = backEnabled && !heroMode) {
        scope.launch {
            listState.animateScrollToItem(0)
            runCatching { playFocus.requestFocus() }
        }
    }
    BackHandler(enabled = backEnabled && heroMode) { onBackToRail() }

    // First composition: billboard Play, or the card we left from when coming back from Detail.
    LaunchedEffect(rows.isNotEmpty(), blocked) {
        if (!blocked && rows.isNotEmpty() && (heroMode || savedRow < 0)) {
            delay(60)
            runCatching { playFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(TvTokens.Canvas).onPreviewKeyEvent { lastKey++; false }) {
        val heroHistory = heroItem?.let { h -> history.firstOrNull { it.animeId == h.id } }
        Billboard(
            item = heroItem,
            heroMode = heroMode,
            playFocus = playFocus,
            trailerCardFocus = trailerCardFocus,
            history = heroHistory,
            heroNextEpisode = heroItem?.let { h -> heroHistory?.continueEpisode(latestAired[h.id])?.takeIf { it != heroHistory.episodeNumber } },
            eyebrow = heroItem?.let { h -> heroEyebrow(h, spotlight.indexOfFirst { it.id == h.id }.takeIf { heroMode }) },
            trailersEnabled = trailersEnabled,
            spotlightCount = if (heroMode) spotlight.size else 0,
            spotlightIndex = spotIndex % spotlight.size.coerceAtLeast(1),
            spotlightKey = lastKey,
            nextTitle = spotlight.getOrNull((spotIndex + 1) % spotlight.size.coerceAtLeast(1))?.takeIf { spotlight.size > 1 }?.let { it.englishTitle?.takeIf { t -> t.isNotBlank() } ?: it.title },
            onPlay = {
                val h = heroItem ?: return@Billboard
                onPlay(h.id, heroHistory?.continueEpisode(latestAired[h.id]) ?: 1)
            },
            onInfo = { heroItem?.let { onOpenDetail(it.id) } },
            onTrailer = { anime, yt -> viewer = anime to yt },
            onButtonsFocused = { heroMode = true },
            onLayout = { info, hero -> heroInfoBottom = info; heroBlockBottom = hero },
        )

        if (rows.isEmpty()) {
            EmptyOrLoading(isLoading)
        } else {
            val density = LocalDensity.current
            // Rows sit a fixed gap below whatever the hero actually occupies: its title/meta/synopsis
            // while browsing rows, plus the buttons and spotlight while the hero owns focus. So a
            // two-line title, a Resume line or a long synopsis pushes the rows down instead of
            // running under them, and a short one lets them come back up.
            val wantTop = with(density) {
                val bottom = if (heroMode) heroBlockBottom else heroInfoBottom
                maxOf(TvTokens.ListTopBrowse, bottom.toDp() + TvTokens.HeroRowGap)
            }
            val listTop by animateDpAsState(wantTop, tween(380, easing = TvEase), label = "listTop")
            val listShift = listTop - TvTokens.ListTopBrowse
            val rowSpec = remember(density) { PivotSpec(with(density) { TvTokens.RowHeader.toPx() }) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = TvTokens.ListTopBrowse)
                        .graphicsLayer { translationY = with(density) { listShift.toPx() } }
                        .focusRestorer()
                        .focusGroup(),
                    contentPadding = PaddingValues(bottom = 300.dp),
                ) {
                    itemsIndexed(rows, key = { _, r -> r.key }) { rowIndex, row ->
                        CardRow(
                            row = row,
                            rowIndex = rowIndex,
                            upTarget = if (rowIndex == 0) playFocus else null,
                            initialFocusCol = if (!heroMode && rowIndex == savedRow) savedCol else -1,
                            onCardFocused = { col, anime ->
                                heroMode = false
                                savedRow = rowIndex; savedCol = col
                                focusCandidate = anime
                            },
                            onClick = { anime ->
                                val h = row.history[anime.id]
                                if (h != null) onPlay(anime.id, h.continueEpisode(row.latest[anime.id])) else onOpenDetail(anime.id)
                            },
                        )
                    }
                }
            }
        }

        viewer?.let { (anime, yt) ->
            val clip = TrailerClip(
                animeId = anime.id, youtubeId = yt, label = "Trailer",
                meta = anime.englishTitle?.takeIf { it.isNotBlank() } ?: anime.title, isCurrent = true,
            )
            TrailerViewer(
                clips = listOf(clip),
                startIndex = 0,
                origin = TransformOrigin(0.86f, 0.5f),
                onClose = {
                    viewer = null
                    scope.launch { delay(60); runCatching { trailerCardFocus.requestFocus() } }
                },
            )
        }
    }
}

/** "#2 in Trending · Airing now" — what the hero eyebrow says about [a]. */
private fun heroEyebrow(a: Anime, spotlightRank: Int?): String {
    val status = when {
        a.comingLabel() != null -> a.comingLabel()
        a.status == "RELEASING" -> a.nextAiringEpisode?.let { "New episode ${it - 1} out" } ?: "Airing now"
        else -> listOfNotNull(a.format?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() }, a.seasonYear?.toString()).joinToString(" · ").ifBlank { null }
    }
    return listOfNotNull(spotlightRank?.takeIf { it >= 0 }?.let { "#${it + 1} in Trending" }, status).joinToString(" · ")
}

/** Keeps the focused child at a fixed offset from the start of the viewport (Netflix-style pivot). */
@OptIn(ExperimentalFoundationApi::class)
internal class PivotSpec(private val pivotPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - pivotPx
}

// ================================================================================================
// Billboard
// ================================================================================================

@Composable
private fun Billboard(
    item: Anime?,
    heroMode: Boolean,
    playFocus: FocusRequester,
    trailerCardFocus: FocusRequester,
    history: WatchHistoryEntry?,
    /** Set when you finished your episode and the next one is out: the hero offers that one. */
    heroNextEpisode: Int?,
    eyebrow: String?,
    trailersEnabled: Boolean,
    spotlightCount: Int,
    spotlightIndex: Int,
    spotlightKey: Int,
    nextTitle: String?,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onTrailer: (Anime, String) -> Unit,
    onButtonsFocused: () -> Unit,
    onLayout: (infoBottomPx: Int, heroBottomPx: Int) -> Unit,
) {
    var infoBottom by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artW = maxWidth * 0.74f
        val artH = artW * 9f / 16f

        // Art only — the trailer never plays here. Cross-dissolve between titles, slow push-in,
        // a grain tile against banding, and scrims that melt it into the canvas.
        Box(Modifier.align(Alignment.TopEnd).width(artW).height(artH).clipToBounds()) {
            AnimatedContent(
                targetState = item,
                contentKey = { it?.id },
                transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
                label = "backdrop",
                modifier = Modifier.fillMaxSize()
            ) { anime ->
                if (anime != null) KenBurnsImage(anime)
            }
            Box(Modifier.fillMaxSize().tvGrain())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvTokens.Canvas, 0.42f to TvTokens.Canvas.copy(alpha = 0.55f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to TvTokens.Canvas)))
        }

        // Info: crossfades + rises with each new title; the title itself wipes in.
        val infoTopPx = with(LocalDensity.current) { TvTokens.HeroInfoTop.roundToPx() }
        Column(
            Modifier
                // Measured before the padding so the height is the block's bottom edge from the top.
                .onGloballyPositioned { onLayout(infoTopPx + infoBottom, it.size.height) }
                .padding(start = TvTokens.ContentStart, top = TvTokens.HeroInfoTop)
                .width(470.dp)
        ) {
            AnimatedContent(
                targetState = item,
                contentKey = { it?.id },
                transitionSpec = {
                    (fadeIn(tween(420, delayMillis = 120)) + slideInVertically(tween(480, delayMillis = 120, easing = TvEase)) { it / 12 }) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 16 })
                },
                label = "heroInfo",
                modifier = Modifier.onGloballyPositioned { infoBottom = it.size.height },
            ) { anime ->
                if (anime != null) HeroInfo(anime, eyebrow) else Spacer(Modifier.height(180.dp))
            }

            // Always composed (only faded) so UP from the first row has somewhere to land and
            // playFocus stays attached.
            val buttonsAlpha by animateFloatAsState(if (heroMode) 1f else 0f, tween(260), label = "btnAlpha")
            val progress = history?.takeIf { it.durationMs > 0 && heroNextEpisode == null }?.let { (it.progressMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
            Row(
                Modifier
                    .padding(top = 18.dp)
                    .graphicsLayer { alpha = buttonsAlpha; translationY = (1f - buttonsAlpha) * 24f }
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroButton(
                    when {
                        heroNextEpisode != null -> "Play new E$heroNextEpisode"
                        history != null -> "Resume E${history.episodeNumber}"
                        else -> "Play"
                    }, Icons.Default.PlayArrow, primary = true,
                    progress = progress, onClick = onPlay, onFocused = onButtonsFocused,
                    modifier = Modifier.primaryFocus(playFocus)
                )
                HeroButton("More info", Icons.Default.Info, primary = false, onClick = onInfo, onFocused = onButtonsFocused)
            }
            if (history != null && history.durationMs > 0 && heroNextEpisode == null) {
                val minsLeft = ((history.durationMs - history.progressMs) / 60_000).coerceAtLeast(0)
                Text(
                    "$minsLeft min left in episode ${history.episodeNumber}",
                    color = TvTokens.TextTertiary, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp).graphicsLayer { alpha = buttonsAlpha }
                )
            }
            if (spotlightCount > 1) {
                SpotlightSegments(spotlightCount, spotlightIndex, spotlightKey, nextTitle, Modifier.padding(top = 18.dp).graphicsLayer { alpha = buttonsAlpha })
            }
        }

        // Side "Watch trailer" card: the only way a trailer plays on Home.
        if (trailersEnabled && item != null) {
            val primary = remember(item.id) { TrailerHost.youtubeId(item.trailerUrl) }
            var yt by remember(item.id) { mutableStateOf(primary ?: MediaExtras.cached(item.id)?.youtubeId) }
            LaunchedEffect(item.id) { if (yt == null) yt = MediaExtras.get(item.id)?.youtubeId }
            val id = yt
            val cardA by animateFloatAsState(if (heroMode && id != null) 1f else 0f, tween(300), label = "trailerCardA")
            if (id != null) {
                TrailerCard(
                    youtubeId = id,
                    title = item.englishTitle?.takeIf { it.isNotBlank() } ?: item.title,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 214.dp, end = 48.dp)
                        .graphicsLayer { alpha = cardA }
                        .focusRequester(trailerCardFocus),
                    onFocused = onButtonsFocused,
                    onClick = { onTrailer(item, id) },
                )
            }
        }
    }
}

/** Thin segments under the hero buttons; the current one fills over the spotlight interval. */
@Composable
private fun SpotlightSegments(count: Int, index: Int, resetKey: Int, nextTitle: String?, modifier: Modifier) {
    val fill = remember(index, resetKey) { Animatable(0f) }
    LaunchedEffect(index, resetKey) { fill.animateTo(1f, tween(TvTokens.SpotlightMs.toInt(), easing = LinearEasing)) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(Modifier.padding(end = 6.dp).width(if (i == index) 34.dp else 18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = if (i < index) 0.55f else 0.18f))) {
                if (i == index) Box(Modifier.fillMaxHeight().fillMaxWidth(fill.value).background(TvTokens.Focus))
            }
        }
        if (nextTitle != null) {
            Text("Next · $nextTitle", color = TvTokens.TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).width(240.dp))
        }
    }
}

@Composable
private fun TrailerCard(youtubeId: String, title: String, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, TvTokens.FocusSpring, label = "tcScale")
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "tcRing")
    val shape = RoundedCornerShape(10.dp)
    // Play ripple runs only while focused, so the hero is still when you're not looking at it.
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        if (!focused) { ripple.snapTo(0f); return@LaunchedEffect }
        while (true) { ripple.snapTo(0f); ripple.animateTo(1f, tween(1400, easing = LinearEasing)) }
    }
    Column(
        modifier
            .width(210.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusRing({ ring }, 10.dp).clip(shape).background(TvTokens.Surface)) {
            val zoom by animateFloatAsState(if (focused) 1.08f else 1f, tween(2400, easing = TvEase), label = "tcZoom")
            AsyncImage(
                model = ImageRequest.Builder(context).data("https://i.ytimg.com/vi/$youtubeId/mqdefault.jpg").size(420, 236).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            Box(Modifier.align(Alignment.Center).size(40.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer { val s = 1f + ripple.value * 0.8f; scaleX = s; scaleY = s; alpha = (1f - ripple.value) * 0.7f }
                        .border(2.dp, TvTokens.Focus, CircleShape)
                )
                Box(
                    Modifier.fillMaxSize().clip(CircleShape)
                        .background(if (focused) TvTokens.Focus else Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Watch trailer", tint = if (focused) TvTokens.OnFocus else TvTokens.TextPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
        Text("Watch trailer", color = TvTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(title, color = TvTokens.TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun KenBurnsImage(anime: Anime) {
    val context = LocalContext.current
    // A single 14 s push-in per title, then it stops — the frame pipeline goes idle again
    // instead of redrawing a full-screen bitmap forever.
    val zoom = remember(anime.id) { Animatable(1f) }
    LaunchedEffect(anime.id) { zoom.animateTo(1.07f, tween(14_000, easing = LinearEasing)) }

    // Wide art only: AniList's banner, else Kitsu's cover (asked for only when the banner is
    // missing or fails to load). With no wide art left the poster is staged instead of cropped to
    // 16:9 — a 2:3 poster blown up across the hero is what used to look blurry.
    val failed = remember(anime.id) { mutableStateListOf<String>() }
    val banner = anime.bannerImage
    val bannerOk = banner != null && banner !in failed
    var kitsuCover by remember(anime.id) { mutableStateOf(MediaExtras.cached(anime.id)?.cover) }
    LaunchedEffect(anime.id, bannerOk) {
        if (!bannerOk && kitsuCover == null) kitsuCover = MediaExtras.get(anime.id)?.cover
    }
    val wide = listOfNotNull(banner, kitsuCover).firstOrNull { it !in failed }

    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }) {
        // The stage stays underneath a late-arriving Kitsu cover so it fades in over it, not over black.
        if (wide == null || wide == kitsuCover) PosterStage(anime.coverImage)
        if (wide != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(wide).size(1280, 720).crossfade(wide == kitsuCover).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed += wide },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Hero art for titles with no wide image: the poster as an ambient wash, the sharp poster set in at
 * the right. The wash is a ~24x36 decode upscaled with bilinear filtering — a blur that costs
 * nothing on any API level (Modifier.blur is a no-op below Android 12, i.e. on most TVs).
 */
@Composable
private fun PosterStage(cover: String?) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize().background(TvTokens.Canvas)) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(cover).size(24, 36)
                // Own memory-cache key, or Coil may hand back the sharp poster it already holds.
                .memoryCacheKey("wash:$cover")
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.8f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.5f)))
            )
        )
        val shape = RoundedCornerShape(10.dp)
        AsyncImage(
            model = ImageRequest.Builder(context).data(cover).size(400, 600).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = maxHeight * 0.08f, end = maxWidth * 0.08f)
                .fillMaxHeight(0.64f)
                .aspectRatio(2f / 3f)
                .shadow(28.dp, shape, ambientColor = TvTokens.AccentGlow, spotColor = TvTokens.AccentGlow)
                .clip(shape)
        )
    }
}

@Composable
private fun HeroInfo(anime: Anime, eyebrow: String?) {
    // Title wipes in left to right; the eyebrow's lead line grows first.
    val wipe = rememberEntrance(anime.id, durationMs = 720, delayMs = 160)
    val line = rememberEntrance(anime.id, durationMs = 480)
    Column {
        if (!eyebrow.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.width(22.dp * line.value).height(2.dp).background(TvTokens.AccentGlow))
                Text(eyebrow, color = TvTokens.AccentPale, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp), maxLines = 1)
            }
        }
        Text(
            text = anime.englishTitle?.takeIf { it.isNotBlank() } ?: anime.title,
            color = TvTokens.TextPrimary,
            fontSize = TvTokens.HeroTitle,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 44.sp,
            letterSpacing = (-0.8).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.wipeReveal { wipe.value }
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            anime.averageScore?.let { RatingChip(it) }
            val coming = anime.comingLabel()
            if (coming == null) anime.seasonYear?.let { Text("$it", color = TvTokens.TextSecondary, fontSize = TvTokens.HeroMeta) }
            anime.format?.let { Text(it.replace('_', ' '), color = TvTokens.TextSecondary, fontSize = TvTokens.HeroMeta) }
            anime.episodeLabel()?.let { Text(it, color = TvTokens.TextSecondary, fontSize = TvTokens.HeroMeta) }
            if (anime.genres.isNotEmpty()) {
                Text(anime.genres.take(2).joinToString(" · "), color = TvTokens.TextSecondary, fontSize = TvTokens.HeroMeta, maxLines = 1)
            }
        }
        anime.description?.let { raw ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = remember(raw) { stripHtml(raw) },
                color = TvTokens.TextSecondary,
                fontSize = TvTokens.HeroBody,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** AniList's 0-100 score shown as "★ 8.6" in a soft chip. */
@Composable
internal fun RatingChip(score: Int) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("★", color = TvTokens.AccentPale, fontSize = 12.sp)
        Text(String.format(java.util.Locale.US, "%.1f", score / 10f), color = TvTokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun HeroButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, TvTokens.FocusSpring, label = "btnScale")
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "btnRing")
    // Progress draws itself along the bottom edge of the Resume button.
    val fill = remember(progress) { Animatable(0f) }
    LaunchedEffect(progress) { if (progress != null) fill.animateTo(progress, tween(900, delayMillis = 300, easing = TvEase)) }
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        primary || focused -> TvTokens.Focus
        else -> Color.White.copy(alpha = 0.14f)
    }
    val fg = if (focused || primary) TvTokens.OnFocus else TvTokens.TextPrimary
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .tvFocusRing({ ring }, 10.dp)
            .clip(shape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (progress != null) {
            // matchParentSize: the track follows the label's width instead of stretching the button.
            Box(Modifier.matchParentSize()) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(TvTokens.OnFocus.copy(alpha = 0.12f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(fill.value).height(3.dp).background(TvTokens.Accent))
            }
        }
    }
}

// ================================================================================================
// Rows and cards
// ================================================================================================

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun CardRow(
    row: HomeRow,
    rowIndex: Int,
    upTarget: FocusRequester?,
    initialFocusCol: Int,
    onCardFocused: (Int, Anime) -> Unit,
    onClick: (Anime) -> Unit,
) {
    // First appearance: rows rise in one after another.
    val enter = rememberEntrance(row.key, durationMs = 560, delayMs = 80 + 70 * rowIndex.coerceAtMost(4))
    Column(
        Modifier.fillMaxWidth().padding(bottom = 4.dp)
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 28.dp.toPx() }
    ) {
        Text(
            row.title,
            color = TvTokens.TextPrimary,
            fontSize = TvTokens.RowTitle,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
            modifier = Modifier.padding(start = TvTokens.ContentStart, bottom = 2.dp)
        )
        val rowState = rememberLazyListState()
        val startPx = with(LocalDensity.current) { TvTokens.ContentStart.toPx() }
        val pivot = remember(startPx) { PivotSpec(startPx) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides pivot) {
        LazyRow(
            state = rowState,
            modifier = Modifier
                .fillMaxWidth()
                // Cards that have scrolled past the left margin fade out instead of being cut off
                // at the screen edge.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        // Clear up to just past the previous card's right edge (start - gap), opaque
                        // just before the focused card, whose scale and ring reach a few dp left.
                        Brush.horizontalGradient(
                            0f to Color.Transparent, 1f to Color.Black,
                            startX = startPx - TvTokens.CardGap.toPx() + 2.dp.toPx(), endX = startPx - 8.dp.toPx(),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .focusProperties { if (upTarget != null) up = upTarget }
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(start = TvTokens.ContentStart, end = 400.dp, top = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(TvTokens.CardGap),
        ) {
            itemsIndexed(row.items, key = { _, a -> a.id }) { col, anime ->
                val h = row.history[anime.id]
                val newEps = h?.newEpisodes(row.latest[anime.id]) ?: 0
                ExpandingCard(
                    anime = anime,
                    rank = if (row.ranked) col + 1 else null,
                    progress = h?.let { if (it.durationMs > 0) (it.progressMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else null },
                    resumeLabel = h?.let { "E${it.episodeNumber}" },
                    caption = h?.let { e ->
                        newEpisodesLabel(newEps)?.let { it to true } ?: run {
                            val left = ((e.durationMs - e.progressMs) / 60_000).coerceAtLeast(0)
                            (if (e.durationMs > 0 && !e.finished) "E${e.episodeNumber} · ${left}m left" else "E${e.episodeNumber}") to false
                        }
                    },
                    isNew = newEps > 0,
                    requestFocusOnStart = col == initialFocusCol,
                    onFocused = { onCardFocused(col, anime) },
                    onClick = { onClick(anime) },
                )
            }
        }
        }
    }
}

@Composable
private fun ExpandingCard(
    anime: Anime,
    rank: Int?,
    progress: Float?,
    resumeLabel: String?,
    /** Continue Watching line under the progress bar; second = highlight it (new episode out). */
    caption: Pair<String, Boolean>? = null,
    isNew: Boolean = false,
    requestFocusOnStart: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focused) {
        if (focused) { delay(TvTokens.ExpandDwellMs); expanded = true } else expanded = false
    }
    LaunchedEffect(Unit) {
        if (requestFocusOnStart) { delay(80); runCatching { focusRequester.requestFocus() } }
    }
    LaunchedEffect(pressed) { if (pressed) { delay(110); pressed = false } }
    val scale by animateFloatAsState(
        when { pressed -> 0.97f; focused -> TvTokens.FocusScale; else -> 1f }, TvTokens.FocusSpring, label = "cardScale"
    )
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "cardRing")
    val width by animateDpAsState(if (expanded) TvTokens.ExpandedWidth else TvTokens.PosterWidth, TvTokens.ExpandSpring, label = "cardWidth")
    val shape = RoundedCornerShape(TvTokens.CardRadius)
    // Continue-watching bars fill in when the row first appears.
    val fill = remember(anime.id) { Animatable(0f) }
    LaunchedEffect(anime.id, progress) { if (progress != null) fill.animateTo(progress, tween(900, delayMillis = 350, easing = TvEase)) }

    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.zIndex(if (focused) 1f else 0f)) {
        if (rank != null) RankNumeral(rank, focused)
        // The card and its Continue Watching bar scale together, so the bar never tucks under it.
        Column(Modifier.width(width).graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0.5f, 0.4f) }) {
        Box(
            Modifier
                .width(width)
                .height(TvTokens.CardHeight)
                .tvFocusRing({ ring }, TvTokens.CardRadius)
                .clip(shape)
                .background(TvTokens.Surface)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { pressed = true; onClick() }
        ) {
            val wide = anime.bannerImage
            if (expanded && wide != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(wide).size(600, 336).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.92f))))
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(anime.coverImage).size(224, 336).crossfade(true).build(),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(TvTokens.PosterWidth).fillMaxHeight()
                )
                if (expanded) {
                    Box(
                        Modifier.fillMaxSize().padding(start = TvTokens.PosterWidth)
                            .background(Brush.horizontalGradient(0f to TvTokens.SurfaceHigh, 1f to TvTokens.Surface))
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(260, delayMillis = 140)) + slideInVertically(tween(320, delayMillis = 140, easing = TvEase)) { it / 4 },
                exit = fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = if (wide != null) 12.dp else TvTokens.PosterWidth + 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                CardDetails(anime, resumeLabel)
            }

            if (isNew && !expanded) {
                Text(
                    "NEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(TvTokens.Accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            if (resumeLabel != null && !expanded) {
                Text(
                    resumeLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        if (progress != null || caption != null) {
            Box(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (isNew) 1f else fill.value).background(if (isNew) TvTokens.AccentGlow else TvTokens.Accent))
            }
            caption?.let { (text, hot) ->
                Text(
                    text, color = if (hot) TvTokens.AccentGlow else TvTokens.TextTertiary,
                    fontSize = 11.sp, fontWeight = if (hot) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun CardDetails(anime: Anime, resumeLabel: String?) {
    Column {
        Text(
            anime.englishTitle?.takeIf { it.isNotBlank() } ?: anime.title,
            color = TvTokens.TextPrimary, fontSize = TvTokens.CardTitle, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            anime.averageScore?.let { Text("★ " + String.format(java.util.Locale.US, "%.1f", it / 10f), color = TvTokens.Score, fontSize = TvTokens.CardMeta, fontWeight = FontWeight.Bold) }
            anime.episodeLabel(short = true)?.let { Text(it, color = TvTokens.TextSecondary, fontSize = TvTokens.CardMeta) }
            anime.seasonYear?.let { Text("$it", color = TvTokens.TextSecondary, fontSize = TvTokens.CardMeta) }
            if (resumeLabel != null) Text("Resume $resumeLabel", color = TvTokens.AccentGlow, fontSize = TvTokens.CardMeta, fontWeight = FontWeight.Bold)
        }
        if (anime.genres.isNotEmpty()) {
            Text(anime.genres.take(3).joinToString(" · "), color = TvTokens.TextTertiary, fontSize = TvTokens.CardMeta, maxLines = 1)
        }
    }
}

/** Outlined Top-10 numeral; it lifts and brightens while its card has focus. */
@Composable
private fun RankNumeral(rank: Int, focused: Boolean) {
    val lift by animateFloatAsState(if (focused) 1f else 0f, TvTokens.FocusSpring, label = "rankLift")
    Box(Modifier.width(if (rank >= 10) 92.dp else 60.dp).height(TvTokens.CardHeight), contentAlignment = Alignment.BottomEnd) {
        Text(
            "$rank",
            style = TextStyle(
                fontSize = 124.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-8).sp,
                color = TvTokens.AccentPale.copy(alpha = 0.45f + 0.5f * lift),
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
            ),
            modifier = Modifier.offset(x = 14.dp, y = 22.dp).graphicsLayer { translationY = -10.dp.toPx() * lift }
        )
    }
}

@Composable
private fun EmptyOrLoading(isLoading: Boolean) {
    Box(Modifier.fillMaxSize().padding(top = TvTokens.ListTopBrowse + 100.dp, start = TvTokens.ContentStart)) {
        if (isLoading) {
            val pulse by rememberShimmer()
            Row(horizontalArrangement = Arrangement.spacedBy(TvTokens.CardGap)) {
                repeat(7) { i ->
                    Box(
                        Modifier.width(TvTokens.PosterWidth).height(TvTokens.CardHeight)
                            .graphicsLayer { alpha = (pulse - i * 0.04f).coerceIn(0.2f, 1f) }
                            .clip(RoundedCornerShape(TvTokens.CardRadius)).background(TvTokens.SurfaceHigh)
                    )
                }
            }
        } else {
            Text(
                "Couldn't load anything right now. Check the connection — the home refreshes on its own.",
                color = TvTokens.TextSecondary, fontSize = 16.sp
            )
        }
    }
}

private val htmlTag = Regex("<[^>]+>")
internal fun stripHtml(s: String): String =
    s.replace("<br>", " ").replace(htmlTag, "").replace("&quot;", "\"").replace("&amp;", "&")
        .replace("&#039;", "'").replace(Regex("\\s+"), " ").trim()
