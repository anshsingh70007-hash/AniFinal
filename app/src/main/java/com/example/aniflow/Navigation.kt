package com.example.aniflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.aniflow.data.repository.DefaultAnimeRepository
import com.example.aniflow.ui.detail.DetailScreen
import com.example.aniflow.ui.main.MainScreen
import com.example.aniflow.ui.player.PlayerScreen
import com.example.aniflow.ui.redesign.RedesignDetailScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val deviceType = LocalDeviceType.current
    val repository = remember { DefaultAnimeRepository(context.applicationContext) }
    val watchlistStore = remember { com.example.aniflow.data.WatchlistStore(context) }
    val watchHistoryStore = remember { com.example.aniflow.data.WatchHistoryStore(context) }
    val settingsStore = remember { com.example.aniflow.data.SettingsStore(context) }
    val backStack = rememberNavBackStack(Main)
    val isRedesign = remember {
        context.packageName.endsWith(".redesign")
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    onItemClick = { navKey -> backStack.add(navKey) },
                    deviceType = deviceType,
                    repository = repository,
                    watchlistStore = watchlistStore,
                    watchHistoryStore = watchHistoryStore,
                    settingsStore = settingsStore
                )
            }
            entry<Detail> { detailKey ->
                if (isRedesign) {
                    RedesignDetailScreen(
                        animeId = detailKey.animeId,
                        repository = repository,
                        deviceType = deviceType,
                        watchlistStore = watchlistStore,
                        onEpisodeClick = { epNum ->
                            backStack.add(Player(detailKey.animeId, epNum))
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                } else {
                    DetailScreen(
                        animeId = detailKey.animeId,
                        repository = repository,
                        deviceType = deviceType,
                        watchlistStore = watchlistStore,
                        onEpisodeClick = { epNum ->
                            backStack.add(Player(detailKey.animeId, epNum))
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
            entry<Player> { playerKey ->
                PlayerScreen(
                    animeId = playerKey.animeId,
                    episodeNumber = playerKey.episodeNumber,
                    repository = repository,
                    deviceType = deviceType,
                    watchHistoryStore = watchHistoryStore,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
