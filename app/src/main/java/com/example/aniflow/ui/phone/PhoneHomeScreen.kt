package com.example.aniflow.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.aniflow.ui.tv.home.episodeLabel
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.model.newEpisodes
import com.example.aniflow.data.model.newEpisodesLabel
import com.example.aniflow.data.UserFeedback
import androidx.compose.ui.text.font.FontStyle
import com.example.aniflow.theme.*
import kotlinx.coroutines.delay

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
    onOpenSchedule: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PrimaryDark),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Spotlight carousel (Premium HorizontalPager with Auto-scroll)
        if (trending.isNotEmpty()) {
            item {
                SpotlightPager(spotlightList = trending.take(5), onAnimeClick = onAnimeClick)
            }
        }

        // Continue Watching Row
        if (history.isNotEmpty()) {
            item {
                ContinueWatchingRow(title = "Continue Watching", list = history, latestAired = latestAired, onHistoryClick = onHistoryClick)
            }
        }
        if (topPicks.isNotEmpty()) {
            item(key = "picks") { AnimeSectionRow(title = "Top Picks for You", list = topPicks, onAnimeClick = onAnimeClick) }
        }
        becauseYouWatched?.let { (seed, picks) ->
            if (picks.isNotEmpty()) {
                item(key = "because") { AnimeSectionRow(title = "Because you watched $seed", list = picks, onAnimeClick = onAnimeClick) }
            }
        }

        // Airing schedule countdowns
        if (airing.isNotEmpty()) {
            item {
                Column {
                    val nowSec = com.example.aniflow.ui.schedule.rememberNowSec()
                    SectionHeader("Airing Today") {
                        Text(
                            "Full schedule ›", color = PrimaryAccentLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onOpenSchedule() }.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Open the row on what's next, with the latest aired episode just in view.
                    val rowState = androidx.compose.foundation.lazy.rememberLazyListState(
                        initialFirstVisibleItemIndex = (com.example.aniflow.ui.schedule.nowSplit(airing, nowSec) - 1).coerceAtLeast(0)
                    )
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(airing, key = { "${it.mediaId}_${it.episode}" }) { item ->
                            AiringCard(
                                item = item,
                                nowSec = nowSec,
                                onClick = {
                                    onAnimeClick(
                                        Anime(
                                            id = item.mediaId,
                                            title = item.title,
                                            coverImage = item.coverImageUrl,
                                            episodes = item.episode
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // User's Choice Row
        if (userFeedbackList.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Your Picks")
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(userFeedbackList) { feedback ->
                            Box(
                                modifier = Modifier
                                    .width(280.dp)
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceCard)
                                    .clickable {
                                        onAnimeClick(feedback.anime.toAnime())
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = feedback.anime.coverImage,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(60.dp)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = feedback.anime.title,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "\"${feedback.feedback}\"",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Trending Row
        item {
            AnimeSectionRow(title = "Trending Now", list = trending, onAnimeClick = onAnimeClick)
        }

        // Recently Updated Row
        if (recentlyUpdated.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "Recently Updated", list = recentlyUpdated, onAnimeClick = onAnimeClick)
            }
        }

        shelves.filter { !it.late }.forEach { shelf ->
            item(key = "shelf_" + shelf.key) { AnimeSectionRow(title = shelf.title, list = shelf.items, onAnimeClick = onAnimeClick) }
        }

        // Popular Row
        item {
            AnimeSectionRow(title = "Popular All Time", list = popular, onAnimeClick = onAnimeClick)
        }

        // Top Rated Row
        if (topRated.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "Highest Rated", list = topRated, onAnimeClick = onAnimeClick)
            }
        }

        // Seasonal Row
        item {
            AnimeSectionRow(title = "Airing This Season", list = seasonal, onAnimeClick = onAnimeClick)
        }

        // Action Genre Row
        if (actionAnime.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "Action & Adventure", list = actionAnime, onAnimeClick = onAnimeClick)
            }
        }

        // Romance Genre Row
        if (romanceAnime.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "Romance Picks", list = romanceAnime, onAnimeClick = onAnimeClick)
            }
        }

        shelves.filter { it.late }.forEach { shelf ->
            item(key = "shelf_" + shelf.key) { AnimeSectionRow(title = shelf.title, list = shelf.items, onAnimeClick = onAnimeClick) }
        }

        // Upcoming Row
        if (upcoming.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "Coming Soon", list = upcoming, onAnimeClick = onAnimeClick)
            }
        }
    }
}

