package com.example.aniflow.data.repository

import android.content.Context
import com.example.aniflow.data.*
import com.example.aniflow.data.model.*
import com.example.aniflow.data.remote.AniListApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.client.request.get
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText

class DefaultAnimeRepository(private val context: Context) : AnimeRepository {
    private val client = NetworkModule.client
    private val aniListApi = AniListApi(client)
    private val aniLightProvider = AniLightProvider(client)
    private val settingsStore = SettingsStore(context)

    private val scheduleMutex = Mutex()
    private var cachedSchedule: List<com.example.aniflow.data.AniLightScheduleEntry>? = null
    private var lastScheduleFetchTime: Long = 0L
    private val SCHEDULE_CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    private var cachedTrending: List<Anime>? = null
    private var lastTrendingFetchTime: Long = 0L

    private var cachedPopular: List<Anime>? = null
    private var lastPopularFetchTime: Long = 0L

    private var cachedSeasonal: List<Anime>? = null
    private var lastSeasonalFetchTime: Long = 0L

    private var cachedTopRated: List<Anime>? = null
    private var lastTopRatedFetchTime: Long = 0L

    private var cachedUpcoming: List<Anime>? = null
    private var lastUpcomingFetchTime: Long = 0L

    private var cachedAction: List<Anime>? = null
    private var lastActionFetchTime: Long = 0L

    private var cachedRomance: List<Anime>? = null
    private var lastRomanceFetchTime: Long = 0L

    private val HOME_CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutes

