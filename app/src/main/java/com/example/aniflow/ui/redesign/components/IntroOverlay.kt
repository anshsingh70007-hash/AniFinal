package com.example.aniflow.ui.redesign.components

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLoader {
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    fun setLoaded(loaded: Boolean) {
        _isLoaded.value = loaded
    }
}

enum class IntroState {
    FIRST_ANIMATION,
    SECOND_ANIMATION,
    FINISHED
}

@OptIn(UnstableApi::class)
@Composable
fun IntroOverlay(
    onStartFadeOut: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var introState by remember { mutableStateOf(IntroState.FIRST_ANIMATION) }
    val isAppLoaded by AppLoader.isLoaded.collectAsState()

    var startFadeOut by remember { mutableStateOf(false) }

    // Single player instance for 60fps high performance
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f // Start muted for the first video
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Preload both videos as a playlist at startup
    LaunchedEffect(Unit) {
        val rawUri1 = Uri.parse("android.resource://${context.packageName}/${com.example.aniflow.R.raw.intro_first}")
        val rawUri2 = Uri.parse("android.resource://${context.packageName}/${com.example.aniflow.R.raw.intro_second}")
        
        player.addMediaItem(MediaItem.fromUri(rawUri1))
        player.addMediaItem(MediaItem.fromUri(rawUri2))
        player.prepare()
        player.playWhenReady = true
    }

    // Set up player listener to manage gapless transition and manual looping
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player.currentMediaItemIndex == 1) {
                    if (isAppLoaded) {
                        player.volume = 1f // Unmute second video
                        introState = IntroState.SECOND_ANIMATION
                    } else {
                        // Not loaded yet, loop first video by seeking back to index 0
                        player.seekTo(0, 0)
                        player.play()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (player.currentMediaItemIndex == 1) {
                        introState = IntroState.FINISHED
                    }
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Start a 2-second timer to trigger visual fade-out once the second video starts
    LaunchedEffect(introState) {
        if (introState == IntroState.SECOND_ANIMATION) {
            delay(2000)
            player.clearVideoSurface() // Freeze last active frame
            startFadeOut = true
            onStartFadeOut()
        }
    }

    if (introState == IntroState.FINISHED) {
        onFinished()
        return
    }

    // Alpha animation for visual fade out (800ms for fast reveal to show pop animation)
    val fadeAlpha by animateFloatAsState(
        targetValue = if (startFadeOut) 0f else 1f,
        animationSpec = tween(durationMillis = 800),
        label = "fadeAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(fadeAlpha)
            .pointerInput(startFadeOut) {
                // If fade out has not started, consume all touches to prevent hitting the app.
                // Once it fades out, let touches pass through to the app underneath.
                if (!startFadeOut) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(com.example.aniflow.R.layout.intro_player_view, null) as PlayerView
                view.player = player
                view
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
