package com.example.aniflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.DeviceType
import com.example.aniflow.data.model.Anime
import com.example.aniflow.theme.*

@Composable
fun BrowseScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedGenre: String?,
    onGenreSelect: (String?) -> Unit,
    results: List<Anime>,
    hasNextPage: Boolean,
    isSearchLoading: Boolean,
    deviceType: DeviceType,
    onLoadMore: () -> Unit,
    onAnimeClick: (Anime) -> Unit
) {
    val genres = remember {
        listOf("Action", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Adventure", "Suspense", "Slice of Life")
    }

    val gridState = rememberLazyGridState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisibleItem > 0 && lastVisibleItem >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && hasNextPage && !isSearchLoading) {
            onLoadMore()
        }
    }

    val outerPadding = if (deviceType == DeviceType.TV) 24.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
            .padding(horizontal = outerPadding, vertical = outerPadding)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search upcoming & popular anime...", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryAccent,
                unfocusedBorderColor = SurfaceBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = PrimaryDarker,
                unfocusedContainerColor = PrimaryDarker
            ),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        // Genre Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (selectedGenre != null) {
                item {
                    AdaptiveGenreChip(
                        text = "Clear Filter (X)",
                        isSelected = true,
                        deviceType = deviceType,
                        onClick = { onGenreSelect(null) }
                    )
                }
            }
            items(genres) { genre ->
                val isSelected = selectedGenre == genre
                AdaptiveGenreChip(
                    text = genre,
                    isSelected = isSelected,
                    deviceType = deviceType,
                    onClick = { onGenreSelect(if (isSelected) null else genre) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // A-Z Alphabetical Search Bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val alphabets = ('A'..'Z').map { it.toString() }
            items(alphabets) { letter ->
                val isSelected = query == letter
                var isFocused by remember { mutableStateOf(false) }
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isFocused) 1.05f else 1.0f,
                    label = "letterScale"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PrimaryAccent else if (isFocused) SurfaceCard else PrimaryDarker)
                        .border(
                            width = if (isFocused) 2.dp else 0.5.dp,
                            color = if (isFocused) SecondaryAccent else if (isSelected) SecondaryAccent else SurfaceBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .run {
                            if (deviceType == DeviceType.TV) {
                                focusable()
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .clickable { onQueryChange(letter) }
                            } else {
                                clickable { onQueryChange(letter) }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = letter,
                        color = if (isSelected || isFocused) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (results.isEmpty() && isSearchLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryAccent)
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (selectedGenre != null) "No results found for genre $selectedGenre" else "Type at least 2 characters to search or select a genre above",
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(if (deviceType == DeviceType.TV) 150.dp else 110.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(results) { anime ->
                    AdaptiveAnimePosterCard(anime = anime, deviceType = deviceType, onClick = { onAnimeClick(anime) })
                }
                
                if (isSearchLoading && hasNextPage) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryAccent, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdaptiveGenreChip(
    text: String,
    isSelected: Boolean,
    deviceType: DeviceType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        label = "chipScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = borderScale
                scaleY = borderScale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) PrimaryAccent 
                else if (isFocused) SurfaceCard 
                else SurfaceCard.copy(alpha = 0.5f)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) SecondaryAccent 
                        else if (isSelected) SecondaryAccent.copy(alpha = 0.5f) 
                        else SurfaceBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .run {
                if (deviceType == DeviceType.TV) {
                    focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onClick() }
                } else {
                    clickable { onClick() }
                }
            }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected || isFocused) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
