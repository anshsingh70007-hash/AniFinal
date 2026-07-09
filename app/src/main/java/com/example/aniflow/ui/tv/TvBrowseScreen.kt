package com.example.aniflow.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.model.Anime
import com.example.aniflow.theme.*

@Composable
fun TvBrowseScreen(
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
            .background(PrimaryDark)
            .padding(horizontal = 24.dp, vertical = 24.dp)
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

        Spacer(Modifier.height(16.dp))

        // Genre Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (selectedGenre != null) {
                item {
                    TvGenreChip(
                        text = "Clear Filter (X)",
                        isSelected = true,
                        onClick = { onGenreSelect(null) }
                    )
                }
            }
            items(genres) { genre ->
                val isSelected = selectedGenre == genre
                TvGenreChip(
                    text = genre,
                    isSelected = isSelected,
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
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSelected -> PrimaryAccent
                                isFocused -> SurfaceBorder
                                else -> PrimaryDarker
                            }
                        )
                        .border(
                            width = 0.5.dp,
                            color = if (isFocused || isSelected) SecondaryAccent else SurfaceBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onQueryChange(letter) }
                        .focusable()
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

        Spacer(Modifier.height(20.dp))

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
                columns = GridCells.Adaptive(150.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(results) { anime ->
                    TvAnimeCard(anime = anime, onClick = { onAnimeClick(anime) })
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
                            CircularProgressIndicator(color = PrimaryAccent, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvGenreChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isSelected -> PrimaryAccent
                    isFocused -> SurfaceBorder
                    else -> SurfaceCard
                }
            )
            .border(
                width = 1.dp,
                color = if (isFocused || isSelected) SecondaryAccent else SurfaceBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable()
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
