package com.example.aniflow.ui.onboarding

import android.content.res.Configuration
import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.aniflow.R
import com.example.aniflow.ui.brand.AniFlowBrand
import com.example.aniflow.ui.brand.AniFlowMark
import com.example.aniflow.ui.brand.AniFlowWordmark
import com.example.aniflow.ui.sound.Sfx
import kotlinx.coroutines.delay

/**
 * Key routing for the first-run overlay. AniFlowLaunch forwards every key here, because the overlay
 * never holds focus (TV Home must keep it underneath), so the panel is driven by a selected index.
 * On phones nothing is pre-selected; the buttons are tapped.
 */
class FirstRunState(val isTv: Boolean) {
    var panel by mutableStateOf(false)
    var selected by mutableIntStateOf(if (isTv) 0 else -1)
    var notice by mutableStateOf<String?>(null)
    internal var activate: (Int) -> Unit = {}

    fun onKey(e: KeyEvent) {
        if (e.type != KeyEventType.KeyDown) return
        if (!panel) { panel = true; return }   // any key during the clip brings the panel up
        when (e.key) {
            Key.DirectionUp, Key.DirectionLeft -> selected = (selected - 1).coerceAtLeast(0)
            Key.DirectionDown, Key.DirectionRight -> selected = (selected + 1).coerceAtMost(OPTIONS.lastIndex)
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> if (selected >= 0) activate(selected)
            Key.Back, Key.Escape -> activate(CONTINUE)
        }
    }

    companion object {
        val OPTIONS = listOf("Create account", "Log in", "Continue without an account")
        const val CONTINUE = 2
    }
}

private const val PANEL_AT_MS = 5_000L
private const val PANEL_VOLUME = 0.75f
private const val LOAD_TIMEOUT_MS = 7_000L

/**
 * First launch after a fresh install, after the logo intro: the welcome clip (the whole ending,
 * 1:30) plays once, start to finish: landscape on TVs, a shot-by-shot reframed 9:16 cut on phones
 * held upright. The sign-up panel comes up after a few seconds (or on any key / tap) and the clip
 * keeps playing behind it, a touch quieter; when it ends its last frame stays as the backdrop. If
 * the clip can't load within a few seconds the panel just shows over the dark stage. [started]
 * stays false while the intro is still on top, so the clip buffers during the intro.
 */
@OptIn(UnstableApi::class)
@Composable
fun FirstRun(state: FirstRunState, started: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val portrait = !state.isTv && LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    var firstFrame by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            // Bundled in the APK (1080p, original bitrate): plays instantly, offline, with no host to fail.
            val clip = if (portrait) R.raw.first_run_clip_portrait else R.raw.first_run_clip
            setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(clip)))
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrame = true }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    if (!state.panel) Sfx.MuteWhileShown()

    state.activate = { i ->
        if (i == FirstRunState.CONTINUE) leaving = true
        else state.notice = "Accounts switch on in an upcoming update. Continue for now: your list and progress stay on this device."
    }
    // Phones' back gesture doesn't always arrive as a key event.
    BackHandler(enabled = !leaving) { if (!state.panel) state.panel = true else state.activate(FirstRunState.CONTINUE) }

    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        player.playWhenReady = true
        val t0 = SystemClock.uptimeMillis()
        while (!firstFrame && !failed && SystemClock.uptimeMillis() - t0 < LOAD_TIMEOUT_MS) delay(100)
        if (!firstFrame) { state.panel = true; player.stop(); return@LaunchedEffect }
        while (!state.panel) {
            if (player.currentPosition >= PANEL_AT_MS) state.panel = true
            delay(100)
        }
    }

    LaunchedEffect(state.panel) {
        if (!state.panel) return@LaunchedEffect
        val v = Animatable(player.volume)
        v.animateTo(PANEL_VOLUME, tween(700)) { player.volume = value }
    }

    val out = remember { Animatable(1f) }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        val v0 = player.volume
        out.animateTo(0f, tween(450)) { player.volume = v0 * value }
        onDone()
    }

    val scrim by animateFloatAsState(if (state.panel) 1f else 0f, tween(700), label = "frScrim")
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = out.value }
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { if (!state.panel) state.panel = true } }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (scrim > 0f) {
            val scrimBrush = if (state.isTv) {
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.9f), 0.55f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.25f))
            } else {
                Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Black.copy(alpha = 0.15f), 0.75f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black.copy(alpha = 0.95f))
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(scrimBrush))
            val slide = Modifier.graphicsLayer {
                alpha = scrim
                if (state.isTv) translationX = -24.dp.toPx() * (1f - scrim) else translationY = 32.dp.toPx() * (1f - scrim)
            }
            if (state.isTv) {
                WelcomePanel(state, slide.align(Alignment.CenterStart).padding(start = 72.dp).width(420.dp))
            } else {
                WelcomePanel(state, slide.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp))
            }
        }
    }
}

@Composable
private fun WelcomePanel(state: FirstRunState, modifier: Modifier) {
    val tv = state.isTv
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AniFlowMark(if (tv) 40.dp else 34.dp)
            AniFlowWordmark(if (tv) 20.dp else 17.dp, Modifier.padding(start = 12.dp))
        }
        Spacer(Modifier.height(if (tv) 28.dp else 20.dp))
        Text("Welcome to AniFlow", color = AniFlowBrand.Paper, fontSize = if (tv) 34.sp else 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Make an account to keep your list and progress, or jump straight in.",
            color = AniFlowBrand.Paper.copy(alpha = 0.72f), fontSize = 15.sp, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(if (tv) 26.dp else 22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FirstRunState.OPTIONS.forEachIndexed { i, label ->
                PanelButton(
                    label, selected = state.selected == i, primary = i == 0,
                    modifier = if (tv) Modifier.width(340.dp) else Modifier.fillMaxWidth(),
                    centred = !tv,
                ) {
                    if (tv) state.selected = i
                    state.activate(i)
                }
            }
        }
        state.notice?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = AniFlowBrand.Orchid, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun PanelButton(label: String, selected: Boolean, primary: Boolean, modifier: Modifier, centred: Boolean, onTap: () -> Unit) {
    val s by animateFloatAsState(if (selected) 1f else 0f, tween(160), label = "frBtn")
    val bg = when {
        selected -> AniFlowBrand.Paper
        primary -> if (centred) AniFlowBrand.Violet else AniFlowBrand.Violet.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = if (centred) 0.14f else 0.10f)
    }
    Box(
        modifier
            .graphicsLayer { val k = 1f + 0.04f * s; scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0.5f) }
            .clip(RoundedCornerShape(if (centred) 16.dp else 12.dp))
            .background(bg)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .padding(horizontal = 20.dp, vertical = if (centred) 16.dp else 14.dp),
        contentAlignment = if (centred) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(label, color = if (selected) AniFlowBrand.Night else AniFlowBrand.Paper, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
