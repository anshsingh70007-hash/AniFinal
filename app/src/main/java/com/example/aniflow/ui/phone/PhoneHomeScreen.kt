package com.example.aniflow.ui.phone

import com.example.aniflow.ui.home.HomeRowSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.UserFeedback
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.model.freshEpisodes
import com.example.aniflow.data.model.continueEpisode
import com.example.aniflow.data.model.finished
import com.example.aniflow.data.model.newEpisodesLabel
import com.example.aniflow.theme.*
import com.example.aniflow.ui.phone.home.SEC
import com.example.aniflow.ui.phone.home.SectionReorderState
import com.example.aniflow.ui.phone.home.liftToMove
import com.example.aniflow.ui.phone.home.mergeOrder
import com.example.aniflow.ui.phone.home.persistOrder
import com.example.aniflow.ui.phone.home.rememberSectionReorderState
import com.example.aniflow.ui.phone.home.swapped
import com.example.aniflow.ui.phone.motion.PhoneMotion
import com.example.aniflow.ui.phone.motion.pressScale
import com.example.aniflow.ui.phone.motion.rememberHaptics
import com.example.aniflow.ui.phone.motion.rememberReduceMotion
import com.example.aniflow.ui.tv.home.episodeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Home segments in the top bar. */
const val SEGMENT_FOR_YOU = 0
const val SEGMENT_SERIES = 1
const val SEGMENT_FILMS = 2
private const val MIN_FILTERED_ROW = 4

private fun List<Anime>.forSegment(segment: Int): List<Anime> = when (segment) {
    SEGMENT_SERIES -> filter { it.format != "MOVIE" && it.format != "MUSIC" }
    SEGMENT_FILMS -> filter { it.format == "MOVIE" }
    else -> this
}

private class HomeSection(
    val key: String,
    val title: String,
    val trailing: (@Composable () -> Unit)? = null,
    val body: @Composable () -> Unit,
)

