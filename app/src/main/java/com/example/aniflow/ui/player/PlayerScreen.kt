package com.example.aniflow.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.aniflow.DeviceType
import com.example.aniflow.LocalDeviceType
import com.example.aniflow.data.*
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.player.components.QualitySelector
import com.example.aniflow.ui.player.components.SubtitleSelector
import com.example.aniflow.ui.player.components.SpeedSelector
import com.example.aniflow.ui.player.components.AdvancedServerSelector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    animeId: Int,
    episodeNumber: Int,
    repository: AnimeRepository,
    deviceType: DeviceType,
    watchHistoryStore: WatchHistoryStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel {
        PlayerViewModel(repository, watchHistoryStore, SettingsStore(context.applicationContext))
    }
    val coroutineScope = rememberCoroutineScope()
    
    val anime by viewModel.anime.collectAsStateWithLifecycle()
    val episodeList by viewModel.episodeList.collectAsStateWithLifecycle()
    val currentEpisodeIndex by viewModel.currentEpisodeIndex.collectAsStateWithLifecycle()
    val streamingSources by viewModel.streamingSources.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasError by viewModel.hasError.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val selectedSubtitle by viewModel.selectedSubtitle.collectAsStateWithLifecycle()
    val selectedVideoQuality by viewModel.selectedVideoQuality.collectAsStateWithLifecycle()
    val selectedQualityPolicy by viewModel.selectedQualityPolicy.collectAsStateWithLifecycle()

    var showAdvancedServerSelector by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showSpeedSelector by remember { mutableStateOf(false) }
    val isOverlayVisible = showQualitySelector || showSubtitleSelector || showSpeedSelector || showAdvancedServerSelector

    var controlsVisible by remember { mutableStateOf(true) }
    
    BackHandler(enabled = true) {
        if (controlsVisible) {
            onBack()
        } else {
            controlsVisible = true
        }
    }
    
    val focusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    var showRewindIndicator by remember { mutableStateOf(false) }
    var showForwardIndicator by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val isRedesign = remember { context.packageName.endsWith(".redesign") }

    val bandwidthMeter = remember {
        androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(15_000_000L)
            .build()
    }

    val httpDataSourceFactory = remember {
        androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setTransferListener(bandwidthMeter)
    }

    val mediaSourceFactory = remember {
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            AdBlockingDataSourceFactory(httpDataSourceFactory)
        )
    }

    var activeEpisodeIndex by remember { mutableStateOf(currentEpisodeIndex) }
    var hasPlayStarted by remember { mutableStateOf(false) }

    val exoPlayer = remember(animeId) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50_000,
                120_000,
                10_000,
                15_000
            )
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        val isEmulator = android.os.Build.HARDWARE.contains("goldfish") ||
                android.os.Build.HARDWARE.contains("ranchu") ||
                android.os.Build.PRODUCT.contains("sdk_gphone") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for x86")

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            if (isEmulator) {
                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    decoders.sortedBy { decoder ->
                        val name = decoder.name.lowercase()
                        if (name.contains("goldfish") || name.contains("ranchu")) 2
                        else if (name.startsWith("c2.android.") || name.startsWith("omx.google.")) 0
                        else 1
                    }
                }
            }
        }

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setAudioAttributes(audioAttributes, true)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
    }

    val currentEp = episodeList.getOrNull(currentEpisodeIndex)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                viewModel.isBuffering.value = state == Player.STATE_BUFFERING
                viewModel.totalDuration.value = exoPlayer.duration.coerceAtLeast(0L)
                if (state == Player.STATE_READY) {
                    hasPlayStarted = true
                }
                if (state == Player.STATE_ENDED) {
                    val autoPlay = viewModel.autoPlayNextEpisode.value
                    val nextIndex = viewModel.currentEpisodeIndex.value + 1
                    if (autoPlay && nextIndex in viewModel.episodeList.value.indices) {
                        viewModel.playNextEpisode()
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                viewModel.isPlaying.value = playing
                viewModel.onPlaybackStateChanged(playing)
                if (!playing) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration
                    if (pos > 0 && dur > 0 && currentEp != null) {
                        viewModel.saveProgress(animeId, pos, dur, currentEp)
                    }
                }
            }
            override fun onRenderedFirstFrame() {
                viewModel.onFirstFrameRendered()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                viewModel.handlePlaybackError(error, exoPlayer.currentPosition)
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                applyVideoQualityOverride(exoPlayer, viewModel.selectedQualityPolicy.value)
                
                val currentSource = viewModel.selectedSource.value
                if (currentSource != null && currentSource.qualityPolicy is QualityPolicy.Auto) {
                    val heights = mutableListOf<Int>()
                    val videoType = androidx.media3.common.C.TRACK_TYPE_VIDEO
                    for (groupInfo in tracks.groups) {
                        if (groupInfo.type == videoType) {
                            val group = groupInfo.mediaTrackGroup
                            for (i in 0 until group.length) {
                                val format = group.getFormat(i)
                                if (format.height > 0) {
                                    heights.add(format.height)
                                }
                            }
                        }
                    }
                    val sortedHeights = heights.distinct().sortedDescending()
                    if (sortedHeights.isNotEmpty()) {
                        viewModel.availableHeightsForCurrentEndpoint.value = sortedHeights
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (pos > 0 && dur > 0 && currentEp != null) {
                viewModel.saveProgress(animeId, pos, dur, currentEp)
            }
            exoPlayer.release()
        }
    }

    LaunchedEffect(animeId, episodeNumber) {
        viewModel.loadAnimeDetails(animeId, episodeNumber)
    }

    LaunchedEffect(selectedSource) {
        val source = selectedSource
        if (source != null) {
            hasPlayStarted = false
            val currentPos = exoPlayer.currentPosition
            viewModel.hasError.value = false
            viewModel.errorMessage.value = ""
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            viewModel.isBuffering.value = true

            val headers = buildPlaybackHeaders(source, streamingSources?.headers)
            httpDataSourceFactory.setDefaultRequestProperties(headers)

            val resolvedUrl = source.url
            // Only declare HLS when the URL or source metadata confirms it.
            // Treating all proxy URLs as HLS was breaking MP4 and single-quality streams.
            val isHls = source.isM3U8 ||
                resolvedUrl.contains(".m3u8", ignoreCase = true) ||
                resolvedUrl.contains("index.txt", ignoreCase = true)
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(resolvedUrl)
                .apply {
                    if (isHls) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(episodeList.getOrNull(currentEpisodeIndex)?.name ?: "Episode")
                        .setArtist(anime?.title ?: "")
                        .build()
                )

            val subtitles = streamingSources?.subtitles ?: emptyList()
            val subtitleConfigs = subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(
                        if (sub.url.contains(".ass", ignoreCase = true)) MimeTypes.TEXT_SSA
                        else MimeTypes.TEXT_VTT
                    )
                    .setLanguage(sub.lang)
                    .setLabel(sub.label)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)

            val isEpisodeSwitch = viewModel.isEpisodeSwitch.value
            val resumePos = if (isEpisodeSwitch) {
                viewModel.isEpisodeSwitch.value = false
                0L
            } else if (currentPos > 0) {
                currentPos
            } else {
                val savedEntry = viewModel.getSavedProgressEntry(animeId)
                val currentEpNum = episodeList.getOrNull(currentEpisodeIndex)?.number ?: -1
                if (savedEntry != null && savedEntry.episodeNumber == currentEpNum) {
                    savedEntry.progressMs
                } else {
                    0L
                }
            }
            activeEpisodeIndex = currentEpisodeIndex

            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.setPlaybackSpeed(playbackSpeed)
            exoPlayer.prepare()

            if (resumePos > 0) {
                exoPlayer.seekTo(resumePos)
            }
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(selectedQualityPolicy, exoPlayer) {
        applyVideoQualityOverride(exoPlayer, selectedQualityPolicy)
    }

    LaunchedEffect(selectedSubtitle, exoPlayer) {
        val sub = selectedSubtitle
        val currentParams = exoPlayer.trackSelectionParameters
        val parameters = currentParams
            .buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, sub == null)
            .apply {
                if (sub != null) {
                    setPreferredTextLanguage(sub.lang)
                }
            }
            .build()
        if (parameters != currentParams) {
            exoPlayer.trackSelectionParameters = parameters
        }
    }

    LaunchedEffect(exoPlayer, controlsVisible) {
        var lastSaveTime = android.os.SystemClock.elapsedRealtime()
        while (true) {
            val delayMs = if (controlsVisible) 250L else 2000L
            delay(delayMs)
            if (exoPlayer.isPlaying) {
                val pos = exoPlayer.currentPosition
                viewModel.currentPosition.value = pos
                viewModel.totalDuration.value = exoPlayer.duration
                
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastSaveTime >= 15000L) {
                    lastSaveTime = now
                    val currentEp = episodeList.getOrNull(currentEpisodeIndex)
                    if (currentEp != null) {
                        viewModel.saveProgress(animeId, pos, exoPlayer.duration, currentEp)
                    }
                }
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(100)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // ignore
            }
        } else {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(deviceType) {
        val activity = context as? ComponentActivity
        if (deviceType == DeviceType.PHONE) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    
    DisposableEffect(deviceType) {
        onDispose {
            val activity = context as? ComponentActivity
            if (deviceType == DeviceType.PHONE) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
            }
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    val keyCode = event.nativeKeyEvent.keyCode
                    if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                        return@onKeyEvent false
                    }
                    if (!controlsVisible) {
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                controlsVisible = true
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                viewModel.currentPosition.value = exoPlayer.currentPosition
                                return@onKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                controlsVisible = true
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                viewModel.currentPosition.value = exoPlayer.currentPosition
                                return@onKeyEvent true
                            }
                            else -> {
                                controlsVisible = true
                                return@onKeyEvent true
                            }
                        }
                    }
                }
                false
            }
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryAccent)
            }
        } else if (hasError) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = "Error", tint = TertiaryAccent, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(text = errorMessage, color = TextPrimary, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { viewModel.loadStreamingSourcesForIndex(currentEpisodeIndex) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                    ) {
                        Text("Retry")
                    }
                    if (streamingSources != null && streamingSources!!.sources.isNotEmpty()) {
                        Button(
                            onClick = { showAdvancedServerSelector = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryAccent)
                        ) {
                            Text("Switch Server")
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = !isOverlayVisible }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val halfWidth = size.width / 2
                                if (offset.x < halfWidth) {
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                    showRewindIndicator = true
                                    coroutineScope.launch {
                                        delay(650)
                                        showRewindIndicator = false
                                    }
                                } else {
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                    showForwardIndicator = true
                                    coroutineScope.launch {
                                        delay(650)
                                        showForwardIndicator = false
                                    }
                                }
                            }
                        )
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryAccent, modifier = Modifier.size(48.dp))
                    }
                }

                // Centered Double-Tap skip indicator overlays
                if (showRewindIndicator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(100.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Rewind", tint = TextPrimary, modifier = Modifier.size(36.dp))
                            Text("-10s", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                if (showForwardIndicator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(100.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Forward", tint = TextPrimary, modifier = Modifier.size(36.dp))
                            Text("+10s", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // Control Overlays
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(400))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Gradient Background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.7f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )

                        // Main Controls Layout
                        Box(modifier = Modifier.fillMaxSize()) {
                        // Top navigation bar
                        val topBarModifier = if (isRedesign) {
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                                .fillMaxWidth()
                                .glassSurface(shape = RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        } else {
                            Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .padding(16.dp)
                        }
                        Row(
                            modifier = topBarModifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isBackFocused by remember { mutableStateOf(false) }
                            val backButtonModifier = if (isRedesign) {
                                Modifier
                                    .focusGlow(isBackFocused, shape = RoundedCornerShape(20.dp))
                                    .glassSurface(shape = RoundedCornerShape(20.dp), borderWidth = 1.dp, isFocused = isBackFocused)
                                    .clickable { onBack() }
                                    .onFocusChanged { isBackFocused = it.isFocused }
                                    .padding(8.dp)
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isBackFocused) PrimaryAccent else Color.Transparent)
                                    .border(
                                        width = if (isBackFocused) 2.dp else 0.dp,
                                        color = if (isBackFocused) SecondaryAccent else Color.Transparent,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { onBack() }
                                    .onFocusChanged { isBackFocused = it.isFocused }
                                    .padding(8.dp)
                            }
                            Box(
                                modifier = backButtonModifier
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                val epNumberStr = episodeList.getOrNull(currentEpisodeIndex)?.let { ep ->
                                    "Ep ${ep.number} / ${episodeList.size}"
                                } ?: "Ep ${currentEpisodeIndex + 1} / ${episodeList.size}"
                                val epName = episodeList.getOrNull(currentEpisodeIndex)?.name ?: "Episode Info"
                                Text(text = anime?.title ?: "Stream Player", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(text = "$epNumberStr - $epName", color = TextSecondary, fontSize = 14.sp)
                            }
                        }

                        // Center Playback Skip Keys (Phone only)
                        if (deviceType == DeviceType.PHONE) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .wrapContentSize(),
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.playPrevEpisode() }, enabled = currentEpisodeIndex > 0) {
                                    Text("|<", color = if (currentEpisodeIndex > 0) TextPrimary else TextTertiary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                }
                                val playPauseModifier = if (isRedesign) {
                                    Modifier
                                        .size(72.dp)
                                        .glassSurface(shape = RoundedCornerShape(36.dp), borderWidth = 2.dp, isFocused = true)
                                } else {
                                    Modifier
                                        .size(72.dp)
                                        .background(PrimaryAccent, RoundedCornerShape(36.dp))
                                }
                                IconButton(
                                    onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                    modifier = playPauseModifier
                                ) {
                                    if (isPlaying) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(modifier = Modifier.width(6.dp).height(24.dp).background(TextPrimary))
                                            Box(modifier = Modifier.width(6.dp).height(24.dp).background(TextPrimary))
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play",
                                            modifier = Modifier.size(48.dp),
                                            tint = TextPrimary
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.playNextEpisode() }, enabled = currentEpisodeIndex < episodeList.lastIndex) {
                                    Text(">|", color = if (currentEpisodeIndex < episodeList.lastIndex) TextPrimary else TextTertiary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                }
                            }
                        }

                        // Bottom Seek & Controls
                        val bottomBarModifier = if (isRedesign) {
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
                                .fillMaxWidth()
                                .glassSurface(shape = RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .align(Alignment.BottomCenter)
                        }
                        Column(
                            modifier = bottomBarModifier
                        ) {
                            ScopedProgressSlider(
                                viewModel = viewModel,
                                exoPlayer = exoPlayer,
                                deviceType = deviceType,
                                modifier = Modifier.fillMaxWidth().height(if (deviceType == DeviceType.TV) 8.dp else 16.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            if (deviceType == DeviceType.TV) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ScopedProgressText(viewModel = viewModel)
                                }
                                Spacer(Modifier.height(12.dp))
                                
                                // Row 1: Primary Playback Keys
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TvPlayerControlItem(
                                            text = "Prev Ep",
                                            isRedesign = isRedesign,
                                            onClick = { viewModel.playPrevEpisode() }
                                        )
                                        TvPlayerControlItem(
                                            text = "-10s",
                                            isRedesign = isRedesign,
                                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) }
                                        )
                                        TvPlayerControlItem(
                                            text = if (isPlaying) "Pause" else "Play",
                                            focusRequester = playPauseFocusRequester,
                                            isRedesign = isRedesign,
                                            onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
                                        )
                                        TvPlayerControlItem(
                                            text = "+10s",
                                            isRedesign = isRedesign,
                                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) }
                                        )
                                        TvPlayerControlItem(
                                            text = "Next Ep",
                                            isRedesign = isRedesign,
                                            onClick = { viewModel.playNextEpisode() }
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(12.dp))
                                
                                // Row 2: Secondary Settings Keys
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val qualityLabel = when (val q = selectedQualityPolicy) {
                                            is QualityPolicy.Auto -> "Auto"
                                            is QualityPolicy.MaxAvailable -> "Best"
                                            is QualityPolicy.FixedHeight -> "${q.height}p"
                                        }
                                        val availableHeights by viewModel.availableHeightsForCurrentEndpoint.collectAsStateWithLifecycle()
                                        val showQualityButton = availableHeights.size > 1
                                        if (showQualityButton) {
                                            TvPlayerControlItem(
                                                text = qualityLabel,
                                                isRedesign = isRedesign,
                                                onClick = { showQualitySelector = true }
                                            )
                                        }
                                        val serverLabel = selectedSource?.let { "${it.server.value} (${it.audioType})" } ?: "Server"
                                        TvPlayerControlItem(
                                            text = serverLabel,
                                            isRedesign = isRedesign,
                                            onClick = { showAdvancedServerSelector = true }
                                        )
                                        TvPlayerControlItem(
                                            text = "Subtitles",
                                            isRedesign = isRedesign,
                                            onClick = { showSubtitleSelector = true }
                                        )
                                        TvPlayerControlItem(
                                            text = "Speed",
                                            isRedesign = isRedesign,
                                            onClick = { showSpeedSelector = true }
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ScopedProgressText(viewModel = viewModel)
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val qualityLabel = when (val q = selectedQualityPolicy) {
                                            is QualityPolicy.Auto -> "Auto"
                                            is QualityPolicy.MaxAvailable -> "Best"
                                            is QualityPolicy.FixedHeight -> "${q.height}p"
                                        }
                                        val availableHeights by viewModel.availableHeightsForCurrentEndpoint.collectAsStateWithLifecycle()
                                        val showQualityButton = availableHeights.size > 1
                                        PhonePlayerControlItem(text = "-10s", isRedesign = isRedesign, onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) })
                                        PhonePlayerControlItem(text = "+10s", isRedesign = isRedesign, onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) })
                                        if (showQualityButton) {
                                            PhonePlayerControlItem(text = qualityLabel, isRedesign = isRedesign, onClick = { showQualitySelector = true })
                                        }
                                        val serverLabel = selectedSource?.let { "${it.server.value} (${it.audioType})" } ?: "Server"
                                        PhonePlayerControlItem(text = serverLabel, isRedesign = isRedesign, onClick = { showAdvancedServerSelector = true })
                                        PhonePlayerControlItem(text = "Subtitles", isRedesign = isRedesign, onClick = { showSubtitleSelector = true })
                                        PhonePlayerControlItem(text = "Speed", isRedesign = isRedesign, onClick = { showSpeedSelector = true })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    if (showAdvancedServerSelector && streamingSources != null) {
        AdvancedServerSelector(
            sources = streamingSources!!.sources,
            selectedSource = selectedSource,
            onSelectServer = { server, audioType -> viewModel.selectServerAndType(server, audioType) },
            onDismiss = { showAdvancedServerSelector = false }
        )
    }

    val availableHeights by viewModel.availableHeightsForCurrentEndpoint.collectAsStateWithLifecycle()
    if (showQualitySelector) {
        QualitySelector(
            availableHeights = availableHeights,
            selectedQualityPolicy = selectedQualityPolicy,
            onSelectQuality = { viewModel.selectQualityByResolution(it) },
            onDismiss = { showQualitySelector = false }
        )
    }

    if (showSubtitleSelector && streamingSources != null) {
        SubtitleSelector(
            subtitles = streamingSources!!.subtitles,
            selectedSubtitle = selectedSubtitle,
            onSelect = { viewModel.selectSubtitle(it) },
            onDismiss = { showSubtitleSelector = false }
        )
    }

    if (showSpeedSelector) {
        SpeedSelector(
            selectedSpeed = playbackSpeed,
            onSelect = {
                exoPlayer.setPlaybackSpeed(it)
                viewModel.playbackSpeed.value = it
            },
            onDismiss = { showSpeedSelector = false }
        )
    }

    LaunchedEffect(Unit) {
        if (deviceType == DeviceType.TV) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun PhonePlayerControlItem(
    text: String,
    isRedesign: Boolean = false,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .let {
            if (isRedesign) {
                it
                    .glassSurface(shape = RoundedCornerShape(8.dp), borderWidth = 1.dp, isFocused = false)
                    .clickable { onClick() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            } else {
                it.clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp)
            }
        }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = text, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun TvPlayerControlItem(
    text: String,
    focusRequester: FocusRequester? = null,
    isRedesign: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val modifier = Modifier
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .onFocusChanged { isFocused = it.isFocused }
        .focusGlow(isFocused, shape = RoundedCornerShape(8.dp))
        .let {
            if (isRedesign) {
                it.glassSurface(shape = RoundedCornerShape(8.dp), borderWidth = 1.dp, isFocused = isFocused)
            } else {
                it
                    .background(if (isFocused) PrimaryAccent else SurfaceCard)
                    .border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) SecondaryAccent else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
            }
        }
        .clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null
        ) { onClick() }
        .padding(horizontal = 16.dp, vertical = 8.dp)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ScopedProgressSlider(
    viewModel: PlayerViewModel,
    exoPlayer: ExoPlayer,
    deviceType: DeviceType,
    modifier: Modifier = Modifier
) {
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val totalDuration by viewModel.totalDuration.collectAsStateWithLifecycle()

    if (deviceType == DeviceType.TV) {
        val progress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(PrimaryAccent)
            )
        }
    } else {
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = {
                exoPlayer.seekTo(it.toLong())
                viewModel.currentPosition.value = it.toLong()
            },
            valueRange = 0f..(totalDuration.toFloat().coerceAtLeast(1f)),
            colors = SliderDefaults.colors(
                activeTrackColor = PrimaryAccent,
                inactiveTrackColor = SurfaceBorder,
                thumbColor = SecondaryAccent
            ),
            modifier = modifier
        )
    }
}

