@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.aniflow.ui.tv.detail

import com.example.aniflow.ui.tv.home.episodeLabel
import com.example.aniflow.ui.tv.home.comingLabel
import com.example.aniflow.ui.detail.franchiseRows
import com.example.aniflow.data.model.FranchiseEntry
import com.example.aniflow.ui.focus.primaryFocus
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.aniflow.data.ProviderMappingStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.Episode
import com.example.aniflow.data.model.ProviderSearchResult
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.model.formatAiringSchedule
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.ui.detail.DetailUiState
import com.example.aniflow.ui.detail.DetailViewModel
import com.example.aniflow.ui.tv.home.KenBurnsImage
import com.example.aniflow.ui.tv.home.PivotSpec
import com.example.aniflow.ui.tv.home.TvTokens
import com.example.aniflow.ui.tv.home.stripHtml
import kotlinx.coroutines.launch
import com.example.aniflow.ui.tv.home.RatingChip
import com.example.aniflow.ui.tv.home.TrailerViewer
import com.example.aniflow.ui.tv.home.TvEase
import com.example.aniflow.ui.tv.home.rememberEntrance
import com.example.aniflow.ui.tv.home.tvFocusRing
import com.example.aniflow.ui.tv.home.tvGrain
import com.example.aniflow.ui.tv.home.wipeReveal
import com.example.aniflow.data.model.TrailerClip
import androidx.compose.ui.graphics.TransformOrigin

private val Start = 56.dp              // no rail on this screen
private const val RANGE = 50            // episodes per range tab: 1,180 eps -> 24 tabs, each a lazy row

