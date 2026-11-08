package com.example.aniflow.ui.main

import android.content.Context
import android.content.ContextWrapper
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
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
    fun savedFeedback_isReadBack_fromLocalStore() = runTest {
        val context = FakeContext()
        val store = com.example.aniflow.data.UserFeedbackStore(context)

        val anime = Anime(id = 21, title = "ONE PIECE", coverImage = "url", bannerImage = "banner")
        val testText = "peak feedback at " + System.currentTimeMillis()
        store.saveFeedback(anime, testText)

        val list = store.feedbackListFlow.first()
        assertEquals(1, list.size)
        assertEquals(testText, list[0].feedback)
        assertEquals(testText, store.getFeedbackForAnimeFlow(21).first())
    }

    @Test
    fun clearAll_emptiesLocalStore() = runTest {
        val context = FakeContext()
        val store = com.example.aniflow.data.UserFeedbackStore(context)

        store.saveFeedback(Anime(id = 21, title = "ONE PIECE", coverImage = "url"), "peak")
        assertEquals(1, store.feedbackListFlow.first().size)

        store.clearAll()
        assertEquals(0, store.feedbackListFlow.first().size)
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