@Composable
fun PhoneHomeScreen(
    trending: List<Anime>,
    popular: List<Anime>,
    seasonal: List<Anime>,
    airing: List<AiringAnime>,
    topRated: List<Anime>,
    upcoming: List<Anime>,
    recentlyUpdated: List<Anime>,
    actionAnime: List<Anime>,
    romanceAnime: List<Anime>,
    history: List<WatchHistoryEntry>,
    /** AniList id -> newest episode out, for shows still airing. */
    latestAired: Map<Int, Int> = emptyMap(),
    topPicks: List<Anime> = emptyList(),
    becauseYouWatched: Pair<String, List<Anime>>? = null,
    shelves: List<com.example.aniflow.data.repository.HomeShelf> = emptyList(),
    userFeedbackList: List<UserFeedback>,
    onAnimeClick: (Anime) -> Unit,
    onHistoryClick: (WatchHistoryEntry) -> Unit,
    onPlay: (animeId: Int, episode: Int) -> Unit = { _, _ -> },
    onOpenSchedule: () -> Unit = {},
    settingsStore: SettingsStore? = null,
    segment: Int = SEGMENT_FOR_YOU,
    bottomInset: Dp = 120.dp,
    listState: LazyListState = rememberLazyListState(),
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val forYou = segment == SEGMENT_FOR_YOU

    // A filtered row with one or two posters looks broken; in Series/Films keep only rows that still fill the width.
    fun List<Anime>.shown(): List<Anime>? = forSegment(segment).takeIf { it.size >= (if (forYou) 1 else MIN_FILTERED_ROW) }
    val sections = buildList {
        if (forYou && history.isNotEmpty()) add(HomeSection(HomeRowSpec.CONTINUE, HomeRowSpec.T_CONTINUE) {
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history, key = { it.animeId }) { entry ->
                    ContinueWatchingCard(entry = entry, newEpisodes = entry.freshEpisodes(latestAired[entry.animeId]), onHistoryClick = onHistoryClick)
                }
            }
        })
        topPicks.shown()?.let { add(HomeSection(HomeRowSpec.PICKS, HomeRowSpec.T_PICKS) { PosterRow(it, onAnimeClick) }) }
        becauseYouWatched?.let { (seed, picks) ->
            picks.shown()?.let { add(HomeSection(HomeRowSpec.BECAUSE, HomeRowSpec.becauseTitle(seed)) { PosterRow(it, onAnimeClick) }) }
        }
        if (segment != SEGMENT_FILMS && airing.isNotEmpty()) add(
            HomeSection(
                HomeRowSpec.AIRING, HomeRowSpec.T_AIRING,
                trailing = {
                    Text(
                        "Schedule ›", color = PrimaryAccentLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onOpenSchedule() }.padding(horizontal = 10.dp, vertical = 12.dp)
                    )
                }
            ) { AiringRow(airing, onAnimeClick) }
        )
        if (forYou && userFeedbackList.isNotEmpty()) add(HomeSection(HomeRowSpec.YOUR_PICKS, HomeRowSpec.T_YOUR_PICKS) { FeedbackRow(userFeedbackList, onAnimeClick) })
        fun row(key: String, title: String, list: List<Anime>) {
            list.shown()?.let { add(HomeSection(key, title) { PosterRow(it, onAnimeClick) }) }
        }
        row(HomeRowSpec.TRENDING, HomeRowSpec.T_TRENDING, trending)
        row(HomeRowSpec.FRESH, HomeRowSpec.T_FRESH, recentlyUpdated)
        shelves.forEach { row(HomeRowSpec.shelfKey(it), it.title, it.items) }
        row(HomeRowSpec.POPULAR, HomeRowSpec.T_POPULAR, popular)
        row(HomeRowSpec.TOP, HomeRowSpec.T_TOP, topRated)
        row(HomeRowSpec.SEASONAL, HomeRowSpec.T_SEASONAL, seasonal)
        row(HomeRowSpec.ACTION, HomeRowSpec.T_ACTION, actionAnime)
        row(HomeRowSpec.ROMANCE, HomeRowSpec.T_ROMANCE, romanceAnime)
        row(HomeRowSpec.UPCOMING, HomeRowSpec.T_UPCOMING, upcoming)
    }.distinctBy { it.key }.let { list ->
        // Same default order as the TV home (HomeRowSpec); the person's own order is applied below.
        val rank = HomeRowSpec.order(shelves).withIndex().associate { it.value to it.index }
        list.sortedBy { rank[it.key] ?: Int.MAX_VALUE }
    }

    // Order: what the person arranged (saved), applied to what's on screen now.
    val saved by (settingsStore?.homeOrder ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var working by remember { mutableStateOf<List<String>?>(null) }
    val defaults = sections.map { it.key }
    val order = mergeOrder(working ?: saved, defaults)
    val byKey = sections.associateBy { it.key }
    val ordered = order.mapNotNull { byKey[it] }

    val density = LocalDensity.current
    val reorder = rememberSectionReorderState(
        list = listState,
        haptics = haptics,
        bottomChromePx = with(density) { bottomInset.toPx() },
        onSwap = { a, b ->
            val base = working ?: saved
            working = persistOrder(base, mergeOrder(base, defaults).swapped(a, b))
        },
        onDrop = {
            val w = working ?: return@rememberSectionReorderState
            settingsStore?.let { s -> scope.launch { s.setHomeOrder(w) } }
        },
    )

    val banner = remember(trending, popular, topRated, segment) {
        (trending + popular + topRated).forSegment(segment).distinctBy { it.id }.take(6)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset + 16.dp),
    ) {
        if (banner.isNotEmpty()) {
            item(key = "banner") {
                FeaturedBanner(banner, history, latestAired, onOpen = onAnimeClick, onPlay = onPlay)
                Spacer(Modifier.height(14.dp))
            }
        }
        if (ordered.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (segment == SEGMENT_FILMS) "No films in today's rows yet. Try Discover to search for one." else "Nothing here yet.",
                    color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp)
                )
            }
        }
        ordered.forEachIndexed { index, s ->
            item(key = SEC + s.key) {
                val dragging = reorder.draggingKey == s.key
                val settling = reorder.settlingKey == s.key
                val anyDragging = reorder.draggingKey != null
                val placement = when {
                    dragging -> Modifier.zIndex(2f).graphicsLayer { translationY = reorder.dragOffset }
                    settling -> Modifier.zIndex(1f).graphicsLayer { translationY = reorder.settleOffset.value }
                    else -> Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = PhoneMotion.smooth())
                }
                DisposableEffect(s.key) { onDispose { reorder.abandon(s.key) } }
                SectionPanel(
                    section = s,
                    index = index,
                    count = ordered.size,
                    order = order,
                    reorder = reorder,
                    dragging = dragging,
                    dimmed = anyDragging && !dragging,
                    modifier = placement,
                )
            }
        }
    }
}

