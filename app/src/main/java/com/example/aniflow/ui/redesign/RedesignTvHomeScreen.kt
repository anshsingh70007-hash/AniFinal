package com.example.aniflow.ui.redesign

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.viewinterop.AndroidView
import android.annotation.SuppressLint
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.UserFeedback
import androidx.compose.ui.text.font.FontStyle
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.components.AmbientBackground
import com.example.aniflow.ui.redesign.components.GlassCard
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.glassSurface

@Composable
fun RedesignTvHomeScreen(
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
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
            // Spotlight Carousel
            if (trending.isNotEmpty()) {
                item {
                    RedesignTvSpotlight(
                        anime = trending.first(),
                        onClick = { onAnimeClick(trending.first()) },
                        onExpandedChanged = { expanded ->
                            if (expanded) {
                                coroutineScope.launch {
                                    // Scroll item 0 (Spotlight) to the top of the LazyColumn
                                    lazyListState.animateScrollToItem(0)
                                }
                            }
                        }
                    )
                }
            }

            // Continue Watching
            if (history.isNotEmpty()) {
                item {
                    RedesignTvContinueWatchingRow(title = "🎬 Continue Watching", list = history, onHistoryClick = onHistoryClick)
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
                                RedesignTvAiringCard(
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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(userFeedbackList) { feedback ->
                                var isFocused by remember { mutableStateOf(false) }
                                GlassCard(
                                    onClick = {
                                        onAnimeClick(feedback.anime.toAnime())
                                    },
                                    modifier = Modifier
                                        .width(320.dp)
                                        .height(110.dp)
                                        .onFocusChanged { isFocused = it.isFocused },
                                    shape = RoundedCornerShape(12.dp)
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
                                                .width(70.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = feedback.anime.title,
                                                color = if (isFocused) GlassTokens.GlowCyan else TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "\"${feedback.feedback}\"",
                                                color = GlassTokens.TextMuted,
                                                fontSize = 12.sp,
                                                fontStyle = FontStyle.Italic,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 15.sp
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
                RedesignTvSectionRow(title = "🔥 Trending Now", list = trending, onAnimeClick = onAnimeClick)
            }

            // Recently Updated
            if (recentlyUpdated.isNotEmpty()) {
                item {
                    RedesignTvSectionRow(title = "🔄 Recently Updated", list = recentlyUpdated, onAnimeClick = onAnimeClick)
                }
            }

            // Popular Row
            item {
                RedesignTvSectionRow(title = "⭐ Popular All Time", list = popular, onAnimeClick = onAnimeClick)
            }

            // Top Rated Row
            if (topRated.isNotEmpty()) {
                item {
                    RedesignTvSectionRow(title = "🌟 Top Rated Hits", list = topRated, onAnimeClick = onAnimeClick)
                }
            }

            // Seasonal Row
            item {
                RedesignTvSectionRow(title = "🌸 Seasonal Hits", list = seasonal, onAnimeClick = onAnimeClick)
            }

            // Action
            if (actionAnime.isNotEmpty()) {
                item {
                    RedesignTvSectionRow(title = "⚔️ Action & Adventure", list = actionAnime, onAnimeClick = onAnimeClick)
                }
            }

            // Romance
            if (romanceAnime.isNotEmpty()) {
                item {
                    RedesignTvSectionRow(title = "💕 Romance Picks", list = romanceAnime, onAnimeClick = onAnimeClick)
                }
            }

            // Upcoming
            if (upcoming.isNotEmpty()) {
                item {
                    RedesignTvSectionRow(title = "📅 Upcoming Season", list = upcoming, onAnimeClick = onAnimeClick)
                }
            }
        }
}

@Composable
fun RedesignTvSpotlight(
    anime: Anime,
    onClick: () -> Unit,
    onExpandedChanged: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(1500)
            isExpanded = true
            onExpandedChanged(true)
        } else {
            isExpanded = false
            onExpandedChanged(false)
        }
    }

    val bannerHeight by animateDpAsState(
        targetValue = if (isExpanded) 400.dp else 340.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bannerHeight"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .onFocusChanged { isFocused = it.isFocused }
            .focusGlow(isFocused, RoundedCornerShape(16.dp), focusedScale = 1.02f)
            .glassSurface(RoundedCornerShape(16.dp), isFocused = isFocused)
            .clickable { onClick() }
    ) {
        if (!anime.trailerUrl.isNullOrEmpty()) {
            BackgroundTrailerPlayer(
                trailerUrl = anime.trailerUrl,
                modifier = Modifier.fillMaxSize(),
                isMuted = !isExpanded
            )
        } else {
            AsyncImage(
                model = anime.bannerImage ?: anime.coverImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Deep fading dark gradient over image banner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, PrimaryDark.copy(alpha = 0.6f), PrimaryDark),
                        startY = 0f
                    )
                )
        )

        // Safe container that guarantees a 24.dp margin from all edges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Frosted card info overlay panel floating on TV Spotlight
            GlassCard(
                onClick = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.55f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "FEATURED TITLE",
                        color = GlassTokens.GlowCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        anime.title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val cleanDescription = remember(anime.description) {
                        anime.description?.replace(Regex("<[^>]*>"), "") ?: ""
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        cleanDescription,
                        color = GlassTokens.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun BackgroundTrailerPlayer(
    trailerUrl: String?,
    modifier: Modifier = Modifier,
    isMuted: Boolean = true,
    onVideoEnded: (() -> Unit)? = null
) {
    if (trailerUrl.isNullOrEmpty()) return

    val videoId = remember(trailerUrl) {
        val afterEmbed = trailerUrl.substringAfter("/embed/")
        afterEmbed.substringBefore("?").substringBefore("/")
    }

    AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                }

                if (onVideoEnded != null) {
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onPlayerStateChange(state: Int) {
                            if (state == 0) { // ended
                                post { onVideoEnded() }
                            }
                        }
                    }, "Android")
                }

                webViewClient = android.webkit.WebViewClient()
                isClickable = false
                isFocusable = false
                setOnTouchListener { _, _ -> true }
                
                if (trailerUrl.contains("youtube")) {
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <style>
                          body { margin: 0; padding: 0; overflow: hidden; background-color: black; }
                          #player { width: 100vw; height: 100vh; pointer-events: none; }
                        </style>
                        </head>
                        <body>
                          <div id="player"></div>
                          <script>
                            var tag = document.createElement('script');
                            tag.src = "https://www.youtube.com/iframe_api";
                            var firstScriptTag = document.getElementsByTagName('script')[0];
                            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                            var player;
                            function onYouTubeIframeAPIReady() {
                              player = new YT.Player('player', {
                                height: '100%',
                                width: '100%',
                                videoId: '$videoId',
                                playerVars: {
                                  'autoplay': 1,
                                  'mute': ${if (isMuted) 1 else 0},
                                  'controls': 0,
                                  'showinfo': 0,
                                  'rel': 0,
                                  'loop': 0,
                                  'playlist': '$videoId',
                                  'modestbranding': 1,
                                  'disablekb': 1,
                                  'enablejsapi': 1,
                                  'origin': 'https://www.youtube-nocookie.com'
                                },
                                events: {
                                  'onReady': onPlayerReady,
                                  'onStateChange': onPlayerStateChange
                                }
                              });
                            }

                            function onPlayerReady(event) {
                              event.target.playVideo();
                            }

                            function onPlayerStateChange(event) {
                              if (window.Android) {
                                window.Android.onPlayerStateChange(event.data);
                              }
                            }

                            function setMuted(muted) {
                              if (player && typeof player.mute === 'function' && typeof player.unMute === 'function') {
                                if (muted) {
                                  player.mute();
                                } else {
                                  player.unMute();
                                  player.setVolume(100);
                                }
                              }
                            }
                          </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                } else {
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <style>
                          body { margin: 0; padding: 0; overflow: hidden; background-color: black; }
                          iframe { border: none; width: 100vw; height: 100vh; pointer-events: none; }
                        </style>
                        </head>
                        <body>
                          <iframe src="$trailerUrl" referrerpolicy="strict-origin-when-cross-origin" allow="autoplay"></iframe>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                }
            }
        },
        update = { webView ->
            if (!isMuted) {
                val downTime = android.os.SystemClock.uptimeMillis()
                val downEvent = android.view.MotionEvent.obtain(
                    downTime, downTime, android.view.MotionEvent.ACTION_DOWN, 50f, 50f, 0
                )
                val upEvent = android.view.MotionEvent.obtain(
                    downTime, downTime + 50, android.view.MotionEvent.ACTION_UP, 50f, 50f, 0
                )
                webView.dispatchTouchEvent(downEvent)
                webView.dispatchTouchEvent(upEvent)
                downEvent.recycle()
                upEvent.recycle()
            }
            webView.evaluateJavascript("if (typeof setMuted === 'function') { setMuted($isMuted); }", null)
        },
        modifier = modifier
    )
}