/**
 * TV detail, built for a remote:
 *  - the hero renders as soon as metadata arrives; episodes resolve underneath with their own
 *    loading / error / not-found / "which one?" states, each with a focusable way forward;
 *  - episodes are a lazy row per 50-episode range (One Piece used to compose 1,180 cards in a
 *    non-lazy grid), so every episode is reachable in a handful of presses;
 *  - focus lands on Play on entry and returns to the episode you played when you come back.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvDetailScreen(
    animeId: Int,
    repository: AnimeRepository,
    watchlistStore: WatchlistStore,
    watchHistoryStore: WatchHistoryStore,
    onPlay: (episode: Int) -> Unit,
    onAnimeClick: (Int) -> Unit,
    onSwitchAnime: (Int) -> Unit = onAnimeClick,
) {
    val context = LocalContext.current
    val vm: DetailViewModel = viewModel {
        DetailViewModel(repository, ProviderMappingStore(context.applicationContext), watchHistoryStore)
    }
    // Coming back from the player re-enters composition; don't reload (it would reset focus).
    LaunchedEffect(animeId) { if (vm.anime.value?.id != animeId) vm.loadAnimeDetails(animeId) }

    val anime by vm.anime.collectAsState()
    val state by vm.uiState.collectAsState()
    val history by vm.watchHistoryEntry.collectAsState()
    val franchise by vm.franchise.collectAsState()
    val bookmarked by watchlistStore.isBookmarkedFlow(animeId).collectAsState(initial = false)
    val trailers by vm.trailers.collectAsState()
    val scope = rememberCoroutineScope()

    val a = anime
    Box(Modifier.fillMaxSize().background(TvTokens.Canvas)) {
        if (a == null) {
            val s = state
            if (s is DetailUiState.Error) {
                CenterMessage("Couldn't open this anime", s.message, onRetry = vm::retry)
            } else {
                HeroSkeleton()
            }
            return@Box
        }

        val episodes = (state as? DetailUiState.Success)?.episodes.orEmpty()
        val listState = rememberLazyListState()
        val playFocus = remember { FocusRequester() }
        val targetFocus = remember { FocusRequester() }
        var lowerFocused by remember { mutableStateOf(false) }
        var resumeTarget by rememberSaveable { mutableStateOf<Int?>(null) }
        var selectedRange by rememberSaveable { mutableStateOf(-1) }
        val ranges = remember(episodes) { episodes.chunked(RANGE) }
        val resumeEp = history?.episodeNumber?.takeIf { n -> episodes.isEmpty() || episodes.any { it.number == n } }
        val rangeRowState = rememberLazyListState()
        val epRowState = rememberLazyListState()
        // Trailer viewer: index into [trailers] while open, -1 when closed.
        var viewerAt by remember { mutableStateOf(-1) }
        var trailerReturn by remember { mutableStateOf(-1) }
        val trailerFocus = remember { FocusRequester() }
        val trailerButtonFocus = remember { FocusRequester() }

        // Pick the range holding the episode the viewer is on, once episodes are known.
        LaunchedEffect(ranges) {
            if (ranges.isNotEmpty() && selectedRange !in ranges.indices) {
                val want = resumeTarget ?: resumeEp
                selectedRange = ranges.indexOfFirst { r -> r.any { it.number == want } }.coerceAtLeast(0)
            }
        }

        // Initial focus: Play. After returning from the player: the episode that was played.
        LaunchedEffect(a.id, episodes.isNotEmpty()) {
            val t = resumeTarget
            val r = ranges.indexOfFirst { rg -> rg.any { it.number == t } }
            if (t != null && r >= 0) {
                selectedRange = r
                listState.scrollToItem(1)
                withFrameNanos { }
                epRowState.scrollToItem(ranges[r].indexOfFirst { it.number == t }.coerceAtLeast(0))
                withFrameNanos { }; withFrameNanos { }
                if (runCatching { targetFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
            if (!lowerFocused) { withFrameNanos { }; runCatching { playFocus.requestFocus() } }
        }

        // BACK from the lower sections returns to the top first, like the home screen.
        BackHandler(enabled = lowerFocused) {
            scope.launch { listState.animateScrollToItem(0) }
            runCatching { playFocus.requestFocus() }
        }

        val backdropAlpha by animateFloatAsState(if (lowerFocused) 0.32f else 1f, tween(450), label = "backdrop")
        Backdrop(a, backdropAlpha)

        val density = LocalDensity.current
        val keepSpec = remember(density) { KeepVisibleSpec(with(density) { 150.dp.toPx() }) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides keepSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 64.dp),
            ) {
                item("hero") {
                    Hero(
                        anime = a,
                        history = history,
                        resumeEp = resumeEp,
                        bookmarked = bookmarked,
                        playFocus = playFocus,
                        onFocused = { scope.launch { listState.animateScrollToItem(0) } },
                        onPlay = {
                            val ep = resumeEp ?: episodes.firstOrNull()?.number ?: 1
                            resumeTarget = ep
                            onPlay(ep)
                        },
                        trailerButtonFocus = trailerButtonFocus,
                        onTrailer = if (trailers.isEmpty()) null else {
                            { trailerReturn = -1; viewerAt = trailers.indexOfFirst { it.isCurrent }.coerceAtLeast(0) }
                        },
                        onToggleList = {
                            scope.launch {
                                if (bookmarked) watchlistStore.removeFromWatchlist(a.id) else watchlistStore.addToWatchlist(a)
                            }
                        },
                    )
                }
                item("episodes") {
                    Column(Modifier.onFocusChanged { lowerFocused = it.hasFocus || lowerFocusedOther(listState) }) {
                        SectionHeader("Episodes", episodes.size.takeIf { it > 0 }?.let { "$it" })
                        when (val s = state) {
                            is DetailUiState.Loading -> EpisodeSkeletonRow()
                            is DetailUiState.Success -> {
                                if (ranges.size > 1) {
                                    RangeTabs(ranges, selectedRange.coerceAtLeast(0), rangeRowState) { selectedRange = it }
                                }
                                val rangeIdx = selectedRange.coerceIn(0, (ranges.size - 1).coerceAtLeast(0))
                                EpisodeRow(
                                    episodes = ranges.getOrElse(rangeIdx) { emptyList() },
                                    rangeKey = rangeIdx,
                                    anime = a,
                                    history = history,
                                    rowState = epRowState,
                                    focusTarget = resumeTarget,
                                    targetFocus = targetFocus,
                                    onClick = { ep -> resumeTarget = ep.number; onPlay(ep.number) },
                                )
                            }
                            is DetailUiState.Ambiguous -> PickSeries(s.candidates) { vm.confirmMapping(a, it) }
                            is DetailUiState.Empty, is DetailUiState.NotFound -> InlineMessage(
                                "Not on the streaming server yet",
                                "AniLight hasn't listed episodes for this title. New and upcoming shows appear once they air.",
                                onRetry = vm::retry,
                            )
                            is DetailUiState.Error -> InlineMessage("Couldn't load episodes", s.message, onRetry = vm::retry)
                        }
                    }
                }
                if (trailers.isNotEmpty()) {
                    item("trailers") {
                        Column(Modifier.padding(top = 28.dp)) {
                            SectionHeader("Trailers", "${trailers.size}")
                            LazyRow(
                                contentPadding = PaddingValues(start = Start, end = 120.dp, top = 8.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.focusRestorer().focusGroup(),
                            ) {
                                itemsIndexed(trailers, key = { _, c -> "${c.animeId}:${c.youtubeId}" }) { i, clip ->
                                    DetailTrailerCard(
                                        clip = clip,
                                        modifier = if (i == trailerReturn) Modifier.focusRequester(trailerFocus) else Modifier,
                                        onClick = { trailerReturn = i; viewerAt = i },
                                    )
                                }
                            }
                        }
                    }
                }
                franchiseRows(franchise).forEachIndexed { i, (title, entries) ->
                    item("franchise$i") {
                        Column(Modifier.padding(top = 28.dp).onFocusChanged { if (it.hasFocus) lowerFocused = true }) {
                            SectionHeader(title, entries.size.toString())
                            FranchiseRow(entries, currentId = a.id) { e ->
                                if (e.id == a.id) {
                                    scope.launch { listState.animateScrollToItem(0) }
                                    runCatching { playFocus.requestFocus() }
                                } else onSwitchAnime(e.id)
                            }
                        }
                    }
                }
                if (a.recommendations.isNotEmpty()) {
                    item("more") {
                        Column(Modifier.padding(top = 28.dp).onFocusChanged { if (it.hasFocus) lowerFocused = true }) {
                            SectionHeader("More Like This", null)
                            val allHistory by vm.allHistory.collectAsState()
                            val ranked = remember(a, franchise, allHistory) {
                                val finished = allHistory.filter { h ->
                                    val total = a.recommendations.firstOrNull { it.id == h.animeId }?.episodes
                                    com.example.aniflow.data.Suggestions.isFinished(h, total)
                                }.map { it.animeId }.toSet()
                                com.example.aniflow.data.Suggestions.moreLikeThis(a, franchise.map { it.id }.toSet(), finished)
                            }
                            MoreLikeThis(ranked, onAnimeClick)
                        }
                    }
                }
            }
        }

        if (viewerAt >= 0 && trailers.isNotEmpty()) {
            TrailerViewer(
                clips = trailers,
                startIndex = viewerAt.coerceAtMost(trailers.lastIndex),
                origin = if (trailerReturn >= 0) TransformOrigin(0.3f, 0.75f) else TransformOrigin(0.25f, 0.6f),
                onClose = {
                    viewerAt = -1
                    scope.launch {
                        kotlinx.coroutines.delay(60)
                        runCatching { if (trailerReturn >= 0) trailerFocus.requestFocus() else trailerButtonFocus.requestFocus() }
                    }
                },
            )
        }
    }
}

// The hero sits above everything else; any focus outside it counts as "lower".
private fun lowerFocusedOther(listState: androidx.compose.foundation.lazy.LazyListState) =
    listState.firstVisibleItemIndex > 0

/** No scroll while the focused thing is already comfortably on screen; otherwise park it at [topPx]. */
private class KeepVisibleSpec(private val topPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (offset >= topPx * 0.5f && offset + size <= containerSize - topPx * 0.2f) 0f else offset - topPx
}