@Composable
fun SpotlightPager(
    spotlightList: List<Anime>,
    onAnimeClick: (Anime) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { spotlightList.size })

    // Advance every 6 s. Keyed on the settled page, so a manual swipe restarts the clock instead
    // of being yanked onward a moment later; never animates while a finger is on it.
    LaunchedEffect(pagerState.settledPage, spotlightList.size) {
        if (spotlightList.size < 2) return@LaunchedEffect
        delay(6_000)
        if (!pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage((pagerState.settledPage + 1) % spotlightList.size)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val anime = spotlightList[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onAnimeClick(anime) }
            ) {
                // AniList banners are ~4.75:1, so on a phone they are only ambience: soft, dim, and
                // drifting at half the page speed. The sharp portrait cover carries the hero.
                AsyncImage(
                    model = anime.bannerImage ?: anime.coverImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Read the scroll fraction here (draw phase), not in composition: a swipe
                            // then redraws one layer per frame instead of recomposing the page.
                            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            translationX = offset * size.width * 0.5f
                            alpha = 0.6f
                        }
                        .blur(14.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(PrimaryDark.copy(alpha = 0.25f), PrimaryDark.copy(alpha = 0.6f), PrimaryDark)
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 30.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AsyncImage(
                        model = anime.coverImage,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(116.dp)
                            .aspectRatio(0.7f)
                            .shadow(20.dp, RoundedCornerShape(12.dp), ambientColor = PrimaryAccent, spotColor = PrimaryAccent)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SPOTLIGHT  #${page + 1}", color = SecondaryAccent, fontSize = 10.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            anime.title, color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 25.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        val meta = remember(anime.id) {
                            listOfNotNull(
                                anime.averageScore?.let { "★ $it%" },
                                anime.format?.replace("_SHORT", " Short"),
                                anime.seasonYear?.toString(),
                                anime.episodeLabel(short = true),
                            ).joinToString("  ·  ")
                        }
                        if (meta.isNotEmpty()) {
                            Text(meta, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (anime.genres.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                anime.genres.take(2).forEach { g ->
                                    Text(
                                        g, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.White.copy(alpha = 0.1f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        val cleanDescription = remember(anime.description) {
                            anime.description?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
                        }
                        Text(
                            cleanDescription, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Indicators: the active one stretches.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(spotlightList.size) { index ->
                val active = pagerState.currentPage == index
                val w by animateDpAsState(if (active) 18.dp else 6.dp, label = "dot")
                Box(
                    modifier = Modifier
                        .size(w, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) PrimaryAccent else TextTertiary)
                )
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
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list) { anime ->
                AnimePosterCard(anime = anime, onClick = { onAnimeClick(anime) })
            }
        }
    }
}

@Composable
fun AnimePosterCard(
    anime: Anime,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(115.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = anime.coverImage,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Score Badge
            anime.averageScore?.let { score ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .border(0.5.dp, SecondaryAccent.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ ${String.format("%.1f", score / 10.0)}",
                        color = SecondaryAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = anime.title,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AiringCard(item: AiringAnime, nowSec: Long = System.currentTimeMillis() / 1000, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(190.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(0.5.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.coverImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 46.dp, height = 64.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                item.title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            val time = remember(item.airingAt) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(item.airingAt * 1000L)) }
            Text("EP ${item.episode} · $time", color = SecondaryAccent, fontSize = 11.sp)
            val (label, tint) = com.example.aniflow.ui.schedule.statusOf(item.airingAt, nowSec, WarningAmber)
            Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ContinueWatchingRow(
    title: String,
    list: List<WatchHistoryEntry>,
    latestAired: Map<Int, Int> = emptyMap(),
    onHistoryClick: (WatchHistoryEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list) { entry ->
                ContinueWatchingCard(entry = entry, newEpisodes = entry.newEpisodes(latestAired[entry.animeId]), onHistoryClick = onHistoryClick)
            }
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
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PrimaryDarker)
            .clickable { onHistoryClick(entry) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            AsyncImage(
                model = entry.coverImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
        Spacer(Modifier.height(6.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                entry.title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

/** One header style for every home row: accent bar, title, optional trailing action. */
@Composable
private fun SectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.width(3.dp).height(18.dp).background(PrimaryAccent, RoundedCornerShape(2.dp)))
        Text(
            title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}
