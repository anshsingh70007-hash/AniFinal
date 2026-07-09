package com.example.aniflow.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.example.aniflow.DeviceType
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.theme.*
import kotlinx.coroutines.delay

fun assetExists(context: Context, path: String): Boolean {
    return try {
        context.assets.open(path).close()
        true
    } catch (e: Exception) {
        false
    }
}

@Composable
fun ShimmerItem(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.05f)
        ),
        start = androidx.compose.ui.geometry.Offset(translateAnim - 300f, translateAnim - 300f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim)
    )

    Box(
        modifier = modifier
            .background(brush, shape)
    )
}

@Composable
fun HomeScreen(
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
    deviceType: DeviceType,
    onAnimeClick: (Anime) -> Unit,
    onHistoryClick: (WatchHistoryEntry) -> Unit
) {
    val outerPadding = if (deviceType == DeviceType.TV) 24.dp else 16.dp
    val spacing = if (deviceType == DeviceType.TV) 28.dp else 24.dp

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Spotlight Pager / Shimmer
        if (trending.isEmpty()) {
            item {
                ShimmerItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (deviceType == DeviceType.TV) 340.dp else 260.dp)
                        .padding(horizontal = outerPadding, vertical = 8.dp)
                )
            }
        } else {
            item {
                SpotlightPager(
                    spotlightList = trending.take(5),
                    deviceType = deviceType,
                    onAnimeClick = onAnimeClick
                )
            }
        }

        // Continue Watching Row
        if (history.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "🎬 Continue Watching",
                        color = TextPrimary,
                        fontSize = if (deviceType == DeviceType.TV) 20.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = outerPadding)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = outerPadding),
                        horizontalArrangement = Arrangement.spacedBy(if (deviceType == DeviceType.TV) 16.dp else 12.dp)
                    ) {
                        items(history) { entry ->
                            ContinueWatchingCard(
                                entry = entry,
                                deviceType = deviceType,
                                onClick = { onHistoryClick(entry) }
                            )
                        }
                    }
                }
            }
        }

        // Airing schedule countdowns
        if (airing.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "📡 Airing Today",
                        color = TextPrimary,
                        fontSize = if (deviceType == DeviceType.TV) 20.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = outerPadding)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = outerPadding),
                        horizontalArrangement = Arrangement.spacedBy(if (deviceType == DeviceType.TV) 16.dp else 12.dp)
                    ) {
                        items(airing) { item ->
                            AdaptiveAiringCard(item = item, deviceType = deviceType)
                        }
                    }
                }
            }
        }

        // Content Rows
        val sections = listOf(
            "🔥 Trending Now" to trending,
            "🔄 Recently Updated" to recentlyUpdated,
            "⭐ Popular All Time" to popular,
            "🌟 Top Rated Hits" to topRated,
            "🌸 Seasonal Hits" to seasonal,
            "⚔️ Action & Adventure" to actionAnime,
            "💕 Romance Picks" to romanceAnime,
            "📅 Upcoming Season" to upcoming
        )

        sections.forEach { (title, list) ->
            if (list.isNotEmpty()) {
                item {
                    AdaptiveAnimeSectionRow(
                        title = title,
                        list = list,
                        deviceType = deviceType,
                        onAnimeClick = onAnimeClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotlightPager(
    spotlightList: List<Anime>,
    deviceType: DeviceType,
    onAnimeClick: (Anime) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { spotlightList.size })
    var isFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(if (isFocused) 1.02f else 1.0f, label = "spotlightScale")
    val context = LocalContext.current

    LaunchedEffect(isFocused) {
        if (!isFocused) {
            while (true) {
                delay(5000)
                if (spotlightList.isNotEmpty()) {
                    val nextPage = (pagerState.currentPage + 1) % spotlightList.size
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (deviceType == DeviceType.TV) 340.dp else 260.dp)
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) SecondaryAccent else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .run {
                if (deviceType == DeviceType.TV) {
                    focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onAnimeClick(spotlightList[pagerState.currentPage]) }
                } else this
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val anime = spotlightList[page]
            val trailerPath = "trailers/${anime.id}.mp4"
            val hasTrailer = remember(anime.id) { assetExists(context, trailerPath) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = deviceType == DeviceType.PHONE) { onAnimeClick(anime) }
            ) {
                if (hasTrailer && deviceType == DeviceType.TV) {
                    val exoPlayer = remember(anime.id) {
                        ExoPlayer.Builder(context).build().apply {
                            val mediaItem = MediaItem.fromUri("asset:///$trailerPath")
                            setMediaItem(mediaItem)
                            volume = 0f
                            repeatMode = Player.REPEAT_MODE_ALL
                            prepare()
                            playWhenReady = true
                        }
                    }
                    DisposableEffect(exoPlayer) {
                        onDispose {
                            exoPlayer.release()
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = anime.bannerImage ?: anime.coverImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

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
                        .padding(if (deviceType == DeviceType.TV) 24.dp else 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("SPOTLIGHT", color = SecondaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (page == 0) {
                            Box(
                                modifier = Modifier
                                    .background(PrimaryAccent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("⭐ #1 Trending", color = PrimaryAccentLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        anime.title,
                        color = TextPrimary,
                        fontSize = if (deviceType == DeviceType.TV) 28.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val cleanDescription = remember(anime.description) {
                        anime.description?.replace(Regex("<[^>]*>"), "") ?: ""
                    }
                    Text(
                        cleanDescription,
                        color = TextSecondary,
                        fontSize = if (deviceType == DeviceType.TV) 13.sp else 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

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
fun AdaptiveAnimeSectionRow(
    title: String,
    list: List<Anime>,
    deviceType: DeviceType,
    onAnimeClick: (Anime) -> Unit
) {
    val outerPadding = if (deviceType == DeviceType.TV) 24.dp else 16.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = TextPrimary,
            fontSize = if (deviceType == DeviceType.TV) 20.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = outerPadding)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = outerPadding),
            horizontalArrangement = Arrangement.spacedBy(if (deviceType == DeviceType.TV) 16.dp else 12.dp)
        ) {
            items(list) { anime ->
                AdaptiveAnimePosterCard(anime = anime, deviceType = deviceType, onClick = { onAnimeClick(anime) })
            }
        }
    }
}

@Composable
fun AdaptiveAnimePosterCard(
    anime: Anime,
    deviceType: DeviceType,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f, label = "posterScale")

    Column(
        modifier = Modifier
            .width(if (deviceType == DeviceType.TV) 150.dp else 115.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .run {
                if (deviceType == DeviceType.TV) {
                    focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onClick() }
                } else {
                    clickable { onClick() }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) SecondaryAccent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
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
            color = if (isFocused) PrimaryAccentLight else TextPrimary,
            fontSize = if (deviceType == DeviceType.TV) 13.sp else 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    deviceType: DeviceType,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f, label = "cwScale")

    Column(
        modifier = Modifier
            .width(if (deviceType == DeviceType.TV) 200.dp else 160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .run {
                if (deviceType == DeviceType.TV) {
                    focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onClick() }
                } else {
                    clickable { onClick() }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (deviceType == DeviceType.TV) 112.dp else 90.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) SecondaryAccent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            AsyncImage(
                model = entry.coverImage,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = 24.sp)
            }
            val progress = if (entry.durationMs > 0) entry.progressMs.toFloat() / entry.durationMs else 0f
            LinearProgressIndicator(
                progress = { progress },
                color = PrimaryAccent,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.title,
            color = if (isFocused) PrimaryAccentLight else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Ep ${entry.episodeNumber} - ${entry.episodeName}",
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AdaptiveAiringCard(
    item: AiringAnime,
    deviceType: DeviceType
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f, label = "airingScale")

    Row(
        modifier = Modifier
            .width(if (deviceType == DeviceType.TV) 240.dp else 200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(
                width = if (isFocused) 1.5.dp else 0.5.dp,
                color = if (isFocused) SecondaryAccent else SurfaceBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .run {
                if (deviceType == DeviceType.TV) {
                    focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                } else this
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.coverImageUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if (deviceType == DeviceType.TV) 64.dp else 50.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Episode ${item.episode}",
                color = PrimaryAccentLight,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            val timeStr = remember(item.airingAt) {
                val diffSec = (item.airingAt - System.currentTimeMillis() / 1000L).coerceAtLeast(0)
                val hours = diffSec / 3600
                val mins = (diffSec % 3600) / 60
                if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            }
            Text(
                text = "Airing in $timeStr",
                color = TextSecondary,
                fontSize = 9.sp
            )
        }
    }
}
