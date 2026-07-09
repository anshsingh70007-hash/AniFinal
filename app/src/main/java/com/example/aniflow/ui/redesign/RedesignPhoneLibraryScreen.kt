package com.example.aniflow.ui.redesign

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.model.Anime
import com.example.aniflow.theme.*
import com.example.aniflow.ui.redesign.components.AmbientBackground
import com.example.aniflow.ui.redesign.theme.GlassTokens

@Composable
fun RedesignPhoneLibraryScreen(
    watchlist: List<Anime>,
    onAnimeClick: (Anime) -> Unit
) {
    AmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = "My Watchlist",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            if (watchlist.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Your watchlist is empty. Bookmark anime to see them here!", color = GlassTokens.TextMuted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(watchlist) { anime ->
                        RedesignAnimePosterCard(anime = anime, onClick = { onAnimeClick(anime) })
                    }
                }
            }
        }
    }
}
