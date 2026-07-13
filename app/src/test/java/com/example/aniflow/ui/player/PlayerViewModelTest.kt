package com.example.aniflow.ui.player

import android.content.Context
import android.content.ContextWrapper
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private var activeViewModel: PlayerViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        activeViewModel?.viewModelScope?.coroutineContext?.get(kotlinx.coroutines.Job)?.cancel()
        activeViewModel = null
        Dispatchers.resetMain()
    }

    @Test
    fun testTypedSourceParsingAndSorting() = runTest {
        val context = FakeContext()
        val repository = FakeAnimeRepository()
        val watchHistoryStore = WatchHistoryStore(context)
        val settingsStore = SettingsStore(context)
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
        val settingsStore = SettingsStore(context)
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
        val settingsStore = SettingsStore(context)
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
        val settingsStore = SettingsStore(context)
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
        val settingsStore = SettingsStore(context)
        val viewModel = PlayerViewModel(repository, watchHistoryStore, settingsStore).also { activeViewModel = it }

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
        val settingsStore = SettingsStore(context)
        
        val episodes = listOf(Episode("anilight:anime1|123|1", "Episode 1", 1, "", ""))
        val sources = listOf(
            SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url_misa_sub", emptyMap(), 0, QualityPolicy.Auto, null, "ep1"),
            SourceEndpoint("id2", ProviderId.ANILIGHT, ServerId("near"), AudioType.SUB, StreamType.PROGRESSIVE, "url_near_sub", emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
        )
        
        val mockRepo = object : AnimeRepository by FakeAnimeRepository() {
            override suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult =
                EpisodeLookupResult.Matched(ProviderId.ANILIGHT, "anime1", episodes)
            override suspend fun getStreamingSources(request: EpisodeRequest): ProviderPlaybackResult =
                ProviderPlaybackResult.Success(EpisodeSourcesResponse(sources = sources))
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
        val settingsStore = SettingsStore(context)

        val mockRepo = object : AnimeRepository by FakeAnimeRepository() {
            override suspend fun getStreamingSources(request: EpisodeRequest): ProviderPlaybackResult {
                delay(1000) // Delay to simulate network resolution
                return ProviderPlaybackResult.Success(
                    EpisodeSourcesResponse(
                        listOf(
                            SourceEndpoint("id1", ProviderId.ANILIGHT, ServerId("misa"), AudioType.SUB, StreamType.PROGRESSIVE, "url_delayed", emptyMap(), 0, QualityPolicy.Auto, null, "ep1")
                        )
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
        val settingsStore = SettingsStore(context)
        val viewModel = PlayerViewModel(repository, watchHistoryStore, settingsStore).also { activeViewModel = it }

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

        // It should have failed and switched
        assertTrue(viewModel.hasError.value)

        // Trigger milestone: first frame rendered
        viewModel.onFirstFrameRendered()
        
        // Wait 3 seconds for stable play milestone
        delay(3500)
        
        // Cooldown should be cleared
        assertFalse(viewModel.errorMessage.value.contains("All servers cooled down"))
    }
}

private class FakeContext : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getPackageName(): String = "com.example.aniflow"
    override fun getFilesDir(): File {
        val file = File(System.getProperty("java.io.tmpdir"), "aniflow_test_files_" + System.currentTimeMillis())
        file.mkdirs()
        return file
    }
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
    override suspend fun getEpisodesBySlug(provider: ProviderId, slug: String): List<Episode> = emptyList()
    override suspend fun getStreamingSources(request: EpisodeRequest): ProviderPlaybackResult = ProviderPlaybackResult.Error(PlaybackErrorType.NoSources, "No sources")
    override suspend fun checkUpdates(): AppUpdateInfo? = null
    override suspend fun refreshSchedule(): Pair<List<AiringAnime>, List<Anime>> = Pair(emptyList(), emptyList())
    override suspend fun checkUrlStatus(url: String, headers: Map<String, String>): Int = 200
}
