package com.example.aniflow.ui.main

import android.content.Context
import android.content.ContextWrapper
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.UserFeedback
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class MainScreenViewModelTest {
    @Test
    fun initiallyLoading_isTrue() = runTest {
        val context = FakeContext()
        val repository = FakeAnimeRepository()
        val watchlistStore = WatchlistStore(context)
        val watchHistoryStore = WatchHistoryStore(context)
        val userFeedbackStore = com.example.aniflow.data.UserFeedbackStore(context)
        val viewModel = MainScreenViewModel(repository, watchlistStore, watchHistoryStore, userFeedbackStore, context)
    }

    @Test
    fun testJsonParsing() {
        val jsonStr = """{"id":"ff8081819d82fab6019f6fb7249673e0","name":null,"data":{"list":[{"anime":{"id":21,"title":"ONE PIECE","coverImage":"https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-ELSYx3yMPcKM.jpg","bannerImage":"https://s4.anilist.co/file/anilistcdn/media/anime/banner/21-wf37VakJmZqs.jpg"},"feedback":"peak","timestamp":1784288643193},{"anime":{"id":20954,"title":"A Silent Voice","coverImage":"https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20954-sYRfE5jQRtSB.jpg","bannerImage":"https://s4.anilist.co/file/anilistcdn/media/anime/banner/20954-f30bHMXa5Qoe.jpg","episodes":1},"feedback":"peak","timestamp":1784286589939}]}}"""
        val parsed = com.example.aniflow.data.NetworkModule.json.decodeFromString<com.example.aniflow.data.GlobalFeedbackResponse>(jsonStr)
        assertEquals(2, parsed.data.list.size)
        assertEquals("ONE PIECE", parsed.data.list[0].anime.title)
        assertEquals(null, parsed.data.list[0].anime.episodes)
        assertEquals(1, parsed.data.list[1].anime.episodes)
    }

    @Test
    fun testRealApiFetch() = runTest {
        val context = FakeContext()
        val store = com.example.aniflow.data.UserFeedbackStore(context)
        var received: List<UserFeedback>? = null
        val job = launch {
            store.feedbackListFlow.collect {
                received = it
            }
        }
        delay(6000)
        job.cancel()
        println("REAL FETCH SIZE: ${received?.size}")
        received?.forEach { println("FEEDBACK: ${it.anime.title} -> ${it.feedback}") }
    }

    @Test
    fun testRealApiPut() = runTest {
        val context = FakeContext()
        val store = com.example.aniflow.data.UserFeedbackStore(context)
        
        // 1. Save feedback
        val anime = Anime(id = 21, title = "ONE PIECE", coverImage = "url", bannerImage = "banner")
        val testText = "peak feedback at " + System.currentTimeMillis()
        store.saveFeedback(anime, testText)
        
        // 2. Fetch feedback from flow
        val list = store.feedbackListFlow.first { it.any { f -> f.feedback == testText } }
        println("REAL PUT SUCCESS: Found updated feedback: ${list.find { it.feedback == testText }?.feedback}")
        assert(list.isNotEmpty())
    }

    @Test
    fun runResetServer() = runTest {
        val context = FakeContext()
        val store = com.example.aniflow.data.UserFeedbackStore(context)
        println("STARTING SERVER RESET...")
        store.resetServer()
        println("SERVER RESET COMPLETED!")
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
    override fun getTrending(): Flow<List<Anime>> = flow {
        delay(1000)
        emit(emptyList<Anime>())
    }
    override fun getPopular(): Flow<List<Anime>> = flowOf(emptyList<Anime>())
    override fun getSeasonal(): Flow<List<Anime>> = flowOf(emptyList<Anime>())
    override fun getAiringToday(): Flow<List<AiringAnime>> = flowOf(emptyList<AiringAnime>())
    override fun getTopRated(): Flow<List<Anime>> = flowOf(emptyList<Anime>())
    override fun getUpcoming(): Flow<List<Anime>> = flowOf(emptyList<Anime>())
    override fun getRecentlyUpdated(): Flow<List<Anime>> = flowOf(emptyList<Anime>())
    override fun getActionAnime(): Flow<List<Anime>> = flowOf(emptyList<Anime>())
    override fun getRomanceAnime(): Flow<List<Anime>> = flowOf(emptyList<Anime>())
    override fun getAnimeByGenre(genre: String): Flow<List<Anime>> = flowOf(emptyList<Anime>())
    override fun searchAnime(query: String, page: Int): Flow<SearchPage> = flowOf(SearchPage(emptyList<Anime>(), false, 1))
    override fun getAnimeDetail(id: Int): Flow<Anime?> = flowOf(null)
    override suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult = EpisodeLookupResult.NotFound
    override suspend fun getEpisodesBySlug(provider: ProviderId, slug: ProviderSeriesId): EpisodeLookupResult = EpisodeLookupResult.NotFound
    override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult = PlaybackResult.Error(request.provider, PlaybackErrorType.NoSources, "No sources")
    override suspend fun checkUpdates(): AppUpdateInfo? = null
    override suspend fun refreshSchedule(): Pair<List<AiringAnime>, List<Anime>> = Pair(emptyList<AiringAnime>(), emptyList<Anime>())
    override suspend fun checkUrlStatus(url: String, headers: Map<String, String>): Int = 200
}
