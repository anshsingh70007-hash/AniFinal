package com.example.aniflow.ui.tv.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.aniflow.data.model.TrailerClip
import kotlinx.coroutines.delay

/**
 * Full-screen trailer layer. Grows out of where it was opened from ([origin], as a fraction of the
 * screen), plays one clip at a time with its own WebView, and lets ◀ ▶ switch between seasons.
 * OK pauses, BACK closes. Controls hide after 3 s without a key press.
 *
 * Each clip's YouTube id comes from its own AniList entry ([TrailerClip]), so a season tab can only
 * ever play that season's trailer. [fallbacks] are extra ids to try for a clip YouTube refuses.
 */
@Composable
fun TrailerViewer(
    clips: List<TrailerClip>,
    startIndex: Int,
    origin: TransformOrigin = TransformOrigin(0.85f, 0.6f),
    fallbacks: Map<Int, String> = emptyMap(),
    onClose: () -> Unit,
) {
    if (clips.isEmpty()) return
    val context = LocalContext.current
    val host = remember { TrailerHost(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> host.onHostPause()
                Lifecycle.Event.ON_RESUME -> host.onHostResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); host.destroy() }
    }

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, clips.lastIndex)) }
    val clip = clips[index]
    var failed by remember(index) { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var lastKey by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    LaunchedEffect(lastKey) { controls = true; delay(3_000); controls = false }

    // Open: a panel grows from the card; the video is attached only once it is full size, because a
    // WebView's video surface does not follow Compose scale/alpha.
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) { open.animateTo(1f, tween(380, easing = TvEase)) }
    LaunchedEffect(closing) {
        if (closing) { host.stop(); open.animateTo(0f, tween(260, easing = TvEase)); onClose() }
    }
    val opened = open.value >= 0.999f && !closing

    LaunchedEffect(index, opened) {
        if (!opened) return@LaunchedEffect
        failed = false
        val ids = listOfNotNull(clip.youtubeId, fallbacks[clip.animeId]?.takeIf { it != clip.youtubeId })
        if (host.playFirst(ids) == null) failed = true
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(enabled = !closing) { closing = true }

    val stillAlpha by animateFloatAsState(if (host.isPlaying && opened) 0f else 1f, tween(600), label = "still")
    val uiAlpha by animateFloatAsState(if (controls || !host.isPlaying || host.isPaused) 1f else 0f, tween(300), label = "ui")

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key != Key.Back
                lastKey++
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> { host.togglePause(); true }
                    Key.DirectionLeft -> { if (index > 0) { host.stop(); index-- }; true }
                    Key.DirectionRight -> { if (index < clips.lastIndex) { host.stop(); index++ }; true }
                    Key.Back -> false
                    else -> true // swallow UP/DOWN so focus never leaks to the screen underneath
                }
            }
            .graphicsLayer {
                val v = open.value
                scaleX = 0.28f + 0.72f * v; scaleY = 0.28f + 0.72f * v
                alpha = (v * 1.6f).coerceAtMost(1f)
                transformOrigin = origin
            }
            .clip(RoundedCornerShape(((1f - open.value) * 24).dp))
            .background(Color.Black)
    ) {
        if (opened) {
            host.webView?.let { web ->
                key(web) {
                    AndroidView(factory = { web.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        // Thumbnail sits above the video until it actually plays (no black flash, no YouTube chrome).
        AsyncImage(
            model = ImageRequest.Builder(context).data(clip.thumbnail).size(960, 540).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = stillAlpha }
        )
        if (opened && !host.isPlaying && !failed) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = TvTokens.AccentGlow, strokeWidth = 3.dp)
        }
        if (failed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This trailer can't play here", color = TvTokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Nothing else plays in its place." + if (clips.size > 1) " Try another with ◀ ▶" else "", color = TvTokens.TextSecondary, fontSize = 13.sp)
            }
        }

        // Chrome: title on top, season tabs + hints at the bottom.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = uiAlpha }) {
            Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
            Box(Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
            Column(Modifier.padding(start = 48.dp, top = 30.dp)) {
                Text(clip.label, color = TvTokens.AccentPale, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(clip.meta.ifBlank { "Trailer" }, color = TvTokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, bottom = 26.dp)) {
                if (clips.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        clips.forEachIndexed { i, c ->
                            val active = i == index
                            val w by animateDpAsState(if (active) 28.dp else 0.dp, TvTokens.PillSpring, label = "tabU")
                            Column {
                                Text(c.label, color = if (active) TvTokens.TextPrimary else TvTokens.TextTertiary, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold)
                                Spacer(Modifier.height(5.dp))
                                Box(Modifier.width(w).height(2.dp).clip(RoundedCornerShape(1.dp)).background(TvTokens.AccentGlow))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Hint("OK", if (host.isPaused) "Play" else "Pause")
                    if (clips.size > 1) Hint("◀ ▶", "Season")
                    Hint("BACK", "Close")
                }
            }
        }
    }
}

@Composable
private fun Hint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            key, color = TvTokens.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Text(label, color = TvTokens.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
