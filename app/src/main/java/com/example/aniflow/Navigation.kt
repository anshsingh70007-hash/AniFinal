package com.example.aniflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
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
import com.example.aniflow.ui.redesign.components.AizenSpiritualPressureHost

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val deviceType = LocalDeviceType.current
    val repository = remember { DefaultAnimeRepository(context.applicationContext) }
    
    DisposableEffect(repository) {
        val engine = SelfHealingEngine(repository)
        engine.start()
        onDispose {
            engine.stop()
        }
    }

    DisposableEffect(Unit) {
        BleachBgmManager.start(context)
        onDispose {
            BleachBgmManager.pause()
        }
    }

    val watchlistStore = remember { com.example.aniflow.data.WatchlistStore(context) }
    val watchHistoryStore = remember { com.example.aniflow.data.WatchHistoryStore(context) }
    val settingsStore = remember { com.example.aniflow.data.SettingsStore(context) }
    val userFeedbackStore = remember { com.example.aniflow.data.UserFeedbackStore(context) }
    val backStack = rememberNavBackStack(Main)
    val isRedesign = remember {
        context.packageName.endsWith(".redesign")
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Without these, every NavEntry shares the Activity's ViewModelStore and
        // SaveableStateRegistry: Detail -> Detail keeps a single DetailViewModel, so
        // popping back leaves the previous entry showing the newer anime's state.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Main> {
                AizenSpiritualPressureHost {
                    MainScreen(
                        onItemClick = { navKey -> backStack.add(navKey) },
                        deviceType = deviceType,
                        repository = repository,
                        watchlistStore = watchlistStore,
                        watchHistoryStore = watchHistoryStore,
                        settingsStore = settingsStore,
                        userFeedbackStore = userFeedbackStore,
                        // NavDisplay handles BACK itself while anything is stacked on top of Main.
                        isTopDestination = backStack.size == 1
                    )
                }
            }
            entry<Detail> { detailKey ->
                AizenSpiritualPressureHost {
                    if (isRedesign) {
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
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
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
