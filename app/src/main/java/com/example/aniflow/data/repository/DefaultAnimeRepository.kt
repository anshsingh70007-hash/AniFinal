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
import io.ktor.client.request.request
import io.ktor.client.request.header

class DefaultAnimeRepository(private val context: Context) : AnimeRepository {
    private val client = NetworkModule.client
    private val aniListApi = AniListApi(client)

    override suspend fun getFranchise(id: Int): List<FranchiseEntry> = try {
        aniListApi.getFranchise(id)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("AnimeRepository", "franchise $id failed: ${e.message}")
        emptyList()
    }

    override suspend fun getLatestAired(ids: List<Int>): Map<Int, Int> = try {
        aniListApi.getLatestAired(ids)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("AnimeRepository", "latest aired failed: ${e.message}")
        emptyMap()
    }

    override suspend fun getTrailers(ids: List<Int>): Map<Int, String> = try {
        aniListApi.getTrailers(ids)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("AnimeRepository", "trailers failed: ${e.message}")
        emptyMap()
    }
    private val backupAnimeApi = com.example.aniflow.data.remote.BackupAnimeApi(client)
    private val feedDiskCache = FeedDiskCache(context)
    private val aniLightProvider = AniLightProvider(client)
    private val settingsStore = SettingsStore(context)

    private val scheduleMutex = Mutex()
    private val knownAnimeCache = java.util.concurrent.ConcurrentHashMap<Int, Anime>()
    private var cachedSchedule: List<com.example.aniflow.data.AniLightScheduleEntry>? = null
    private var lastScheduleFetchTime: Long = 0L
    private val SCHEDULE_CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    private fun cacheAnimeList(list: List<Anime>) {
        for (anime in list) {
            knownAnimeCache[anime.id] = anime
        }
    }

