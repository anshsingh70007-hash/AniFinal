package com.example.aniflow.ui.tv

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.ui.focus.primaryFocus
import com.example.aniflow.ui.tv.home.TvTokens

private enum class LibTab(val label: String) { List("My List"), History("History") }

/**
 * The viewer's own shelf: what they saved, and everything they've started, newest first.
 * Tabs follow focus like the rest of the TV app; no OK press needed to switch.
 */
@Composable
fun TvLibraryScreen(
    watchlist: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    history: List<WatchHistoryEntry> = emptyList(),
    onHistoryClick: (WatchHistoryEntry) -> Unit = {},
) {
    val items = remember(watchlist) { watchlist.distinctBy { it.id } }
    val recent = remember(history) { history.distinctBy { it.animeId }.sortedByDescending { it.lastWatchedTime } }
    // Open on whichever tab has something in it.
    var tab by rememberSaveable { mutableStateOf(if (items.isEmpty() && recent.isNotEmpty()) LibTab.History else LibTab.List) }
    val firstTab = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {
        Text("Library", color = TvTokens.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.focusRestorer(firstTab).focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibTab.entries.forEach { t ->
                val count = if (t == LibTab.List) items.size else recent.size
                TabChip(
                    label = t.label, count = count, selected = t == tab,
                    modifier = if (t == tab) Modifier.primaryFocus(firstTab) else Modifier,
                    onFocused = { tab = t },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(120))) },
            label = "lib",
        ) { t ->
            when {
                t == LibTab.List && items.isEmpty() -> Empty(
                    "Nothing saved yet",
                    "Choose  + My List  on any title and it will wait for you here."
                )
                t == LibTab.History && recent.isEmpty() -> Empty(
                    "No history yet",
                    "Everything you start watching shows up here, with where you left off."
                )
                t == LibTab.List -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(116.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 48.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer().focusGroup()
                ) {
                    items(items, key = { it.id }) { anime -> TvPosterTile(anime) { onAnimeClick(anime) } }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer().focusGroup()
                ) {
                    items(recent, key = { it.animeId }) { h -> HistoryCard(h) { onHistoryClick(h) } }
                }
            }
        }
    }
}

@Composable
private fun Empty(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = TvTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = TvTokens.TextTertiary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TabChip(label: String, count: Int, selected: Boolean, modifier: Modifier, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, TvTokens.FocusSpring, label = "s")
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) TvTokens.AccentGradient else SolidColor(TvTokens.Surface))
            .border(if (focused) 2.dp else 1.dp, if (focused) TvTokens.AccentPale else Color.White.copy(alpha = 0.06f), shape)
            .clickable(interaction, indication = null) { onFocused() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (selected) Color.White else TvTokens.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count", color = if (selected) Color.White else TvTokens.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (selected) 0.2f else 0.06f)).padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun HistoryCard(h: WatchHistoryEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, TvTokens.FocusSpring, label = "s")
    val bg by animateColorAsState(if (focused) TvTokens.SurfaceHigh else TvTokens.Surface, label = "bg")
    val shape = RoundedCornerShape(14.dp)
    val progress = if (h.durationMs > 0) (h.progressMs.toFloat() / h.durationMs).coerceIn(0f, 1f) else 0f
    val left = if (h.durationMs > 0) ((h.durationMs - h.progressMs).coerceAtLeast(0) / 60_000L) else -1
    val status = when {
        progress >= 0.9f -> "Finished episode ${h.episodeNumber}"
        left > 0 -> "Episode ${h.episodeNumber} · ${left}m left"
        else -> "Episode ${h.episodeNumber}"
    }
    val ago = remember(h.lastWatchedTime) {
        DateUtils.getRelativeTimeSpanString(h.lastWatchedTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    val px = with(LocalDensity.current) { 70.dp.roundToPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(116.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) TvTokens.AccentGradient else SolidColor(Color.White.copy(alpha = 0.05f)), shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(h.coverImage).size(px, px * 3 / 2).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(66.dp).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(TvTokens.SurfaceHigh),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, color = TvTokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(status, color = TvTokens.AccentPale, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(ago, color = TvTokens.TextTertiary, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Brush.horizontalGradient(listOf(TvTokens.Accent, TvTokens.AccentGlow))))
            }
        }
    }
}
