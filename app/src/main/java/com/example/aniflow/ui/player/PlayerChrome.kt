package com.example.aniflow.ui.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.aniflow.data.model.Episode
import com.example.aniflow.ui.tv.home.TvTokens
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/*
 * Visual layer of the embed player: icons, controls, panels and cards. Holds no playback state —
 * EmbedPlayer owns that and passes values and callbacks in. Black + one purple family (TvTokens).
 */

internal enum class PlayerPanel { Episodes, AudioSubs, Speed, Quality }

internal enum class SkipKind { Intro, Outro }

internal val PlayerSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun speedLabel(rate: Float): String =
    if (abs(rate - 1f) < 0.01f) "Normal" else (if (rate % 1f == 0f) "${rate.toInt()}x" else "${rate}x")

internal fun fmtTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

internal fun episodeLabel(e: Episode?): String {
    if (e == null) return ""
    val name = e.name.takeIf { it.isNotBlank() && !it.equals("Episode ${e.number}", ignoreCase = true) }
    return if (name != null) "E${e.number} · $name" else "Episode ${e.number}"
}

// ---------------------------------------------------------------------------------------------
// Icons. Only material-icons-core ships with the app, so the player glyphs are drawn here.
// ---------------------------------------------------------------------------------------------

internal enum class Glyph { Pause, Back10, Fwd10, SkipNext, Subtitles, Speed }

@Composable
internal fun GlyphIcon(glyph: Glyph, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, tint) }
}

private fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.085f
    when (g) {
        Glyph.Pause -> {
            val bw = w * 0.19f
            val r = CornerRadius(bw * 0.35f)
            drawRoundRect(c, Offset(w * 0.25f, h * 0.17f), Size(bw, h * 0.66f), r)
            drawRoundRect(c, Offset(w * 0.56f, h * 0.17f), Size(bw, h * 0.66f), r)
        }
        Glyph.Back10, Glyph.Fwd10 -> {
            val fwd = g == Glyph.Fwd10
            val inset = w * 0.15f
            // Open circle with the gap at the top, arrowhead at 12 o'clock pointing into the gap.
            drawArc(
                color = c,
                startAngle = if (fwd) -30f else 210f,
                sweepAngle = if (fwd) 300f else -300f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(w - 2 * inset, h - 2 * inset),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            val cx = w / 2
            val a = w * 0.13f
            val dir = if (fwd) 1f else -1f
            val head = Path().apply {
                moveTo(cx + dir * a, inset)
                lineTo(cx - dir * a * 0.35f, inset - a * 0.85f)
                lineTo(cx - dir * a * 0.35f, inset + a * 0.85f)
                close()
            }
            drawPath(head, c)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = c.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = w * 0.3f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            drawContext.canvas.nativeCanvas.drawText("10", cx, h * 0.5f + paint.textSize * 0.36f, paint)
        }
        Glyph.SkipNext -> {
            val p = Path().apply {
                moveTo(w * 0.2f, h * 0.2f); lineTo(w * 0.64f, h * 0.5f); lineTo(w * 0.2f, h * 0.8f); close()
            }
            drawPath(p, c)
            drawRoundRect(c, Offset(w * 0.68f, h * 0.2f), Size(w * 0.12f, h * 0.6f), CornerRadius(w * 0.03f))
        }
        Glyph.Subtitles -> {
            drawRoundRect(c, Offset(w * 0.1f, h * 0.2f), Size(w * 0.8f, h * 0.6f), CornerRadius(w * 0.1f), style = Stroke(sw))
            val y1 = h * 0.45f
            val y2 = h * 0.6f
            drawLine(c, Offset(w * 0.27f, y1), Offset(w * 0.42f, y1), sw, StrokeCap.Round)
            drawLine(c, Offset(w * 0.52f, y1), Offset(w * 0.73f, y1), sw, StrokeCap.Round)
            drawLine(c, Offset(w * 0.27f, y2), Offset(w * 0.62f, y2), sw, StrokeCap.Round)
        }
        Glyph.Speed -> {
            val inset = w * 0.13f
            drawArc(
                color = c, startAngle = 150f, sweepAngle = 240f, useCenter = false,
                topLeft = Offset(inset, inset + h * 0.05f), size = Size(w - 2 * inset, h - 2 * inset),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            val center = Offset(w / 2, h / 2 + h * 0.05f)
            drawLine(c, center, Offset(w * 0.68f, h * 0.33f), sw, StrokeCap.Round)
            drawCircle(c, sw * 0.95f, center)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/**
 * One button style for the whole player. Focused: purple gradient, glow, spring scale.
 * [expandLabel] (TV): the label slides out only while focused, so the row stays a quiet icon strip.
 */
@Composable
internal fun ChromeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    expandLabel: Boolean = false,
    height: Dp = 44.dp,
    textSize: TextUnit = 14.sp,
    enabled: Boolean = true,
    outlined: Boolean = false,
    focusRequester: FocusRequester? = null,
    icon: @Composable (tint: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, TvTokens.FocusSpring, label = "chromeBtn")
    val round = label == null || (expandLabel && !focused)
    val shape = RoundedCornerShape(50)
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    val idle = when {
        outlined -> Color.Black.copy(alpha = 0.45f)
        label != null && !expandLabel -> Color.White.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Row(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .then(if (focused) Modifier.shadow(14.dp, shape, ambientColor = TvTokens.AccentGlow, spotColor = TvTokens.AccentGlow) else Modifier)
            .clip(shape)
            .background(if (focused) TvTokens.AccentGradient else SolidColor(idle))
            .then(if (outlined && !focused) Modifier.border(1.dp, Color.White.copy(alpha = 0.55f), shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .height(height)
            .then(if (round) Modifier.width(height) else Modifier.padding(horizontal = 16.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        icon(tint)
        if (label != null) {
            if (expandLabel) {
                AnimatedVisibility(focused, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                    Text(label, color = tint, fontSize = textSize, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Text(label, color = tint, fontSize = textSize, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
internal fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp) {
    if (playing) GlyphIcon(Glyph.Pause, tint, size)
    else Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = tint, modifier = Modifier.size(size))
}

// ---------------------------------------------------------------------------------------------
// Seek bar
// ---------------------------------------------------------------------------------------------

/**
 * Purple progress with buffer track and glowing thumb. [previewMs] (scrubbing) moves the thumb and
 * shows a time bubble above it. On phones, tap or drag to seek through [onDragPreview]/[onDragCommit].
 */
@Composable
internal fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    previewMs: Long?,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    onDragPreview: ((Long?) -> Unit)? = null,
    onDragCommit: ((Long) -> Unit)? = null,
) {
    val d = durationMs.coerceAtLeast(1)
    val shown = (previewMs ?: positionMs).coerceIn(0, d)
    val frac = shown.toFloat() / d
    val buf = (bufferedMs.toFloat() / d).coerceIn(0f, 1f)
    val thick by animateFloatAsState(if (focused || previewMs != null) 1f else 0f, label = "seekThick")
    val preview by rememberUpdatedState(onDragPreview)
    val commit by rememberUpdatedState(onDragCommit)
    BoxWithConstraints(modifier.height(30.dp)) {
        val gestures = if (onDragCommit != null) {
            Modifier
                .pointerInput(d) {
                    detectTapGestures { o -> commit?.invoke(((o.x / size.width).coerceIn(0f, 1f) * d).toLong()) }
                }
                .pointerInput(d) {
                    var drag = 0L
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = ((o.x / size.width).coerceIn(0f, 1f) * d).toLong(); preview?.invoke(drag) },
                        onDragEnd = { commit?.invoke(drag); preview?.invoke(null) },
                        onDragCancel = { preview?.invoke(null) },
                        onHorizontalDrag = { ch, dx ->
                            ch.consume()
                            drag = (drag + (dx / size.width * d).toLong()).coerceIn(0, d)
                            preview?.invoke(drag)
                        }
                    )
                }
        } else Modifier
        Canvas(Modifier.fillMaxSize().then(gestures)) {
            val th = (4f + 3f * thick).dp.toPx()
            val cy = size.height / 2
            val top = cy - th / 2
            val r = CornerRadius(th / 2)
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, top), Size(size.width, th), r)
            if (buf > 0f) drawRoundRect(Color.White.copy(alpha = 0.30f), Offset(0f, top), Size(size.width * buf, th), r)
            val x = size.width * frac
            if (x > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(TvTokens.AccentDeep, TvTokens.Accent, TvTokens.AccentGlow), startX = 0f, endX = x.coerceAtLeast(1f)),
                    Offset(0f, top), Size(x, th), r
                )
            }
            val tr = (5.5f + 3f * thick).dp.toPx()
            drawCircle(TvTokens.AccentGlow.copy(alpha = 0.22f + 0.25f * thick), tr * 2.1f, Offset(x, cy))
            drawCircle(Color.White, tr, Offset(x, cy))
        }
        if (previewMs != null) {
            val bubble = 74.dp
            val bx = (maxWidth * frac - bubble / 2).coerceIn(0.dp, (maxWidth - bubble).coerceAtLeast(0.dp))
            Text(
                fmtTime(previewMs),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = bx, y = (-34).dp)
                    .width(bubble)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvTokens.Surface.copy(alpha = 0.92f))
                    .border(1.dp, TvTokens.Accent, RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Chrome: top info + bottom controls
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TopInfo(
    title: String,
    subtitle: String,
    endsAt: String?,
    badge: String,
    isTv: Boolean,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(start = if (isTv) 48.dp else 12.dp, end = if (isTv) 48.dp else 20.dp, top = if (isTv) 28.dp else 12.dp, bottom = 36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            ChromeButton(onClick = onBack, height = 40.dp) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = it) }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TvTokens.TextPrimary, fontSize = if (isTv) 22.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = TvTokens.TextSecondary, fontSize = if (isTv) 15.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                badge, color = TvTokens.AccentPale, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(TvTokens.AccentDeep.copy(alpha = 0.55f))
                    .border(1.dp, TvTokens.Accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
            if (endsAt != null) {
                Spacer(Modifier.height(4.dp))
                Text("Ends at $endsAt", color = TvTokens.TextTertiary, fontSize = 13.sp)
            }
        }
    }
}

/** Callbacks from the controls; implemented by EmbedPlayer. */
internal class ChromeActions(
    val playPause: () -> Unit,
    val back10: () -> Unit,
    val fwd10: () -> Unit,
    val next: () -> Unit,
    val openPanel: (PlayerPanel) -> Unit,
    val dragPreview: (Long?) -> Unit,
    val dragCommit: (Long) -> Unit,
)

@Composable
internal fun BottomControls(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    previewMs: Long?,
    playing: Boolean,
    isTv: Boolean,
    hasNext: Boolean,
    hasQuality: Boolean,
    rate: Float,
    seekModifier: Modifier,
    seekFocused: Boolean,
    playFocus: FocusRequester,
    panelFocus: Map<PlayerPanel, FocusRequester>,
    actions: ChromeActions,
    modifier: Modifier = Modifier,
) {
    val shownPos = previewMs ?: positionMs
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.92f))))
            .padding(start = if (isTv) 48.dp else 20.dp, end = if (isTv) 48.dp else 20.dp, top = 48.dp, bottom = if (isTv) 26.dp else 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtTime(shownPos), color = TvTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.width(if (isTv) 64.dp else 52.dp))
            SeekBar(
                positionMs, durationMs, bufferedMs, previewMs,
                modifier = Modifier.weight(1f).then(seekModifier),
                focused = seekFocused,
                onDragPreview = if (isTv) null else actions.dragPreview,
                onDragCommit = if (isTv) null else actions.dragCommit,
            )
            Text(
                "-" + fmtTime(durationMs - shownPos), color = TvTokens.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.End,
                modifier = Modifier.width(if (isTv) 64.dp else 56.dp)
            )
        }
        Spacer(Modifier.height(if (isTv) 12.dp else 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isTv) 10.dp else 6.dp)) {
            val h = if (isTv) 44.dp else 38.dp
            if (isTv) {
                ChromeButton(actions.playPause, height = h, focusRequester = playFocus) { PlayPauseIcon(playing, it, 26.dp) }
                ChromeButton(actions.back10, height = h) { GlyphIcon(Glyph.Back10, it, 28.dp) }
                ChromeButton(actions.fwd10, height = h) { GlyphIcon(Glyph.Fwd10, it, 28.dp) }
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            val expand = isTv
            val ts = if (isTv) 14.sp else 12.sp
            ChromeButton({ actions.openPanel(PlayerPanel.Episodes) }, label = "Episodes", expandLabel = expand, height = h, textSize = ts, focusRequester = panelFocus[PlayerPanel.Episodes]) {
                Icon(Icons.AutoMirrored.Filled.List, null, tint = it, modifier = Modifier.size(22.dp))
            }
            ChromeButton({ actions.openPanel(PlayerPanel.AudioSubs) }, label = "Audio & Subtitles", expandLabel = expand, height = h, textSize = ts, focusRequester = panelFocus[PlayerPanel.AudioSubs]) {
                GlyphIcon(Glyph.Subtitles, it, 24.dp)
            }
            ChromeButton({ actions.openPanel(PlayerPanel.Speed) }, label = "Speed (${speedLabel(rate).lowercase(Locale.US).let { s -> if (s == "normal") "1x" else s }})", expandLabel = expand, height = h, textSize = ts, focusRequester = panelFocus[PlayerPanel.Speed]) {
                GlyphIcon(Glyph.Speed, it, 24.dp)
            }
            if (hasQuality) {
                ChromeButton({ actions.openPanel(PlayerPanel.Quality) }, label = "Quality", expandLabel = expand, height = h, textSize = ts, focusRequester = panelFocus[PlayerPanel.Quality]) {
                    Icon(Icons.Filled.Settings, null, tint = it, modifier = Modifier.size(21.dp))
                }
            }
            if (hasNext) {
                ChromeButton(actions.next, label = "Next Episode", expandLabel = expand, height = h, textSize = ts) {
                    GlyphIcon(Glyph.SkipNext, it, 22.dp)
                }
            }
            if (!isTv) Spacer(Modifier.weight(1f))
        }
    }
}

/** Phone: the big centre cluster, Netflix-mobile style. */
@Composable
internal fun CenterControls(playing: Boolean, actions: ChromeActions, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(44.dp)) {
        PhoneSkip10(forward = false, onClick = actions.back10)
        ChromeButton(actions.playPause, height = 76.dp) { PlayPauseIcon(playing, it, 52.dp) }
        PhoneSkip10(forward = true, onClick = actions.fwd10)
    }
}

