package com.example.aniflow.ui.redesign

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import com.example.aniflow.ui.redesign.components.AmbientBackground
import com.example.aniflow.ui.redesign.components.GlassCard
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.glassSurface
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aniflow.data.ProviderMappingStore
import com.example.aniflow.ui.detail.DetailUiState
import com.example.aniflow.ui.detail.DetailViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items


@Composable
fun RedesignDetailScreen(
    animeId: Int,
    repository: AnimeRepository,
    deviceType: DeviceType,
    watchlistStore: WatchlistStore,
    onEpisodeClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: DetailViewModel = viewModel {
        DetailViewModel(repository, ProviderMappingStore(context.applicationContext))
    }

    LaunchedEffect(animeId) {
        viewModel.loadAnimeDetails(animeId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val isBookmarked by watchlistStore.isBookmarkedFlow(animeId).collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()
    var isBackFocused by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is DetailUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(PrimaryDark), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassTokens.GlowCyan)
            }
        }
        is DetailUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().background(PrimaryDark),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = state.message, color = TextPrimary, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { onBack() }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)) {
                        Text("Back", color = TextPrimary)
                    }
                    Button(onClick = { viewModel.loadAnimeDetails(animeId) }, colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.GlowCyan)) {
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
                else -> null
            }
            val episodes = when (state) {
                is DetailUiState.Success -> state.episodes
                else -> emptyList()
            }

            if (currentAnime != null) {
            val chunkSize = 100
            val episodeChunks = remember(episodes) {
                if (episodes.isEmpty()) emptyList()
                else episodes.chunked(chunkSize)
            }
            var selectedChunkIndex by remember(episodes) { mutableStateOf(0) }
            val currentEpisodes = remember(episodeChunks, selectedChunkIndex) {
                episodeChunks.getOrNull(selectedChunkIndex) ?: emptyList()
            }

            AmbientBackground {
                val isBlurry = state is DetailUiState.Ambiguous
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (isBlurry) it.blur(20.dp) else it }
                ) {
                    // Panoramic Background Image with soft dark blur overlay
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
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PrimaryDark.copy(alpha = 0.5f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (deviceType == DeviceType.TV) 380.dp else 260.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, PrimaryDark.copy(alpha = 0.6f), PrimaryDark),
                                    startY = 0f
                                )
                            )
                    )

                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 32.dp)
                    ) {
                        // Top back button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isBackFocused = it.isFocused }
                                    .focusGlow(isBackFocused, RoundedCornerShape(20.dp), focusedScale = 1.04f)
                                    .glassSurface(RoundedCornerShape(20.dp), isFocused = isBackFocused)
                                    .clickable { onBack() }
                                    .focusable()
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

                        // Push details card down to show the panoramic banner behind it cleanly
                        Spacer(modifier = Modifier.height(if (deviceType == DeviceType.TV) 140.dp else 80.dp))

                        // Frosted Information Details Panel Card
                        GlassCard(
                            onClick = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (deviceType == DeviceType.TV) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // TV left-side Cover image
                                        AsyncImage(
                                            model = currentAnime.coverImage,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(160.dp)
                                                .height(230.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        )

                                        // TV right-side metadata Column
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = currentAnime.title,
                                                color = TextPrimary,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            currentAnime.englishTitle?.let { engTitle ->
                                                if (engTitle != currentAnime.title) {
                                                    Text(
                                                        text = engTitle,
                                                        color = GlassTokens.TextMuted,
                                                        fontSize = 14.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // Format, Score, Episodes, Studio metadata
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                currentAnime.averageScore?.let { score ->
                                                    Text(
                                                        "★ ${String.format("%.1f", score / 10.0)}",
                                                        color = WarningAmber,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }

                                                if (currentAnime.episodes != null) {
                                                    Text(
                                                        "${currentAnime.episodes} Episodes",
                                                        color = GlassTokens.TextMuted,
                                                        fontSize = 12.sp
                                                    )
                                                }

                                                val detailsStr = listOfNotNull(
                                                    currentAnime.season?.let { "$it ${currentAnime.seasonYear ?: ""}" },
                                                    currentAnime.studioName
                                                ).joinToString(" • ")

                                                if (detailsStr.isNotEmpty()) {
                                                    Text(
                                                        detailsStr,
                                                        color = GlassTokens.TextMuted,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            // Genres tags row
                                            if (currentAnime.genres.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    currentAnime.genres.take(4).forEach { genre ->
                                                        Box(
                                                            modifier = Modifier
                                                                .glassSurface(RoundedCornerShape(6.dp), isFocused = false)
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(genre, color = TextPrimary, fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }

                                            // Description
                                            val cleanDescription = remember(currentAnime.description) {
                                                currentAnime.description?.replace(Regex("<[^>]*>"), "") ?: "No description available."
                                            }
                                            Text(
                                                text = cleanDescription,
                                                color = GlassTokens.TextMuted,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(Modifier.height(12.dp))

                                            // Buttons Row
                                            ActionButtonsRow(
                                                episodes = episodes,
                                                isBookmarked = isBookmarked,
                                                onEpisodeClick = onEpisodeClick,
                                                watchlistStore = watchlistStore,
                                                currentAnime = currentAnime,
                                                coroutineScope = coroutineScope
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Phone stacked layout with overlapping style
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Phone Cover image
                                        AsyncImage(
                                            model = currentAnime.coverImage,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(90.dp)
                                                .height(130.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        )

                                        // Phone title / stats Column
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = currentAnime.title,
                                                color = TextPrimary,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            currentAnime.englishTitle?.let { engTitle ->
                                                if (engTitle != currentAnime.title) {
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = engTitle,
                                                        color = GlassTokens.TextMuted,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                currentAnime.averageScore?.let { score ->
                                                    Text(
                                                        "★ ${String.format("%.1f", score / 10.0)}",
                                                        color = WarningAmber,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }

                                                if (currentAnime.episodes != null) {
                                                    Text(
                                                        "${currentAnime.episodes} eps",
                                                        color = GlassTokens.TextMuted,
                                                        fontSize = 12.sp
                                                    )
                                                }

                                                if (currentAnime.studioName != null) {
                                                    Text(
                                                        currentAnime.studioName,
                                                        color = GlassTokens.TextMuted,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            // Genres row for phone
                                            if (currentAnime.genres.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    currentAnime.genres.take(3).forEach { genre ->
                                                        Box(
                                                            modifier = Modifier
                                                                .glassSurface(RoundedCornerShape(4.dp), isFocused = false)
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(genre, color = TextPrimary, fontSize = 9.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // Description
                                    val cleanDescription = remember(currentAnime.description) {
                                        currentAnime.description?.replace(Regex("<[^>]*>"), "") ?: "No description available."
                                    }
                                    Text(
                                        text = cleanDescription,
                                        color = GlassTokens.TextMuted,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        maxLines = 5,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(Modifier.height(16.dp))

                                    // Buttons Row
                                    ActionButtonsRow(
                                        episodes = episodes,
                                        isBookmarked = isBookmarked,
                                        onEpisodeClick = onEpisodeClick,
                                        watchlistStore = watchlistStore,
                                        currentAnime = currentAnime,
                                        coroutineScope = coroutineScope
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        // Episodes Section
                        Text(
                            text = "🎬 Episodes",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        // Episode pagination ranges
                        if (episodeChunks.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                itemsIndexed(episodeChunks) { index, chunk ->
                                    val start = chunk.first().number
                                    val end = chunk.last().number
                                    val label = if (start == end) "$start" else "$start - $end"
                                    val isSelected = selectedChunkIndex == index
                                    var isFocused by remember { mutableStateOf(false) }

                                    Box(
                                        modifier = Modifier
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .focusGlow(isFocused, RoundedCornerShape(12.dp), focusedScale = 1.04f)
                                            .glassSurface(
                                                shape = RoundedCornerShape(12.dp),
                                                isFocused = isFocused || isSelected
                                            )
                                            .clickable { selectedChunkIndex = index }
                                            .focusable()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected || isFocused) GlassTokens.GlowCyan else TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (currentEpisodes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No episodes found.", color = GlassTokens.TextMuted, fontSize = 14.sp)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = if (deviceType == DeviceType.TV) 48.dp else 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                currentEpisodes.forEach { episode ->
                                    RedesignEpisodeCard(
                                        episode = episode,
                                        onClick = { onEpisodeClick(episode.number) }
                                    )
                                }
                            }
                        }
                    }
                    if (isBlurry) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f))
                                .clickable(enabled = true, onClick = {})
                        )
                        RedesignAmbiguousSelectionDialog(
                            candidates = (state as DetailUiState.Ambiguous).candidates,
                            onSelect = { candidate ->
                                viewModel.confirmMapping(currentAnime, candidate)
                            },
                            onDismiss = {
                                viewModel.uiState.value = DetailUiState.NotFound(currentAnime)
                            }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ActionButtonsRow(
    episodes: List<Episode>,
    isBookmarked: Boolean,
    onEpisodeClick: (Int) -> Unit,
    watchlistStore: WatchlistStore,
    currentAnime: Anime,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var isPlayFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .onFocusChanged { isPlayFocused = it.isFocused }
                .focusGlow(isPlayFocused, RoundedCornerShape(24.dp), focusedScale = 1.02f)
                .glassSurface(RoundedCornerShape(24.dp), isFocused = isPlayFocused)
                .clickable {
                    if (episodes.isNotEmpty()) {
                        onEpisodeClick(episodes.first().number)
                    } else {
                        onEpisodeClick(1)
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = if (isPlayFocused) GlassTokens.GlowCyan else TextPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Play Episode 1",
                    color = if (isPlayFocused) GlassTokens.GlowCyan else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        var isFavFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(48.dp)
                .onFocusChanged { isFavFocused = it.isFocused }
                .focusGlow(isFavFocused, RoundedCornerShape(24.dp), focusedScale = 1.05f)
                .glassSurface(RoundedCornerShape(24.dp), isFocused = isFavFocused)
                .clickable {
                    coroutineScope.launch {
                        if (isBookmarked) {
                            watchlistStore.removeFromWatchlist(currentAnime.id)
                        } else {
                            watchlistStore.addToWatchlist(currentAnime)
                        }
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Watchlist",
                tint = if (isBookmarked) GlassTokens.GlowRose else TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RedesignEpisodeCard(
    episode: Episode,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusGlow(isFocused, RoundedCornerShape(12.dp), focusedScale = 1.03f)
            .glassSurface(RoundedCornerShape(12.dp), isFocused = isFocused)
            .clickable { onClick() }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${episode.number}",
                    color = GlassTokens.GlowCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (isFocused) GlassTokens.GlowCyan else GlassTokens.TextMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RedesignAmbiguousSelectionDialog(
    candidates: List<com.example.aniflow.data.model.ProviderSearchResult>,
    onSelect: (com.example.aniflow.data.model.ProviderSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(420.dp)
                .wrapContentHeight()
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F0E17).copy(alpha = 0.98f)
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Multiple Matches Found",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Please choose the correct title below:",
                    color = GlassTokens.TextMuted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(candidates) { candidate ->
                        var isItemFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .focusGlow(isItemFocused, RoundedCornerShape(12.dp), focusedScale = 1.02f)
                                .glassSurface(RoundedCornerShape(12.dp), isFocused = isItemFocused)
                                .clickable { onSelect(candidate) }
                                .focusable()
                                .padding(16.dp)
                        ) {
                            Text(candidate.title, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    var isCancelFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .onFocusChanged { isCancelFocused = it.isFocused }
                            .focusable()
                    ) {
                        Text(
                            "Cancel",
                            color = if (isCancelFocused) GlassTokens.GlowCyan else GlassTokens.TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

