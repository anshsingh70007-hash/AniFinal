package com.example.aniflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.aniflow.data.SelfHealingEngine
import com.example.aniflow.data.repository.DefaultAnimeRepository
import com.example.aniflow.ui.detail.DetailScreen
import com.example.aniflow.ui.main.MainScreen
import com.example.aniflow.ui.player.PlayerScreen
import com.example.aniflow.ui.redesign.RedesignDetailScreen
import com.example.aniflow.ui.redesign.audio.BleachBgmManager
import com.example.aniflow.ui.maintenance.MaintenanceScreen
import com.example.aniflow.ui.brand.AniFlowLaunch
import com.example.aniflow.ui.tv.detail.TvDetailScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val deviceType = LocalDeviceType.current

    // Maintenance lock disabled for development/testing
    var isMaintenanceActive by remember { mutableStateOf(false) }

    if (isMaintenanceActive) {
        MaintenanceScreen(deviceType = deviceType)
        return
    }

    val repository = remember { DefaultAnimeRepository(context.applicationContext) }
    
    DisposableEffect(repository) {
        val engine = SelfHealingEngine(repository)
        engine.start()
        onDispose {
            engine.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            BleachBgmManager.pause()
        }
    }

    val watchlistStore = remember { com.example.aniflow.data.WatchlistStore(context.applicationContext) }
    val watchHistoryStore = remember { com.example.aniflow.data.WatchHistoryStore(context.applicationContext) }
    val settingsStore = remember { com.example.aniflow.data.SettingsStore(context.applicationContext) }
    val userFeedbackStore = remember { com.example.aniflow.data.UserFeedbackStore(context.applicationContext) }
    val backStack = rememberNavBackStack(Main)
    val isRedesign = remember {
        context.packageName.endsWith(".redesign")
    }

    AniFlowLaunch {
    NavDisplay(
        backStack = backStack,
        onBack = {
            com.example.aniflow.ui.sound.Sfx.back()
            backStack.removeLastOrNull()
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Main> {
                    MainScreen(
                        onItemClick = { navKey -> backStack.add(navKey) },
                        deviceType = deviceType,
                        repository = repository,
                        watchlistStore = watchlistStore,
                        watchHistoryStore = watchHistoryStore,
                        settingsStore = settingsStore,
                        userFeedbackStore = userFeedbackStore,
                        isTopDestination = backStack.size == 1
                    )
            }
            entry<Detail> { detailKey ->
                    if (deviceType == DeviceType.TV) {
                        // Remote-first detail: lazy episode ranges, Play focused on entry, focus
                        // returns to the played episode.
                        TvDetailScreen(
                            animeId = detailKey.animeId,
                            repository = repository,
                            watchlistStore = watchlistStore,
                            watchHistoryStore = watchHistoryStore,
                            onPlay = { epNum -> backStack.add(Player(detailKey.animeId, epNum)) },
                            onAnimeClick = { id -> backStack.add(Detail(id)) },
                            onSwitchAnime = { id -> switchDetail(backStack, id) },
                        )
                    } else if (isRedesign) {
                        RedesignDetailScreen(
                            animeId = detailKey.animeId,
                            repository = repository,
                            deviceType = deviceType,
                            watchlistStore = watchlistStore,
                            watchHistoryStore = watchHistoryStore,
                            userFeedbackStore = userFeedbackStore,
                            onEpisodeClick = { epNum ->
                                backStack.add(Player(detailKey.animeId, epNum))
                            },
                            onAnimeClick = { id ->
                                backStack.add(Detail(id))
                            },
                            onBack = { backStack.removeLastOrNull() }
                        )
                    } else {
                        DetailScreen(
                            animeId = detailKey.animeId,
                            repository = repository,
                            deviceType = deviceType,
                            watchlistStore = watchlistStore,
                            watchHistoryStore = watchHistoryStore,
                            userFeedbackStore = userFeedbackStore,
                            onEpisodeClick = { epNum ->
                                backStack.add(Player(detailKey.animeId, epNum))
                            },
                            onAnimeClick = { id ->
                                backStack.add(Detail(id))
                            },
                            onBack = { backStack.removeLastOrNull() },
                            onSwitchAnime = { id -> switchDetail(backStack, id) }
                        )
                    }
            }
            entry<Player> { playerKey ->
                DisposableEffect(Unit) {
                    BleachBgmManager.setVideoSuppressed(true)
                    onDispose {
                        BleachBgmManager.setVideoSuppressed(false)
                    }
                }
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
}

/**
 * Season/movie switch from a detail page: replace the page instead of stacking a new one, so
 * BACK leaves the franchise rather than walking back through every season visited.
 */
private fun switchDetail(backStack: MutableList<androidx.navigation3.runtime.NavKey>, id: Int) {
    if (backStack.lastOrNull() is Detail) backStack[backStack.lastIndex] = Detail(id) else backStack.add(Detail(id))
}
