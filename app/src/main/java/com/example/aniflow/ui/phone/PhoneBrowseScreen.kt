package com.example.aniflow.ui.phone

import androidx.compose.ui.focus.focusRequester

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.model.Anime
import com.example.aniflow.theme.*

@Composable
fun PhoneBrowseScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedGenre: String?,
    onGenreSelect: (String?) -> Unit,
    results: List<Anime>,
    hasNextPage: Boolean,
    isSearchLoading: Boolean,
    onLoadMore: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    recent: List<String> = emptyList(),
    onClearRecent: () -> Unit = {},
    /** Bumped by the top bar's search button: focus the field (and raise the keyboard). */
    focusSearch: Int = 0,
) {
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Remember which press was handled, so coming back to this tab later doesn't pop the keyboard again.
    var handledFocus by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(focusSearch) {
        if (focusSearch > handledFocus) {
            handledFocus = focusSearch
            runCatching { searchFocus.requestFocus() }
        }
    }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
            placeholder = { Text("Search anime by title…", color = TextTertiary) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = TextSecondary) } }
            } else null,
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

        if (recent.isNotEmpty() && query.isBlank() && selectedGenre == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(recent) { r -> GenreChip(text = r, isSelected = false, onClick = { onQueryChange(r) }) }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Clear", color = PrimaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClearRecent).padding(6.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        // Genre Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (selectedGenre != null) {
                item {
                    GenreChip(
                        text = "✕  Clear",
                        isSelected = true,
                        onClick = { onGenreSelect(null) }
                    )
                }
            }
            items(genres) { genre ->
                val isSelected = selectedGenre == genre
                GenreChip(
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PrimaryAccent else PrimaryDarker)
                        .border(
                            width = 0.5.dp,
                            color = if (isSelected) SecondaryAccent else SurfaceBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onQueryChange(letter) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = letter,
                        color = if (isSelected) TextPrimary else TextSecondary,
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
                    // The repository folds network failures into an empty page, so the copy has to be
                    // true for both "no match" and "offline".
                    val (headline, hint) = when {
                        query.isNotBlank() -> "No matches for “${query.trim()}”" to "Check the spelling, try the Japanese title, or check your connection."
                        selectedGenre != null -> "Nothing in $selectedGenre right now" to "Check your connection, or pick another genre."
                        else -> "Couldn't load titles" to "Check your connection, then search or pick a genre."
                    }
                    Text(headline, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(hint, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(110.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = LocalPhoneBottomInset.current + 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(results) { anime ->
                    AnimePosterCard(anime = anime, onClick = { onAnimeClick(anime) })
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
fun GenreChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) PrimaryAccent else SurfaceCard)
            .border(
                width = 1.dp,
                color = if (isSelected) SecondaryAccent else SurfaceBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