/**
 * Motion board tile 23: the arrow turns 35° and springs back, "−10 s" floats up and fades in
 * 720 ms, one CLOCK_TICK per press. Each press restarts it, so rapid presses stay readable.
 */
@Composable
private fun PhoneSkip10(forward: Boolean, onClick: () -> Unit) {
    val haptics = com.example.aniflow.ui.phone.motion.rememberHaptics()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val turn = remember { androidx.compose.animation.core.Animatable(0f) }
    val rise = remember { androidx.compose.animation.core.Animatable(1f) }
    Box(contentAlignment = Alignment.Center) {
        ChromeButton({
            haptics.clockTick()
            scope.launch {
                turn.snapTo(0f)
                turn.animateTo(if (forward) 35f else -35f, tween(110))
                turn.animateTo(0f, com.example.aniflow.ui.phone.motion.PhoneMotion.spring())
            }
            scope.launch {
                rise.snapTo(0f)
                rise.animateTo(1f, tween(720, easing = com.example.aniflow.ui.phone.motion.PhoneMotion.ArriveEasing))
            }
            onClick()
        }, height = 56.dp) {
            GlyphIcon(if (forward) Glyph.Fwd10 else Glyph.Back10, it, 40.dp, Modifier.graphicsLayer { rotationZ = turn.value })
        }
        Text(
            if (forward) "+10 s" else "−10 s",
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            // Readable on a bright frame too.
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 8f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-26).dp)
                .graphicsLayer {
                    translationY = -rise.value * 14.dp.toPx()
                    alpha = if (rise.value >= 1f) 0f else 1f - rise.value
                }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Panels
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SidePanel(title: String, isTv: Boolean, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .width(if (isTv) 380.dp else 340.dp)
            .background(Brush.horizontalGradient(listOf(TvTokens.Surface.copy(alpha = 0.94f), TvTokens.Canvas.copy(alpha = 0.98f))))
            .border(width = 1.dp, brush = Brush.verticalGradient(listOf(TvTokens.AccentDeep, TvTokens.Accent.copy(alpha = 0.2f))), shape = RoundedCornerShape(0.dp))
            .padding(start = 20.dp, end = 20.dp, top = if (isTv) 32.dp else 16.dp)
    ) {
        Text(title, color = TvTokens.TextPrimary, fontSize = if (isTv) 22.sp else 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(TvTokens.AccentGradient))
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp), content = content)
    }
}