@Composable
private fun SectionPanel(
    section: HomeSection,
    index: Int,
    count: Int,
    order: List<String>,
    reorder: SectionReorderState,
    dragging: Boolean,
    dimmed: Boolean,
    modifier: Modifier,
) {
    val reduce = rememberReduceMotion()
    val haptics = rememberHaptics()
    val pressing = remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (dragging) 1f else 0f, PhoneMotion.spring(), label = "lift")
    val dip by animateFloatAsState(if (pressing.value) 0.985f else 1f, PhoneMotion.press(), label = "dip")
    val alphaT by animateFloatAsState(if (dimmed) 0.55f else 1f, PhoneMotion.fade(), label = "dim")
    val moving = reorder.moveKey == section.key
    val panel = SurfaceCard
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .graphicsLayer {
                val pop = if (reduce) 1f else 1f + 0.035f * lift
                val sc = (if (reduce) 1f else dip) * pop
                scaleX = sc; scaleY = sc
                alpha = alphaT
                shadowElevation = 22.dp.toPx() * lift
                shape = RoundedCornerShape(22.dp)
                clip = false
            }
            .drawBehind {
                val a = maxOf(lift, if (moving) 0.6f else 0f)
                if (a > 0.01f) drawRoundRect(panel.copy(alpha = a), cornerRadius = CornerRadius(22.dp.toPx()))
            }
            .padding(bottom = 18.dp)
            .semantics {
                customActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction("Move up") { reorder.moveBy(section.key, -1, order); true })
                    if (index < count - 1) add(CustomAccessibilityAction("Move down") { reorder.moveBy(section.key, 1, order); true })
                }
            }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .liftToMove(section.key, reorder, pressing)
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(18.dp).background(PrimaryAccent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(
                section.title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (moving) {
                IconButton(onClick = { reorder.moveBy(section.key, -1, order) }, enabled = index > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Move ${section.title} up", tint = if (index > 0) TextPrimary else TextTertiary)
                }
                IconButton(onClick = { reorder.moveBy(section.key, 1, order) }, enabled = index < count - 1) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Move ${section.title} down", tint = if (index < count - 1) TextPrimary else TextTertiary)
                }
            } else {
                section.trailing?.invoke()
            }
            IconButton(onClick = {
                reorder.moveKey = if (moving) null else section.key
                haptics.segmentTick()
            }) {
                if (moving) Icon(Icons.Rounded.Check, "Done moving ${section.title}", tint = SecondaryAccent)
                else GripDots(contentDescription = "Move ${section.title}")
            }
        }
        section.body()
    }
}

@Composable
private fun GripDots(contentDescription: String) {
    Canvas(Modifier.size(18.dp).semantics { this.contentDescription = contentDescription }) {
        val r = 1.6.dp.toPx()
        val c = TextTertiary
        for (col in 0..1) for (row in 0..2) {
            drawCircle(c, r, Offset(size.width * (0.36f + 0.28f * col), size.height * (0.22f + 0.28f * row)))
        }
    }
}

