package com.example.aniflow.ui.player

import android.content.Context
import android.content.ContextWrapper
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.data.ProviderRegistry
import com.example.aniflow.data.model.ProviderStatus
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.lifecycle.viewModelScope
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private var activeViewModel: PlayerViewModel? = null

    private fun createSettingsStore(context: Context): SettingsStore {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher + SupervisorJob()),
            produceFile = { File(context.filesDir, "test_preferences.preferences_pb") }
        )
        return SettingsStore(context, testDataStore)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ProviderRegistry.forceEnableAllForTesting = false
    }

    @After
    fun tearDown() {
        activeViewModel?.viewModelScope?.coroutineContext?.get(kotlinx.coroutines.Job)?.cancel()
        activeViewModel = null
        Dispatchers.resetMain()
        ProviderRegistry.forceEnableAllForTesting = false
    }

    @Test
    fun testTypedSourceParsingAndSorting() = runTest {
        val context = FakeContext()
        val repository = FakeAnimeRepository()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        val viewModel = PlayerViewModel(repository, watchHistoryStore, settingsStore).also { activeViewModel = it }

        val sources = listOf(
            SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url1", emptyMap(), 0, QualityPolicy.FixedHeight(1080), 1080, "ep1"),
            SourceEndpoint("id2", ProviderId.ANILIGHT, ServerId("near"), AudioType.SUB, StreamType.PROGRESSIVE, "url2", emptyMap(), 0, QualityPolicy.Auto, null, "ep1"),
            SourceEndpoint("id3", ProviderId.ANILIGHT, ServerId("misa"), AudioType.DUB, StreamType.PROGRESSIVE, "url3", emptyMap(), 0, QualityPolicy.FixedHeight(720), 720, "ep1")
        )

        assertEquals(viewModel.getQualityPolicyFromString("1080p"), QualityPolicy.FixedHeight(1080))
        assertEquals(viewModel.getQualityPolicyFromString("auto"), QualityPolicy.Auto)
        assertEquals(viewModel.getQualityPolicyFromString("random"), QualityPolicy.MaxAvailable)
    }

    @Test
    fun testServerAndLanguageSeparation() = runTest {
        val context = FakeContext()
        val repository = FakeAnimeRepository()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        val viewModel = PlayerViewModel(repository, watchHistoryStore, settingsStore).also { activeViewModel = it }

        val sources = listOf(
            SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url1", emptyMap(), 0, QualityPolicy.FixedHeight(1080), 1080, "ep1"),
            SourceEndpoint("id2", ProviderId.ANILIGHT, ServerId("near"), AudioType.SUB, StreamType.PROGRESSIVE, "url2", emptyMap(), 0, QualityPolicy.FixedHeight(720), 720, "ep1"),
            SourceEndpoint("id3", ProviderId.ANILIGHT, ServerId("misa"), AudioType.DUB, StreamType.PROGRESSIVE, "url3", emptyMap(), 0, QualityPolicy.FixedHeight(720), 720, "ep1")
        )

        viewModel.streamingSources.value = EpisodeSourcesResponse(sources)
        
        viewModel.selectServerAndType("misa", AudioType.SUB)
        assertEquals(viewModel.selectedServer.value, "misa")
        assertEquals(viewModel.selectedAudioType.value, AudioType.SUB)
        assertEquals(viewModel.selectedSource.value?.url, "url1")

        viewModel.selectServerAndType("near", AudioType.SUB)
        assertEquals(viewModel.selectedServer.value, "near")
        assertEquals(viewModel.selectedAudioType.value, AudioType.SUB)
        assertEquals(viewModel.selectedSource.value?.url, "url2")
    }

    @Test
    fun testRenditionAvailabilityFromStaticSources() = runTest {
        val context = FakeContext()
        val repository = FakeAnimeRepository()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        val viewModel = PlayerViewModel(repository, watchHistoryStore, settingsStore).also { activeViewModel = it }

        val sources = listOf(
            SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url1", emptyMap(), 0, QualityPolicy.FixedHeight(1080), 1080, "ep1"),
            SourceEndpoint("id2", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url2", emptyMap(), 0, QualityPolicy.FixedHeight(720), 720, "ep1"),
            SourceEndpoint("id3", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url3", emptyMap(), 0, QualityPolicy.FixedHeight(480), 480, "ep1")
        )

        viewModel.updateAvailableHeightsFromStaticSources(sources, "misa", AudioType.SUB)
        val heights = viewModel.availableHeightsForCurrentEndpoint.value
        assertEquals(heights.size, 3)
        assertTrue(heights.contains(1080))
        assertTrue(heights.contains(720))
        assertTrue(heights.contains(480))
    }

    @Test
    fun testSessionOnlyPlaybackSpeed() = runTest {
        val context = FakeContext()
        val repository = FakeAnimeRepository()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        val viewModel = PlayerViewModel(repository, watchHistoryStore, settingsStore).also { activeViewModel = it }

        settingsStore.setDefaultPlaybackSpeed(1.5f)
        
        val initialSpeed = settingsStore.defaultPlaybackSpeed.first()
        assertEquals(initialSpeed, 1.5f)

        viewModel.playbackSpeed.value = 2.0f
        
        assertEquals(viewModel.playbackSpeed.value, 2.0f)
        assertEquals(settingsStore.defaultPlaybackSpeed.first(), 1.5f)
    }

    @Test
    fun testFailoverOnPlaybackError() = runTest {
        val context = FakeContext()
        val repository = FakeAnimeRepository()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        val viewModel = PlayerViewModel(repository, watchHistoryStore, settingsStore).also { activeViewModel = it }

        val anime = Anime(id = 1, title = "Anime 1", coverImage = "", episodes = 12, averageScore = 80, genres = listOf("Action"))
        val episodes = listOf(Episode("ep1", "Episode 1", 1, "", ""))
        viewModel.anime.value = anime
        viewModel.episodeList.value = episodes
        viewModel.currentEpisodeIndex.value = 0

        val sources = listOf(
            SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url1", emptyMap(), 0, QualityPolicy.FixedHeight(1080), 1080, "ep1"),
            SourceEndpoint("id2", ProviderId.ANILIGHT, ServerId("near"), AudioType.SUB, StreamType.PROGRESSIVE, "url2", emptyMap(), 0, QualityPolicy.FixedHeight(1080), 1080, "ep1")
        )

        viewModel.streamingSources.value = EpisodeSourcesResponse(sources)
        viewModel.selectedServer.value = "misa"
        viewModel.selectedAudioType.value = AudioType.SUB
        viewModel.selectedSource.value = sources[0]

        // Trigger generic playback error
        val mockException = androidx.media3.common.PlaybackException(
            "Playback failed", null, androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )
        viewModel.handlePlaybackError(mockException, 0L)
        
        // Assert failover to NEAR
        assertEquals(viewModel.selectedServer.value, "near")
        assertEquals(viewModel.selectedSource.value?.url, "url2")
    }

    @Test
    fun testParallelServerAutoselection() = runTest {
        val context = FakeContext()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        settingsStore.setQuality("auto")
        settingsStore.setLanguage("sub")
        settingsStore.setProvider("anilight")
        
        val episodes = listOf(Episode("anilight:anime1|123|1", "Episode 1", 1, "", ""))
        val sources = listOf(
            SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url_misa_sub", emptyMap(), 0, QualityPolicy.Auto, null, "ep1"),
            SourceEndpoint("id2", ProviderId.ANILIGHT, ServerId("near"), AudioType.SUB, StreamType.PROGRESSIVE, "url_near_sub", emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
        )
        
        val mockRepo = object : AnimeRepository by FakeAnimeRepository() {
            override suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult =
                EpisodeLookupResult.Matched(ProviderId.ANILIGHT, ProviderSeriesId("anime1"), episodes)
            override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult =
                PlaybackResult.NativeSources(ProviderId.ANILIGHT, sources)
            override suspend fun checkUrlStatus(url: String, headers: Map<String, String>): Int =
                if (url == "url_near_sub") 200 else 404
        }
        
        val testViewModel = PlayerViewModel(mockRepo, watchHistoryStore, settingsStore, testDispatcher).also { activeViewModel = it }
        testViewModel.anime.value = Anime(id = 1, title = "Anime 1", coverImage = "", episodes = 12, averageScore = 80, genres = listOf("Action"))
        testViewModel.episodeList.value = episodes
        
        testViewModel.loadStreamingSourcesForIndex(0)
        advanceUntilIdle()

        // NEAR should be autoselected since checkUrlStatus returned 200 (live)
        assertEquals(testViewModel.selectedServer.value, "near")
        assertEquals(testViewModel.selectedSource.value?.url, "url_near_sub")
    }

    @Test
    fun testStaleWriteCancellableJob() = runTest {
        val context = FakeContext()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)

        val mockRepo = object : AnimeRepository by FakeAnimeRepository() {
            override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult {
                delay(1000) // Delay to simulate network resolution
                return PlaybackResult.NativeSources(
                    provider = ProviderId.ANILIGHT,
                    sources = listOf(
                        SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url_delayed", emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
                    )
                )
            }
        }

        val testViewModel = PlayerViewModel(mockRepo, watchHistoryStore, settingsStore, testDispatcher).also { activeViewModel = it }
        testViewModel.anime.value = Anime(id = 1, title = "Anime 1", coverImage = "", episodes = 12, averageScore = 80, genres = listOf("Action"))
        testViewModel.episodeList.value = listOf(Episode("ep1", "Episode 1", 1, "", ""))

        // Start loading first source
        testViewModel.loadStreamingSourcesForIndex(0)
        
        // Immediately start loading next episode to trigger cancellation
        testViewModel.loadStreamingSourcesForIndex(0)
        
        advanceUntilIdle()
        // Stale write should be discarded/ignored, only one stable state resolved
        assertEquals(testViewModel.errorMessage.value, "")
    }

    @Test
    fun testMonotonicCooldownMilestones() = runTest {
        val context = FakeContext()
        val repository = FakeAnimeRepository()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        val viewModel = PlayerViewModel(repository, watchHistoryStore, settingsStore).also { activeViewModel = it }

        val anime = Anime(id = 1, title = "Anime 1", coverImage = "", episodes = 12, averageScore = 80, genres = listOf("Action"))
        val episodes = listOf(Episode("ep1", "Episode 1", 1, "", ""))
        viewModel.anime.value = anime
        viewModel.episodeList.value = episodes
        viewModel.currentEpisodeIndex.value = 0

        val sources = listOf(
            SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url1", emptyMap(), 0, QualityPolicy.FixedHeight(1080), 1080, "ep1")
        )
        viewModel.streamingSources.value = EpisodeSourcesResponse(sources)
        viewModel.selectedSource.value = sources[0]

        // Mark it failed
        val mockException = androidx.media3.common.PlaybackException(
            "Playback failed", null, androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )
        viewModel.handlePlaybackError(mockException, 0L)
        viewModel.handlePlaybackError(mockException, 0L)

        advanceUntilIdle()

        // It should have failed and switched
        assertTrue(viewModel.hasError.value)

        // Trigger milestone: first frame rendered
        viewModel.onFirstFrameRendered()
        
        // Wait 3 seconds for stable play milestone
        delay(3500)
        
        // Cooldown should be cleared
        assertFalse(viewModel.errorMessage.value.contains("All servers cooled down"))
    }

    @Test
    fun testManualProviderSwitchEmbedOnlyRejection() = runTest {
        val context = FakeContext()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        settingsStore.setQuality("auto")
        settingsStore.setLanguage("sub")
        settingsStore.setProvider("anilight")

        val episodes = listOf(Episode("anilight:anime1|123|1", "Episode 1", 1, "", ""))
        val mockRepo = object : AnimeRepository by FakeAnimeRepository() {
            override suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult =
                EpisodeLookupResult.Matched(ProviderId.ANILIGHT, ProviderSeriesId("anime1"), episodes)
            override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult {
                println("getStreamingSources called for ${request.provider}")
                if (request.provider == ProviderId.MIRURO) {
                    return PlaybackResult.EmbedOnly(ProviderId.MIRURO, "https://embed.com/1")
                } else {
                    return PlaybackResult.NativeSources(ProviderId.ANILIGHT, listOf(
                        SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url1", emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
                    ))
                }
            }
        }

        val testViewModel = PlayerViewModel(mockRepo, watchHistoryStore, settingsStore, testDispatcher)
        testViewModel.anime.value = Anime(id = 1, title = "Anime 1", coverImage = "", episodes = 12, averageScore = 80, genres = listOf("Action"))
        testViewModel.episodeList.value = episodes
        testViewModel.currentEpisodeIndex.value = 0

        // Set initial successful source
        testViewModel.loadStreamingSourcesForIndex(0)
        advanceUntilIdle()

        assertEquals("url1", testViewModel.selectedSource.value?.url)

        // Try to switch to MIRURO (which returns EmbedOnly)
        ProviderRegistry.forceEnableAllForTesting = true
        testViewModel.selectProviderManual(ProviderId.MIRURO, 5000L)
        advanceUntilIdle()

        // Switch should be rejected, error message shown, and previous source retained
        assertTrue(testViewModel.hasError.value)
        assertTrue(testViewModel.errorMessage.value.contains("only offers non-native embed"))
        assertEquals("url1", testViewModel.selectedSource.value?.url)
    }

    @Test
    fun testManualProviderSwitchIdentityMismatchRollback() = runTest {
        val context = FakeContext()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        settingsStore.setQuality("auto")
        settingsStore.setLanguage("sub")
        settingsStore.setProvider("anilight")

        val episodes = listOf(Episode("anilight:anime1|123|1", "Episode 1", 1, "", ""))
        val mockRepo = object : AnimeRepository by FakeAnimeRepository() {
            override suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult =
                EpisodeLookupResult.Matched(ProviderId.ANILIGHT, ProviderSeriesId("anime1"), episodes)
            override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult =
                if (request.provider == ProviderId.MIRURO) {
                    PlaybackResult.Error(ProviderId.MIRURO, PlaybackErrorType.IdentityMismatch, "Title mismatch")
                } else {
                    PlaybackResult.NativeSources(ProviderId.ANILIGHT, listOf(
                        SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url1", emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
                    ))
                }
        }

        val testViewModel = PlayerViewModel(mockRepo, watchHistoryStore, settingsStore, testDispatcher)
        testViewModel.anime.value = Anime(id = 1, title = "Anime 1", coverImage = "", episodes = 12, averageScore = 80, genres = listOf("Action"))
        testViewModel.episodeList.value = episodes
        testViewModel.currentEpisodeIndex.value = 0

        testViewModel.loadStreamingSourcesForIndex(0)
        advanceUntilIdle()

        assertEquals("url1", testViewModel.selectedSource.value?.url)

        ProviderRegistry.forceEnableAllForTesting = true
        testViewModel.selectProviderManual(ProviderId.MIRURO, 5000L)
        advanceUntilIdle()

        // Switch should fail, mismatched marked, and previous source retained
        assertTrue(testViewModel.hasError.value)
        assertEquals("url1", testViewModel.selectedSource.value?.url)
        
        // MIRURO statuses should be IdentityMismatch
        testViewModel.updateProviderStatuses()
        assertEquals(ProviderStatus.IdentityMismatch, testViewModel.providerStatuses.value[ProviderId.MIRURO])
    }

    @Test
    fun testManualProviderSwitchRapidSwitchingStaleResult() = runTest {
        val context = FakeContext()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        settingsStore.setQuality("auto")
        settingsStore.setLanguage("sub")
        settingsStore.setProvider("anilight")

        val episodes = listOf(Episode("anilight:anime1|123|1", "Episode 1", 1, "", ""))
        val mockRepo = object : AnimeRepository by FakeAnimeRepository() {
            override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult {
                println("getStreamingSources started for ${request.provider}")
                try {
                    if (request.provider == ProviderId.MIRURO) {
                        delay(200L) // Simulate slow loading
                        println("MIRURO delay completed")
                        return PlaybackResult.NativeSources(ProviderId.MIRURO, listOf(
                            SourceEndpoint("id2", ProviderId.MIRURO, ServerId("near"), AudioType.SUB, StreamType.PROGRESSIVE, "url2_miruro", emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
                        ))
                    } else if (request.provider == ProviderId.ANIKOTO) {
                        delay(50L) // Faster loading
                        println("ANIKOTO delay completed")
                        return PlaybackResult.NativeSources(ProviderId.ANIKOTO, listOf(
                            SourceEndpoint("id3", ProviderId.ANIKOTO, ServerId("near"), AudioType.SUB, StreamType.PROGRESSIVE, "url3_anikoto", emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
                        ))
                    }
                } catch (e: Exception) {
                    println("getStreamingSources cancelled for ${request.provider}: ${e.message}")
                    throw e
                }
                return PlaybackResult.Error(request.provider, PlaybackErrorType.NoSources, "Error")
            }
        }

        val testViewModel = PlayerViewModel(mockRepo, watchHistoryStore, settingsStore, testDispatcher)
        testViewModel.anime.value = Anime(id = 1, title = "Anime 1", coverImage = "", episodes = 12, averageScore = 80, genres = listOf("Action"))
        testViewModel.episodeList.value = episodes
        testViewModel.currentEpisodeIndex.value = 0

        ProviderRegistry.forceEnableAllForTesting = true
        
        println("Calling selectProviderManual for MIRURO")
        testViewModel.selectProviderManual(ProviderId.MIRURO, 5000L)
        println("Calling selectProviderManual for ANIKOTO")
        testViewModel.selectProviderManual(ProviderId.ANIKOTO, 5000L)
        
        println("Advancing virtual clock")
        advanceUntilIdle()
        println("Virtual clock finished. selectedSource=${testViewModel.selectedSource.value?.url}")

        // Stale result (MIRURO) must be ignored, and only ANIKOTO committed
        assertEquals("url3_anikoto", testViewModel.selectedSource.value?.url)
    }

    @Test
    fun testCheckpointLanguageSubtitleQualityPreservation() = runTest {
        val context = FakeContext()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = createSettingsStore(context)
        settingsStore.setQuality("1080p")
        settingsStore.setLanguage("sub")
        settingsStore.setProvider("anilight")

        val episodes = listOf(Episode("anilight:anime1|123|1", "Episode 1", 1, "", ""))
        val mockRepo = object : AnimeRepository by FakeAnimeRepository() {
            override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult =
                PlaybackResult.NativeSources(request.provider, listOf(
                    SourceEndpoint("id_" + request.provider.name, request.provider, ServerId("near"), AudioType.SUB, StreamType.PROGRESSIVE, "url_" + request.provider.name.lowercase(), emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
                ))
        }

        val testViewModel = PlayerViewModel(mockRepo, watchHistoryStore, settingsStore, testDispatcher)
        testViewModel.anime.value = Anime(id = 1, title = "Anime 1", coverImage = "", episodes = 12, averageScore = 80, genres = listOf("Action"))
        testViewModel.episodeList.value = episodes
        testViewModel.currentEpisodeIndex.value = 0

        ProviderRegistry.forceEnableAllForTesting = true
        testViewModel.selectProviderManual(ProviderId.MIRURO, 8500L)
        advanceUntilIdle()

        // Position and preferences must be preserved
        assertEquals("url_miruro", testViewModel.selectedSource.value?.url)
        assertEquals(8500L, testViewModel.currentPosition.value)
        assertEquals(AudioType.SUB, testViewModel.selectedAudioType.value)
        assertEquals(QualityPolicy.FixedHeight(1080), testViewModel.selectedQualityPolicy.value)
    }
}

private class FakeContext : ContextWrapper(null) {
    private val filesDirLazy by lazy {
        val file = File(System.getProperty("java.io.tmpdir"), "aniflow_test_files_" + System.nanoTime())
        file.mkdirs()
        file
    }
    override fun getApplicationContext(): Context = this
    override fun getPackageName(): String = "com.example.aniflow"
    override fun getFilesDir(): File = filesDirLazy
}

private class FakeAnimeRepository : AnimeRepository {
    override fun getTrending(): Flow<List<Anime>> = flowOf(emptyList())
    override fun getPopular(): Flow<List<Anime>> = flowOf(emptyList())
    override fun getSeasonal(): Flow<List<Anime>> = flowOf(emptyList())
    override fun getAiringToday(): Flow<List<AiringAnime>> = flowOf(emptyList())
    override fun getTopRated(): Flow<List<Anime>> = flowOf(emptyList())
    override fun getUpcoming(): Flow<List<Anime>> = flowOf(emptyList())
    override fun getRecentlyUpdated(): Flow<List<Anime>> = flowOf(emptyList())
    override fun getActionAnime(): Flow<List<Anime>> = flowOf(emptyList())
    override fun getRomanceAnime(): Flow<List<Anime>> = flowOf(emptyList())
    override fun getAnimeByGenre(genre: String): Flow<List<Anime>> = flowOf(emptyList())
    override fun searchAnime(query: String, page: Int): Flow<SearchPage> = flowOf(SearchPage(emptyList(), false, 1))
    override fun getAnimeDetail(id: Int): Flow<Anime?> = flowOf(null)
    override suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult = EpisodeLookupResult.NotFound
    override suspend fun getEpisodesBySlug(provider: ProviderId, slug: ProviderSeriesId): EpisodeLookupResult = EpisodeLookupResult.NotFound
    override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult = PlaybackResult.Error(request.provider, PlaybackErrorType.NoSources, "No sources")
    override suspend fun checkUpdates(): AppUpdateInfo? = null
    override suspend fun refreshSchedule(): Pair<List<AiringAnime>, List<Anime>> = Pair(emptyList(), emptyList())
    override suspend fun checkUrlStatus(url: String, headers: Map<String, String>): Int = 200
}
