package com.example.aniflow.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aniflow.data.ProviderMappingStore
import com.example.aniflow.data.model.EpisodeLookupResult
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@Composable
fun DetailScreen(
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
                CircularProgressIndicator(color = PrimaryAccent)
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
                else -> null
            }
            val episodes = when (state) {
                is DetailUiState.Success -> state.episodes
                else -> emptyList()
            }

            if (currentAnime != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PrimaryDark)
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
                        modifier = Modifier.fillMaxSize()
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
                                .clickable { onBack() }
                                .onFocusChanged { isBackFocused = it.isFocused }
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
                            Text(
                                text = "${currentAnime.studioName ?: "Unknown Studio"} • ${currentAnime.averageScore ?: "--"}% Score",
                                color = SecondaryAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Action row: Play Ep 1, bookmark
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var isWatchFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(if (isWatchFocused) SecondaryAccent else PrimaryAccent)
                                        .border(
                                            width = if (isWatchFocused) 2.dp else 0.dp,
                                            color = if (isWatchFocused) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .clickable { onEpisodeClick(1) }
                                        .onFocusChanged { isWatchFocused = it.isFocused }
                                        .focusable()
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = TextPrimary)
                                        Text("Watch Ep 1", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

                    // Episodes Grid or List
                    if (deviceType == DeviceType.TV) {
                        TvEpisodesGrid(episodes = episodes, onEpisodeClick = onEpisodeClick)
                    } else {
                        PhoneEpisodesList(episodes = episodes, onEpisodeClick = onEpisodeClick)
                    }
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
                }
            }
        }
    }
}
}

@Composable
fun PhoneEpisodesList(
    episodes: List<Episode>,
    onEpisodeClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        episodes.forEach { episode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCard)
                    .clickable { onEpisodeClick(episode.number) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Number Circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PrimaryAccent.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = episode.number.toString(),
                        color = PrimaryAccentLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(episode.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        episode.description ?: "Play high quality streaming source",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TvEpisodesGrid(
    episodes: List<Episode>,
    onEpisodeClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
    ) {
        val chunked = episodes.chunked(3)
        chunked.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { episode ->
                    var isFocused by remember { mutableStateOf(false) }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFocused) PrimaryAccent else SurfaceCard)
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) SecondaryAccent else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onEpisodeClick(episode.number) }
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = episode.number.toString(),
                                color = if (isFocused) TextPrimary else PrimaryAccentLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = episode.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Click to Stream HLS",
                                    color = if (isFocused) TextPrimary.copy(alpha = 0.8f) else TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                if (row.size < 3) {
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
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

