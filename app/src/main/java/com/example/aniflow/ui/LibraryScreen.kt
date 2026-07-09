package com.example.aniflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.DeviceType
import com.example.aniflow.data.model.Anime
import com.example.aniflow.theme.*

@Composable
fun LibraryScreen(
    watchlist: List<Anime>,
    deviceType: DeviceType,
    onAnimeClick: (Anime) -> Unit
) {
    val outerPadding = if (deviceType == DeviceType.TV) 24.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
            .padding(horizontal = outerPadding, vertical = outerPadding)
    ) {
        Text(
            text = "My Watchlist",
            color = TextPrimary,
            fontSize = if (deviceType == DeviceType.TV) 24.sp else 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        if (watchlist.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Your watchlist is empty. Bookmark anime to see them here!", color = TextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (deviceType == DeviceType.TV) 150.dp else 110.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(watchlist) { anime ->
                    AdaptiveAnimePosterCard(anime = anime, deviceType = deviceType, onClick = { onAnimeClick(anime) })
                }
            }
        }
    }
}