// ================================================================================================
// Hero

@Composable
private fun Backdrop(anime: Anime, alpha: Float) {
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.74f).aspectRatio(16f / 9f).clipToBounds()) {
            KenBurnsImage(anime)
            Box(Modifier.fillMaxSize().tvGrain())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvTokens.Canvas, 0.45f to TvTokens.Canvas.copy(alpha = 0.55f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to TvTokens.Canvas)))
        }
    }
}

@Composable
private fun Hero(
    anime: Anime,
    history: WatchHistoryEntry?,
    resumeEp: Int?,
    bookmarked: Boolean,
    playFocus: FocusRequester,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    trailerButtonFocus: FocusRequester,
    onTrailer: (() -> Unit)?,
    onToggleList: () -> Unit,
) {
    // Arrives with a short rise + fade the first time the metadata lands.
    val enter = remember(anime.id) { Animatable(0f) }
    LaunchedEffect(anime.id) { enter.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(410.dp)
            .padding(start = Start, top = 44.dp)
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 24.dp.toPx() }
    ) {
        val wipe = rememberEntrance(anime.id, durationMs = 720, delayMs = 200)
        Text(
            anime.englishTitle?.takeIf { it.isNotBlank() } ?: anime.title,
            color = TvTokens.TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold,
            lineHeight = 42.sp, letterSpacing = (-0.8).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(560.dp).wipeReveal { wipe.value }
        )
        Spacer(Modifier.height(10.dp))
        MetaRow(anime)
        if (anime.genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(anime.genres.take(4).joinToString("  ·  "), color = TvTokens.TextSecondary, fontSize = TvTokens.HeroMeta)
        }
        anime.description?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                stripHtml(it), color = TvTokens.TextSecondary, fontSize = TvTokens.HeroBody, lineHeight = 20.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(520.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.onFocusChanged { if (it.hasFocus) onFocused() }.focusRestorer().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvButton(
                label = if (resumeEp != null) "Resume  E$resumeEp" else "Play",
                icon = Icons.Default.PlayArrow,
                primary = true,
                modifier = Modifier.primaryFocus(playFocus),
                onClick = onPlay,
            )
            TvButton(
                label = if (bookmarked) "In My List" else "My List",
                icon = if (bookmarked) Icons.Default.Check else Icons.Default.Add,
                primary = false,
                onClick = onToggleList,
            )
            if (onTrailer != null) {
                TvButton(label = "Trailers", icon = null, primary = false, modifier = Modifier.focusRequester(trailerButtonFocus), onClick = onTrailer)
            }
        }
        if (history != null && history.durationMs > 0) {
            Spacer(Modifier.height(12.dp))
            val p = (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
            val minsLeft = ((history.durationMs - history.progressMs) / 60_000).coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(140.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(TvTokens.Accent))
                }
                Spacer(Modifier.width(10.dp))
                Text("E${history.episodeNumber}  ·  ${minsLeft}m left", color = TvTokens.TextTertiary, fontSize = TvTokens.CardMeta)
            }
        }
    }
}