    private suspend fun getCachedSchedule(): List<com.example.aniflow.data.AniLightScheduleEntry> = scheduleMutex.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedSchedule
        if (cached != null && now - lastScheduleFetchTime < SCHEDULE_CACHE_DURATION_MS) {
            return@withLock cached
        }
        val fresh = try {
            aniLightProvider.getSchedule()
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting schedule in getCachedSchedule", e)
            emptyList()
        }
        if (fresh.isNotEmpty()) {
            cachedSchedule = fresh
            lastScheduleFetchTime = now
        }
        return@withLock fresh.ifEmpty { cached ?: emptyList() }
    }


    override fun getTrending(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedTrending
        if (cached != null && cached.size > 3 && now - lastTrendingFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        try {
            val list = aniListApi.getTrending()
            if (list.isNotEmpty()) {
                cachedTrending = list
                lastTrendingFetchTime = now
                emit(list)
            } else {
                emit(cached ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting trending", e)
            emit(cached ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getPopular(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedPopular
        if (cached != null && cached.size > 3 && now - lastPopularFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        try {
            val list = aniListApi.getPopular()
            if (list.isNotEmpty()) {
                cachedPopular = list
                lastPopularFetchTime = now
                emit(list)
            } else {
                emit(cached ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting popular", e)
            emit(cached ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getSeasonal(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedSeasonal
        if (cached != null && cached.size > 3 && now - lastSeasonalFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        try {
            val list = aniListApi.getSeasonal()
            if (list.isNotEmpty()) {
                cachedSeasonal = list
                lastSeasonalFetchTime = now
                emit(list)
            } else {
                emit(cached ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting seasonal", e)
            emit(cached ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getAiringToday(): Flow<List<AiringAnime>> = flow {
        val data = refreshSchedule()
        emit(data.first.ifEmpty { getFallbackAiringList() })
    }.flowOn(Dispatchers.IO)

    override fun getTopRated(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedTopRated
        if (cached != null && cached.size > 3 && now - lastTopRatedFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        try {
            val list = aniListApi.getTopRated()
            if (list.isNotEmpty()) {
                cachedTopRated = list
                lastTopRatedFetchTime = now
                emit(list)
            } else {
                emit(cached ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting top rated", e)
            emit(cached ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getUpcoming(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedUpcoming
        if (cached != null && cached.size > 3 && now - lastUpcomingFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        try {
            val list = aniListApi.getUpcoming()
            if (list.isNotEmpty()) {
                cachedUpcoming = list
                lastUpcomingFetchTime = now
                emit(list)
            } else {
                emit(cached ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting upcoming", e)
            emit(cached ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getRecentlyUpdated(): Flow<List<Anime>> = flow {
        val data = refreshSchedule()
        emit(data.second.ifEmpty { getFallbackAnimeList() })
    }.flowOn(Dispatchers.IO)

    override fun getActionAnime(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedAction
        if (cached != null && cached.size > 3 && now - lastActionFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        try {
            val list = aniListApi.getAnimeByGenre("Action")
            if (list.isNotEmpty()) {
                cachedAction = list
                lastActionFetchTime = now
                emit(list)
            } else {
                emit(cached ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting action anime", e)
            emit(cached ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getRomanceAnime(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedRomance
        if (cached != null && cached.size > 3 && now - lastRomanceFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        try {
            val list = aniListApi.getAnimeByGenre("Romance")
            if (list.isNotEmpty()) {
                cachedRomance = list
                lastRomanceFetchTime = now
                emit(list)
            } else {
                emit(cached ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting romance anime", e)
            emit(cached ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getAnimeByGenre(genre: String): Flow<List<Anime>> = flow {
        val list = aniListApi.getAnimeByGenre(genre)
        emit(list.ifEmpty { getFallbackAnimeList() })
    }.flowOn(Dispatchers.IO)

    override fun searchAnime(query: String, page: Int): Flow<SearchPage> = flow {
        val searchPage = aniListApi.searchAnime(query, page)
        val list = if (searchPage.results.isEmpty() && page == 1) {
            getFallbackAnimeList()
        } else {
            searchPage.results
        }
        emit(searchPage.copy(results = list))
    }.flowOn(Dispatchers.IO)

    override fun getAnimeDetail(id: Int): Flow<Anime?> = flow {
        val detail = aniListApi.getAnimeDetail(id)
        emit(detail ?: getFallbackAnimeList().find { it.id == id } ?: getFallbackAnimeList().first())
    }.flowOn(Dispatchers.IO)

    private fun cleanTitleForSearch(title: String): String {
        return title
            .replace(Regex("(?i):.*"), "")
            .replace(Regex("(?i)Season\\s*\\d+"), "")
            .replace(Regex("(?i)Part\\s*\\d+"), "")
            .replace(Regex("(?i)TV"), "")
            .replace(Regex("(?i)Uncensored"), "")
            .replace(Regex("(?i)Specials?"), "")
            .trim()
    }

    override suspend fun getEpisodes(animeId: Int, title: String): List<Episode> {
        val episodesList = mutableListOf<Episode>()

        try {
            // Fetch the details from AniList first to get all title versions (Romaji & English)
            val detail = try { aniListApi.getAnimeDetail(animeId) } catch (e: Exception) { null }
            val titlesToTry = mutableListOf<String>()
            if (detail != null) {
                // Try English first since it is often matched on AniLight, then Romaji (detail.title is romaji or english)
                detail.englishTitle?.let { titlesToTry.add(it) }
                if (detail.title != detail.englishTitle) {
                    titlesToTry.add(detail.title)
                }
            } else {
                titlesToTry.add(title)
            }

            val searchTitles = mutableListOf<String>()
            for (t in titlesToTry) {
                if (!searchTitles.contains(t) && t.isNotEmpty()) {
                    searchTitles.add(t)
                }
                val cleaned = cleanTitleForSearch(t)
                if (cleaned.isNotEmpty() && !searchTitles.contains(cleaned)) {
                    searchTitles.add(cleaned)
                }
            }

            var searchResults = emptyList<ProviderSearchResult>()
            var bestMatch: ProviderSearchResult? = null

            for (searchTitle in searchTitles) {
                val results = aniLightProvider.search(searchTitle)
                if (results.isNotEmpty()) {
                    val idMatch = results.find { it.anilistId == animeId }
                    if (idMatch != null) {
                        bestMatch = idMatch
                        break
                    }
                    if (searchResults.isEmpty()) {
                        searchResults = results
                    }
                }
            }

            if (bestMatch == null && searchResults.isNotEmpty()) {
                bestMatch = searchResults.find { res ->
                    titlesToTry.any { toTry -> res.title.equals(toTry, ignoreCase = true) }
                } ?: searchResults.firstOrNull()
            }

            if (bestMatch != null) {
                val list = aniLightProvider.getEpisodeList(bestMatch.slug)
                episodesList.addAll(list)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return episodesList
    }

    override suspend fun getStreamingSources(episodeId: String): EpisodeSourcesResponse {
        var response: EpisodeSourcesResponse? = null
        if (episodeId.startsWith("anilight:")) {
            try {
                val sources = aniLightProvider.getStreamUrl(episodeId)
                val filteredSources = sources.sources.filter { !AdBlocker.shouldBlock(it.url) }
                if (filteredSources.isNotEmpty()) {
                    val preferredLang = try { settingsStore.languagePreference.first() } catch (e: Exception) { "sub" }
                    val preferredQuality = try { settingsStore.qualityPreference.first() } catch (e: Exception) { "auto" }

                    val sortedSources = filteredSources.sortedWith(compareByDescending<StreamingSource> { src ->
                        val isPreferredLang = if (preferredLang.lowercase() == "dub") {
                            src.quality.contains("(DUB)", ignoreCase = true)
                        } else {
                            src.quality.contains("(SUB)", ignoreCase = true)
                        }
                        if (isPreferredLang) 2 else 0
                    }.thenByDescending { src ->
                        if (preferredQuality != "auto") {
                            if (src.quality.contains(preferredQuality, ignoreCase = true)) 1 else 0
                        } else {
                            if (src.quality.contains("1080p", ignoreCase = true)) 4
                            else if (src.quality.contains("720p", ignoreCase = true)) 3
                            else if (src.quality.contains("480p", ignoreCase = true)) 2
                            else if (src.quality.contains("360p", ignoreCase = true)) 1
                            else 0
                        }
                    })
                    response = sources.copy(sources = sortedSources)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (response != null) {
            return response
        }

        return EpisodeSourcesResponse(
            sources = listOf(
                StreamingSource(
                    url = "https://www.w3schools.com/html/mov_bbb.mp4",
                    quality = "1080p (Fallback)",
                    isM3U8 = false
                )
            ),
            subtitles = listOf(
                SubtitleTrack(
                    url = "https://raw.githubusercontent.com/run-to-git/vtt-subtitles/main/english.vtt",
                    lang = "en",
                    label = "English"
                )
            )
        )
    }

    private fun getFallbackAnimeList(): List<Anime> {
        return listOf(
            Anime(
                id = 1535,
                title = "Death Note",
                coverImage = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/nx1535-7X1VDQ5fa9bc.jpg",
                bannerImage = "https://picsum.photos/1920/1080?random=1",
                description = "A high school student discovers a supernatural notebook that grants him the ability to kill anyone whose name and face he knows.",
                episodes = 37,
                averageScore = 86,
                genres = listOf("Action", "Mystery", "Psychological", "Supernatural", "Thriller"),
                studioName = "Madhouse"
            ),
            Anime(
                id = 21,
                title = "One Piece",
                coverImage = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-46G9t3k0W68D.png",
                bannerImage = "https://picsum.photos/1920/1080?random=2",
                description = "Monkey D. Luffy refuses to let anyone or anything stand in the way of his quest to become the king of all pirates.",
                episodes = 1100,
                averageScore = 88,
                genres = listOf("Action", "Adventure", "Comedy", "Fantasy"),
                studioName = "Toei Animation"
            ),
            Anime(
                id = 16498,
                title = "Attack on Titan",
                coverImage = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16498-m5pewa1otmNj.png",
                bannerImage = "https://picsum.photos/1920/1080?random=3",
                description = "Humans fight for survival against giant man-eating humanoids called Titans behind massive walls.",
                episodes = 75,
                averageScore = 90,
                genres = listOf("Action", "Drama", "Fantasy", "Mystery"),
                studioName = "MAPPA"
            )
        )
    }

    private fun getFallbackAiringList(): List<AiringAnime> {
        val now = System.currentTimeMillis() / 1000
        return listOf(
            AiringAnime(
                mediaId = 21,
                title = "One Piece",
                coverImageUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-46G9t3k0W68D.png",
                airingAt = now + 7200,
                episode = 1112
            )
        )
    }

    override suspend fun checkUpdates(): AppUpdateInfo? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val response = client.get(com.example.aniflow.utils.UpdateConfig.UPDATE_JSON_URL + "?t=${System.currentTimeMillis()}")
            val jsonText = response.bodyAsText()
            android.util.Log.d("DefaultAnimeRepository", "checkUpdates raw JSON: $jsonText")
            val json = org.json.JSONObject(jsonText)
            AppUpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                updateUrl = json.getString("updateUrl"),
                updateNotes = if (json.isNull("updateNotes")) null else json.getString("updateNotes"),
                forceUpdate = json.optBoolean("forceUpdate", false),
                silentUpdate = json.optBoolean("silentUpdate", false)
            )
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Failed to check update", e)
            null
        }
    }

    override suspend fun refreshSchedule(): Pair<List<AiringAnime>, List<Anime>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val schedule = getCachedSchedule()
        val now = System.currentTimeMillis() / 1000

        // Airing Today: upcoming today in user's local timezone
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val localDayStart = calendar.timeInMillis / 1000
        val localDayEnd = localDayStart + 86400

        val airingTodayList = schedule.filter { entry ->
            entry.airingAt > now && entry.airingAt <= localDayEnd
        }.map { entry ->
            AiringAnime(
                mediaId = entry.anime.anilistId ?: entry.anime.id,
                title = entry.anime.title.english ?: entry.anime.title.romaji ?: entry.anime.title.native ?: "Airing Anime",
                coverImageUrl = entry.anime.coverImage?.large ?: entry.anime.coverImage?.extraLarge ?: "",
                airingAt = entry.airingAt,
                episode = entry.episode
            )
        }

        // Recently Released: already aired/released
        val recentlyUpdatedList = schedule.filter { entry ->
            entry.airingAt <= now
        }.sortedByDescending { entry ->
            entry.airingAt
        }.map { entry ->
            Anime(
                id = entry.anime.anilistId ?: entry.anime.id,
                title = entry.anime.title.english ?: entry.anime.title.romaji ?: entry.anime.title.native ?: "Anime Title",
                englishTitle = entry.anime.title.english,
                coverImage = entry.anime.coverImage?.large ?: entry.anime.coverImage?.extraLarge ?: "",
                episodes = entry.episode,
                status = entry.anime.status ?: "RELEASING",
                genres = entry.anime.genres,
                averageScore = entry.anime.averageScore
            )
        }

        Pair(airingTodayList, recentlyUpdatedList)
    }
}