internal fun LazyListScope.panelHeader(text: String) {
    item(key = "h_$text") {
        Text(
            text.uppercase(Locale.US), color = TvTokens.AccentPale, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp, start = 4.dp)
        )
    }
}

@Composable
internal fun PanelItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, TvTokens.FocusSpring, label = "panelItem")
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(shape)
            .background(
                when {
                    focused -> TvTokens.AccentGradient
                    selected -> SolidColor(TvTokens.AccentDeep.copy(alpha = 0.35f))
                    else -> SolidColor(Color.White.copy(alpha = 0.04f))
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(
                text, color = if (enabled) TvTokens.TextPrimary else TvTokens.TextTertiary, fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (sub != null) Text(sub, color = if (focused) TvTokens.TextPrimary else TvTokens.AccentPale, fontSize = 12.sp, maxLines = 1)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = if (focused) Color.White else TvTokens.AccentGlow, modifier = Modifier.size(20.dp))
    }
}

/** Episodes drawer: focus (and scroll) start on the episode that is playing. */
@Composable
internal fun EpisodesPanel(episodes: List<Episode>, current: Int, isTv: Boolean, art: String?, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(5) { delay(60); if (runCatching { currentFocus.requestFocus() }.isSuccess) return@LaunchedEffect }
    }
    val context = LocalContext.current
    SidePanel("Episodes", isTv, modifier.padding(0.dp)) {
        itemsIndexed(episodes, key = { _, e -> e.id }) { i, e ->
            val name = e.name.takeIf { it.isNotBlank() && !it.equals("Episode ${e.number}", true) }
            PanelItem(
                text = name ?: "Episode ${e.number}",
                sub = if (i == current) "Now playing" else if (name != null) "Episode ${e.number}" else null,
                selected = i == current,
                onClick = { onPick(i) },
                focusRequester = if (i == current) currentFocus else null,
                leading = {
                    Box(Modifier.width(88.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(TvTokens.SurfaceHigh), contentAlignment = Alignment.Center) {
                        val img = e.thumbnail ?: art
                        if (img != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(img).size(176, 99).crossfade(true).build(),
                                contentDescription = null, contentScale = ContentScale.Crop, alpha = if (e.thumbnail != null) 1f else 0.45f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text("${e.number}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

/** Generic single-choice drawer section rows. Focus starts on the selected row. */
@Composable
internal fun ChoicePanel(
    title: String,
    isTv: Boolean,
    sections: List<Triple<String, List<String>, Int>>,
    note: String? = null,
    onPick: (section: Int, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(5) { delay(60); if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect }
    }
    SidePanel(title, isTv, modifier) {
        var focusGiven = false
        sections.forEachIndexed { si, (header, options, selected) ->
            if (header.isNotEmpty()) panelHeader(header)
            options.forEachIndexed { oi, label ->
                val takeFocus = !focusGiven && (oi == selected || (selected !in options.indices && oi == 0))
                if (takeFocus) focusGiven = true
                item(key = "o_${si}_$oi") {
                    PanelItem(label, selected = oi == selected, onClick = { onPick(si, oi) }, focusRequester = if (takeFocus) first else null)
                }
            }
        }
        if (note != null) item(key = "note") {
            Text(note, color = TvTokens.TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stages and cards
// ---------------------------------------------------------------------------------------------

/** Rotating comet ring around the folded-play mark. */
@Composable
internal fun BrandSpinner(size: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spinner")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "angle")
    val sweep by t.animateFloat(70f, 290f, infiniteRepeatable(tween(950), RepeatMode.Reverse), label = "sweep")
    val pulse by t.animateFloat(0.82f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier.size(size)) {
        val sw = this.size.width * 0.075f
        val box = Size(this.size.width - sw, this.size.height - sw)
        val tl = Offset(sw / 2, sw / 2)
        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, tl, box, style = Stroke(sw))
        rotate(angle) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    (sweep / 360f) * 0.5f to TvTokens.AccentDeep,
                    (sweep / 360f) to TvTokens.AccentGlow,
                    1f to Color.Transparent,
                ),
                startAngle = 0f, sweepAngle = sweep, useCenter = false, topLeft = tl, size = box,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        // Folded play: the triangle's upper half lighter than its lower half.
        val w = this.size.width
        val s = w * 0.30f * pulse
        val cx = w / 2 + s * 0.12f
        val cy = this.size.height / 2
        val upper = Path().apply { moveTo(cx - s * 0.5f, cy - s * 0.6f); lineTo(cx + s * 0.55f, cy); lineTo(cx - s * 0.5f, cy); close() }
        val lower = Path().apply { moveTo(cx - s * 0.5f, cy); lineTo(cx + s * 0.55f, cy); lineTo(cx - s * 0.5f, cy + s * 0.6f); close() }
        drawPath(upper, TvTokens.AccentPale)
        drawPath(lower, TvTokens.Accent)
    }
}

@Composable
internal fun LoadingStage(art: String?, title: String, subtitle: String, status: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize().background(TvTokens.Canvas)) {
        if (art != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(art).size(32, 18).memoryCacheKey("pwash:$art").build(),
                contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.6f, modifier = Modifier.fillMaxSize()
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.9f)))))
        Column(Modifier.align(Alignment.Center).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandSpinner(72.dp)
            Spacer(Modifier.height(22.dp))
            Text(title, color = TvTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (subtitle.isNotEmpty()) Text(subtitle, color = TvTokens.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text(status, color = TvTokens.AccentPale, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CardFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .shadow(24.dp, shape, ambientColor = TvTokens.AccentDeep, spotColor = TvTokens.AccentDeep)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(TvTokens.SurfaceHigh.copy(alpha = 0.97f), TvTokens.Surface.copy(alpha = 0.97f))))
            .border(1.dp, Brush.linearGradient(listOf(TvTokens.Accent.copy(alpha = 0.7f), TvTokens.AccentDeep.copy(alpha = 0.2f))), shape)
    ) { content() }
}

@Composable
internal fun SkipButton(kind: SkipKind?, focusRequester: FocusRequester, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val last = remember { arrayOf(SkipKind.Intro) }
    if (kind != null) last[0] = kind
    ChromeButton(
        onClick, modifier, label = if (last[0] == SkipKind.Intro) "Skip Intro" else "Skip Outro",
        height = 46.dp, textSize = 16.sp, outlined = true, focusRequester = focusRequester
    ) { GlyphIcon(Glyph.SkipNext, it, 20.dp) }
}

/** Shown while the credits roll. [countdown] fills the primary button as it runs down. */
@Composable
internal fun UpNextCard(
    episode: Episode?,
    art: String?,
    countdown: Int?,
    focusRequester: FocusRequester,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = remember { arrayOfNulls<Episode>(1) }
    if (episode != null) last[0] = episode
    val e = last[0]
    val context = LocalContext.current
    val fill by animateFloatAsState(
        targetValue = if (countdown == null) 0f else ((10 - countdown + 1) / 10f).coerceIn(0f, 1f),
        animationSpec = tween(1_000, easing = LinearEasing), label = "countdown"
    )
    CardFrame(modifier.width(340.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 7f)) {
                val img = e?.thumbnail ?: art
                if (img != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(img).size(640, 280).crossfade(true).build(),
                        contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, TvTokens.SurfaceHigh))))
                Text(
                    "UP NEXT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(TvTokens.Accent).padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                Text(episodeLabel(e), color = TvTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        ChromeButton(onPlay, label = if (countdown != null) "Next in $countdown" else "Next Episode", height = 42.dp, textSize = 15.sp, outlined = true, focusRequester = focusRequester) {
                            Icon(Icons.Filled.PlayArrow, null, tint = it, modifier = Modifier.size(22.dp))
                        }
                        if (fill > 0f) {
                            Box(
                                Modifier.matchParentSize().padding(horizontal = 18.dp).padding(bottom = 4.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Box(Modifier.fillMaxWidth(fill).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.85f)))
                            }
                        }
                    }
                    ChromeButton(onDismiss, label = "Watch credits", height = 42.dp, textSize = 14.sp) {}
                }
            }
        }
    }
}