@Composable
private fun MetaRow(anime: Anime) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        anime.averageScore?.let { RatingChip(it) }
        anime.seasonYear?.let { Text("$it", color = TvTokens.TextSecondary, fontSize = TvTokens.HeroMeta) }
        anime.format?.let {
            Text(
                it.replace('_', ' '), color = TvTokens.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.border(1.dp, TvTokens.TextTertiary, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        val airing = anime.status == "RELEASING" && anime.nextAiringEpisode != null
        when {
            airing -> {
                Text(
                    "AIRING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(TvTokens.Accent).padding(horizontal = 6.dp, vertical = 1.dp)
                )
                val next = anime.nextAiringAt?.let { "  ·  EP ${anime.nextAiringEpisode} ${formatAiringSchedule(it)}" } ?: ""
                Text("EP ${(anime.nextAiringEpisode ?: 1) - 1} out$next", color = TvTokens.TextSecondary, fontSize = TvTokens.HeroMeta)
            }
            anime.episodeLabel() != null -> Text(anime.episodeLabel()!!, color = TvTokens.TextSecondary, fontSize = TvTokens.HeroMeta)
            anime.comingLabel() != null -> Text(anime.comingLabel()!!, color = TvTokens.AccentGlow, fontSize = TvTokens.HeroMeta, fontWeight = FontWeight.Bold)
        }
        anime.studioName?.let { Text(it, color = TvTokens.TextTertiary, fontSize = TvTokens.HeroMeta, maxLines = 1) }
    }
}

@Composable
private fun TvButton(label: String, icon: ImageVector?, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, TvTokens.FocusSpring, label = "btnScale")
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "btnRing")
    val bg = when {
        focused || primary -> TvTokens.Focus
        else -> Color.White.copy(alpha = 0.12f)
    }
    val fg = if (focused || primary) TvTokens.OnFocus else TvTokens.TextPrimary
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .tvFocusRing({ ring }, 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            // onFocusChanged sits outside clickable so it observes clickable's own focus target:
            // one element, one focus stop (the old `.clickable().focusable()` made two).
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ================================================================================================
// Episodes

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(Modifier.padding(start = Start, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = TvTokens.TextPrimary, fontSize = TvTokens.RowTitle, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(trailing, color = TvTokens.TextTertiary, fontSize = TvTokens.CardMeta)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RangeTabs(
    ranges: List<List<Episode>>,
    selected: Int,
    state: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember(density) { PivotSpec(with(density) { Start.toPx() }) }) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = Start, end = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 14.dp).focusRestorer().focusGroup(),
        ) {
            itemsIndexed(ranges, key = { i, _ -> i }) { i, r ->
                var focused by remember { mutableStateOf(false) }
                val isSel = i == selected
                Text(
                    "${r.first().number}–${r.last().number}",
                    color = when { focused -> Color.Black; isSel -> Color.White; else -> TvTokens.TextTertiary },
                    fontSize = 14.sp,
                    fontWeight = if (isSel || focused) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            when { focused -> Color.White; isSel -> Color.White.copy(alpha = 0.16f); else -> Color.Transparent }
                        )
                        .border(1.dp, if (isSel && !focused) Color.White.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(50))
                        // Moving across tabs switches the range immediately, so DPAD-right through
                        // tabs then DOWN lands straight in the episodes you were looking for.
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect(i) }
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodeRow(
    episodes: List<Episode>,
    rangeKey: Int,
    anime: Anime,
    history: WatchHistoryEntry?,
    rowState: androidx.compose.foundation.lazy.LazyListState,
    focusTarget: Int?,
    targetFocus: FocusRequester,
    onClick: (Episode) -> Unit,
) {
    var focusedEp by remember { mutableStateOf<Episode?>(null) }
    // Switching range: start the new row at its first episode, sliding in.
    val slide = remember { Animatable(1f) }
    LaunchedEffect(rangeKey) {
        if (focusTarget == null || episodes.none { it.number == focusTarget }) rowState.scrollToItem(0)
        slide.snapTo(0f); slide.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    val density = LocalDensity.current
    Column {
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember(density) { PivotSpec(with(density) { Start.toPx() }) }) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(start = Start, end = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .graphicsLayer { alpha = slide.value; translationX = (1f - slide.value) * 36.dp.toPx() }
                    .focusRestorer()
                    .focusGroup(),
            ) {
                itemsIndexed(episodes, key = { _, ep -> ep.number }) { _, ep ->
                    EpisodeCard(
                        ep = ep,
                        anime = anime,
                        watched = history != null && ep.number < history.episodeNumber,
                        progress = history?.takeIf { it.episodeNumber == ep.number && it.durationMs > 0 }
                            ?.let { (it.progressMs.toFloat() / it.durationMs).coerceIn(0f, 1f) },
                        modifier = if (ep.number == focusTarget) Modifier.focusRequester(targetFocus) else Modifier,
                        onFocused = { focusedEp = ep },
                        onClick = { onClick(ep) },
                    )
                }
            }
        }
        // Synopsis of the focused episode, under the row - Netflix-style, without growing cards.
        AnimatedContent(
            targetState = focusedEp?.takeIf { !it.description.isNullOrBlank() },
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(120)) },
            label = "epSynopsis",
            modifier = Modifier.padding(start = Start, top = 12.dp).width(620.dp).height(44.dp)
        ) { ep ->
            if (ep != null) {
                Text(
                    stripHtml(ep.description.orEmpty()), color = TvTokens.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    ep: Episode,
    anime: Anime,
    watched: Boolean,
    progress: Float?,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) TvTokens.FocusScale else 1f, TvTokens.FocusSpring, label = "epScale")
    val defaultName = ep.name.isNullOrBlank() || ep.name.equals("Episode ${ep.number}", ignoreCase = true)
    Column(
        modifier
            .width(232.dp)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(TvTokens.CardRadius))
                .background(TvTokens.Surface)
                .border(
                    if (focused) 2.dp else 0.dp,
                    if (focused) Color.White else Color.Transparent,
                    RoundedCornerShape(TvTokens.CardRadius)
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ep.thumbnail?.takeIf { it.isNotBlank() } ?: anime.bannerImage ?: anime.coverImage)
                    .size(464, 260)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (watched && !focused) 0.55f else 1f }
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            Text(
                "${ep.number}",
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp)
            )
            if (watched) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Check, contentDescription = "Watched", tint = TvTokens.Score, modifier = Modifier.size(15.dp)) }
            }
            androidx.compose.animation.AnimatedVisibility(focused, enter = fadeIn(tween(160)), exit = fadeOut(tween(120)), modifier = Modifier.align(Alignment.Center)) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                }
            }
            if (progress != null) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(TvTokens.Accent))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (defaultName) "Episode ${ep.number}" else ep.name.orEmpty(),
            color = if (focused) TvTokens.TextPrimary else TvTokens.TextSecondary,
            fontSize = 14.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpisodeSkeletonRow() {
    val pulse by rememberInfiniteTransition(label = "skel").animateFloat(
        0.35f, 0.7f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "skelA"
    )
    Row(Modifier.padding(start = Start), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(5) {
            Column {
                Box(Modifier.width(232.dp).height(130.dp).clip(RoundedCornerShape(TvTokens.CardRadius)).background(TvTokens.SurfaceHigh.copy(alpha = pulse)))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(150.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(TvTokens.SurfaceHigh.copy(alpha = pulse)))
            }
        }
    }
}