@Composable
fun RedesignTvSectionRow(
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
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(list) { anime ->
                RedesignTvPosterCard(anime = anime, onClick = { onAnimeClick(anime) })
            }
        }
    }
}

@Composable
fun RedesignTvPosterCard(
    anime: Anime,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(140.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusGlow(isFocused, RoundedCornerShape(12.dp))
            .glassSurface(RoundedCornerShape(12.dp), isFocused = isFocused)
            .clickable { onClick() }
            .focusable()
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = anime.coverImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                anime.averageScore?.let { score ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "★ ${String.format("%.1f", score / 10.0)}",
                            color = GlassTokens.GlowCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = anime.title,
                color = if (isFocused) GlassTokens.GlowCyan else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun RedesignTvAiringCard(
    item: AiringAnime,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusGlow(isFocused, RoundedCornerShape(12.dp))
            .glassSurface(RoundedCornerShape(12.dp), isFocused = isFocused)
            .clickable { onClick() }
            .focusable()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.coverImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 54.dp, height = 76.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    item.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Episode ${item.episode}",
                    color = GlassTokens.GlowCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Airing Today",
                    color = WarningAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun RedesignTvContinueWatchingRow(
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
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(list) { entry ->
                RedesignTvContinueWatchingCard(entry = entry, onHistoryClick = onHistoryClick)
            }
        }
    }
}

@Composable
fun RedesignTvContinueWatchingCard(
    entry: WatchHistoryEntry,
    onHistoryClick: (WatchHistoryEntry) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(160.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusGlow(isFocused, RoundedCornerShape(12.dp))
            .glassSurface(RoundedCornerShape(12.dp), isFocused = isFocused)
            .clickable { onHistoryClick(entry) }
            .focusable()
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                        .height(5.dp)
                        .align(Alignment.BottomCenter),
                    color = GlassTokens.GlowCyan,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    entry.title,
                    color = if (isFocused) GlassTokens.GlowCyan else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Ep ${entry.episodeNumber} • ${entry.episodeName}",
                    color = GlassTokens.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
