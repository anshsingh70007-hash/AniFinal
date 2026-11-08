package com.example.aniflow.ui.phone

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.ui.tv.home.TvTokens

/** Phone twin of the TV Library: My List and History, newest first, with progress. */
@Composable
fun PhoneLibraryScreen(
    watchlist: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    history: List<WatchHistoryEntry> = emptyList(),
    onHistoryClick: (WatchHistoryEntry) -> Unit = {},
) {
    val items = remember(watchlist) { watchlist.distinctBy { it.id } }
    val recent = remember(history) { history.distinctBy { it.animeId }.sortedByDescending { it.lastWatchedTime } }
    var showHistory by rememberSaveable { mutableStateOf(items.isEmpty() && recent.isNotEmpty()) }

    Box(
        Modifier.fillMaxSize().background(TvTokens.Canvas).drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(TvTokens.AccentDeep.copy(alpha = 0.3f), Color.Transparent),
                    center = Offset(0f, 0f), radius = size.width
                )
            )
        }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                "Library", color = TvTokens.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segment("My List", items.size, !showHistory) { showHistory = false }
                Segment("History", recent.size, showHistory) { showHistory = true }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = showHistory,
                transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(120))) },
                label = "lib",
            ) { hist ->
                when {
                    !hist && items.isEmpty() -> EmptyState("Nothing saved yet", "Tap  + My List  on any title and it will wait for you here.")
                    hist && recent.isEmpty() -> EmptyState("No history yet", "Everything you start watching shows up here, with where you left off.")
                    !hist -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items, key = { it.id }) { anime -> AnimePosterCard(anime = anime, onClick = { onAnimeClick(anime) }) }
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(recent, key = { it.animeId }) { h -> HistoryRow(h) { onHistoryClick(h) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Segment(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) TvTokens.AccentGradient else SolidColor(TvTokens.Surface))
            .border(1.dp, if (selected) TvTokens.AccentPale.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (selected) Color.White else TvTokens.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (count > 0) {
            Spacer(Modifier.width(7.dp))
            Text(
                "$count", color = if (selected) Color.White else TvTokens.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(shape).background(Color.White.copy(alpha = if (selected) 0.2f else 0.06f)).padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(bottom = 80.dp, start = 32.dp, end = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = TvTokens.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = TvTokens.TextTertiary, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HistoryRow(h: WatchHistoryEntry, onClick: () -> Unit) {
    val progress = if (h.durationMs > 0) (h.progressMs.toFloat() / h.durationMs).coerceIn(0f, 1f) else 0f
    val left = if (h.durationMs > 0) (h.durationMs - h.progressMs).coerceAtLeast(0) / 60_000L else -1
    val status = when {
        progress >= 0.9f -> "Finished episode ${h.episodeNumber}"
        left > 0 -> "Episode ${h.episodeNumber} · ${left}m left"
        else -> "Episode ${h.episodeNumber}"
    }
    val ago = remember(h.lastWatchedTime) {
        DateUtils.getRelativeTimeSpanString(h.lastWatchedTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    val px = with(LocalDensity.current) { 56.dp.roundToPx() }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TvTokens.Surface).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(h.coverImage).size(px, px * 3 / 2).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(10.dp)).background(TvTokens.SurfaceHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, color = TvTokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(status, color = TvTokens.AccentPale, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(ago, color = TvTokens.TextTertiary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Brush.horizontalGradient(listOf(TvTokens.Accent, TvTokens.AccentGlow))))
            }
        }
    }
}
