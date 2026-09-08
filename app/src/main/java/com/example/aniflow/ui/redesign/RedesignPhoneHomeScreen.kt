package com.example.aniflow.ui.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.data.UserFeedback
import androidx.compose.ui.text.font.FontStyle
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.components.GlassCard
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.redesign.theme.pressSpring
import com.example.aniflow.ui.redesign.theme.premiumGlassPanel
import com.example.aniflow.ui.redesign.theme.glassSurface
import com.example.aniflow.ui.redesign.theme.BleachTybwTokens
import com.example.aniflow.ui.redesign.theme.isBleachTybw
import com.example.aniflow.ui.redesign.components.BleachReiatsuParticles
import com.example.aniflow.ui.redesign.components.BleachBladeSlashSheen
import com.example.aniflow.ui.redesign.components.BleachLightingBorderGlow
import com.example.aniflow.ui.redesign.components.BleachElectricLightning
import com.example.aniflow.ui.redesign.components.BleachTributePill
import com.example.aniflow.ui.redesign.components.BleachBankaiButton
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.aniflow.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer


@Composable
fun RedesignPhoneHomeScreen(
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
    var fullscreenTrailerUrl by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(PrimaryDark)) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(GlassTokens.SECTION_GAP_MOBILE.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Spotlight carousel
                if (trending.isNotEmpty()) {
                    item {
                        RedesignSpotlightPager(
                            spotlightList = trending.take(5),
                            onAnimeClick = onAnimeClick,
                            onViewTrailer = { url -> fullscreenTrailerUrl = url }
                        )
                    }
                }

                // Continue Watching Row
                if (history.isNotEmpty()) {
                    item {
                        RedesignContinueWatchingRow(
                            title = "Continue Watching",
                            list = history,
                            onHistoryClick = onHistoryClick
                        )
                    }
                }

                // Airing schedule countdowns
                if (airing.isNotEmpty()) {
                    item {
                        Column {
                            RedesignSectionHeader(title = "Airing Schedule")
                            Spacer(Modifier.height(GlassTokens.ROW_TITLE_BOTTOM.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(GlassTokens.CARD_GAP_MOBILE.dp)
                            ) {
                                items(airing, key = { it.mediaId }) { item ->
                                    RedesignAiringCard(
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
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // User's Choice Row
                if (userFeedbackList.isNotEmpty()) {
                    item {
                        Column {
                            RedesignSectionHeader(title = "User's Choice")
                            Spacer(Modifier.height(GlassTokens.ROW_TITLE_BOTTOM.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(GlassTokens.CARD_GAP_MOBILE.dp)
                            ) {
                                items(userFeedbackList, key = { "${it.anime.id}_${it.timestamp}" }) { feedback ->
                                    GlassCard(
                                        onClick = {
                                            onAnimeClick(feedback.anime.toAnime())
                                        },
                                        modifier = Modifier
                                            .width(280.dp)
                                            .height(96.dp),
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
                                                    .width(60.dp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = feedback.anime.title,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "\"${feedback.feedback}\"",
                                                    color = GlassTokens.TextMuted,
                                                    fontSize = 11.sp,
                                                    fontStyle = FontStyle.Italic,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Trending Row
                item {
                    RedesignAnimeSectionRow(
                        title = "Trending Now",
                        list = trending,
                        onAnimeClick = onAnimeClick,
                        onViewAll = { /* Handle View All if needed */ }
                    )
                }

                // Recently Updated Row
                if (recentlyUpdated.isNotEmpty()) {
                    item {
                        RedesignAnimeSectionRow(
                            title = "Recently Updated",
                            list = recentlyUpdated,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }

                // Popular Row
                item {
                    RedesignAnimeSectionRow(
                        title = "Popular All Time",
                        list = popular,
                        onAnimeClick = onAnimeClick
                    )
                }

                // Top Rated Row
                if (topRated.isNotEmpty()) {
                    item {
                        RedesignAnimeSectionRow(
                            title = "Top Rated Hits",
                            list = topRated,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }

                // Seasonal Row
                item {
                    RedesignAnimeSectionRow(
                        title = "Seasonal Hits",
                        list = seasonal,
                        onAnimeClick = onAnimeClick
                    )
                }

                // Action Genre Row
                if (actionAnime.isNotEmpty()) {
                    item {
                        RedesignAnimeSectionRow(
                            title = "Action & Adventure",
                            list = actionAnime,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }

                // Romance Genre Row
                if (romanceAnime.isNotEmpty()) {
                    item {
                        RedesignAnimeSectionRow(
                            title = "Romance Picks",
                            list = romanceAnime,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }

                // Upcoming Row
                if (upcoming.isNotEmpty()) {
                    item {
                        RedesignAnimeSectionRow(
                            title = "Upcoming Season",
                            list = upcoming,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = fullscreenTrailerUrl != null,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
            ) {
                if (fullscreenTrailerUrl != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        BackgroundTrailerPlayer(
                            trailerUrl = fullscreenTrailerUrl,
                            modifier = Modifier.fillMaxSize(),
                            isMuted = false,
                            isPlaying = true,
                            isBleach = fullscreenTrailerUrl?.contains("bleach", ignoreCase = true) == true,
                            onVideoEnded = {
                                fullscreenTrailerUrl = null
                            }
                        )

                        IconButton(
                            onClick = { fullscreenTrailerUrl = null },
                            modifier = Modifier
                                .statusBarsPadding()
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RedesignSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(PrimaryAccent, RoundedCornerShape(2.dp))
            )
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
        if (onViewAll != null) {
            Text(
                text = "View All",
                color = GlassTokens.GlowCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onViewAll() }
            )
        }
    }
}

@Composable
fun RedesignSpotlightPager(
    spotlightList: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onViewTrailer: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { spotlightList.size })
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(pagerState.currentPage) {
        val currentAnime = spotlightList.getOrNull(pagerState.currentPage)
        val hasTrailer = !currentAnime?.trailerUrl.isNullOrEmpty()
        
        if (hasTrailer) {
            delay(40_000) // Safety timeout
        } else {
            delay(8_000)  // Standard sliding delay
        }
        
        val nextPage = (pagerState.currentPage + 1) % spotlightList.size
        pagerState.animateScrollToPage(nextPage)
    }

    // Static scale for spotlight background for optimal 60fps performance
    val heroScale = 1.0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(450.dp) // Cinematic 450dp height
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val anime = spotlightList[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onAnimeClick(anime) }
            ) {
                AsyncImage(
                    model = anime.bannerImage ?: anime.coverImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = heroScale
                            scaleY = heroScale
                        }
                )
                
                val isBleach = anime.isBleachTybw()
                val hasTrailer = !anime.trailerUrl.isNullOrEmpty() || isBleach
                if (pagerState.currentPage == page && pagerState.isScrollInProgress.not() && hasTrailer) {
                    var showTrailer by remember(anime.id) { mutableStateOf(false) }
                    LaunchedEffect(pagerState.currentPage, anime.id) {
                        showTrailer = false
                        delay(1500L)
                        if (pagerState.currentPage == page) {
                            showTrailer = true
                        }
                    }
                    if (showTrailer) {
                        BackgroundTrailerPlayer(
                            trailerUrl = anime.trailerUrl,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = heroScale
                                    scaleY = heroScale
                                },
                            isMuted = false,
                            isPlaying = (pagerState.currentPage == page),
                            isBleach = isBleach,
                            onVideoEnded = {
                                coroutineScope.launch {
                                    val nextPage = (pagerState.currentPage + 1) % spotlightList.size
                                    pagerState.animateScrollToPage(nextPage)
                                }
                            }
                        )
                    }
                }

                if (isBleach) {
                    BleachElectricLightning(
                        modifier = Modifier.fillMaxSize(),
                        boltCount = 2
                    )
                }

                // Vignette gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                radius = 1200f
                            )
                        )
                )

                // Backdrop vertical fade overlay to blend bottom details
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = if (isBleach) listOf(
                                    Color.Transparent,
                                    BleachTybwTokens.ReiatsuDark.copy(alpha = 0.25f),
                                    BleachTybwTokens.ReiatsuDark.copy(alpha = 0.7f),
                                    BleachTybwTokens.ReiatsuObsidian.copy(alpha = 0.95f),
                                    BleachTybwTokens.ReiatsuObsidian
                                ) else listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    PrimaryDark.copy(alpha = 0.4f),
                                    PrimaryDark.copy(alpha = 0.85f),
                                    PrimaryDark
                                ),
                                startY = 0f
                            )
                        )
                )
                
                // Floating details panel (Custom Bleach Reiatsu card when TYBW)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .let { mod ->
                                if (isBleach) {
                                    mod.background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                BleachTybwTokens.ReiatsuDark.copy(alpha = 0.94f),
                                                BleachTybwTokens.ReiatsuObsidian.copy(alpha = 0.98f)
                                            )
                                        )
                                    )
                                } else {
                                    mod.premiumGlassPanel(RoundedCornerShape(16.dp))
                                }
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isBleach) {
                                BleachTributePill()
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(GlassTokens.GlowCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "FEATURED",
                                            color = GlassTokens.GlowCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                    
                                    anime.studioName?.let { studio ->
                                        Text(
                                            text = studio,
                                            color = GlassTokens.TextSubtle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            anime.averageScore?.let { score ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .background(
                                            if (isBleach) BleachTybwTokens.ReiatsuDark.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.4f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isBleach) BleachTybwTokens.BankaiGold.copy(alpha = 0.6f) else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = "Rating",
                                        tint = if (isBleach) BleachTybwTokens.BankaiGold else WarningAmber,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = String.format("%.1f", score / 10.0),
                                        color = if (isBleach) BleachTybwTokens.BankaiGold else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        if (isBleach) {
                            Image(
                                painter = painterResource(id = R.drawable.bleach_tybw_logo_crimson),
                                contentDescription = "Bleach: Thousand-Year Blood War",
                                modifier = Modifier
                                    .height(34.dp)
                                    .padding(vertical = 2.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        Text(
                            text = anime.title,
                            color = if (isBleach) BleachTybwTokens.QuincyWhite else TextPrimary,
                            fontSize = if (isBleach) 21.sp else 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = if (isBleach) BleachTybwTokens.BleachFontFamily else null,
                            letterSpacing = if (isBleach) 0.6.sp else 0.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(4.dp))

                        if (anime.genres.isNotEmpty()) {
                            Text(
                                text = anime.genres.take(3).joinToString(" • "),
                                color = if (isBleach) BleachTybwTokens.QuincyIceCyan.copy(alpha = 0.85f) else GlassTokens.TextSubtle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        val cleanDescription = remember(anime.description) {
                            anime.description?.replace(Regex("<[^>]*>"), "") ?: ""
                        }
                        if (cleanDescription.isNotEmpty()) {
                            Text(
                                text = cleanDescription,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isBleach) {
                                BleachBankaiButton(
                                    text = "卍解 START BANKAI",
                                    onClick = { onAnimeClick(anime) }
                                )
                            } else {
                                Button(
                                    onClick = { onAnimeClick(anime) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(GlassTokens.GlowCyan, PrimaryAccent)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Watch Now",
                                            color = Color.Black,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (!anime.trailerUrl.isNullOrEmpty()) {
                                OutlinedButton(
                                    onClick = { onViewTrailer(anime.trailerUrl) },
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isBleach) BleachTybwTokens.QuincyReishiBlue.copy(alpha = 0.5f) else GlassTokens.GlowCyan.copy(alpha = 0.4f)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isBleach) BleachTybwTokens.QuincyReishiBlue.copy(alpha = 0.1f) else GlassTokens.GlowCyan.copy(alpha = 0.08f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isBleach) BleachTybwTokens.QuincyReishiBlue else GlassTokens.GlowCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Trailer",
                                            color = if (isBleach) BleachTybwTokens.QuincyReishiBlue else GlassTokens.GlowCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { onAnimeClick(anime) },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isBleach) BleachTybwTokens.ReiatsuBorder.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isBleach) BleachTybwTokens.ReiatsuSurface.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Info",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    if (isBleach) {
                        BleachLightingBorderGlow(
                            modifier = Modifier.matchParentSize(),
                            shape = RoundedCornerShape(16.dp),
                            strokeWidth = 2.dp
                        )
                        BleachBladeSlashSheen(
                            modifier = Modifier.matchParentSize(),
                            intervalMs = 7500
                        )
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(spotlightList.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (active) 12.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) GlassTokens.GlowCyan else Color.White.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
fun RedesignAnimeSectionRow(
    title: String,
    list: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onViewAll: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RedesignSectionHeader(
            title = title,
            onViewAll = onViewAll
        )
        Spacer(Modifier.height(GlassTokens.ROW_TITLE_BOTTOM.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.CARD_GAP_MOBILE.dp)
        ) {
            itemsIndexed(list, key = { index, anime -> "${title}_${anime.id}_$index" }) { index, anime ->
                RedesignAnimePosterCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    rank = if (title == "Trending Now" && index < 10) index + 1 else null
                )
            }
        }
    }
}

@Composable
fun RedesignAnimePosterCard(
    anime: Anime,
    onClick: () -> Unit,
    rank: Int? = null
) {
    val isBleach = anime.isBleachTybw()

    Column(
        modifier = Modifier.width(115.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
        ) {
            GlassCard(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .pressSpring(),
                shape = RoundedCornerShape(12.dp),
                glowColors = if (isBleach) listOf(
                    BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.65f),
                    BleachTybwTokens.BankaiGold.copy(alpha = 0.45f),
                    BleachTybwTokens.ReiatsuBlood.copy(alpha = 0.25f),
                    Color.Transparent
                ) else null,
                focusedBorderBrush = if (isBleach) Brush.linearGradient(
                    listOf(
                        BleachTybwTokens.ReiatsuCrimson,
                        BleachTybwTokens.BankaiGold,
                        BleachTybwTokens.ReiatsuCrimson
                    )
                ) else null,
                unfocusedBorderBrush = if (isBleach) Brush.linearGradient(
                    listOf(
                        BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.5f),
                        BleachTybwTokens.BankaiGold.copy(alpha = 0.35f)
                    )
                ) else null
            ) {
                AsyncImage(
                    model = anime.coverImage,
                    contentDescription = anime.title,
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
                                .padding(4.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            BleachTybwTokens.ReiatsuCrimson,
                                            BleachTybwTokens.ReiatsuBlood
                                        )
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(1.dp, BleachTybwTokens.BankaiGold, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.bleach_shinigami_skull_white),
                                contentDescription = null,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "#$r 卍解",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = BleachTybwTokens.BleachFontFamily
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$r",
                                color = GlassTokens.GlowCyan,
                                fontSize = 9.sp,
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
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .border(1.dp, badgeColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = fmt,
                            color = badgeColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (anime.status.contains("NOT_YET", ignoreCase = true) || anime.status.contains("UPCOMING", ignoreCase = true)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .border(1.dp, GlassTokens.GlowPurple.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SOON",
                            color = GlassTokens.GlowPurple,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (anime.episodes != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .border(1.dp, GlassTokens.GlowCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "EP ${anime.episodes}",
                            color = GlassTokens.GlowCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Score Badge in custom neon accent
                anime.averageScore?.let { score ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(if (isBleach) 4.dp else 6.dp)
                            .background(
                                if (isBleach) BleachTybwTokens.ReiatsuDark.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isBleach) BleachTybwTokens.BankaiGold.copy(alpha = 0.8f) else GlassTokens.GlowCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★ ${String.format("%.1f", score / 10.0)}",
                            color = if (isBleach) BleachTybwTokens.BankaiGold else GlassTokens.GlowCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isBleach) {
                // High-voltage ambient Reiatsu aura around card
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.45f),
                                    BleachTybwTokens.QuincyReishiBlue.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                )

                BleachLightingBorderGlow(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(12.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (isBleach) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .background(
                        BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.22f),
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        0.5.dp,
                        BleachTybwTokens.BankaiGold.copy(alpha = 0.6f),
                        RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.bleach_shinigami_skull_crimson),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = "TYBW CLIMAX",
                    color = BleachTybwTokens.BankaiGold,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = BleachTybwTokens.BleachFontFamily,
                    letterSpacing = 0.6.sp
                )
            }
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = anime.title,
            color = if (isBleach) BleachTybwTokens.ReiatsuBlood else TextPrimary,
            fontSize = 11.sp,
            fontWeight = if (isBleach) FontWeight.Bold else FontWeight.Medium,
            fontFamily = if (isBleach) BleachTybwTokens.BleachFontFamily else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun RedesignAiringCard(item: AiringAnime, onClick: () -> Unit) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .width(190.dp)
            .pressSpring(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.coverImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 46.dp, height = 64.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    item.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text("Episode ${item.episode}", color = GlassTokens.GlowCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(com.example.aniflow.data.model.formatAiringSchedule(item.airingAt), color = WarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun RedesignContinueWatchingRow(
    title: String,
    list: List<WatchHistoryEntry>,
    onHistoryClick: (WatchHistoryEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RedesignSectionHeader(
            title = title,
            onViewAll = { /* View All if needed */ }
        )
        Spacer(Modifier.height(GlassTokens.ROW_TITLE_BOTTOM.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.CARD_GAP_MOBILE.dp)
        ) {
            items(list) { entry ->
                RedesignContinueWatchingCard(entry = entry, onHistoryClick = onHistoryClick)
            }
        }
    }
}

@Composable
fun RedesignContinueWatchingCard(
    entry: WatchHistoryEntry,
    onHistoryClick: (WatchHistoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(260.dp)
            .aspectRatio(1.6f)
            .pressSpring()
            .glassSurface(RoundedCornerShape(16.dp), isFocused = false)
            .clickable { onHistoryClick(entry) }
    ) {
        AsyncImage(
            model = entry.coverImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Bottom overlay gradient to blend details
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 100f
                    )
                )
        )
        
        // Info overlay inside card
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = entry.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            
            // Subtitle with time remaining
            val remainingMs = entry.durationMs - entry.progressMs
            val remainingMinutes = (remainingMs / 60000).coerceAtLeast(1)
            val remainingText = if (entry.durationMs > 0) " • ${remainingMinutes}m left" else ""
            Text(
                text = "Ep ${entry.episodeNumber}$remainingText",
                color = GlassTokens.TextSubtle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            
            val progressFraction = if (entry.durationMs > 0) entry.progressMs.toFloat() / entry.durationMs else 0f
            // Progress bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = GlassTokens.GlowCyan,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}
