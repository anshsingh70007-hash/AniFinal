package com.example.aniflow.ui.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.model.Anime
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.components.AmbientBackground
import com.example.aniflow.ui.redesign.components.GlassCard
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.redesign.theme.glassSurface

@Composable
fun RedesignPhoneBrowseScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedGenre: String?,
    onGenreSelect: (String?) -> Unit,
    results: List<Anime>,
    hasNextPage: Boolean,
    isSearchLoading: Boolean,
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

    AmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Frosted Outlined Search Input Field
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(RoundedCornerShape(12.dp)),
                placeholder = { Text("Search upcoming & popular anime...", color = GlassTokens.TextMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // Genre Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (selectedGenre != null) {
                    item {
                        RedesignGenreChip(
                            text = "Clear Filter (X)",
                            isSelected = true,
                            onClick = { onGenreSelect(null) }
                        )
                    }
                }
                items(genres) { genre ->
                    val isSelected = selectedGenre == genre
                    RedesignGenreChip(
                        text = genre,
                        isSelected = isSelected,
                        onClick = { onGenreSelect(genre) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Results grid
            Box(modifier = Modifier.weight(1f)) {
                if (results.isEmpty() && !isSearchLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (query.isEmpty() && selectedGenre == null) "Select a genre or start searching..." else "No results found.",
                            color = GlassTokens.TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results) { anime ->
                            RedesignAnimePosterCard(anime = anime, onClick = { onAnimeClick(anime) })
                        }

                        if (isSearchLoading) {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = GlassTokens.GlowCyan, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RedesignGenreChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .glassSurface(
                shape = RoundedCornerShape(18.dp),
                isFocused = isSelected
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) GlassTokens.GlowCyan else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
