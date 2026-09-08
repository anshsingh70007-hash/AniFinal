package com.example.aniflow.ui.redesign

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import android.view.KeyEvent
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import coil3.compose.AsyncImage
import com.example.aniflow.ui.redesign.audio.BleachBgmManager
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.UserFeedback
import androidx.compose.ui.text.font.FontStyle
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.components.GlassCard
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.premiumGlassPanel
import com.example.aniflow.ui.redesign.theme.BleachTybwTokens
import com.example.aniflow.ui.redesign.theme.isBleachTybw
import com.example.aniflow.ui.redesign.components.BleachReiatsuParticles
import com.example.aniflow.ui.redesign.components.BleachBladeSlashSheen
import com.example.aniflow.ui.redesign.components.BleachLightingBorderGlow
import com.example.aniflow.ui.redesign.components.BleachElectricLightning
import com.example.aniflow.ui.redesign.components.BleachSpiritualVortex
import com.example.aniflow.ui.redesign.components.BleachTributePill
import com.example.aniflow.ui.redesign.components.BleachBankaiButton
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.aniflow.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow

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
    val scrollState = rememberScrollState()

    val activeSections = remember(
        trending.size, popular.size, seasonal.size, airing.size,
        topRated.size, upcoming.size, recentlyUpdated.size,
        actionAnime.size, romanceAnime.size, history.size, userFeedbackList.size
    ) {
        buildList {
            if (trending.isNotEmpty()) add("spotlight")
            if (history.isNotEmpty()) add("continue_watching")
            if (airing.isNotEmpty()) add("airing")
            if (userFeedbackList.isNotEmpty()) add("user_choice")
            if (trending.isNotEmpty()) add("trending")
            if (recentlyUpdated.isNotEmpty()) add("recently_updated")
            if (popular.isNotEmpty()) add("popular")
            if (topRated.isNotEmpty()) add("top_rated")
            if (seasonal.isNotEmpty()) add("seasonal")
            if (actionAnime.isNotEmpty()) add("action")
            if (romanceAnime.isNotEmpty()) add("romance")
            if (upcoming.isNotEmpty()) add("upcoming")
        }
    }

    val sectionRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String): FocusRequester = sectionRequesters.getOrPut(key) { FocusRequester() }

    fun onDownFor(key: String): () -> Boolean = {
        val currentIndex = activeSections.indexOf(key)
        if (currentIndex in 0 until activeSections.lastIndex) {
            val nextKey = activeSections[currentIndex + 1]
            try {
                requesterFor(nextKey).requestFocus()
            } catch (_: Exception) {}
            true
        } else {
            // Reached bottom of screen: consume DPAD_DOWN so it NEVER wraps around to the top!
            true
        }
    }

    fun onUpFor(key: String): () -> Boolean = {
        val currentIndex = activeSections.indexOf(key)
        if (currentIndex > 0) {
            val prevKey = activeSections[currentIndex - 1]
            try {
                requesterFor(prevKey).requestFocus()
            } catch (_: Exception) {}
            true
        } else {
            // Reached top section (spotlight): do not consume DPAD_UP so it flows up to TvTopNavBar!
            false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.SECTION_GAP_TV.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        // Spotlight Carousel (Full-bleed except page margins handled inside)
        if (trending.isNotEmpty()) {
            val isHeroVisible = remember {
                derivedStateOf { scrollState.value < 400 }
            }
            RedesignTvSpotlight(
                anime = trending.first(),
                onClick = { onAnimeClick(trending.first()) },
                onExpandedChanged = {},
                isVisible = isHeroVisible.value,
                focusRequester = requesterFor("spotlight"),
                onDown = onDownFor("spotlight"),
                onUp = onUpFor("spotlight")
            )
        }

        // Continue Watching
        if (history.isNotEmpty()) {
            RedesignTvContinueWatchingRow(
                title = "Continue Watching",
                list = history,
                onHistoryClick = onHistoryClick,
                rowFocusRequester = requesterFor("continue_watching"),
                onDown = onDownFor("continue_watching"),
                onUp = onUpFor("continue_watching")
            )
        }

        // Airing schedule
        if (airing.isNotEmpty()) {
            var lastAiringIndex by remember { mutableIntStateOf(0) }
            val airingBringIntoView = remember { BringIntoViewRequester() }
            val airingScope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(airingBringIntoView)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> onDownFor("airing")()
                                KeyEvent.KEYCODE_DPAD_UP -> onUpFor("airing")()
                                else -> false
                            }
                        } else false
                    }
            ) {
                RedesignTvSectionHeader(title = "Airing Schedule")
                Spacer(Modifier.height(GlassTokens.ROW_TITLE_BOTTOM.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.CARD_GAP_TV.dp),
                    contentPadding = PaddingValues(horizontal = GlassTokens.OVERSCAN_MARGIN_TV.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(airing, key = { _, item -> "airing_${item.mediaId}" }) { index, item ->
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
                            },
                            focusRequester = if (index == lastAiringIndex.coerceIn(0, airing.lastIndex)) requesterFor("airing") else null,
                            onCardFocused = {
                                lastAiringIndex = index
                                airingScope.launch { airingBringIntoView.bringIntoView() }
                            }
                        )
                    }
                }
            }
        }

        // User's Choice Row
        if (userFeedbackList.isNotEmpty()) {
            var lastFeedbackIndex by remember { mutableIntStateOf(0) }
            val feedbackBringIntoView = remember { BringIntoViewRequester() }
            val feedbackScope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(feedbackBringIntoView)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> onDownFor("user_choice")()
                                KeyEvent.KEYCODE_DPAD_UP -> onUpFor("user_choice")()
                                else -> false
                            }
                        } else false
                    }
            ) {
                RedesignTvSectionHeader(title = "User's Choice")
                Spacer(Modifier.height(GlassTokens.ROW_TITLE_BOTTOM.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.CARD_GAP_TV.dp),
                    contentPadding = PaddingValues(horizontal = GlassTokens.OVERSCAN_MARGIN_TV.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(userFeedbackList, key = { _, it -> "${it.anime.id}_${it.timestamp}" }) { index, feedback ->
                        var isFocused by remember { mutableStateOf(false) }
                        val bringIntoViewRequester = remember { BringIntoViewRequester() }
                        val coroutineScope = rememberCoroutineScope()
                        val feedbackRequester = if (index == lastFeedbackIndex.coerceIn(0, userFeedbackList.lastIndex)) requesterFor("user_choice") else null
                        val focusMod = if (feedbackRequester != null) Modifier.focusRequester(feedbackRequester) else Modifier

                        GlassCard(
                            onClick = {
                                onAnimeClick(feedback.anime.toAnime())
                            },
                            modifier = Modifier
                                .width(360.dp)
                                .height(110.dp)
                                .then(focusMod)
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .onFocusChanged {
                                    isFocused = it.isFocused
                                    if (it.isFocused) {
                                        lastFeedbackIndex = index
                                        feedbackScope.launch { feedbackBringIntoView.bringIntoView() }
                                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                                    }
                                },
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

        // Trending Row
        RedesignTvSectionRow(
            title = "Trending Now",
            list = trending,
            onAnimeClick = onAnimeClick,
            rowFocusRequester = requesterFor("trending"),
            onDown = onDownFor("trending"),
            onUp = onUpFor("trending")
        )

        // Recently Updated
        if (recentlyUpdated.isNotEmpty()) {
            RedesignTvSectionRow(
                title = "Recently Updated",
                list = recentlyUpdated,
                onAnimeClick = onAnimeClick,
                rowFocusRequester = requesterFor("recently_updated"),
                onDown = onDownFor("recently_updated"),
                onUp = onUpFor("recently_updated")
            )
        }

        // Popular Row
        RedesignTvSectionRow(
            title = "Popular All Time",
            list = popular,
            onAnimeClick = onAnimeClick,
            rowFocusRequester = requesterFor("popular"),
            onDown = onDownFor("popular"),
            onUp = onUpFor("popular")
        )

        // Top Rated Row
        if (topRated.isNotEmpty()) {
            RedesignTvSectionRow(
                title = "Top Rated Hits",
                list = topRated,
                onAnimeClick = onAnimeClick,
                rowFocusRequester = requesterFor("top_rated"),
                onDown = onDownFor("top_rated"),
                onUp = onUpFor("top_rated")
            )
        }

        // Seasonal Row
        RedesignTvSectionRow(
            title = "Seasonal Hits",
            list = seasonal,
            onAnimeClick = onAnimeClick,
            rowFocusRequester = requesterFor("seasonal"),
            onDown = onDownFor("seasonal"),
            onUp = onUpFor("seasonal")
        )

        // Action
        if (actionAnime.isNotEmpty()) {
            RedesignTvSectionRow(
                title = "Action & Adventure",
                list = actionAnime,
                onAnimeClick = onAnimeClick,
                rowFocusRequester = requesterFor("action"),
                onDown = onDownFor("action"),
                onUp = onUpFor("action")
            )
        }

        // Romance
        if (romanceAnime.isNotEmpty()) {
            RedesignTvSectionRow(
                title = "Romance Picks",
                list = romanceAnime,
                onAnimeClick = onAnimeClick,
                rowFocusRequester = requesterFor("romance"),
                onDown = onDownFor("romance"),
                onUp = onUpFor("romance")
            )
        }

        // Upcoming
        if (upcoming.isNotEmpty()) {
            RedesignTvSectionRow(
                title = "Upcoming Season",
                list = upcoming,
                onAnimeClick = onAnimeClick,
                rowFocusRequester = requesterFor("upcoming"),
                onDown = onDownFor("upcoming"),
                onUp = onUpFor("upcoming")
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun RedesignTvSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GlassTokens.OVERSCAN_MARGIN_TV.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .background(PrimaryAccent, RoundedCornerShape(2.dp))
            )
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        if (onViewAll != null) {
            Text(
                text = "View All",
                color = GlassTokens.GlowCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onViewAll() }
            )
        }
    }
}

@Composable
fun RedesignTvSpotlight(
    anime: Anime,
    onClick: () -> Unit,
    onExpandedChanged: (Boolean) -> Unit = {},
    isVisible: Boolean = true,
    focusRequester: FocusRequester? = null,
    onDown: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null
) {
    val isBleach = anime.isBleachTybw()
    var isFocused by remember { mutableStateOf(false) }
    var isTrailerMuted by remember { mutableStateOf(true) }

    val bannerHeight = 310.dp
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusMod = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    val btnGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                GlassTokens.GlowCyan,
                GlassTokens.GlowPurple,
                GlassTokens.GlowCyan
            )
        )
    }

    val btnScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "btnScale"
    )

    val glowColors = if (isBleach) listOf(
        BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.65f),
        BleachTybwTokens.BankaiGold.copy(alpha = 0.45f),
        BleachTybwTokens.ReiatsuBlood.copy(alpha = 0.25f),
        Color.Transparent
    ) else null

    val focusedBorder = if (isBleach) Brush.linearGradient(
        listOf(
            BleachTybwTokens.ReiatsuCrimson,
            BleachTybwTokens.BankaiGold,
            BleachTybwTokens.ReiatsuCrimson
        )
    ) else null

    val unfocusedBorder = if (isBleach) Brush.linearGradient(
        listOf(
            BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.5f),
            BleachTybwTokens.BankaiGold.copy(alpha = 0.35f)
        )
    ) else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .padding(horizontal = GlassTokens.OVERSCAN_MARGIN_TV.dp)
            .then(focusMod)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onDown != null) onDown() else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onUp != null) onUp() else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_PROG_YELLOW,
                        KeyEvent.KEYCODE_M -> {
                            isTrailerMuted = !isTrailerMuted
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .focusGlow(isFocused, RoundedCornerShape(24.dp), focusedScale = 1.02f, glowColors = glowColors)
            .glassSurface(
                shape = RoundedCornerShape(24.dp),
                borderWidth = if (isBleach) 2.dp else 1.dp,
                isFocused = isFocused,
                showBorderUnfocused = isBleach,
                focusedBorderBrush = focusedBorder,
                unfocusedBorderBrush = unfocusedBorder
            )
    ) {
        // Right Side: Art/Trailer Player (fills right 68% for cinematic 16:9 view)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.68f)
                .align(Alignment.CenterEnd)
        ) {
            // Base cover/banner image underneath for instant visual until video frame renders
            AsyncImage(
                model = anime.bannerImage ?: anime.coverImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            val hasTrailer = !anime.trailerUrl.isNullOrEmpty() || isBleach
            if (hasTrailer) {
                BackgroundTrailerPlayer(
                    trailerUrl = anime.trailerUrl,
                    modifier = Modifier.fillMaxSize(),
                    isMuted = isTrailerMuted,
                    isPlaying = isVisible,
                    isBleach = isBleach
                )
            }

            // Left fade gradient to blend smoothly into obsidian panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.42f)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                if (isBleach) BleachTybwTokens.ReiatsuObsidian else PrimaryDark,
                                (if (isBleach) BleachTybwTokens.ReiatsuObsidian else PrimaryDark).copy(alpha = 0.85f),
                                (if (isBleach) BleachTybwTokens.ReiatsuObsidian else PrimaryDark).copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Bottom fade
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (isBleach) BleachTybwTokens.ReiatsuObsidian.copy(alpha = 0.9f) else PrimaryDark.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            // Top-right Sound Status Indicator Pill (defocused for TV remote navigation)
            val hasSoundPill = !anime.trailerUrl.isNullOrEmpty() || isBleach
            if (hasSoundPill) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .focusProperties { canFocus = false }
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(
                            1.dp,
                            if (isBleach) BleachTybwTokens.BankaiGold.copy(alpha = 0.6f) else GlassTokens.GlowCyan.copy(alpha = 0.4f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { isTrailerMuted = !isTrailerMuted }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (isTrailerMuted) Color.Gray else if (isBleach) BleachTybwTokens.ReiatsuCrimson else GlassTokens.GlowCyan,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = if (isTrailerMuted) "SOUND OFF" else "SOUND ON",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        // Left gradient overlay for high contrast text readability (starts solid on left, fades to transparent)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.65f)
                .align(Alignment.CenterStart)
                .background(
                    Brush.horizontalGradient(
                        colors = if (isBleach) listOf(
                            BleachTybwTokens.ReiatsuObsidian,
                            BleachTybwTokens.ReiatsuObsidian.copy(alpha = 0.95f),
                            BleachTybwTokens.ReiatsuDark.copy(alpha = 0.85f),
                            BleachTybwTokens.ReiatsuDark.copy(alpha = 0.3f),
                            Color.Transparent
                        ) else listOf(
                            PrimaryDark.copy(alpha = 0.95f),
                            PrimaryDark.copy(alpha = 0.85f),
                            PrimaryDark.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bleach TYBW Spiritual VFX Layer (Dynamic spiritual vortex with integrated Reiatsu motes behind typography)
        if (isBleach) {
            BleachSpiritualVortex(
                modifier = Modifier.matchParentSize(),
                tendrilCount = 3,
                particleCount = 35
            )
        }

        // Left Side: Sleek overlay details (floating directly on banner background gradient)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.52f)
                .align(Alignment.CenterStart)
                .padding(start = 32.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(if (isBleach) 7.dp else 10.dp)
            ) {
                if (isBleach) {
                    BleachTributePill()
                } else {
                    Box(
                        modifier = Modifier
                            .background(GlassTokens.GlowCyan.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "FEATURED TITLE",
                            color = GlassTokens.GlowCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                if (isBleach) {
                    Image(
                        painter = painterResource(id = R.drawable.bleach_tybw_logo_crimson),
                        contentDescription = "Bleach: Thousand-Year Blood War",
                        modifier = Modifier
                            .height(32.dp)
                            .padding(vertical = 1.dp)
                    )
                }

                Text(
                    text = anime.title,
                    color = if (isBleach) BleachTybwTokens.QuincyWhite else TextPrimary,
                    fontSize = if (isBleach) 24.sp else 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = if (isBleach) BleachTybwTokens.BleachFontFamily else null,
                    letterSpacing = if (isBleach) 0.8.sp else 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Release Year
                    anime.seasonYear?.let { year ->
                        Text(
                            text = year.toString(),
                            color = GlassTokens.TextSubtle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Format Tag
                    anime.format?.let { fmt ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isBleach) BleachTybwTokens.ReiatsuDark.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(4.dp)
                                )
                                .border(1.dp, if (isBleach) BleachTybwTokens.ReiatsuBorder else Color.Transparent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = fmt,
                                color = if (isBleach) BleachTybwTokens.ReiatsuBlood else TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Episodes
                    anime.episodes?.let { eps ->
                        Text(
                            text = "$eps Episodes",
                            color = GlassTokens.TextSubtle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Score
                    anime.averageScore?.let { score ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isBleach) BleachTybwTokens.BankaiGold else GlassTokens.GlowCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = String.format("%.1f", score / 10.0),
                                color = if (isBleach) BleachTybwTokens.BankaiGold else GlassTokens.GlowCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val cleanDescription = remember(anime.description) {
                    anime.description?.replace(Regex("<[^>]*>"), "") ?: ""
                }
                if (cleanDescription.isNotEmpty()) {
                    Text(
                        text = cleanDescription,
                        color = GlassTokens.TextSubtle,
                        fontSize = 13.sp,
                        maxLines = if (isBleach) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBleach) {
                        BleachBankaiButton(
                            text = "卍解 START BANKAI",
                            onClick = onClick,
                            isTv = true,
                            enabled = false,
                            isFocusedOverride = isFocused
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = btnScale
                                    scaleY = btnScale
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(btnGradient)
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Watch Now",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (isBleach) {
            BleachLightingBorderGlow(
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(24.dp),
                strokeWidth = 2.dp
            )
            BleachElectricLightning(
                modifier = Modifier.matchParentSize(),
                boltCount = 3
            )
            BleachBladeSlashSheen(
                modifier = Modifier.matchParentSize(),
                intervalMs = 7000
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun BackgroundTrailerPlayer(
    trailerUrl: String?,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    isPlaying: Boolean = true,
    isBleach: Boolean = false,
    onVideoEnded: (() -> Unit)? = null
) {
    if (trailerUrl.isNullOrEmpty() && !isBleach) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isBleachTrailer = isBleach || trailerUrl?.contains("e8YBesRKq_U") == true || trailerUrl?.contains("bleach", ignoreCase = true) == true

    if (isBleachTrailer) {
        // High-performance Native Media3 ExoPlayer with bundled 720p H.264 trailer
        var isReady by remember { mutableStateOf(false) }

        val exoPlayer = remember {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

            ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, false)
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    val rawUri = Uri.parse("android.resource://${context.packageName}/${R.raw.bleach_tybw_trailer}")
                    setMediaItem(MediaItem.fromUri(rawUri))
                    volume = if (isMuted) 0f else 1f
                    playWhenReady = isPlaying
                    prepare()
                }
        }

        // Keep ExoPlayer listeners to handle readiness and loop callbacks
        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        isReady = true
                    } else if (playbackState == Player.STATE_ENDED) {
                        onVideoEnded?.invoke()
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
            }
        }

        // Strict Play/Pause control bound to isPlaying (hero focus/visibility)
        LaunchedEffect(isPlaying) {
            exoPlayer.playWhenReady = isPlaying
            if (isPlaying) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }
        }

        // Audio volume & Bleach BGM coordination
        LaunchedEffect(isMuted, isPlaying) {
            exoPlayer.volume = if (isMuted) 0f else 1f
            BleachBgmManager.setVideoSuppressed(isPlaying && !isMuted)
        }

        // Full lifecycle management: pauses on app background, releases on screen navigate-away
        DisposableEffect(lifecycleOwner, exoPlayer) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                        exoPlayer.pause()
                        BleachBgmManager.setVideoSuppressed(false)
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        if (isPlaying) {
                            exoPlayer.play()
                            if (!isMuted) BleachBgmManager.setVideoSuppressed(true)
                        }
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                try {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    exoPlayer.release()
                } catch (e: Exception) {
                    android.util.Log.w("BackgroundTrailerPlayer", "Error releasing trailer ExoPlayer: ${e.message}")
                }
                BleachBgmManager.setVideoSuppressed(false)
            }
        }

        Box(modifier = modifier) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
        }
    } else {
        // Fallback for other non-Bleach titles with YouTube URLs
        // Enhanced with lifecycle cleanup and play/pause JavaScript calls to eliminate leaks
        val videoId = remember(trailerUrl) {
            val afterEmbed = trailerUrl?.substringAfter("/embed/") ?: ""
            afterEmbed.substringBefore("?").substringBefore("/")
        }

        var isVideoPlaying by remember { mutableStateOf(false) }
        var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
        val alpha by animateFloatAsState(
            targetValue = if (isVideoPlaying && isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.EaseInOut),
            label = "trailerFadeIn"
        )

        LaunchedEffect(isPlaying, isMuted) {
            webViewRef?.let { webView ->
                if (isPlaying) {
                    webView.evaluateJavascript("if (player && typeof player.playVideo === 'function') { player.playVideo(); }", null)
                } else {
                    webView.evaluateJavascript("if (player && typeof player.pauseVideo === 'function') { player.pauseVideo(); }", null)
                }
                webView.evaluateJavascript("if (typeof setMuted === 'function') { setMuted(${isMuted || !isPlaying}); }", null)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                try {
                    webViewRef?.stopLoading()
                    webViewRef?.loadUrl("about:blank")
                    webViewRef?.destroy()
                    webViewRef = null
                } catch (_: Exception) {}
            }
        }

        Box(modifier = modifier) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))

            AndroidView(
                factory = { context ->
                    android.webkit.WebView(context).apply {
                        webViewRef = this
                        isFocusable = false
                        isFocusableInTouchMode = false
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                        settings.apply {
                            javaScriptEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        }

                        addJavascriptInterface(object {
                            @android.webkit.JavascriptInterface
                            fun onPlayerStateChange(state: Int) {
                                if (state == 1) { // PLAYING
                                    post { isVideoPlaying = true }
                                }
                                if (state == 0 && onVideoEnded != null) { // ENDED
                                    post { onVideoEnded() }
                                }
                            }
                        }, "Android")

                        webChromeClient = object : android.webkit.WebChromeClient() {}

                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript("""
                                    (function() {
                                        var style = document.createElement('style');
                                        style.textContent = '.ytp-chrome-top, .ytp-chrome-bottom, .ytp-watermark, .ytp-show-cards-title, .ytp-pause-overlay, .ytp-gradient-top, .ytp-gradient-bottom, .ytp-spinner { display: none !important; opacity: 0 !important; }';
                                        document.head.appendChild(style);
                                        if (typeof setMuted === 'function') { setMuted(${isMuted || !isPlaying}); }
                                    })();
                                """.trimIndent(), null)
                            }
                        }

                        isClickable = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        setOnTouchListener { _, _ -> true }
                        setBackgroundColor(android.graphics.Color.BLACK)

                        if (trailerUrl?.contains("youtube") == true) {
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                <style>
                                  * { margin: 0; padding: 0; box-sizing: border-box; }
                                  html, body { width: 100%; height: 100%; overflow: hidden; background-color: #000000; }
                                  #player { width: 100%; height: 100%; pointer-events: none; }
                                  .ytp-chrome-top, .ytp-chrome-bottom, .ytp-watermark,
                                  .ytp-show-cards-title, .ytp-pause-overlay,
                                  .ytp-gradient-top, .ytp-gradient-bottom,
                                  .ytp-pause-overlay-container, .ytp-spinner {
                                      display: none !important;
                                      opacity: 0 !important;
                                      visibility: hidden !important;
                                  }
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
                                          'autoplay': ${if (isPlaying) 1 else 0},
                                          'mute': ${if (isMuted || !isPlaying) 1 else 0},
                                          'controls': 0,
                                          'showinfo': 0,
                                          'rel': 0,
                                          'loop': 1,
                                          'playlist': '$videoId',
                                          'modestbranding': 1,
                                          'disablekb': 1,
                                          'enablejsapi': 1,
                                          'iv_load_policy': 3,
                                          'cc_load_policy': 0,
                                          'playsinline': 1,
                                          'origin': 'https://www.youtube-nocookie.com'
                                        },
                                        events: {
                                          'onReady': onPlayerReady,
                                          'onStateChange': onPlayerStateChange
                                        }
                                      });
                                    }

                                    function onPlayerReady(event) {
                                      try {
                                        if (typeof event.target.setPlaybackQuality === 'function') {
                                          event.target.setPlaybackQuality('hd720');
                                        }
                                      } catch(e) {}
                                      if (${!isMuted && isPlaying}) {
                                        try {
                                          event.target.unMute();
                                          event.target.setVolume(100);
                                        } catch(e) {}
                                      } else {
                                        try {
                                          event.target.mute();
                                        } catch(e) {}
                                      }
                                      if (${isPlaying}) {
                                        event.target.playVideo();
                                      }
                                    }

                                    function onPlayerStateChange(event) {
                                      if (window.Android) {
                                        window.Android.onPlayerStateChange(event.data);
                                      }
                                    }

                                    function setMuted(muted) {
                                      if (player && typeof player.mute === 'function') {
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
                                <!DOCTYPE html><html><head>
                                <style>body{margin:0;padding:0;overflow:hidden;background:#000}
                                iframe{border:none;width:100vw;height:100vh;pointer-events:none}</style>
                                </head><body>
                                <iframe src="$trailerUrl" referrerpolicy="strict-origin-when-cross-origin" allow="autoplay"></iframe>
                                </body></html>
                            """.trimIndent()
                            loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                        }
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    webView.evaluateJavascript("if (typeof setMuted === 'function') { setMuted(${isMuted || !isPlaying}); }", null)
                    if (isPlaying) {
                        webView.evaluateJavascript("if (player && typeof player.playVideo === 'function') { player.playVideo(); }", null)
                    } else {
                        webView.evaluateJavascript("if (player && typeof player.pauseVideo === 'function') { player.pauseVideo(); }", null)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha }
            )
        }
    }
}

@Composable
fun RedesignTvSectionRow(
    title: String,
    list: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    rowFocusRequester: FocusRequester? = null,
    onDown: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null
) {
    var lastFocusedIndex by remember { mutableIntStateOf(0) }
    val rowBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(rowBringIntoViewRequester)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (onDown != null) onDown() else false
                        KeyEvent.KEYCODE_DPAD_UP -> if (onUp != null) onUp() else false
                        else -> false
                    }
                } else false
            }
    ) {
        RedesignTvSectionHeader(title = title)
        Spacer(Modifier.height(GlassTokens.ROW_TITLE_BOTTOM.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.CARD_GAP_TV.dp),
            contentPadding = PaddingValues(horizontal = GlassTokens.OVERSCAN_MARGIN_TV.dp, vertical = 8.dp)
        ) {
            itemsIndexed(list, key = { index, anime -> "${title}_${anime.id}_$index" }) { index, anime ->
                val cardRequester = if (index == lastFocusedIndex.coerceIn(0, list.lastIndex)) rowFocusRequester else null
                RedesignTvPosterCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    rank = if (title == "Trending Now" && index < 10) index + 1 else null,
                    focusRequester = cardRequester,
                    onCardFocused = {
                        lastFocusedIndex = index
                        coroutineScope.launch {
                            rowBringIntoViewRequester.bringIntoView()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RedesignTvPosterCard(
    anime: Anime,
    modifier: Modifier = Modifier.width(150.dp),
    onClick: () -> Unit,
    rank: Int? = null,
    focusRequester: FocusRequester? = null,
    onCardFocused: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val isBleach = anime.isBleachTybw()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusMod = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Box(
        modifier = modifier
            .then(focusMod)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onCardFocused?.invoke()
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .focusGlow(
                isFocused = isFocused,
                shape = RoundedCornerShape(16.dp),
                glowColors = if (isBleach) listOf(
                    BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.65f),
                    BleachTybwTokens.BankaiGold.copy(alpha = 0.45f),
                    BleachTybwTokens.ReiatsuBlood.copy(alpha = 0.25f),
                    Color.Transparent
                ) else null
            )
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                borderWidth = if (isBleach) 2.dp else 1.dp,
                isFocused = isFocused,
                showBorderUnfocused = false,
                focusedBorderBrush = if (isBleach) Brush.linearGradient(
                    listOf(
                        BleachTybwTokens.ReiatsuCrimson,
                        BleachTybwTokens.BankaiGold,
                        BleachTybwTokens.ReiatsuCrimson
                    )
                ) else null,
                unfocusedBorderBrush = if (isBleach) Brush.linearGradient(
                    listOf(
                        BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.35f),
                        BleachTybwTokens.BankaiGold.copy(alpha = 0.2f)
                    )
                ) else null
            )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = anime.coverImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isBleach) {
                    BleachElectricLightning(modifier = Modifier.fillMaxSize(), boltCount = 2)
                }

                // Rank badge (top-left if present)
                if (rank != null) {
                    val r = rank
                    if (isBleach) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            BleachTybwTokens.ReiatsuCrimson,
                                            BleachTybwTokens.ReiatsuBlood
                                        )
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(1.dp, BleachTybwTokens.BankaiGold, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.bleach_shinigami_skull_white),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "#$r 卍解",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = BleachTybwTokens.BleachFontFamily
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "#$r",
                                color = GlassTokens.GlowCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (anime.format != null && anime.format != "TV") {
                    val fmt = anime.format
                    val badgeColor = when (fmt) {
                        "MOVIE" -> BleachTybwTokens.BankaiGold
                        "OVA" -> GlassTokens.GlowCyan
                        "ONA" -> GlassTokens.GlowPurple
                        else -> Color.White
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .border(1.dp, badgeColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = fmt,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (anime.status.contains("NOT_YET", ignoreCase = true) || anime.status.contains("UPCOMING", ignoreCase = true)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                            .border(1.dp, GlassTokens.GlowPurple.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "SOON",
                            color = GlassTokens.GlowPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (anime.episodes != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                            .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "EP ${anime.episodes}",
                            color = GlassTokens.GlowCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                anime.averageScore?.let { score ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                if (isBleach) BleachTybwTokens.ReiatsuDark.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (isBleach) BleachTybwTokens.BankaiGold.copy(alpha = 0.8f) else GlassTokens.GlowCyan.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "★ ${String.format("%.1f", score / 10.0)}",
                            color = if (isBleach) BleachTybwTokens.BankaiGold else GlassTokens.GlowCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (isBleach) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .background(
                            BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.22f),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            0.5.dp,
                            BleachTybwTokens.BankaiGold.copy(alpha = 0.6f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bleach_shinigami_skull_crimson),
                        contentDescription = null,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "TYBW CLIMAX",
                        color = BleachTybwTokens.BankaiGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = BleachTybwTokens.BleachFontFamily,
                        letterSpacing = 0.6.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = anime.title,
                color = if (isFocused) {
                    if (isBleach) BleachTybwTokens.BankaiGold else GlassTokens.GlowCyan
                } else {
                    if (isBleach) BleachTybwTokens.ReiatsuBlood else TextPrimary
                },
                fontSize = 13.sp,
                fontWeight = if (isBleach) FontWeight.Bold else FontWeight.Medium,
                fontFamily = if (isBleach) BleachTybwTokens.BleachFontFamily else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (isBleach) {
            // High-voltage ambient Reiatsu aura around card
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BleachTybwTokens.ReiatsuCrimson.copy(alpha = if (isFocused) 0.5f else 0.35f),
                                BleachTybwTokens.QuincyReishiBlue.copy(alpha = if (isFocused) 0.25f else 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
            )

            if (isFocused) {
                BleachLightingBorderGlow(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(16.dp),
                    strokeWidth = 2.8.dp
                )
            }
        }
    }
}

@Composable
fun RedesignTvAiringCard(
    item: AiringAnime,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onCardFocused: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusMod = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Box(
        modifier = Modifier
            .width(240.dp)
            .then(focusMod)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onCardFocused?.invoke()
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .focusGlow(isFocused, RoundedCornerShape(14.dp))
            .glassSurface(RoundedCornerShape(14.dp), isFocused = isFocused)
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
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
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
                    com.example.aniflow.data.model.formatAiringSchedule(item.airingAt),
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
    onHistoryClick: (WatchHistoryEntry) -> Unit,
    rowFocusRequester: FocusRequester? = null,
    onDown: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null
) {
    var lastIndex by remember { mutableIntStateOf(0) }
    val rowBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(rowBringIntoViewRequester)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (onDown != null) onDown() else false
                        KeyEvent.KEYCODE_DPAD_UP -> if (onUp != null) onUp() else false
                        else -> false
                    }
                } else false
            }
    ) {
        RedesignTvSectionHeader(title = title)
        Spacer(Modifier.height(GlassTokens.ROW_TITLE_BOTTOM.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.CARD_GAP_TV.dp),
            contentPadding = PaddingValues(horizontal = GlassTokens.OVERSCAN_MARGIN_TV.dp, vertical = 8.dp)
        ) {
            itemsIndexed(list, key = { _, it -> "cw_${it.animeId}" }) { index, entry ->
                val cardRequester = if (index == lastIndex.coerceIn(0, list.lastIndex)) rowFocusRequester else null
                RedesignTvContinueWatchingCard(
                    entry = entry,
                    onHistoryClick = onHistoryClick,
                    focusRequester = cardRequester,
                    onCardFocused = {
                        lastIndex = index
                        coroutineScope.launch {
                            rowBringIntoViewRequester.bringIntoView()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RedesignTvContinueWatchingCard(
    entry: WatchHistoryEntry,
    onHistoryClick: (WatchHistoryEntry) -> Unit,
    focusRequester: FocusRequester? = null,
    onCardFocused: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusMod = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Box(
        modifier = Modifier
            .width(280.dp)
            .aspectRatio(1.77f)
            .then(focusMod)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onCardFocused?.invoke()
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onHistoryClick(entry) }
            .focusGlow(isFocused, RoundedCornerShape(16.dp))
            .glassSurface(RoundedCornerShape(16.dp), isFocused = isFocused)
    ) {
        AsyncImage(
            model = entry.coverImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 80f
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = entry.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            
            val remainingMs = entry.durationMs - entry.progressMs
            val remainingMinutes = (remainingMs / 60000).coerceAtLeast(1)
            val remainingText = if (entry.durationMs > 0) " • ${remainingMinutes}m left" else ""
            Text(
                text = "Episode ${entry.episodeNumber}$remainingText",
                color = GlassTokens.TextSubtle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            
            val progressFraction = if (entry.durationMs > 0) entry.progressMs.toFloat() / entry.durationMs else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = GlassTokens.GlowCyan,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}
