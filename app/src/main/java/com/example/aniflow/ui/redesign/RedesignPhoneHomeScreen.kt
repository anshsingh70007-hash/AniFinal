package com.example.aniflow.ui.redesign

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
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.components.AmbientBackground
import com.example.aniflow.ui.redesign.components.GlassCard
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.redesign.theme.filmGrainOverlay
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star

@Composable
fun RedesignPhoneHomeScreen(
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
    onAnimeClick: (Anime) -> Unit,
    onHistoryClick: (WatchHistoryEntry) -> Unit
) {
    AmbientBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Spotlight carousel
            if (trending.isNotEmpty()) {
                item {
                    RedesignSpotlightPager(spotlightList = trending.take(5), onAnimeClick = onAnimeClick)
                }
            }

            // Continue Watching Row
            if (history.isNotEmpty()) {
                item {
                    RedesignContinueWatchingRow(title = "🎬 Continue Watching", list = history, onHistoryClick = onHistoryClick)
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
                                RedesignAiringCard(
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

            // Trending Row
            item {
                RedesignAnimeSectionRow(title = "🔥 Trending Now", list = trending, onAnimeClick = onAnimeClick)
            }

            // Recently Updated Row
            if (recentlyUpdated.isNotEmpty()) {
                item {
                    RedesignAnimeSectionRow(title = "🔄 Recently Updated", list = recentlyUpdated, onAnimeClick = onAnimeClick)
                }
            }

            // Popular Row
            item {
                RedesignAnimeSectionRow(title = "⭐ Popular All Time", list = popular, onAnimeClick = onAnimeClick)
            }

            // Top Rated Row
            if (topRated.isNotEmpty()) {
                item {
                    RedesignAnimeSectionRow(title = "🌟 Top Rated Hits", list = topRated, onAnimeClick = onAnimeClick)
                }
            }

            // Seasonal Row
            item {
                RedesignAnimeSectionRow(title = "🌸 Seasonal Hits", list = seasonal, onAnimeClick = onAnimeClick)
            }

            // Action Genre Row
            if (actionAnime.isNotEmpty()) {
                item {
                    RedesignAnimeSectionRow(title = "⚔️ Action & Adventure", list = actionAnime, onAnimeClick = onAnimeClick)
                }
            }

            // Romance Genre Row
            if (romanceAnime.isNotEmpty()) {
                item {
                    RedesignAnimeSectionRow(title = "💕 Romance Picks", list = romanceAnime, onAnimeClick = onAnimeClick)
                }
            }

            // Upcoming Row
            if (upcoming.isNotEmpty()) {
                item {
                    RedesignAnimeSectionRow(title = "📅 Upcoming Season", list = upcoming, onAnimeClick = onAnimeClick)
                }
            }
        }
    }
}

@Composable
fun RedesignSpotlightPager(
    spotlightList: List<Anime>,
    onAnimeClick: (Anime) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { spotlightList.size })
    
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
            .height(300.dp)
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
                // Backdrop gradient overlay to blend character art beautifully
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    PrimaryDark.copy(alpha = 0.2f),
                                    PrimaryDark.copy(alpha = 0.7f),
                                    PrimaryDark
                                ),
                                startY = 0f
                            )
                        )
                )
                
                // Bottom details overlay layout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(GlassTokens.GlowCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SPOTLIGHT",
                                    color = GlassTokens.GlowCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            
                            anime.studioName?.let { studio ->
                                Text(
                                    text = studio,
                                    color = GlassTokens.TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        anime.averageScore?.let { score ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "Rating",
                                    tint = WarningAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = String.format("%.1f", score / 10.0),
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = anime.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    if (anime.genres.isNotEmpty()) {
                        Text(
                            text = anime.genres.take(3).joinToString(" • "),
                            color = GlassTokens.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    val cleanDescription = remember(anime.description) {
                        anime.description?.replace(Regex("<[^>]*>"), "") ?: ""
                    }
                    if (cleanDescription.isNotEmpty()) {
                        Text(
                            text = cleanDescription,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onAnimeClick(anime) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(GlassTokens.GlowCyan, PrimaryAccent)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Watch Now",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { onAnimeClick(anime) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.06f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Info",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(spotlightList.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (active) 12.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) GlassTokens.GlowCyan else Color.White.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
fun RedesignAnimeSectionRow(
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
                RedesignAnimePosterCard(anime = anime, onClick = { onAnimeClick(anime) })
            }
        }
    }
}

@Composable
fun RedesignAnimePosterCard(
    anime: Anime,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(115.dp)
    ) {
        GlassCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
            shape = RoundedCornerShape(12.dp)
        ) {
            AsyncImage(
                model = anime.coverImage,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Score Badge in custom neon accent
            anime.averageScore?.let { score ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ ${String.format("%.1f", score / 10.0)}",
                        color = GlassTokens.GlowCyan,
                        fontSize = 9.sp,
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
fun RedesignAiringCard(item: AiringAnime, onClick: () -> Unit) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier.width(190.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.coverImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 46.dp, height = 64.dp)
                    .clip(RoundedCornerShape(6.dp))
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
                Spacer(Modifier.height(2.dp))
                Text("Episode ${item.episode}", color = GlassTokens.GlowCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("Airing Today", color = WarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun RedesignContinueWatchingRow(
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
                RedesignContinueWatchingCard(entry = entry, onHistoryClick = onHistoryClick)
            }
        }
    }
}

@Composable
fun RedesignContinueWatchingCard(
    entry: WatchHistoryEntry,
    onHistoryClick: (WatchHistoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(140.dp)
    ) {
        GlassCard(
            onClick = { onHistoryClick(entry) },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            AsyncImage(
                model = entry.coverImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val progressFraction = if (entry.durationMs > 0) entry.progressMs.toFloat() / entry.durationMs else 0f
            // Neon cyan custom progress bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .align(Alignment.BottomCenter),
                color = GlassTokens.GlowCyan,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                entry.title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Ep ${entry.episodeNumber} • ${entry.episodeName}",
                color = GlassTokens.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