    private fun isAnimeTitleMatching(t1: String, t2: String): Boolean {
        val c1 = t1.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()
        val c2 = t2.lowercase().replace(Regex("[^a-z0-9]"), " ").trim()
        if (c1.isEmpty() || c2.isEmpty()) return false
        if (c1 == c2 || c1.contains(c2) || c2.contains(c1)) return true
        val w1 = c1.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        val w2 = c2.split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        if (w1.isEmpty() || w2.isEmpty()) return false
        return w1.intersect(w2).isNotEmpty()
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<List<Anime>>.emitAndCache(list: List<Anime>) {
        cacheAnimeList(list)
        emit(list)
    }

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
            emitAndCache(cached)
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.trending.isNotEmpty() && cached == null) {
            emitAndCache(diskFeed.trending)
        }
        try {
            // First try AniLight official /homepage trending to match anilight.live exactly
            var list = try { backupAnimeApi.getAniLightHomepageTrending() } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) {
                list = try { aniListApi.getTrending() } catch (e: Exception) { emptyList() }
            }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniLight & AniList trending unavailable, trying backup API...")
                list = backupAnimeApi.getTrending()
            }
            if (list.isNotEmpty()) {
                cachedTrending = list
                lastTrendingFetchTime = now
                feedDiskCache.updateSection(trending = list)
                emitAndCache(list)
            } else {
                emitAndCache(cached ?: diskFeed?.trending?.takeIf { it.isNotEmpty() } ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting trending", e)
            emitAndCache(cached ?: diskFeed?.trending?.takeIf { it.isNotEmpty() } ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getPopular(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedPopular
        if (cached != null && cached.size > 3 && now - lastPopularFetchTime < HOME_CACHE_DURATION_MS) {
            emitAndCache(backupAnimeApi.deduplicateFranchises(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.popular.isNotEmpty() && cached == null) {
            emitAndCache(backupAnimeApi.deduplicateFranchises(diskFeed.popular))
        }
        try {
            var list = aniLightSection("popular").ifEmpty { try { aniListApi.getPopular() } catch (e: Exception) { emptyList() } }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList popular unavailable, trying backup API...")
                list = backupAnimeApi.getPopular()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedPopular = deduped
                lastPopularFetchTime = now
                feedDiskCache.updateSection(popular = deduped)
                emitAndCache(deduped)
            } else {
                emitAndCache(cached ?: diskFeed?.popular?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackPopularList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting popular", e)
            emitAndCache(cached ?: diskFeed?.popular?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackPopularList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getSeasonal(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedSeasonal
        if (cached != null && cached.size > 3 && now - lastSeasonalFetchTime < HOME_CACHE_DURATION_MS) {
            emitAndCache(backupAnimeApi.deduplicateFranchises(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.seasonal.isNotEmpty() && cached == null) {
            emitAndCache(backupAnimeApi.deduplicateFranchises(diskFeed.seasonal))
        }
        try {
            var list = aniLightSection("seasonal").ifEmpty { try { aniListApi.getSeasonal() } catch (e: Exception) { emptyList() } }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList seasonal unavailable, trying backup API...")
                list = backupAnimeApi.getSeasonal()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedSeasonal = deduped
                lastSeasonalFetchTime = now
                feedDiskCache.updateSection(seasonal = deduped)
                emitAndCache(deduped)
            } else {
                emitAndCache(cached ?: diskFeed?.seasonal?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackSeasonalList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting seasonal", e)
            emitAndCache(cached ?: diskFeed?.seasonal?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackSeasonalList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getAiringToday(): Flow<List<AiringAnime>> = flow {
        // The disk copy can be days old: keep only entries from the last 24 h onwards, or a
        // week-old row would show "Airing Now" forever.
        val cutoff = System.currentTimeMillis() / 1000 - 24 * 3600
        val stale = feedDiskCache.loadCache()?.airingToday.orEmpty().filter { it.airingAt >= cutoff }
        if (stale.isNotEmpty()) emit(stale)
        val fresh = refreshSchedule().first
        emit(fresh.ifEmpty { stale })
    }.flowOn(Dispatchers.IO)

    override fun getTopRated(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedTopRated
        if (cached != null && cached.size > 3 && now - lastTopRatedFetchTime < HOME_CACHE_DURATION_MS) {
            emitAndCache(backupAnimeApi.deduplicateFranchises(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.topRated.isNotEmpty() && cached == null) {
            emitAndCache(backupAnimeApi.deduplicateFranchises(diskFeed.topRated))
        }
        try {
            var list = aniLightSection("topRated").ifEmpty { try { aniListApi.getTopRated() } catch (e: Exception) { emptyList() } }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList top rated unavailable, trying backup API...")
                list = backupAnimeApi.getTopRated()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedTopRated = deduped
                lastTopRatedFetchTime = now
                feedDiskCache.updateSection(topRated = deduped)
                emitAndCache(deduped)
            } else {
                emitAndCache(cached ?: diskFeed?.topRated?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackTopRatedList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting top rated", e)
            emitAndCache(cached ?: diskFeed?.topRated?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackTopRatedList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getUpcoming(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedUpcoming
        if (cached != null && cached.size > 3 && now - lastUpcomingFetchTime < HOME_CACHE_DURATION_MS) {
            emitAndCache(cached)
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.upcoming.isNotEmpty() && cached == null) {
            val safeDisk = backupAnimeApi.deduplicateFranchises(diskFeed.upcoming).filter {
                it.status.contains("UPCOMING", ignoreCase = true) || it.status.contains("NOT_YET", ignoreCase = true)
            }
            if (safeDisk.isNotEmpty()) {
                emitAndCache(safeDisk)
            }
        }
        try {
            var list = aniLightSection("upcoming").ifEmpty { try { aniListApi.getUpcoming() } catch (e: Exception) { emptyList() } }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList upcoming unavailable, trying backup API...")
                list = backupAnimeApi.getUpcoming()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedUpcoming = deduped
                lastUpcomingFetchTime = now
                feedDiskCache.updateSection(upcoming = deduped)
                emitAndCache(deduped)
            } else {
                emitAndCache(cached ?: diskFeed?.upcoming?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackUpcomingList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting upcoming", e)
            emitAndCache(cached ?: diskFeed?.upcoming?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackUpcomingList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getRecentlyUpdated(): Flow<List<Anime>> = flow {
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.recentlyUpdated.isNotEmpty()) {
            emitAndCache(diskFeed.recentlyUpdated)
        }
        // anilight.live's own "recently added episodes" first (it is what the site shows), then the
        // schedule's aired episodes it doesn't have. Seasonal titles are not "recently updated":
        // with neither source, fall back to the last real list or nothing.
        val site = aniLightSection("recentlyAddedEpisodes")
        val data = refreshSchedule()
        val merged = (site + data.second).distinctBy { it.id }.take(24)
        if (site.isNotEmpty()) {
            cacheAnimeList(site)
            feedDiskCache.updateSection(recentlyUpdated = merged)
        }
        emitAndCache(merged.ifEmpty { diskFeed?.recentlyUpdated.orEmpty() })
    }.flowOn(Dispatchers.IO)

    override fun getHomeShelves(): Flow<List<HomeShelf>> = flow {
        val home = try { backupAnimeApi.getAniLightHome() } catch (e: Exception) { null }
        val shelves = mutableListOf<HomeShelf>()
        val (bestYear, best) = try { aniListApi.getBestOfYear() } catch (e: Exception) { 0 to emptyList() }
        if (best.size >= 4) {
            cacheAnimeList(best)
            shelves += HomeShelf("bestYear", "Best of $bestYear", best)
        }
        home?.sections?.get("popularMovies")?.takeIf { it.size >= 4 }?.let {
            shelves += HomeShelf("movies", "Blockbuster Movies", it)
        }
        home?.sections?.get("previousSeason")?.takeIf { it.size >= 4 }?.let { list ->
            // Name the season from the titles themselves (most common season/year), so the label
            // is always the one the site means.
            val tag = list.mapNotNull { a -> a.season?.let { se -> a.seasonYear?.let { y -> se to y } } }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val title = tag?.let { (se, y) -> "Best of ${se.lowercase().replaceFirstChar { it.uppercase() }} $y" } ?: "Last Season's Hits"
            shelves += HomeShelf("lastSeason", title, list)
        }
        home?.genreSpotlights?.forEach { (genre, list) ->
            if (list.size >= 4) shelves += HomeShelf("spot_$genre", "Spotlight: $genre", list, late = true)
        }
        home?.sections?.values?.forEach { cacheAnimeList(it) }
        emit(shelves)
    }.flowOn(Dispatchers.IO)

    override fun getWeekSchedule(): Flow<List<AiringAnime>> = flow {
        val week = getCachedSchedule()
            .filter { it.anime.anilistId != null && !it.isDelayedSlot }
            .mapNotNull { e ->
                val title = e.anime.title.english?.takeIf { it.isNotBlank() }
                    ?: e.anime.title.romaji?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val cover = e.anime.coverImage?.large?.takeIf { it.isNotBlank() }
                    ?: e.anime.coverImage?.extraLarge?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                AiringAnime(mediaId = e.anime.anilistId!!, title = title, coverImageUrl = cover, airingAt = e.airingAt, episode = e.episode)
            }
            // Sub and dub of one episode are separate entries.
            .distinctBy { it.mediaId to it.episode }
            .sortedBy { it.airingAt }
        if (week.isNotEmpty()) emit(week)
        // anilight.live only publishes Monday-Sunday of the current week, so on a Sunday the
        // days ahead are blank. AniList fills the next six days (cached for half an hour).
        val now = System.currentTimeMillis()
        val ahead = cachedAhead?.takeIf { now - aheadFetchedAt < AHEAD_TTL_MS } ?: run {
            val fromSec = now / 1000 - 86_400
            runCatching { aniListApi.getAiringRange(fromSec, now / 1000 + 7 * 86_400) }
                .getOrDefault(emptyList())
                .also { if (it.isNotEmpty()) { cachedAhead = it; aheadFetchedAt = now } }
        }
        val merged = (week + ahead).filter { it.mediaId > 0 }.distinctBy { it.mediaId to it.episode }.sortedBy { it.airingAt }
        if (merged.size > week.size || week.isEmpty()) emit(merged)
    }.flowOn(Dispatchers.IO)

    @Volatile private var cachedAhead: List<AiringAnime>? = null
    @Volatile private var aheadFetchedAt = 0L

    /** One shelf of anilight.live's home page, or empty when the site is unreachable. */
    private suspend fun aniLightSection(key: String): List<Anime> =
        try { backupAnimeApi.getAniLightHome()?.sections?.get(key).orEmpty() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { emptyList() }

    override fun getActionAnime(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedAction
        if (cached != null && cached.size > 3 && now - lastActionFetchTime < HOME_CACHE_DURATION_MS) {
            emitAndCache(cached)
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.actionAnime.isNotEmpty() && cached == null) {
            emitAndCache(diskFeed.actionAnime)
        }
        try {
            var list = try { aniListApi.getAnimeByGenre("Action") } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList action genre unavailable, trying backup API...")
                list = backupAnimeApi.getActionAnime()
            }
            if (list.isNotEmpty()) {
                cachedAction = list
                lastActionFetchTime = now
                feedDiskCache.updateSection(actionAnime = list)
                emitAndCache(list)
            } else {
                emitAndCache(cached ?: diskFeed?.actionAnime?.takeIf { it.isNotEmpty() } ?: getFallbackActionList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting action anime", e)
            emitAndCache(cached ?: diskFeed?.actionAnime?.takeIf { it.isNotEmpty() } ?: getFallbackActionList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getRomanceAnime(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedRomance
        if (cached != null && cached.size > 3 && now - lastRomanceFetchTime < HOME_CACHE_DURATION_MS) {
            emitAndCache(backupAnimeApi.deduplicateFranchises(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.romanceAnime.isNotEmpty() && cached == null) {
            emitAndCache(backupAnimeApi.deduplicateFranchises(diskFeed.romanceAnime))
        }
        try {
            var list = try { aniListApi.getAnimeByGenre("Romance") } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList romance genre unavailable, trying backup API...")
                list = backupAnimeApi.getRomanceAnime()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedRomance = deduped
                lastRomanceFetchTime = now
                feedDiskCache.updateSection(romanceAnime = deduped)
                emitAndCache(deduped)
            } else {
                emitAndCache(cached ?: diskFeed?.romanceAnime?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackRomanceList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting romance anime", e)
            emitAndCache(cached ?: diskFeed?.romanceAnime?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackRomanceList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getAnimeByGenre(genre: String): Flow<List<Anime>> = flow {
        var list = try { aniListApi.getAnimeByGenre(genre) } catch (e: Exception) { emptyList() }
        if (list.isEmpty()) {
            if (genre.equals("Action", ignoreCase = true)) {
                list = backupAnimeApi.getActionAnime()
            } else if (genre.equals("Romance", ignoreCase = true)) {
                list = backupAnimeApi.getRomanceAnime()
            }
        }
        val fallback = if (genre.equals("Romance", ignoreCase = true)) getFallbackRomanceList() else getFallbackAnimeList()
        emitAndCache(list.ifEmpty { fallback })
    }.flowOn(Dispatchers.IO)

    override fun searchAnime(query: String, page: Int): Flow<SearchPage> = flow {
        val optimizedQuery = optimizeQuery(query)
        var searchPage = try { aniListApi.searchAnime(optimizedQuery, page) } catch (e: Exception) { SearchPage(emptyList(), false, page) }
        
        if (searchPage.results.isEmpty() && page == 1) {
            val correctedQuery = findSpellingCorrection(optimizedQuery)
            if (correctedQuery != null && correctedQuery != optimizedQuery) {
                val fallbackPage = try { aniListApi.searchAnime(correctedQuery, page) } catch (e: Exception) { SearchPage(emptyList(), false, page) }
                if (fallbackPage.results.isNotEmpty()) {
                    searchPage = fallbackPage
                }
            }
        }

        // Failover to backup API if AniList returns empty or is down
        if (searchPage.results.isEmpty()) {
            android.util.Log.i("DefaultAnimeRepository", "AniList search empty, trying backup API for: $optimizedQuery")
            val backupPage = try { backupAnimeApi.searchAnime(optimizedQuery, page) } catch (e: Exception) { null }
            if (backupPage != null && backupPage.results.isNotEmpty()) {
                searchPage = backupPage
            }
        }

        // Direct AniLight stream catalog search fallback (guarantees finding anything streamable)
        if (searchPage.results.isEmpty() && page == 1) {
            try {
                val directResults = aniLightProvider.search(optimizedQuery)
                if (directResults.isNotEmpty()) {
                    val directAnime = directResults.map { item ->
                        val itemAnilistId = item.anilistId ?: kotlin.math.abs(item.slug.hashCode())
                        providerMappingStore.setMapping(
                            ProviderMapping(
                                provider = ProviderId.ANILIGHT,
                                anilistId = itemAnilistId,
                                slug = item.slug,
                                evidence = MappingEvidence(item.title, optimizedQuery, "DIRECT_SEARCH", 1.0)
                            )
                        )
                        Anime(
                            id = itemAnilistId,
                            title = item.title,
                            englishTitle = item.title,
                            coverImage = item.posterUrl,
                            description = "Watch ${item.title} on AniFlow",
                            status = "RELEASING"
                        )
                    }
                    searchPage = SearchPage(results = directAnime, hasNextPage = false, currentPage = 1)
                }
            } catch (e: Exception) {
                android.util.Log.e("DefaultAnimeRepository", "AniLight fallback search failed", e)
            }
        }
        
        val list = if (searchPage.results.isEmpty() && page == 1) {
            getFallbackAnimeList().filter { 
                it.title.contains(optimizedQuery, ignoreCase = true) ||
                it.englishTitle?.contains(optimizedQuery, ignoreCase = true) == true ||
                it.genres.any { g -> g.contains(optimizedQuery, ignoreCase = true) }
            }
        } else {
            searchPage.results
        }
        cacheAnimeList(list)
        emit(searchPage.copy(results = list))
    }.flowOn(Dispatchers.IO)

    override fun getAnimeDetail(id: Int): Flow<Anime?> = flow {
        // First query authoritative AniList API by id
        var detail = try { aniListApi.getAnimeDetail(id) } catch (e: Exception) { null }
        if (detail != null) {
            detailDiskCache.save(detail)
        } else {
            // AniList down / rate-limited: the last good copy of this exact title beats any
            // fuzzy re-search. With the AniList breaker open this path is instant.
            detail = detailDiskCache.load(id)
        }

        if (detail == null) {
            if (id == 185874) {
                detail = backupAnimeApi.bleachTybwAnime
            }
            val known = knownAnimeCache[id] ?: feedDiskCache.findAnimeById(id) ?: getFallbackAnimeList().find { it.id == id }
            if (known != null) {
                // If known anime has a title, attempt to resolve via AniList search
                val searchQuery = known.englishTitle?.takeIf { it.isNotBlank() } ?: known.title
                val searched = try {
                    aniListApi.searchAnime(searchQuery, 1).results.firstOrNull { 
                        isAnimeTitleMatching(it.title, searchQuery) || isAnimeTitleMatching(it.englishTitle ?: "", searchQuery)
                    }
                } catch (e: Exception) { null }
                if (searched != null) {
                    detail = try { aniListApi.getAnimeDetail(searched.id) } catch (e: Exception) { null }
                }
                if (detail == null) {
                    detail = known
                }
            }
        }
        
        if (detail == null) {
            detail = try { backupAnimeApi.getAnimeDetail(id) } catch (e: Exception) { null }
        }
        detail?.let { knownAnimeCache[it.id] = it }
        emit(detail)
    }.flowOn(Dispatchers.IO)

    private val detailDiskCache = DetailDiskCache(context)
    private val listingCorrectionManager = ListingCorrectionManager(context, client)
    private val providerMappingStore = ProviderMappingStore(context)
    private val providers: Map<ProviderId, EpisodeProvider> = mapOf(
        ProviderId.ANILIGHT to aniLightProvider,
        ProviderId.ANIKOTO to AnikotoProvider(client),
        ProviderId.MIRURO to MiruroProvider(client)
    )

    private companion object FastPaths {
        const val AHEAD_TTL_MS = 30 * 60 * 1000L
        /** AniList id -> verified AniLight slug. */
        val FAST_PATH_SLUGS = mapOf(
            185874 to "bleach-sennen-kessen-hen-kashin-tan-kyzw",      // TYBW Part 4: The Calamity
            169755 to "bleach-sennen-kessen-hen-soukoku-tan-o672",     // TYBW Part 3: The Conflict
            159322 to "bleach-sennen-kessen-hen-ketsubetsu-tan-p8pl",  // TYBW Part 2: The Separation
            116674 to "bleach-sennen-kessen-hen",                      // TYBW Part 1
            269 to "bleach-sfvn",                                      // Bleach (2004)
            1686 to "bleach-memories-of-nobody-7zas",                  // Bleach Movie 1
        )
    }

    override suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult {
        try {
            val providerId = ProviderId.ANILIGHT
            val provider = providers[providerId] ?: return EpisodeLookupResult.Error("Provider not found")
            
            // 0. Check dynamic remote corrections first (allows instant zero-prompt fixes for broken episodes or missing anime)
            val remoteCorrection = listingCorrectionManager.getCorrection(identity.anilistId)
                ?: listingCorrectionManager.getCorrectionByTitle(identity.title)
            if (remoteCorrection != null) {
                val correctionProviderId = if (remoteCorrection.provider.equals("ANIKOTO", true)) ProviderId.ANIKOTO
                    else if (remoteCorrection.provider.equals("MIRURO", true)) ProviderId.MIRURO
                    else ProviderId.ANILIGHT
                val targetProvider = providers[correctionProviderId] ?: provider
                val epResult = targetProvider.getEpisodes(ProviderSeriesId(remoteCorrection.slug))
                if (epResult is EpisodeLookupResult.Matched && epResult.episodes.isNotEmpty()) {
                    return epResult
                }
            }

            val stored = providerMappingStore.getMapping(providerId, identity.anilistId)
            val userPinned = stored?.evidence?.matchedBy == "USER_CONFIRMED"

            // Hand-verified slugs for shows the title search gets wrong. Keyed by AniList id ONLY:
            // the old title-word checks ("Conflict", "Separation", "Calamity") hijacked any show
            // with those words in its name. A user's own pick always wins over these.
            val fastSlug = if (userPinned) null else FAST_PATH_SLUGS[identity.anilistId]
            if (fastSlug != null) {
                val r = provider.getEpisodes(ProviderSeriesId(fastSlug))
                if (r is EpisodeLookupResult.Matched && r.episodes.isNotEmpty()) {
                    if (stored?.slug != fastSlug) {
                        providerMappingStore.setMapping(
                            ProviderMapping(providerId, identity.anilistId, fastSlug,
                                MappingEvidence(fastSlug, identity.title, "FAST_PATH", 1.0))
                        )
                    }
                    return r
                }
            }

            var primaryResult: EpisodeLookupResult? = null
            var rejectedSlug: String? = null
            if (stored != null) {
                when (val r = provider.getEpisodes(ProviderSeriesId(stored.slug))) {
                    is EpisodeLookupResult.Matched -> {
                        // Fewer episodes than AniList's planned total is normal (airing shows,
                        // partial uploads). Only a list far LONGER than expected means the mapping
                        // points at the wrong thing (a franchise compilation, another season).
                        val expected = identity.expectedEpisodes
                        val wildlyOff = expected != null && expected > 0 && r.episodes.size > expected * 2 + 2
                        if (r.episodes.isNotEmpty() && (userPinned || !wildlyOff)) {
                            primaryResult = r
                        } else {
                            rejectedSlug = stored.slug
                            providerMappingStore.invalidateMapping(providerId, identity.anilistId)
                        }
                    }
                    EpisodeLookupResult.NotFound -> {
                        // The provider says the slug is gone (HTTP 404): the mapping is dead.
                        rejectedSlug = stored.slug
                        providerMappingStore.invalidateMapping(providerId, identity.anilistId)
                    }
                    // Network trouble: keep the mapping and tell the user, don't "self-heal" it away.
                    is EpisodeLookupResult.Error -> return r
                    is EpisodeLookupResult.Ambiguous -> Unit
                }
            }

            if (primaryResult == null) {
                // Fallback to candidate search
                val lookupResult = provider.findSeries(identity)
                primaryResult = when (lookupResult) {
                    is SeriesMatchResult.Matched -> {
                        if (lookupResult.seriesId.value != rejectedSlug) providerMappingStore.setMapping(
                            ProviderMapping(
                                provider = providerId,
                                anilistId = identity.anilistId,
                                slug = lookupResult.seriesId.value,
                                evidence = lookupResult.evidence
                            )
                        )
                        provider.getEpisodes(lookupResult.seriesId)
                    }
                    is SeriesMatchResult.Ambiguous -> {
                        val candidatesToTry = lookupResult.candidates.take(3)
                        var resolved: EpisodeLookupResult? = null
                        for (cand in candidatesToTry) {
                            val candidateResult = provider.getEpisodes(ProviderSeriesId(cand.slug))
                            if (candidateResult is EpisodeLookupResult.Matched && candidateResult.episodes.isNotEmpty()) {
                                providerMappingStore.setMapping(
                                    ProviderMapping(
                                        provider = providerId,
                                        anilistId = identity.anilistId,
                                        slug = cand.slug,
                                        evidence = MappingEvidence(cand.title, identity.title, "AMBIGUOUS_FALLBACK", 0.75)
                                    )
                                )
                                resolved = candidateResult
                                break
                            }
                        }
                        resolved ?: EpisodeLookupResult.Ambiguous(lookupResult.candidates)
                    }
                    is SeriesMatchResult.NotFound -> {
                        EpisodeLookupResult.NotFound
                    }
                }
            }

            if (primaryResult is EpisodeLookupResult.Matched) {
                return primaryResult
            }

            // No episode-list fallback exists: MIRURO and ANIKOTO both wrap MegaPlay, which has
            // no episode-list endpoint. They stay registered for stream resolution/failover only.
            // Surface the primary provider's failure instead of inventing episodes.
            return primaryResult
        } catch (e: Exception) {
            e.printStackTrace()
            return EpisodeLookupResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }

    override suspend fun getEpisodesBySlug(provider: ProviderId, slug: ProviderSeriesId): EpisodeLookupResult {
        val providerImpl = providers[provider] ?: return EpisodeLookupResult.Error("Provider not found")
        return try {
            providerImpl.getEpisodes(slug)
        } catch (e: Exception) {
            EpisodeLookupResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }

    override suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult {
        val providerImpl = providers[request.provider]
            ?: return PlaybackResult.Error(request.provider, PlaybackErrorType.NoProviderMatch, "Provider not found")
        
        var resolvedSlug = providerMappingStore.getMapping(request.provider, request.animeId)?.slug
        if (resolvedSlug == null || resolvedSlug.contains(" ")) {
            if (request.episodeId.startsWith("anilight:")) {
                val candidate = request.episodeId.removePrefix("anilight:").substringBefore("|")
                if (candidate.isNotEmpty() && !candidate.contains(" ")) {
                    resolvedSlug = candidate
                }
            } else if (request.episodeId.contains(":")) {
                val candidate = request.episodeId.substringAfter(":").substringBefore("|")
                if (candidate.isNotEmpty() && !candidate.contains(" ")) {
                    resolvedSlug = candidate
                }
            }
        }
        if (resolvedSlug == null) {
            resolvedSlug = request.seriesSlug
        }
 
        val correctedRequest = request.copy(seriesSlug = resolvedSlug)
        val result = providerImpl.resolve(correctedRequest)
        if (result is PlaybackResult.NativeSources) {
            val filteredSources = result.sources.filter { !AdBlocker.shouldBlock(it.url) }
            if (filteredSources.isEmpty()) {
                return PlaybackResult.Error(request.provider, PlaybackErrorType.NoSources, "No unblocked streaming sources found.")
            }
            return PlaybackResult.NativeSources(result.provider, filteredSources, result.subtitles)
        }
        if (result is PlaybackResult.EmbedOnly) {
            return PlaybackResult.TemporarilyUnavailable(request.provider)
        }
        return result
    }

    private fun getFallbackAnimeList(): List<Anime> {
        return backupAnimeApi.curatedTrendingAnime
    }

    private fun getFallbackPopularList(): List<Anime> {
        return backupAnimeApi.curatedBlockbusterHits.sortedByDescending { it.averageScore ?: 0 }
    }

    private fun getFallbackTopRatedList(): List<Anime> {
        return backupAnimeApi.curatedBlockbusterHits.sortedByDescending { it.averageScore ?: 0 }.take(10)
    }

    private fun getFallbackSeasonalList(): List<Anime> {
        return backupAnimeApi.curatedBlockbusterHits.filter { it.status == "RELEASING" }
    }

    private fun getFallbackActionList(): List<Anime> {
        return backupAnimeApi.curatedBlockbusterHits.filter {
            it.genres.any { g -> g.contains("Action", ignoreCase = true) }
        }
    }

    private fun getFallbackUpcomingList(): List<Anime> {
        return backupAnimeApi.curatedUpcomingAnime
    }

    private fun getFallbackRomanceList(): List<Anime> {
        return backupAnimeApi.curatedRomanceAnime
    }

    override suspend fun checkUpdates(): AppUpdateInfo? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Silently sync dynamic remote listing and episode corrections in background
        listingCorrectionManager.syncRemoteCorrections()
        // AniFlow's own signed update channel (data/update/UpdateChannel.kt): every mirror asked,
        // newest validly signed answer wins. Null = couldn't check; an UpToDate sentinel below
        // tells the UI the check worked and there is nothing to do.
        try {
            val channel = com.example.aniflow.data.update.UpdateChannel(
                mirrors = com.example.aniflow.utils.UpdateConfig.MANIFEST_MIRRORS,
                trustedKeys = com.example.aniflow.utils.UpdateConfig.MANIFEST_KEYS,
                store = com.example.aniflow.data.update.UpdatePrefs(context),
                fetch = { url ->
                    kotlinx.coroutines.withTimeoutOrNull(12_000) {
                        val r = client.get(url) { header("Cache-Control", "no-cache") }
                        if (r.status.value == 200 && r.call.request.url.protocol.name == "https") r.bodyAsText() else null
                    }
                },
            )
            val result = channel.check() ?: return@withContext null
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val installed = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            channel.toUpdateInfo(result, installed, android.os.Build.VERSION.SDK_INT)
                ?: AppUpdateInfo(versionCode = installed.toInt(), versionName = result.manifest.versionName, updateUrl = "")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Failed to check update", e)
            null
        }
    }

    override suspend fun refreshSchedule(): Pair<List<AiringAnime>, List<Anime>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val schedule = getCachedSchedule()
        val now = System.currentTimeMillis() / 1000

        fun titleOf(e: com.example.aniflow.data.AniLightScheduleEntry) =
            e.anime.title.english?.takeIf { it.isNotBlank() }
                ?: e.anime.title.romaji?.takeIf { it.isNotBlank() }
                ?: e.anime.title.native?.takeIf { it.isNotBlank() }
        fun coverOf(e: com.example.aniflow.data.AniLightScheduleEntry) =
            e.anime.coverImage?.large?.takeIf { it.isNotBlank() } ?: e.anime.coverImage?.extraLarge?.takeIf { it.isNotBlank() }

        // Only entries we can open: an AniList id (AniLight's own id is a different number
        // space - using it as an AniList id opened unrelated shows), a title and a cover.
        // Delayed placeholder slots are not real airings. Sub and dub of the same episode are
        // separate entries; collapse them.
        val usable = schedule
            .filter { it.anime.anilistId != null && !it.isDelayedSlot && titleOf(it) != null && coverOf(it) != null }
            .distinctBy { it.anime.anilistId to it.episode }

        // "Airing Today" means the user's local calendar day - aired earlier today included.
        // Calendar, not java.time: java.time needs API 26 and minSdk is 24 (Android 7 TVs crashed here).
        val cal = java.util.Calendar.getInstance()
        fun localDay(epochSec: Long): Int {
            cal.timeInMillis = epochSec * 1000L
            return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        }
        val today = localDay(System.currentTimeMillis() / 1000L)
        val todays = usable.filter { localDay(it.airingAt) == today }.sortedBy { it.airingAt }
        // A quiet day still gets a useful row: what airs next.
        val airingSource = todays.ifEmpty { usable.filter { it.airingAt > now }.sortedBy { it.airingAt }.take(12) }
        val airingTodayList = airingSource.map { e ->
            AiringAnime(
                mediaId = e.anime.anilistId!!,
                title = titleOf(e)!!,
                coverImageUrl = coverOf(e)!!,
                airingAt = e.airingAt,
                episode = e.episode
            )
        }.toMutableList()

        // Recently Updated: episodes that have actually aired (and, when the server says so, are
        // playable), newest first, one card per show. No padding with curated "hits": a short
        // honest row beats invented freshness.
        val recentlyUpdatedList = usable
            .filter { it.airingAt <= now && it.displayStatus?.isPlayable != false }
            .sortedByDescending { it.airingAt }
            .distinctBy { it.anime.anilistId }
            .take(20)
            .map { e ->
                val cover = coverOf(e)!!
                Anime(
                    id = e.anime.anilistId!!,
                    title = titleOf(e)!!,
                    englishTitle = e.anime.title.english?.takeIf { it.isNotBlank() },
                    romajiTitle = e.anime.title.romaji?.takeIf { it.isNotBlank() },
                    coverImage = cover,
                    bannerImage = e.anime.coverImage?.extraLarge?.takeIf { it.isNotBlank() } ?: cover,
                    // Unknown total. (This used to be set to the latest episode number, so cards
                    // claimed a 1,100-episode show had "5 episodes".) The latest aired episode is
                    // carried as nextAiringEpisode - 1, which is exactly what the episode list's
                    // aired-only cut expects.
                    episodes = null,
                    nextAiringEpisode = e.episode + 1,
                    status = e.anime.status ?: "RELEASING",
                    genres = e.anime.genres,
                    averageScore = e.anime.averageScore,
                    nextAiringAt = e.airingAt
                )
            }.toMutableList()

        if (airingTodayList.isNotEmpty() || recentlyUpdatedList.isNotEmpty()) {
            cacheAnimeList(recentlyUpdatedList)
            for (item in airingTodayList) {
                knownAnimeCache.putIfAbsent(
                    item.mediaId,
                    Anime(
                        id = item.mediaId,
                        title = item.title,
                        englishTitle = item.title,
                        coverImage = item.coverImageUrl,
                        bannerImage = item.coverImageUrl,
                        episodes = null,
                        nextAiringEpisode = item.episode,
                        status = "RELEASING",
                        nextAiringAt = item.airingAt
                    )
                )
            }
            feedDiskCache.updateSection(
                airingToday = if (airingTodayList.isNotEmpty()) airingTodayList else null,
                recentlyUpdated = if (recentlyUpdatedList.isNotEmpty()) recentlyUpdatedList else null
            )
        }

        Pair(airingTodayList, recentlyUpdatedList)
    }

    override suspend fun checkUrlStatus(url: String, headers: Map<String, String>): Int = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val response = client.request(url) {
                method = io.ktor.http.HttpMethod.Head
                headers.forEach { (k, v) ->
                    header(k, v)
                }
            }
            response.status.value
        } catch (e: Exception) {
            try {
                val response = client.request(url) {
                    method = io.ktor.http.HttpMethod.Get
                    headers.forEach { (k, v) ->
                        header(k, v)
                    }
                    header("Range", "bytes=0-0")
                }
                response.status.value
            } catch (e2: Exception) {
                0
            }
        }
    }

    private fun optimizeQuery(query: String): String {
        var optimized = query.lowercase().trim()
        val expansions = mapOf(
            "jjk" to "jujutsu kaisen",
            "aot" to "attack on titan",
            "sao" to "sword art online",
            "hxh" to "hunter x hunter",
            "fmab" to "fullmetal alchemist brotherhood",
            "opm" to "one punch man",
            "op" to "one piece",
            "dn" to "death note",
            "mha" to "my hero academia",
            "bhna" to "boku no hero academia",
            "ds" to "demon slayer",
            "kny" to "kimetsu no yaiba",
            "mt" to "mushoku tensei",
            "danmachi" to "dungeon ni deai wo motomeru",
            "oregairu" to "yahari ore no seishun",
            "konosuba" to "kono subarashii sekai",
            "tensura" to "tensei shitara slime datta ken",
            "slime isekai" to "tensei shitara slime datta ken",
            "ngnl" to "no game no life",
            "tg" to "tokyo ghoul"
        )
        for ((abbrev, expansion) in expansions) {
            val regex = Regex("\\b$abbrev\\b")
            if (regex.containsMatchIn(optimized)) {
                optimized = optimized.replace(regex, expansion)
            }
        }
        return optimized
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[len1][len2]
    }

    private val popularAnimeDictionary = listOf(
        "Naruto", "Naruto Shippuden", "Bleach", "Bleach: Thousand-Year Blood War", "One Piece", "Death Note",
        "Attack on Titan", "Shingeki no Kyojin", "My Hero Academia", "Boku no Hero Academia",
        "Demon Slayer", "Kimetsu no Yaiba", "Jujutsu Kaisen", "Hunter x Hunter", "Fullmetal Alchemist",
        "Fullmetal Alchemist: Brotherhood", "Sword Art Online", "One Punch Man", "Chainsaw Man",
        "Frieren: Beyond Journey's End", "Sousou no Frieren", "Mushoku Tensei", "Mushoku Tensei: Jobless Reincarnation",
        "Tokyo Ghoul", "Steins;Gate", "No Game No Life", "Code Geass", "Re:Zero", "Re:Zero - Starting Life in Another World",
        "KonoSuba", "Tensei Shitara Slime Datta Ken", "That Time I Got Reincarnated as a Slime",
        "Dungeon ni Deai wo Motomeru", "Is It Wrong to Try to Pick Up Girls in a Dungeon?",
        "Yahari Ore no Seishun Love Comedy wa Machigatteiru", "Oregairu", "Cyberpunk: Edgerunners",
        "Vinland Saga", "Monster", "Mob Psycho 100", "Bocchi the Rock!", "Kaguya-sama: Love is War",
        "Kaguya-sama wa Kokurasetai", "Oshi no Ko", "Spy x Family", "Solo Leveling", "Kaiju No. 8",
        "Black Clover", "Fairy Tail", "Dragon Ball Z", "Dragon Ball Super", "Neon Genesis Evangelion",
        "Your Name", "Kimi no Na wa", "A Silent Voice", "Koe no Katachi", "Spirited Away", "Princess Mononoke",
        "Cowboy Bebop", "Samurai Champloo", "Code Geass: Lelouch of the Rebellion", "Tengen Toppa Gurren Lagann",
        "Your Lie in April", "Shigatsu wa Kimi no Uso", "Toradora!", "Clannad", "Clannad: After Story",
        "Fate/Zero", "Fate/stay night: Unlimited Blade Works", "Fate/stay night: Heaven's Feel",
        "Haikyu!!", "Kuroko's Basketball", "Kuroko no Basket", "Blue Lock", "Jujutsu Kaisen 2nd Season",
        "Dr. STONE", "Fire Force", "Enen no Shouboutai", "The Rising of the Shield Hero", "Tate no Yuusha no Nariagari",
        "Overlord", "Goblin Slayer", "The Eminence in Shadow", "Kage no Jitsuryokusha ni Naritakute!",
        "Erased", "Boku dake ga Inai Machi", "Death Parade", "Noragami", "Soul Eater", "Assassination Classroom",
        "Ansatsu Kyoushitsu", "Angel Beats!", "Anohana: The Flower We Saw That Day", "Charlotte",
        "Tokyo Revengers", "Hell's Paradise", "Jigokuraku", "Dangers in My Heart", "Boku no Kokoro no Yabai Yatsu"
    )

    private fun findSpellingCorrection(query: String): String? {
        val q = query.lowercase().trim()
        if (q.length < 3) return null
        var bestMatch: String? = null
        var minDistance = Int.MAX_VALUE
        for (title in popularAnimeDictionary) {
            val t = title.lowercase()
            if (t.contains(q)) return title
            val dist = levenshteinDistance(q, t)
            if (dist < minDistance) {
                minDistance = dist
                bestMatch = title
            }
        }
        val threshold = when {
            q.length <= 4 -> 1
            q.length <= 7 -> 2
            else -> 3
        }
        return if (minDistance <= threshold) bestMatch else null
    }

    fun healAllCaches() {
        val now = System.currentTimeMillis()
        if (now - lastTrendingFetchTime > 30 * 60 * 1000L) { cachedTrending = null }
        if (now - lastPopularFetchTime > 30 * 60 * 1000L) { cachedPopular = null }
        if (now - lastSeasonalFetchTime > 30 * 60 * 1000L) { cachedSeasonal = null }
        if (now - lastTopRatedFetchTime > 30 * 60 * 1000L) { cachedTopRated = null }
        if (now - lastUpcomingFetchTime > 30 * 60 * 1000L) { cachedUpcoming = null }
        if (now - lastActionFetchTime > 30 * 60 * 1000L) { cachedAction = null }
        if (now - lastRomanceFetchTime > 30 * 60 * 1000L) { cachedRomance = null }
        if (now - lastScheduleFetchTime > 30 * 60 * 1000L) { cachedSchedule = null }
        
        try {
            aniLightProvider.healWatchCache()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
