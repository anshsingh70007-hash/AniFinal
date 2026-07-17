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
    userFeedbackList: List<UserFeedback>,
    onAnimeClick: (Anime) -> Unit,
    onHistoryClick: (WatchHistoryEntry) -> Unit
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
                ContinueWatchingRow(title = "🎬 Continue Watching", list = history, onHistoryClick = onHistoryClick)
            }
        }

        // Airing schedule countdowns
        if (airing.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "📡 Airing Today",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(airing) { item ->
                            AiringCard(
                                item = item,
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
                    Text(
                        text = "❤️ User's Choice",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
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
            AnimeSectionRow(title = "🔥 Trending Now", list = trending, onAnimeClick = onAnimeClick)
        }

        // Recently Updated Row
        if (recentlyUpdated.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "🔄 Recently Updated", list = recentlyUpdated, onAnimeClick = onAnimeClick)
            }
        }

        // Popular Row
        item {
            AnimeSectionRow(title = "⭐ Popular All Time", list = popular, onAnimeClick = onAnimeClick)
        }

        // Top Rated Row
        if (topRated.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "🌟 Top Rated Hits", list = topRated, onAnimeClick = onAnimeClick)
            }
        }

        // Seasonal Row
        item {
            AnimeSectionRow(title = "🌸 Seasonal Hits", list = seasonal, onAnimeClick = onAnimeClick)
        }

        // Action Genre Row
        if (actionAnime.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "⚔️ Action & Adventure", list = actionAnime, onAnimeClick = onAnimeClick)
            }
        }

        // Romance Genre Row
        if (romanceAnime.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "💕 Romance Picks", list = romanceAnime, onAnimeClick = onAnimeClick)
            }
        }

        // Upcoming Row
        if (upcoming.isNotEmpty()) {
            item {
                AnimeSectionRow(title = "📅 Upcoming Season", list = upcoming, onAnimeClick = onAnimeClick)
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
    
    // Auto-scroll logic
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % spotlightList.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
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
                AsyncImage(
                    model = anime.bannerImage ?: anime.coverImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, PrimaryDark.copy(alpha = 0.8f), PrimaryDark),
                                startY = 0f
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text("SPOTLIGHT", color = SecondaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(anime.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val cleanDescription = remember(anime.description) {
                        anime.description?.replace(Regex("<[^>]*>"), "") ?: ""
                    }
                    Text(
                        cleanDescription,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        // Custom Indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(spotlightList.size) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 16.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (pagerState.currentPage == index) PrimaryAccent else TextTertiary)
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
        Text(
            title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun AiringCard(item: AiringAnime, onClick: () -> Unit) {
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
            Text("Episode ${item.episode}", color = SecondaryAccent, fontSize = 11.sp)
            Text("Airing Today", color = WarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ContinueWatchingRow(
    title: String,
    list: List<WatchHistoryEntry>,
    onHistoryClick: (WatchHistoryEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list) { entry ->
                ContinueWatchingCard(entry = entry, onHistoryClick = onHistoryClick)
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    onHistoryClick: (WatchHistoryEntry) -> Unit,
    modifier: Modifier = Modifier
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
            val progressFraction = if (entry.durationMs > 0) entry.progressMs.toFloat() / entry.durationMs else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.BottomCenter),
                color = PrimaryAccent,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                entry.title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Ep ${entry.episodeNumber} • ${entry.episodeName}",
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
