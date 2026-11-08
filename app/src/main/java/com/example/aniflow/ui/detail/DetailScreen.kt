package com.example.aniflow.ui.detail

import com.example.aniflow.ui.tv.home.comingLabel
import com.example.aniflow.ui.tv.home.episodeLabel
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aniflow.DeviceType
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.Episode
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aniflow.data.ProviderMappingStore
import com.example.aniflow.data.model.EpisodeLookupResult
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.blur
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.UserFeedbackStore
import com.example.aniflow.data.UserFeedback

@Composable
fun DetailScreen(
    animeId: Int,
    repository: AnimeRepository,
    deviceType: DeviceType,
    watchlistStore: WatchlistStore,
    watchHistoryStore: WatchHistoryStore,
    userFeedbackStore: UserFeedbackStore,
    onEpisodeClick: (Int) -> Unit,
    onAnimeClick: (Int) -> Unit,
    onBack: () -> Unit,
    onSwitchAnime: (Int) -> Unit = onAnimeClick
) {
    val context = LocalContext.current
    val viewModel: DetailViewModel = viewModel {
        DetailViewModel(repository, ProviderMappingStore(context.applicationContext), watchHistoryStore)
    }

    LaunchedEffect(animeId) {
        viewModel.loadAnimeDetails(animeId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val watchHistoryEntry by viewModel.watchHistoryEntry.collectAsState()
    // Metadata (often from cache) arrives separately from episodes. Offline, the episode lookup can
    // fail while the page itself is perfectly showable, so only a missing anime is a full-page error.
    val loadedAnime by viewModel.anime.collectAsState()
    val franchise by viewModel.franchise.collectAsState()
    val isBookmarked by watchlistStore.isBookmarkedFlow(animeId).collectAsState(initial = false)
    val userFeedbackText by userFeedbackStore.getFeedbackForAnimeFlow(animeId).collectAsState(initial = null)
    var showFeedbackDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isBackFocused by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is DetailUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(PrimaryDark), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryAccent)
            }
        }
        is DetailUiState.Error if loadedAnime == null -> {
            Column(
                modifier = Modifier.fillMaxSize().background(PrimaryDark).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = state.message, color = TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { onBack() }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)) {
                        Text("Back", color = TextPrimary)
                    }
                    Button(onClick = { viewModel.loadAnimeDetails(animeId) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) {
                        Text("Retry")
                    }
                }
            }
        }
        else -> {
            val currentAnime = when (state) {
                is DetailUiState.Success -> state.anime
                is DetailUiState.Empty -> state.anime
                is DetailUiState.Ambiguous -> state.anime
                is DetailUiState.NotFound -> state.anime
                is DetailUiState.Error -> loadedAnime
                else -> null
            }
            val episodes = when (state) {
                is DetailUiState.Success -> state.episodes
                else -> emptyList()
            }

            if (currentAnime != null) {
                val shouldBlur = showFeedbackDialog
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PrimaryDark)
                            .let { if (shouldBlur) it.blur(20.dp) else it }
                            .focusProperties { canFocus = !shouldBlur }
                    ) {
                // Backdrop Image with vertical fading gradient
                val bannerUrl = currentAnime.bannerImage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (deviceType == DeviceType.TV) 380.dp else 260.dp)
                ) {
                    AsyncImage(
                        model = bannerUrl ?: currentAnime.coverImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // A 4.75:1 banner cropped to this box shows a random slice of the art, so it is
                        // ambience only (soft, dim); the sharp poster below carries the identity.
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.7f }.blur(10.dp)
                    )
                    if (bannerUrl == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PrimaryDark.copy(alpha = 0.75f))
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (deviceType == DeviceType.TV) 380.dp else 260.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, PrimaryDark.copy(alpha = 0.5f), PrimaryDark),
                                startY = 0f
                            )
                        )
                )

                // Scrollable details content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    // Top Navigation / Header row directly in column to enable seamless D-pad traversal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isBackFocused) PrimaryAccent else PrimaryDark.copy(alpha = 0.6f))
                                .border(
                                    width = if (isBackFocused) 2.dp else 0.dp,
                                    color = if (isBackFocused) SecondaryAccent else Color.Transparent,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .onFocusChanged { isBackFocused = it.isFocused }
                                .clickable { onBack() }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (deviceType == DeviceType.TV) 120.dp else 60.dp))
                    
                    // Main info row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Poster Image
                        AsyncImage(
                            model = currentAnime.coverImage,
                            contentDescription = currentAnime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(if (deviceType == DeviceType.TV) 150.dp else 110.dp)
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        // Title, Studio, Score
                        Column {
                            Text(
                                text = currentAnime.title,
                                color = TextPrimary,
                                fontSize = if (deviceType == DeviceType.TV) 28.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Only what we actually know: no "Unknown Studio" or "--% Score" filler.
                            val meta = listOfNotNull(
                                currentAnime.studioName?.takeIf { it.isNotBlank() },
                                currentAnime.averageScore?.takeIf { it > 0 }?.let { "★ $it%" },
                                currentAnime.comingLabel() ?: currentAnime.episodeLabel(short = true),
                            ).joinToString("  •  ")
                            if (meta.isNotEmpty()) {
                                Text(text = meta, color = SecondaryAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            val nextAt = currentAnime.nextAiringAt
                            val nextEp = currentAnime.nextAiringEpisode
                            if (currentAnime.status == "RELEASING" && nextAt != null && nextEp != null) {
                                val nowSec = com.example.aniflow.ui.schedule.rememberNowSec()
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (nextAt > nowSec) "EP $nextEp ${com.example.aniflow.ui.schedule.countdownText(nextAt - nowSec)}" else "EP $nextEp out now",
                                    color = com.example.aniflow.ui.tv.home.TvTokens.AccentPale,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(com.example.aniflow.ui.tv.home.TvTokens.Accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Action row: Play Ep 1, bookmark
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isWatchFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        // Shrinks before it can push the round buttons off-screen.
                                        .weight(1f, fill = false)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(if (isWatchFocused) SecondaryAccent else PrimaryAccent)
                                        .border(
                                            width = if (isWatchFocused) 2.dp else 0.dp,
                                            color = if (isWatchFocused) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .onFocusChanged { isWatchFocused = it.isFocused }
                                        .clickable { onEpisodeClick(watchHistoryEntry?.episodeNumber ?: 1) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = TextPrimary)
                                        Text(if (watchHistoryEntry != null) "Resume Ep ${watchHistoryEntry?.episodeNumber}" else "Watch Ep 1", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }

                                var isFavFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(if (isFavFocused) PrimaryAccent else SurfaceCard)
                                        .border(
                                            width = if (isFavFocused) 2.dp else 0.dp,
                                            color = if (isFavFocused) SecondaryAccent else Color.Transparent,
                                            shape = RoundedCornerShape(22.dp)
                                        )
                                        .clickable {
                                            coroutineScope.launch {
                                                if (isBookmarked) {
                                                    watchlistStore.removeFromWatchlist(currentAnime.id)
                                                } else {
                                                    watchlistStore.addToWatchlist(currentAnime)
                                                }
                                            }
                                        }
                                        .onFocusChanged { isFavFocused = it.isFocused }
                                        .focusable(),
                                    contentAlignment = Alignment.Center
                                 ) {
                                     Icon(
                                         imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                         contentDescription = "Bookmark",
                                         tint = PrimaryAccentLight,
                                         modifier = Modifier.size(20.dp)
                                     )
                                 }

                                 var isFeedbackFocused by remember { mutableStateOf(false) }
                                 Box(
                                     modifier = Modifier
                                         .size(44.dp)
                                         .clip(RoundedCornerShape(22.dp))
                                         .background(if (isFeedbackFocused) PrimaryAccent else SurfaceCard)
                                         .border(
                                             width = if (isFeedbackFocused) 2.dp else 0.dp,
                                             color = if (isFeedbackFocused) SecondaryAccent else Color.Transparent,
                                             shape = RoundedCornerShape(22.dp)
                                         )
                                         .onFocusChanged { isFeedbackFocused = it.isFocused }
                                         .clickable { showFeedbackDialog = true },
                                     contentAlignment = Alignment.Center
                                 ) {
                                     Icon(
                                         imageVector = Icons.Default.Star,
                                         contentDescription = "Feedback",
                                         tint = PrimaryAccentLight,
                                         modifier = Modifier.size(20.dp)
                                     )
                                 }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Description & Genres
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp)
                    ) {
                        val cleanDescription = remember(currentAnime.description) {
                            currentAnime.description?.replace(Regex("<[^>]*>"), "")
                                ?.replace("&nbsp;", " ")
                                ?.replace("&quot;", "\"")
                                ?.replace("&apos;", "'")
                                ?.replace("&amp;", "&")
                                ?: "No description available."
                        }
                        Text(
                            text = cleanDescription,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Genre list
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            currentAnime.genres.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .background(PrimaryDarker, RoundedCornerShape(16.dp))
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(genre, color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Episodes Header
                    Text(
                        text = "Episodes",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // TV never reaches this screen (Navigation routes it to TvDetailScreen).
                    if (state is DetailUiState.Error) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard).padding(16.dp)
                        ) {
                            Text("Couldn't load episodes", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(state.message, color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.loadAnimeDetails(animeId) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) {
                                Text("Retry")
                            }
                        }
                    } else {
                        PhoneEpisodesList(episodes = episodes, current = watchHistoryEntry, onEpisodeClick = onEpisodeClick)
                    }

                    PhoneFranchiseSection(
                        entries = franchise,
                        currentId = currentAnime.id,
                        horizontalPadding = if (deviceType == DeviceType.TV) 48.dp else 16.dp,
                        onSelect = onSwitchAnime
                    )

                    // Similar Recommendations Section
                    if (currentAnime.recommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Similar Recommendations",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            items(currentAnime.recommendations) { recAnime ->
                                var isFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .width(if (deviceType == DeviceType.TV) 140.dp else 120.dp)
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .clickable { onAnimeClick(recAnime.id) }
                                        .background(if (isFocused) SurfaceCard else Color.Transparent, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.7f)
                                                .clip(RoundedCornerShape(6.dp))
                                        ) {
                                            AsyncImage(
                                                model = recAnime.coverImage,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = recAnime.title,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    }
                    if (state is DetailUiState.Ambiguous || showFeedbackDialog) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f))
                                .clickable(enabled = true, onClick = {})
                        )
                        if (state is DetailUiState.Ambiguous) {
                            AmbiguousSelectionDialog(
                                candidates = state.candidates,
                                onSelect = { candidate ->
                                    viewModel.confirmMapping(currentAnime, candidate)
                                },
                                onDismiss = {
                                    viewModel.uiState.value = DetailUiState.NotFound(currentAnime)
                                }
                            )
                        }
                        if (showFeedbackDialog) {
                            FeedbackInputDialog(
                                initialFeedback = userFeedbackText ?: "",
                                onDismiss = { showFeedbackDialog = false },
                                onSubmit = { text ->
                                    coroutineScope.launch {
                                        userFeedbackStore.saveFeedback(currentAnime, text)
                                    }
                                    showFeedbackDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun PhoneEpisodesList(
    episodes: List<Episode>,
    current: com.example.aniflow.data.model.WatchHistoryEntry? = null,
    onEpisodeClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        episodes.forEach { episode ->
            // Where the viewer is: earlier episodes are done, the current one shows its progress.
            val isCurrent = current?.episodeNumber == episode.number
            val watched = current != null && episode.number < current.episodeNumber
            val progress = if (isCurrent && current!!.durationMs > 0) (current.progressMs.toFloat() / current.durationMs).coerceIn(0f, 1f) else 0f
            val shape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(SurfaceCard)
                    .then(if (isCurrent) Modifier.border(1.dp, PrimaryAccent.copy(alpha = 0.7f), shape) else Modifier)
                    .clickable { onEpisodeClick(episode.number) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (isCurrent) PrimaryAccent else PrimaryAccent.copy(alpha = if (watched) 0.1f else 0.2f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (watched) "✓" else episode.number.toString(),
                        color = if (isCurrent) TextPrimary else PrimaryAccentLight.copy(alpha = if (watched) 0.7f else 1f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        episode.name, color = if (watched) TextSecondary else TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val sub = when {
                        isCurrent && progress >= 0.9f -> "Watched · tap to rewatch"
                        isCurrent && current!!.durationMs > 0 -> "Continue · ${((current.durationMs - current.progressMs).coerceAtLeast(0) / 60_000L).coerceAtLeast(1)}m left"
                        else -> episode.description ?: "Episode ${episode.number}"
                    }
                    Text(
                        sub,
                        color = if (isCurrent) PrimaryAccentLight else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCurrent && progress > 0f) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(PrimaryAccent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AmbiguousSelectionDialog(
    candidates: List<com.example.aniflow.data.model.ProviderSearchResult>,
    onSelect: (com.example.aniflow.data.model.ProviderSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Correct Title Match", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates) { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .clickable { onSelect(candidate) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(candidate.title, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PrimaryAccent)
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackInputDialog(
    initialFeedback: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialFeedback) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = SurfaceCard
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(400.dp)
                .padding(24.dp)
                .border(1.dp, PrimaryAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Why do you think this anime is good?",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
                    placeholder = { Text("Write your reason...", color = TextSecondary, fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                        cursorColor = PrimaryAccent
                    )
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    var isCancelFocused by remember { mutableStateOf(false) }
                    var isSubmitFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .onFocusChanged { isCancelFocused = it.isFocused }
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isCancelFocused) PrimaryAccent else Color.Transparent)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = if (isCancelFocused) TextPrimary else TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .onFocusChanged { isSubmitFocused = it.isFocused }
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSubmitFocused) PrimaryAccent else SecondaryAccent)
                            .clickable { onSubmit(text) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Submit", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

