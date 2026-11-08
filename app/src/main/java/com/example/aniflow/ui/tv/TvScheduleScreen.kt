package com.example.aniflow.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
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
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.ui.focus.primaryFocus
import com.example.aniflow.ui.schedule.*
import com.example.aniflow.ui.tv.home.TvTokens

/**
 * This week's simulcasts. Day chips across the top follow focus (no OK needed); below, the day's
 * episodes grouped by time, with a NOW marker between what has aired and what is coming. Times are
 * the device's own timezone and 12/24 h setting. Countdowns tick every 30 s.
 */
@Composable
fun TvScheduleScreen(schedule: List<AiringAnime>, onOpenDetail: (Int) -> Unit, failed: Boolean = false, onRetry: () -> Unit = {}) {
    val context = LocalContext.current
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val nowSec = rememberNowSec()

    val todayKey = dayKey(nowSec)
    val days = remember(schedule, todayKey) { buildScheduleDays(schedule) }
    var selectedKey by rememberSaveable { mutableIntStateOf(todayKey) }
    val selected = days.firstOrNull { it.key == selectedKey } ?: days.first { it.isToday }
    val todayRequester = remember { FocusRequester() }
    val nextUp = remember(schedule, nowSec) { schedule.firstOrNull { it.airingAt > nowSec } }

    Box(
        Modifier.fillMaxSize().background(TvTokens.Canvas).drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(TvTokens.AccentDeep.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.6f
                )
            )
        }
    ) {
        Column(Modifier.fillMaxSize().padding(start = TvTokens.ContentStart, top = 28.dp, end = 32.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Schedule", color = TvTokens.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("This week's simulcasts, in your local time", color = TvTokens.TextSecondary, fontSize = 14.sp)
                }
                nextUp?.let { NextUpPill(it, nowSec) }
            }
            Spacer(Modifier.height(16.dp))
            // Entering the chip row (from the rail or the grid) lands on Today the first time,
            // then on whichever day was last focused.
            Row(Modifier.focusRestorer(todayRequester).focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                days.forEach { day ->
                    DayChip(
                        day = day,
                        selected = day.key == selected.key,
                        modifier = if (day.isToday) Modifier.primaryFocus(todayRequester) else Modifier,
                        onFocused = { selectedKey = day.key },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    val dir = if (targetState.key > initialState.key) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it / 10 })
                        .togetherWith(fadeOut(tween(120)))
                },
                contentKey = { it.key },
                label = "day",
            ) { day ->
                if (day.items.isEmpty()) {
                    ScheduleEmptyState(weekLoaded = schedule.isNotEmpty(), failed = failed, onRetry = onRetry, tv = true)
                } else {
                    DayGrid(day, nowSec, timeFormat, onOpenDetail)
                }
            }
        }
    }
}

@Composable
private fun DayGrid(day: ScheduleDay, nowSec: Long, timeFormat: java.text.DateFormat, onOpenDetail: (Int) -> Unit) {
    // Today's list gets a NOW divider between what has aired and what is coming, and opens
    // scrolled so the divider sits near the top with one row of context above it.
    val split = if (day.isToday) nowSplit(day.items, nowSec) else -1
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = if (split > 0) ((split - 1) / 3) * 3 else 0)
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp, end = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        day.items.forEachIndexed { i, a ->
            if (i == split) nowItem(nowSec, timeFormat)
            item(key = "${a.mediaId}_${a.episode}") {
                EpisodeCard(a, nowSec, timeFormat.format(java.util.Date(a.airingAt * 1000L)), onOpenDetail)
            }
        }
        if (split == day.items.size) nowItem(nowSec, timeFormat)
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.nowItem(nowSec: Long, timeFormat: java.text.DateFormat) =
    item(key = "now", span = { GridItemSpan(maxLineSpan) }) { NowMarker(timeFormat.format(java.util.Date(nowSec * 1000L))) }

@Composable
private fun NowMarker(time: String) {
    val pulse = rememberInfiniteTransition(label = "now")
    val a by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.size(10.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(LiveDot))
        Spacer(Modifier.width(8.dp))
        Text("NOW · $time", color = LivePink, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(2.dp).background(Brush.horizontalGradient(listOf(LiveDot, Color.Transparent))))
    }
}

@Composable
private fun DayChip(day: ScheduleDay, selected: Boolean, modifier: Modifier, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, TvTokens.FocusSpring, label = "s")
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) TvTokens.AccentGradient else SolidColor(TvTokens.Surface))
            .border(if (focused) 2.dp else 1.dp, if (focused) TvTokens.AccentPale else Color.White.copy(alpha = 0.06f), shape)
            .clickable(interaction, indication = null) { onFocused() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(min = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(day.weekday, color = if (selected) Color.White else TvTokens.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("${day.date}", color = if (selected) Color.White else TvTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (day.items.isEmpty()) "—" else "${day.items.size} eps",
            color = if (selected) Color.White.copy(alpha = 0.85f) else TvTokens.TextTertiary, fontSize = 11.sp
        )
    }
}

@Composable
private fun EpisodeCard(a: AiringAnime, nowSec: Long, time: String, onOpenDetail: (Int) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, TvTokens.FocusSpring, label = "s")
    val bg by animateColorAsState(if (focused) TvTokens.SurfaceHigh else TvTokens.Surface, label = "bg")
    val shape = RoundedCornerShape(12.dp)
    val delta = a.airingAt - nowSec
    val (label, tint) = statusOf(a.airingAt, nowSec, TvTokens.AccentGlow)
    val px = with(LocalDensity.current) { 64.dp.roundToPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(114.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (focused || delta > -LIVE_WINDOW_S) 1f else 0.72f }
            .clip(shape)
            .background(bg)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) TvTokens.AccentGradient else SolidColor(Color.White.copy(alpha = 0.05f)), shape)
            .clickable(interaction, indication = null) { onOpenDetail(a.mediaId) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(a.coverImageUrl).size(px, px * 3 / 2).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(60.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(TvTokens.SurfaceHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$time  ·  EP ${a.episode}",
                color = if (delta > 0) TvTokens.AccentPale else TvTokens.TextSecondary,
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(a.title, color = TvTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun NextUpPill(a: AiringAnime, nowSec: Long) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(TvTokens.Surface)
            .border(1.dp, Brush.horizontalGradient(listOf(TvTokens.AccentGlow.copy(alpha = 0.7f), Color.Transparent)), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("NEXT", color = TvTokens.AccentGlow, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            "${a.title} · EP ${a.episode} · ${countdownText(a.airingAt - nowSec)}",
            color = TvTokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 380.dp)
        )
    }
}
