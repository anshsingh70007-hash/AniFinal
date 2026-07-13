package com.example.aniflow.ui.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.model.Anime
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.components.AmbientBackground
import com.example.aniflow.ui.redesign.components.GlassCard
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.redesign.theme.focusGlow
import com.example.aniflow.ui.redesign.theme.glassSurface

@Composable
fun RedesignTvBrowseScreen(
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Frosted search input
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

        Spacer(Modifier.height(8.dp))

        // Genre Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (selectedGenre != null) {
                item {
                    RedesignTvGenreChip(
                        text = "Clear Filter (X)",
                        isSelected = true,
                        onClick = { onGenreSelect(null) }
                    )
                }
            }
            items(genres) { genre ->
                val isSelected = selectedGenre == genre
                RedesignTvGenreChip(
                    text = genre,
                    isSelected = isSelected,
                    onClick = { onGenreSelect(if (isSelected) null else genre) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

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
                
                Box(
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusGlow(isFocused, RoundedCornerShape(12.dp))
                        .glassSurface(
                            shape = RoundedCornerShape(12.dp),
                            isFocused = isFocused || isSelected
                        )
                        .clickable { onQueryChange(letter) }
                        .focusable()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = letter,
                        color = if (isSelected || isFocused) GlassTokens.GlowCyan else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (results.isEmpty() && isSearchLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassTokens.GlowCyan)
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (selectedGenre != null) "No results found for genre $selectedGenre" else "Type at least 2 characters to search or select a genre above",
                        color = GlassTokens.TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(results) { anime ->
                    RedesignTvPosterCard(anime = anime, onClick = { onAnimeClick(anime) })
                }
                
                if (hasNextPage && !isSearchLoading && results.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(Unit) {
                            onLoadMore()
                        }
                    }
                }
                
                if (isSearchLoading && hasNextPage) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GlassTokens.GlowCyan, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RedesignTvGenreChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusGlow(isFocused, RoundedCornerShape(16.dp))
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                isFocused = isFocused || isSelected
            )
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected || isFocused) GlassTokens.GlowCyan else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