// ================================================================================================
// States

@Composable
private fun InlineMessage(title: String, body: String, onRetry: () -> Unit) {
    Column(Modifier.padding(start = Start, top = 4.dp).width(560.dp)) {
        Text(title, color = TvTokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = TvTokens.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(14.dp))
        TvButton("Retry", Icons.Default.Refresh, primary = false, onClick = onRetry)
    }
}

@Composable
private fun CenterMessage(title: String, body: String, onRetry: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = TvTokens.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = TvTokens.TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        TvButton("Retry", Icons.Default.Refresh, primary = true, modifier = Modifier.focusRequester(focus), onClick = onRetry)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PickSeries(candidates: List<ProviderSearchResult>, onPick: (ProviderSearchResult) -> Unit) {
    Column {
        Text(
            "We found several matches on the streaming server. Which one is it?",
            color = TvTokens.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(start = Start, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(start = Start, end = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusRestorer().focusGroup()
        ) {
            itemsIndexed(candidates.take(10), key = { _, c -> c.slug }) { _, c ->
                PosterCard(c.posterUrl, c.title, onClick = { onPick(c) })
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MoreLikeThis(items: List<Anime>, onAnimeClick: (Int) -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember(density) { PivotSpec(with(density) { Start.toPx() }) }) {
        LazyRow(
            contentPadding = PaddingValues(start = Start, end = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(TvTokens.CardGap),
            modifier = Modifier.focusRestorer().focusGroup()
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { _, item ->
                PosterCard(item.coverImage, item.englishTitle ?: item.title, onClick = { onAnimeClick(item.id) })
            }
        }
    }
}

@Composable
private fun FranchiseRow(entries: List<FranchiseEntry>, currentId: Int, onSelect: (FranchiseEntry) -> Unit) {
    val density = LocalDensity.current
    val currentIdx = entries.indexOfFirst { it.id == currentId }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = currentIdx.coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    CompositionLocalProvider(LocalBringIntoViewSpec provides remember(density) { PivotSpec(with(density) { Start.toPx() }) }) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(start = Start, end = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(TvTokens.CardGap),
            // Entering the row lands on the title being viewed, not whichever card is nearest.
            modifier = Modifier.focusRestorer(if (currentIdx >= 0) currentFocus else FocusRequester.Default).focusGroup()
        ) {
            itemsIndexed(entries, key = { _, it -> it.id }) { _, e ->
                FranchiseCard(e, current = e.id == currentId, focus = if (e.id == currentId) currentFocus else null) { onSelect(e) }
            }
        }
    }
}

@Composable
private fun FranchiseCard(e: FranchiseEntry, current: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) TvTokens.FocusScale else 1f, TvTokens.FocusSpring, label = "franchiseScale")
    val shape = RoundedCornerShape(TvTokens.CardRadius)
    Column(
        Modifier
            .width(TvTokens.PosterWidth)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TvTokens.CardHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(TvTokens.Surface)
                .border(
                    2.dp,
                    when { focused -> Color.White; current -> TvTokens.AccentGlow; else -> Color.Transparent },
                    shape
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(e.coverImage).size(224, 336).build(),
                contentDescription = e.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val badge = if (current) "NOW VIEWING" else e.relationLabel?.uppercase()
            if (badge != null) {
                Text(
                    badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            if (current) TvTokens.AccentGradient
                            else Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Black.copy(alpha = 0.78f))),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            if (e.status == "RELEASING") {
                Text(
                    "AIRING", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(TvTokens.AccentDeep, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            e.label, color = if (focused || current) TvTokens.TextPrimary else TvTokens.TextSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(e.meta, color = TvTokens.TextTertiary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun PosterCard(image: String, title: String, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) TvTokens.FocusScale else 1f, TvTokens.FocusSpring, label = "posterScale")
    Column(
        Modifier
            .width(TvTokens.PosterWidth)
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(image).size(224, 336).build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(TvTokens.CardHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(TvTokens.CardRadius))
                .background(TvTokens.Surface)
                .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(TvTokens.CardRadius))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title, color = if (focused) TvTokens.TextPrimary else TvTokens.TextTertiary, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeroSkeleton() {
    val pulse by rememberInfiniteTransition(label = "heroSkel").animateFloat(
        0.3f, 0.6f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "heroSkelA"
    )
    Column(Modifier.padding(start = Start, top = 52.dp)) {
        listOf(420.dp to 30.dp, 300.dp to 30.dp, 260.dp to 14.dp, 480.dp to 12.dp, 460.dp to 12.dp).forEach { (w, h) ->
            Bar(w, h, pulse); Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Bar(130.dp, 44.dp, pulse); Bar(130.dp, 44.dp, pulse) }
    }
}

@Composable
private fun Bar(w: Dp, h: Dp, alpha: Float) =
    Box(Modifier.width(w).height(h).clip(RoundedCornerShape(6.dp)).background(TvTokens.SurfaceHigh.copy(alpha = alpha)))

/** One season's (or movie's) trailer. The current title's card says so with a pulsing pill. */
@Composable
private fun DetailTrailerCard(clip: TrailerClip, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, TvTokens.FocusSpring, label = "dtcScale")
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "dtcRing")
    val zoom by animateFloatAsState(if (focused) 1.08f else 1f, tween(2400, easing = TvEase), label = "dtcZoom")
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .width(232.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).tvFocusRing({ ring }, 10.dp).clip(shape).background(TvTokens.Surface)) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(clip.thumbnail).size(464, 261).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            Box(
                Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape)
                    .background(if (focused) TvTokens.Focus else Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play trailer", tint = if (focused) TvTokens.OnFocus else TvTokens.TextPrimary, modifier = Modifier.size(22.dp))
            }
            if (clip.isCurrent) {
                val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "onThis").animateFloat(
                    0.6f, 1f,
                    androidx.compose.animation.core.infiniteRepeatable(tween(1100), androidx.compose.animation.core.RepeatMode.Reverse),
                    label = "onThisA"
                )
                Text(
                    "You're on this one", color = TvTokens.OnFocus, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).graphicsLayer { alpha = pulse }
                        .clip(RoundedCornerShape(5.dp)).background(TvTokens.AccentPale).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(clip.label, color = TvTokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text(clip.meta, color = TvTokens.TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
