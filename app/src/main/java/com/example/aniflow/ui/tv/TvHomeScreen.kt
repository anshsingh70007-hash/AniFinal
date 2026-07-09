package com.example.aniflow.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
fun TvHomeScreen(
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        // Spotlight Carousel
        if (trending.isNotEmpty()) {
            item {
                TvSpotlight(anime = trending.first(), onClick = { onAnimeClick(trending.first()) })
            }
        }

        // Continue Watching
        if (history.isNotEmpty()) {
            item {
                TvContinueWatchingRow(title = "🎬 Continue Watching", list = history, onHistoryClick = onHistoryClick)
            }
        }

        // Airing schedule
        if (airing.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "📡 Airing Today",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(airing) { item ->
                            TvAiringCard(
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

        // Trending
        item {
            TvAnimeSectionRow(title = "🔥 Trending Now", list = trending, onAnimeClick = onAnimeClick)
        }

        // Recently Updated
        if (recentlyUpdated.isNotEmpty()) {
            item {
                TvAnimeSectionRow(title = "🔄 Recently Updated", list = recentlyUpdated, onAnimeClick = onAnimeClick)
            }
        }

        // Popular
        item {
            TvAnimeSectionRow(title = "⭐ Popular All Time", list = popular, onAnimeClick = onAnimeClick)
        }

        // Top Rated
        if (topRated.isNotEmpty()) {
            item {
                TvAnimeSectionRow(title = "🌟 Top Rated Hits", list = topRated, onAnimeClick = onAnimeClick)
            }
        }

        // Seasonal
        item {
            TvAnimeSectionRow(title = "🌸 Seasonal Hits", list = seasonal, onAnimeClick = onAnimeClick)
        }

        // Action
        if (actionAnime.isNotEmpty()) {
            item {
                TvAnimeSectionRow(title = "⚔️ Action & Adventure", list = actionAnime, onAnimeClick = onAnimeClick)
            }
        }

        // Romance
        if (romanceAnime.isNotEmpty()) {
            item {
                TvAnimeSectionRow(title = "💕 Romance Picks", list = romanceAnime, onAnimeClick = onAnimeClick)
            }
        }

        // Upcoming
        if (upcoming.isNotEmpty()) {
            item {
                TvAnimeSectionRow(title = "📅 Upcoming Season", list = upcoming, onAnimeClick = onAnimeClick)
            }
        }
    }
}

@Composable
fun TvSpotlight(
    anime: Anime,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1.0f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SecondaryAccent else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .focusable()
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
                        colors = listOf(Color.Transparent, PrimaryDark.copy(alpha = 0.9f), PrimaryDark),
                        startY = 0f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Text("TRENDING SPOTLIGHT", color = SecondaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(anime.title, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            val cleanDescription = remember(anime.description) {
                anime.description?.replace(Regex("<[^>]*>"), "") ?: ""
            }
            Text(
                cleanDescription,
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TvAnimeSectionRow(
    title: String,
    list: List<Anime>,
    onAnimeClick: (Anime) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(list) { anime ->
                TvAnimeCard(anime = anime, onClick = { onAnimeClick(anime) })
            }
        }
    }
}

@Composable
fun TvAnimeCard(
    anime: Anime,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f)
    
    Box(
        modifier = Modifier
            .width(150.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) PrimaryAccent else SurfaceCard)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SecondaryAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .focusable()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
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
                color = if (isFocused) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun TvAiringCard(item: AiringAnime, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f)
    
    Row(
        modifier = Modifier
            .width(240.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) PrimaryAccent else SurfaceCard)
            .border(
                width = if (isFocused) 1.5.dp else 0.5.dp,
                color = if (isFocused) SecondaryAccent else SurfaceBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable()
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
fun TvContinueWatchingRow(
    title: String,
    list: List<WatchHistoryEntry>,
    onHistoryClick: (WatchHistoryEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(list) { entry ->
                TvContinueWatchingCard(entry = entry, onHistoryClick = onHistoryClick)
            }
        }
    }
}

@Composable
fun TvContinueWatchingCard(
    entry: WatchHistoryEntry,
    onHistoryClick: (WatchHistoryEntry) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f)

    Column(
        modifier = Modifier
            .width(160.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) SurfaceBorder else PrimaryDarker)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SecondaryAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onHistoryClick(entry) }
            .focusable()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
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
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                entry.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Ep ${entry.episodeNumber} • ${entry.episodeName}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