/** Centre dialog used by the end, still-watching and failure states. */
@Composable
internal fun CenterCard(
    title: String,
    body: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    secondary: List<Pair<String, () -> Unit>> = emptyList(),
    showSpinner: Boolean = false,
) {
    // Capped, not fixed: a portrait phone is ~411dp wide and a fixed 520dp card ran off both edges.
    CardFrame(modifier.padding(horizontal = 20.dp).widthIn(max = 520.dp)) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSpinner) { BrandSpinner(48.dp); Spacer(Modifier.height(12.dp)) }
            Text(title, color = TvTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, color = TvTokens.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChromeButton(onPrimary, label = primaryLabel, height = 44.dp, textSize = 15.sp, outlined = true, focusRequester = focusRequester) {
                    Icon(if (primaryLabel.startsWith("Retry") || primaryLabel.startsWith("Replay")) Icons.Filled.Refresh else Icons.Filled.PlayArrow, null, tint = it, modifier = Modifier.size(20.dp))
                }
                secondary.forEach { (label, action) -> ChromeButton(action, label = label, height = 44.dp, textSize = 14.sp) {} }
            }
        }
    }
}

/** Hidden-controls seek feedback: side badge with the jump size, and a slim bar with the target. */
@Composable
internal fun SeekHud(targetMs: Long, positionMs: Long, durationMs: Long, bufferedMs: Long, isTv: Boolean, modifier: Modifier = Modifier) {
    val delta = targetMs - positionMs
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.align(if (delta >= 0) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = if (isTv) 96.dp else 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                    .border(1.5.dp, TvTokens.AccentGlow.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(if (delta >= 0) Glyph.Fwd10 else Glyph.Back10, Color.White, 46.dp) }
            Spacer(Modifier.height(8.dp))
            Text((if (delta >= 0) "+" else "−") + fmtTime(abs(delta)), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(start = if (isTv) 48.dp else 20.dp, end = if (isTv) 48.dp else 20.dp, top = 40.dp, bottom = if (isTv) 30.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(fmtTime(targetMs), color = Color.White, fontSize = 13.sp, modifier = Modifier.width(60.dp))
            SeekBar(positionMs, durationMs, bufferedMs, targetMs, Modifier.weight(1f), focused = true)
            Text("-" + fmtTime(durationMs - targetMs), color = TvTokens.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
        }
    }
}

/** Phone brightness / volume swipe feedback. */
@Composable
internal fun LevelHud(label: String, level: Float, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$label ${(level * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(140.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(TvTokens.AccentGradient))
        }
    }
}

@Composable
internal fun PlayerToast(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(TvTokens.Surface.copy(alpha = 0.92f))
            .border(1.dp, TvTokens.Accent.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 9.dp)
    )
}

/**
 * Phone touch layer above the WebView, so no touch ever reaches the page's ad overlays.
 * Tap = controls, double-tap left/right third = seek, centre = play/pause, vertical swipe left
 * half = brightness, right half = volume.
 */
@Composable
internal fun GestureLayer(
    onTap: () -> Unit,
    onDoubleTap: (side: Int) -> Unit,
    onLevel: (side: Int, delta: Float) -> Unit,
    onLevelEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tap by rememberUpdatedState(onTap)
    val dbl by rememberUpdatedState(onDoubleTap)
    val level by rememberUpdatedState(onLevel)
    val levelEnd by rememberUpdatedState(onLevelEnd)
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { o ->
                        dbl(
                            when {
                                o.x < size.width / 3f -> -1
                                o.x > size.width * 2f / 3f -> 1
                                else -> 0
                            }
                        )
                    }
                )
            }
            .pointerInput(Unit) {
                var side = 0
                detectVerticalDragGestures(
                    onDragStart = { o -> side = if (o.x < size.width / 2f) -1 else 1 },
                    onDragEnd = { levelEnd() },
                    onDragCancel = { levelEnd() },
                    onVerticalDrag = { ch, dy -> ch.consume(); level(side, -dy / (size.height * 0.85f)) }
                )
            }
    )
}

/** Swallows touches behind an open panel and closes it. */
@Composable
internal fun Scrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    )
}
