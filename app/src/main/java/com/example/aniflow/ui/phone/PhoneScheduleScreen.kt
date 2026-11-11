package com.example.aniflow.ui.phone

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.aniflow.ui.schedule.*
import com.example.aniflow.ui.tv.home.TvTokens

/**
 * Phone twin of the TV schedule: swipeable day chips, a timeline with a NOW divider, live
 * countdowns. Opens on Today, scrolled to what's next.
 */
@Composable
fun PhoneScheduleScreen(schedule: List<AiringAnime>, onOpenDetail: (Int) -> Unit, failed: Boolean = false, onRetry: () -> Unit = {}) {
    val context = LocalContext.current
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val nowSec = rememberNowSec()
    val todayKey = dayKey(nowSec)
    val days = remember(schedule, todayKey) { buildScheduleDays(schedule) }
    var selectedKey by rememberSaveable { mutableIntStateOf(todayKey) }
    val selected = days.firstOrNull { it.key == selectedKey } ?: days.first { it.isToday }
    val nextUp = remember(schedule, nowSec) { schedule.firstOrNull { it.airingAt > nowSec } }

    Box(
        Modifier.fillMaxSize().background(TvTokens.Canvas).drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(TvTokens.AccentDeep.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width, 0f), radius = size.width
                )
            )
        }
    ) {
        // The phone top bar shows "Schedule"; this keeps only the subtitle.
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 2.dp)) {
                Text("This week's simulcasts, in your local time", color = TvTokens.TextSecondary, fontSize = 13.sp)
                nextUp?.let {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TvTokens.Surface)
                            .border(1.dp, Brush.horizontalGradient(listOf(TvTokens.AccentGlow.copy(alpha = 0.6f), Color.Transparent)), RoundedCornerShape(14.dp))
                            .clickable { onOpenDetail(it.mediaId) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NEXT", color = TvTokens.AccentGlow, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${it.title} · EP ${it.episode}", color = TvTokens.TextPrimary, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(countdownText(it.airingAt - nowSec), color = TvTokens.AccentPale, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val chipState = rememberLazyListState(initialFirstVisibleItemIndex = 0)
            LazyRow(
                state = chipState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(days, key = { it.key }) { day ->
                    PhoneDayChip(day, day.key == selected.key) { selectedKey = day.key }
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    val dir = if (targetState.key > initialState.key) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it / 8 })
                        .togetherWith(fadeOut(tween(120)))
                },
                contentKey = { it.key },
                label = "day",
            ) { day ->
                if (day.items.isEmpty()) {
                    ScheduleEmptyState(weekLoaded = schedule.isNotEmpty(), failed = failed, onRetry = onRetry, tv = false)
                } else {
                    val split = if (day.isToday) nowSplit(day.items, nowSec) else -1
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (split - 1).coerceAtLeast(0))
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = LocalPhoneBottomInset.current + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        day.items.forEachIndexed { i, a ->
                            if (i == split) item(key = "now") { PhoneNowMarker(timeFormat.format(java.util.Date(nowSec * 1000L))) }
                            item(key = "${a.mediaId}_${a.episode}") {
                                PhoneEpisodeRow(a, nowSec, timeFormat.format(java.util.Date(a.airingAt * 1000L))) { onOpenDetail(a.mediaId) }
                            }
                        }
                        if (split == day.items.size) item(key = "now") { PhoneNowMarker(timeFormat.format(java.util.Date(nowSec * 1000L))) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneDayChip(day: ScheduleDay, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .background(if (selected) TvTokens.AccentGradient else SolidColor(TvTokens.Surface))
            .border(1.dp, if (selected) TvTokens.AccentPale.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.06f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .widthIn(min = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(day.weekday, color = if (selected) Color.White else TvTokens.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("${day.date}", color = if (selected) Color.White else TvTokens.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (day.items.isEmpty()) "—" else "${day.items.size}",
            color = if (selected) Color.White.copy(alpha = 0.85f) else TvTokens.TextTertiary, fontSize = 11.sp
        )
    }
}

@Composable
private fun PhoneNowMarker(time: String) {
    val pulse = rememberInfiniteTransition(label = "now")
    val a by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(9.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(LiveDot))
        Spacer(Modifier.width(8.dp))
        Text("NOW · $time", color = LivePink, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(2.dp).background(Brush.horizontalGradient(listOf(LiveDot, Color.Transparent))))
    }
}

@Composable
private fun PhoneEpisodeRow(a: AiringAnime, nowSec: Long, time: String, onClick: () -> Unit) {
    val (label, tint) = statusOf(a.airingAt, nowSec, TvTokens.AccentGlow)
    val upcoming = a.airingAt > nowSec
    val px = with(LocalDensity.current) { 56.dp.roundToPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (upcoming || nowSec - a.airingAt < LIVE_WINDOW_S) 1f else 0.78f }
            .clip(RoundedCornerShape(14.dp))
            .background(TvTokens.Surface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(a.coverImageUrl).size(px, px * 3 / 2).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(10.dp)).background(TvTokens.SurfaceHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$time  ·  EP ${a.episode}",
                color = if (upcoming) TvTokens.AccentPale else TvTokens.TextSecondary,
                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(a.title, color = TvTokens.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