@Composable
private fun PosterRow(list: List<Anime>, onAnimeClick: (Anime) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { anime -> AnimePosterCard(anime = anime, onClick = { onAnimeClick(anime) }) }
    }
}

@Composable
private fun AiringRow(airing: List<AiringAnime>, onAnimeClick: (Anime) -> Unit) {
    val nowSec = com.example.aniflow.ui.schedule.rememberNowSec()
    // Open the row on what's next, with the latest aired episode just in view.
    val rowState = rememberLazyListState(
        initialFirstVisibleItemIndex = (com.example.aniflow.ui.schedule.nowSplit(airing, nowSec) - 1).coerceAtLeast(0)
    )
    LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(airing, key = { "${it.mediaId}_${it.episode}" }) { item ->
            AiringCard(item = item, nowSec = nowSec, onClick = {
                onAnimeClick(Anime(id = item.mediaId, title = item.title, coverImage = item.coverImageUrl, episodes = item.episode))
            })
        }
    }
}

@Composable
private fun FeedbackRow(list: List<UserFeedback>, onAnimeClick: (Anime) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list) { feedback ->
            Row(
                Modifier.width(280.dp).height(96.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceCard)
                    .clickable { onAnimeClick(feedback.anime.toAnime()) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = feedback.anime.coverImage, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(60.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(feedback.anime.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "\"${feedback.feedback}\"", color = TextSecondary, fontSize = 11.sp, fontStyle = FontStyle.Italic,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * The featured banner (motion board tile 19): peeking cards; the artwork's back layer trails the
 * card by 22%, the poster by 45%, the text by 12% and fades as its card leaves. One very light
 * tick as each card takes over under the finger; none when it advances on its own.
 */
@Composable
private fun FeaturedBanner(
    items: List<Anime>,
    history: List<WatchHistoryEntry>,
    latestAired: Map<Int, Int>,
    onOpen: (Anime) -> Unit,
    onPlay: (Int, Int) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { items.size })
    val haptics = rememberHaptics()
    val reduce = rememberReduceMotion()
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val draggedNow = rememberUpdatedState(dragged)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().drop(1).collect { if (draggedNow.value) haptics.frequentTick() }
    }
    // Advance every 6 s; a manual swipe restarts the clock; never moves under a finger.
    LaunchedEffect(pager.settledPage, items.size, reduce) {
        if (items.size < 2 || reduce) return@LaunchedEffect
        delay(6_000)
        if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.settledPage + 1) % items.size, animationSpec = PhoneMotion.glide())
    }
    Column {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            key = { items[it].id },
            modifier = Modifier.fillMaxWidth().height(216.dp),
        ) { page ->
            val anime = items[page]
            val interaction = remember { MutableInteractionSource() }
            val off = { (pager.currentPage - page) + pager.currentPageOffsetFraction }
            Box(
                Modifier
                    .fillMaxSize()
                    .pressScale(interaction, 0.975f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Aubergine)
                    .clickable(interactionSource = interaction, indication = null) { onOpen(anime) }
            ) {
                AsyncImage(
                    model = anime.bannerImage ?: anime.coverImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = 1.3f; scaleY = 1.3f
                        translationX = off() * size.width * 0.22f
                    }
                )
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(PrimaryDark.copy(alpha = 0.92f), PrimaryDark.copy(alpha = 0.55f), Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, PrimaryDark.copy(alpha = 0.7f)))))
                AsyncImage(
                    model = anime.coverImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .width(92.dp)
                        .aspectRatio(0.7f)
                        .graphicsLayer {
                            translationX = off() * 300.dp.toPx() * 0.45f
                            shadowElevation = 16.dp.toPx(); shape = RoundedCornerShape(12.dp); clip = true
                        }
                        .background(SurfaceCard)
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, end = 124.dp, bottom = 16.dp)
                        .graphicsLayer {
                            val o = off()
                            translationX = o * 300.dp.toPx() * 0.12f
                            alpha = 1f - minOf(1f, abs(o)) * 0.8f
                        }
                ) {
                    Text("FEATURED", color = SecondaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(anime.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                    val meta = remember(anime.id) {
                        listOfNotNull(
                            anime.averageScore?.let { "★ $it%" },
                            anime.format?.replace("_SHORT", " Short")?.replace("_", " "),
                            anime.episodeLabel(short = true),
                        ).joinToString("  ·  ")
                    }
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(meta, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (anime.status != "NOT_YET_RELEASED") {
                        val h = history.firstOrNull { it.animeId == anime.id }
                        val ep = h?.continueEpisode(latestAired[anime.id]) ?: 1
                        val playLabel = when {
                            h == null -> "Play"
                            !h.finished -> "Resume Ep $ep"
                            else -> "Play Ep $ep"
                        }
                        val playInteraction = remember { MutableInteractionSource() }
                        Row(
                            Modifier
                                .pressScale(playInteraction)
                                .clip(RoundedCornerShape(50))
                                .background(TextPrimary)
                                .clickable(interactionSource = playInteraction, indication = null) { onPlay(anime.id, ep) }
                                .heightIn(min = 40.dp)
                                .padding(start = 12.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = PrimaryDarker, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(playLabel, color = PrimaryDarker, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (items.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(items.size) { i ->
                    val active = pager.currentPage == i
                    val w by animateDpAsState(if (active) 18.dp else 6.dp, PhoneMotion.snappy(), label = "dot")
                    Box(Modifier.size(w, 6.dp).clip(CircleShape).background(if (active) PrimaryAccent else TextTertiary.copy(alpha = 0.6f)))
                }
            }
        }
    }
}

@Composable
fun AnimeSectionRow(
    title: String,
    list: List<Anime>,
    onAnimeClick: (Anime) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        PosterRow(list, onAnimeClick)
    }
}

@Composable
fun AnimePosterCard(
    anime: Anime,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(112.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
        ) {
            AsyncImage(
                model = anime.coverImage,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            anime.averageScore?.let { score ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, SecondaryAccent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ ${String.format(java.util.Locale.US, "%.1f", score / 10.0)}",
                        color = SecondaryAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = anime.title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun AiringCard(item: AiringAnime, nowSec: Long = System.currentTimeMillis() / 1000, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .width(196.dp)
            .pressScale(interaction, 0.975f)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(0.5.dp, SurfaceBorder, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.coverImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 46.dp, height = 64.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(item.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val context = androidx.compose.ui.platform.LocalContext.current
            val time = remember(item.airingAt) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(item.airingAt * 1000L)) }
            Text("EP ${item.episode} · $time", color = SecondaryAccent, fontSize = 11.sp)
            val (label, tint) = com.example.aniflow.ui.schedule.statusOf(item.airingAt, nowSec, WarningAmber)
            Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    onHistoryClick: (WatchHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
    newEpisodes: Int = 0,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(140.dp)
            .pressScale(interaction, 0.975f)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .clickable(interactionSource = interaction, indication = null) { onHistoryClick(entry) }
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            AsyncImage(model = entry.coverImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (newEpisodes > 0) {
                Text(
                    "NEW", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(PrimaryAccent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        // Progress sits under the cover, not on it; full and bright when a new episode is out.
        val progressFraction = if (newEpisodes > 0) 1f else if (entry.durationMs > 0) (entry.progressMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = PrimaryAccent,
            trackColor = Color.White.copy(alpha = 0.16f),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(entry.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val fresh = newEpisodesLabel(newEpisodes)
            Text(
                fresh?.let { "$it · you're on Ep ${entry.episodeNumber}" } ?: "Ep ${entry.episodeNumber} • ${entry.episodeName}",
                color = if (fresh != null) PrimaryAccent else TextSecondary,
                fontSize = 10.sp,
                fontWeight = if (fresh != null) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