@Composable
private fun ScopedProgressText(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val totalDuration by viewModel.totalDuration.collectAsStateWithLifecycle()

    Text(
        text = "${formatTime(currentPosition)} / ${formatTime(totalDuration)}",
        color = TextSecondary,
        fontSize = 12.sp,
        modifier = modifier
    )
}

@SuppressLint("DefaultLocale")
private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun buildPlaybackHeaders(
    source: SourceEndpoint,
    globalHeaders: Map<String, String>?
): Map<String, String> {
    val merged = LinkedHashMap<String, String>()
    globalHeaders?.forEach { (key, value) -> merged[key] = value }
    source.headers?.forEach { (key, value) -> merged[key] = value }
    if (!merged.containsKey("Referer")) {
        merged["Referer"] = "https://anilight.live"
    }
    if (!merged.containsKey("Origin")) {
        merged["Origin"] = "https://anilight.live"
    }
    return merged
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun applyVideoQualityOverride(exoPlayer: ExoPlayer, qualityPolicy: QualityPolicy) {
    val params = exoPlayer.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
        .clearVideoSizeConstraints()
    
    val targetHeight = when (qualityPolicy) {
        is QualityPolicy.FixedHeight -> qualityPolicy.height
        else -> 0
    }
    
    if (targetHeight <= 0) {
        exoPlayer.trackSelectionParameters = params.build()
        return
    }
    
    var hasTargetTrack = false
    var matchedWidth = targetHeight * 16 / 9
    var matchedHeight = targetHeight
    var targetGroup: androidx.media3.common.TrackGroup? = null
    var targetTrackIndex = -1
    
    val tracks = exoPlayer.currentTracks
    val videoType = androidx.media3.common.C.TRACK_TYPE_VIDEO
    
    for (groupInfo in tracks.groups) {
        if (groupInfo.type == videoType) {
            val group = groupInfo.mediaTrackGroup
            for (i in 0 until group.length) {
                val format = group.getFormat(i)
                if (kotlin.math.abs(format.height - targetHeight) <= 20) {
                    hasTargetTrack = true
                    matchedWidth = format.width
                    matchedHeight = format.height
                    targetGroup = group
                    targetTrackIndex = i
                    break
                }
            }
        }
        if (hasTargetTrack) break
    }
    
    if (hasTargetTrack && targetGroup != null && targetTrackIndex != -1) {
        params.setMaxVideoSize(matchedWidth, matchedHeight)
        params.setMinVideoSize(matchedWidth, matchedHeight)
        params.addOverride(androidx.media3.common.TrackSelectionOverride(targetGroup, targetTrackIndex))
    } else {
        params.setMaxVideoSize(targetHeight * 16 / 9, targetHeight)
        params.setMinVideoSize(0, 0)
    }
    
    val newParams = params.build()
    if (exoPlayer.trackSelectionParameters != newParams) {
        exoPlayer.trackSelectionParameters = newParams
        if (exoPlayer.playbackState == Player.STATE_READY) {
            exoPlayer.seekTo(exoPlayer.currentPosition)
        }
    }
}

