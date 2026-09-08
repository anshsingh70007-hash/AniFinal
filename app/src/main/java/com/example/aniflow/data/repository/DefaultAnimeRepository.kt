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
    private val backupAnimeApi = com.example.aniflow.data.remote.BackupAnimeApi(client)
    private val feedDiskCache = FeedDiskCache(context)
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
            emit(backupAnimeApi.ensureBleachFirst(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.trending.isNotEmpty() && cached == null) {
            emit(backupAnimeApi.ensureBleachFirst(diskFeed.trending))
        }
        try {
            var list = try { aniListApi.getTrending() } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList trending unavailable, trying backup API...")
                list = backupAnimeApi.getTrending()
            }
            if (list.isNotEmpty()) {
                val sanitized = backupAnimeApi.ensureBleachFirst(list)
                cachedTrending = sanitized
                lastTrendingFetchTime = now
                feedDiskCache.updateSection(trending = sanitized)
                emit(sanitized)
            } else {
                emit(cached ?: diskFeed?.trending?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.ensureBleachFirst(it) } ?: getFallbackAnimeList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting trending", e)
            emit(cached ?: diskFeed?.trending?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.ensureBleachFirst(it) } ?: getFallbackAnimeList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getPopular(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedPopular
        if (cached != null && cached.size > 3 && now - lastPopularFetchTime < HOME_CACHE_DURATION_MS) {
            emit(backupAnimeApi.deduplicateFranchises(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.popular.isNotEmpty() && cached == null) {
            emit(backupAnimeApi.deduplicateFranchises(diskFeed.popular))
        }
        try {
            var list = try { aniListApi.getPopular() } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList popular unavailable, trying backup API...")
                list = backupAnimeApi.getPopular()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedPopular = deduped
                lastPopularFetchTime = now
                feedDiskCache.updateSection(popular = deduped)
                emit(deduped)
            } else {
                emit(cached ?: diskFeed?.popular?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackPopularList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting popular", e)
            emit(cached ?: diskFeed?.popular?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackPopularList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getSeasonal(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedSeasonal
        if (cached != null && cached.size > 3 && now - lastSeasonalFetchTime < HOME_CACHE_DURATION_MS) {
            emit(backupAnimeApi.deduplicateFranchises(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.seasonal.isNotEmpty() && cached == null) {
            emit(backupAnimeApi.deduplicateFranchises(diskFeed.seasonal))
        }
        try {
            var list = try { aniListApi.getSeasonal() } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList seasonal unavailable, trying backup API...")
                list = backupAnimeApi.getSeasonal()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedSeasonal = deduped
                lastSeasonalFetchTime = now
                feedDiskCache.updateSection(seasonal = deduped)
                emit(deduped)
            } else {
                emit(cached ?: diskFeed?.seasonal?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackSeasonalList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting seasonal", e)
            emit(cached ?: diskFeed?.seasonal?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackSeasonalList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getAiringToday(): Flow<List<AiringAnime>> = flow {
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.airingToday.isNotEmpty()) {
            emit(diskFeed.airingToday)
        }
        val data = refreshSchedule()
        emit(data.first.ifEmpty { diskFeed?.airingToday?.takeIf { it.isNotEmpty() } ?: getFallbackAiringList() })
    }.flowOn(Dispatchers.IO)

    override fun getTopRated(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedTopRated
        if (cached != null && cached.size > 3 && now - lastTopRatedFetchTime < HOME_CACHE_DURATION_MS) {
            emit(backupAnimeApi.deduplicateFranchises(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.topRated.isNotEmpty() && cached == null) {
            emit(backupAnimeApi.deduplicateFranchises(diskFeed.topRated))
        }
        try {
            var list = try { aniListApi.getTopRated() } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList top rated unavailable, trying backup API...")
                list = backupAnimeApi.getTopRated()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedTopRated = deduped
                lastTopRatedFetchTime = now
                feedDiskCache.updateSection(topRated = deduped)
                emit(deduped)
            } else {
                emit(cached ?: diskFeed?.topRated?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackTopRatedList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting top rated", e)
            emit(cached ?: diskFeed?.topRated?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackTopRatedList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getUpcoming(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedUpcoming
        if (cached != null && cached.size > 3 && now - lastUpcomingFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.upcoming.isNotEmpty() && cached == null) {
            val safeDisk = backupAnimeApi.deduplicateFranchises(diskFeed.upcoming).filter {
                it.status.contains("UPCOMING", ignoreCase = true) || it.status.contains("NOT_YET", ignoreCase = true)
            }
            if (safeDisk.isNotEmpty()) {
                emit(safeDisk)
            }
        }
        try {
            var list = try { aniListApi.getUpcoming() } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) {
                android.util.Log.i("DefaultAnimeRepository", "AniList upcoming unavailable, trying backup API...")
                list = backupAnimeApi.getUpcoming()
            }
            if (list.isNotEmpty()) {
                val deduped = backupAnimeApi.deduplicateFranchises(list)
                cachedUpcoming = deduped
                lastUpcomingFetchTime = now
                feedDiskCache.updateSection(upcoming = deduped)
                emit(deduped)
            } else {
                emit(cached ?: diskFeed?.upcoming?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackUpcomingList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting upcoming", e)
            emit(cached ?: diskFeed?.upcoming?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackUpcomingList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getRecentlyUpdated(): Flow<List<Anime>> = flow {
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.recentlyUpdated.isNotEmpty()) {
            emit(diskFeed.recentlyUpdated)
        }
        val data = refreshSchedule()
        emit(data.second.ifEmpty { diskFeed?.recentlyUpdated?.takeIf { it.isNotEmpty() } ?: getFallbackSeasonalList() })
    }.flowOn(Dispatchers.IO)

    override fun getActionAnime(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedAction
        if (cached != null && cached.size > 3 && now - lastActionFetchTime < HOME_CACHE_DURATION_MS) {
            emit(cached)
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.actionAnime.isNotEmpty() && cached == null) {
            emit(diskFeed.actionAnime)
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
                emit(list)
            } else {
                emit(cached ?: diskFeed?.actionAnime?.takeIf { it.isNotEmpty() } ?: getFallbackActionList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting action anime", e)
            emit(cached ?: diskFeed?.actionAnime?.takeIf { it.isNotEmpty() } ?: getFallbackActionList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getRomanceAnime(): Flow<List<Anime>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedRomance
        if (cached != null && cached.size > 3 && now - lastRomanceFetchTime < HOME_CACHE_DURATION_MS) {
            emit(backupAnimeApi.deduplicateFranchises(cached))
            return@flow
        }
        val diskFeed = feedDiskCache.loadCache()
        if (diskFeed != null && diskFeed.romanceAnime.isNotEmpty() && cached == null) {
            emit(backupAnimeApi.deduplicateFranchises(diskFeed.romanceAnime))
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
                emit(deduped)
            } else {
                emit(cached ?: diskFeed?.romanceAnime?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackRomanceList())
            }
        } catch (e: Exception) {
            android.util.Log.e("DefaultAnimeRepository", "Error getting romance anime", e)
            emit(cached ?: diskFeed?.romanceAnime?.takeIf { it.isNotEmpty() }?.let { backupAnimeApi.deduplicateFranchises(it) } ?: getFallbackRomanceList())
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
        emit(list.ifEmpty { fallback })
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
        
        val list = if (searchPage.results.isEmpty() && page == 1) {
            getFallbackAnimeList().filter { 
                it.title.contains(optimizedQuery, ignoreCase = true) ||
                it.englishTitle?.contains(optimizedQuery, ignoreCase = true) == true ||
                it.genres.any { g -> g.contains(optimizedQuery, ignoreCase = true) }
            }
        } else {
            searchPage.results
        }
        emit(searchPage.copy(results = list))
    }.flowOn(Dispatchers.IO)

    override fun getAnimeDetail(id: Int): Flow<Anime?> = flow {
        if (id == 185874 || id == 169755 || id == 116674) {
            emit(backupAnimeApi.bleachTybwAnime)
            return@flow
        }
        var detail = try { aniListApi.getAnimeDetail(id) } catch (e: Exception) { null }
        if (detail == null) {
            detail = try { backupAnimeApi.getAnimeDetail(id) } catch (e: Exception) { null }
        }
        if (detail == null) {
            detail = getFallbackAnimeList().find { it.id == id }
        }
        if (detail == null) {
            detail = feedDiskCache.findAnimeById(id)
        }
        emit(detail)
    }.flowOn(Dispatchers.IO)

    private val listingCorrectionManager = ListingCorrectionManager(context, client)
    private val providerMappingStore = ProviderMappingStore(context)
    private val providers: Map<ProviderId, EpisodeProvider> = mapOf(
        ProviderId.ANILIGHT to aniLightProvider,
        ProviderId.ANIKOTO to AnikotoProvider(client),
        ProviderId.MIRURO to MiruroProvider(client)
    )

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

            // Fast-path instant mapping for Bleach TYBW Part 4 (The Calamity)
            if (identity.anilistId == 185874 || identity.title.contains("Calamity", ignoreCase = true) || identity.title.contains("Kashin", ignoreCase = true)) {
                val fastPathSlug = "bleach-sennen-kessen-hen-kashin-tan-kyzw"
                val epResult = provider.getEpisodes(ProviderSeriesId(fastPathSlug))
                if (epResult is EpisodeLookupResult.Matched && epResult.episodes.isNotEmpty()) {
                    return epResult
                }
            }

            // Fast-path instant mapping for Bleach TYBW Part 3 (The Conflict)
            if (identity.anilistId == 169755) {
                val fastPathSlug = "bleach-sennen-kessen-hen-soukoku-tan-o672"
                val epResult = provider.getEpisodes(ProviderSeriesId(fastPathSlug))
                if (epResult is EpisodeLookupResult.Matched && epResult.episodes.isNotEmpty()) {
                    return epResult
                }
            }

            // Check persistence mapping store
            val mapping = providerMappingStore.getMapping(providerId, identity.anilistId)
            var primaryResult: EpisodeLookupResult? = null
            if (mapping != null) {
                val epResult = provider.getEpisodes(ProviderSeriesId(mapping.slug))
                if (epResult is EpisodeLookupResult.Matched && epResult.episodes.isNotEmpty()) {
                    val expected = identity.expectedEpisodes
                    val found = epResult.episodes.size
                    if (expected != null && expected > 0 && Math.abs(found - expected).toDouble() / expected > 0.50) {
                        android.util.Log.w("DefaultAnimeRepository", "Self-Heal: Mapping episode count mismatch (found: $found, expected: $expected). Invalidating stale mapping.")
                        providerMappingStore.invalidateMapping(providerId, identity.anilistId)
                    } else {
                        primaryResult = epResult
                    }
                } else {
                    providerMappingStore.invalidateMapping(providerId, identity.anilistId)
                }
            }
            
            if (primaryResult == null) {
                // Fallback to candidate search
                val lookupResult = provider.findSeries(identity)
                primaryResult = when (lookupResult) {
                    is SeriesMatchResult.Matched -> {
                        providerMappingStore.setMapping(
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
                        EpisodeLookupResult.Ambiguous(lookupResult.candidates)
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
        
        val resolvedSlug = providerMappingStore.getMapping(request.provider, request.animeId)?.slug
            ?: request.seriesSlug
 
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
        return backupAnimeApi.curatedBlockbusterHits
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

    private fun getFallbackAiringList(): List<AiringAnime> {
        val now = System.currentTimeMillis() / 1000
        return listOf(
            AiringAnime(
                mediaId = 185874,
                title = "Bleach: Thousand-Year Blood War - The Calamity",
                coverImageUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx185874-WkL6oB6Gj2x6.jpg",
                airingAt = now + 3600,
                episode = 1
            ),
            AiringAnime(
                mediaId = 21,
                title = "One Piece",
                coverImageUrl = "https://media.kitsu.app/anime/poster_images/12/large.jpg",
                airingAt = now + 7200,
                episode = 1112
            ),
            AiringAnime(
                mediaId = 151807,
                title = "Solo Leveling",
                coverImageUrl = "https://media.kitsu.app/anime/46231/poster_image/large-cdadff31f42490b9f48a035939a01a92.jpeg",
                airingAt = now + 10800,
                episode = 13
            )
        )
    }

    override suspend fun checkUpdates(): AppUpdateInfo? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Silently sync dynamic remote listing and episode corrections in background
        listingCorrectionManager.syncRemoteCorrections()
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

        // Airing Schedule: upcoming releases sorted chronologically by airingAt ASC
        val validUpcoming = schedule.filter { entry ->
            entry.airingAt > now &&
            (entry.anime.title.english?.isNotBlank() == true || entry.anime.title.romaji?.isNotBlank() == true) &&
            (entry.anime.coverImage?.large?.isNotBlank() == true || entry.anime.coverImage?.extraLarge?.isNotBlank() == true)
        }.sortedBy { it.airingAt }

        val airingTodayList = validUpcoming.distinctBy { entry ->
            val rawTitle = entry.anime.title.english?.takeIf { it.isNotBlank() }
                ?: entry.anime.title.romaji?.takeIf { it.isNotBlank() }
                ?: entry.anime.slug
            backupAnimeApi.extractFranchiseKey(rawTitle)
        }.take(20).map { entry ->
            val title = entry.anime.title.english?.takeIf { it.isNotBlank() }
                ?: entry.anime.title.romaji?.takeIf { it.isNotBlank() }
                ?: entry.anime.title.native?.takeIf { it.isNotBlank() }
                ?: "Airing Anime"
            val cover = entry.anime.coverImage?.large?.takeIf { it.isNotBlank() } ?: entry.anime.coverImage?.extraLarge ?: ""
            AiringAnime(
                mediaId = entry.anime.anilistId ?: entry.anime.id,
                title = title,
                coverImageUrl = cover,
                airingAt = entry.airingAt,
                episode = entry.episode
            )
        }.toMutableList()

        // If airing list is sparse, blend with premier flagship anime
        if (airingTodayList.size < 3) {
            val fallbackAiring = getFallbackAiringList()
            for (fb in fallbackAiring) {
                if (airingTodayList.none { it.mediaId == fb.mediaId }) {
                    airingTodayList.add(fb)
                }
            }
        }

        // Recently Updated: genuine recently aired/released episodes from schedule
        // Accept null score (since freshly aired episodes often do not have ratings yet)
        val baseRecentlyUpdated = schedule.filter { entry ->
            entry.airingAt <= now &&
            (entry.anime.title.english?.isNotBlank() == true || entry.anime.title.romaji?.isNotBlank() == true) &&
            (entry.anime.coverImage?.large?.isNotBlank() == true || entry.anime.coverImage?.extraLarge?.isNotBlank() == true) &&
            (entry.anime.averageScore == null || entry.anime.averageScore >= 45)
        }.sortedByDescending { it.airingAt }

        val recentlyUpdatedList = baseRecentlyUpdated.distinctBy { entry ->
            val rawTitle = entry.anime.title.english?.takeIf { it.isNotBlank() }
                ?: entry.anime.title.romaji?.takeIf { it.isNotBlank() }
                ?: entry.anime.slug
            backupAnimeApi.extractFranchiseKey(rawTitle)
        }.take(20).map { entry ->
            val title = entry.anime.title.english?.takeIf { it.isNotBlank() }
                ?: entry.anime.title.romaji?.takeIf { it.isNotBlank() }
                ?: entry.anime.title.native?.takeIf { it.isNotBlank() }
                ?: "Anime Title"
            val cover = entry.anime.coverImage?.large?.takeIf { it.isNotBlank() } ?: entry.anime.coverImage?.extraLarge ?: ""
            Anime(
                id = entry.anime.anilistId ?: entry.anime.id,
                title = title,
                englishTitle = entry.anime.title.english?.takeIf { it.isNotBlank() },
                coverImage = cover,
                bannerImage = entry.anime.coverImage?.extraLarge?.takeIf { it.isNotBlank() } ?: cover,
                episodes = entry.episode,
                status = entry.anime.status ?: "RELEASING",
                genres = entry.anime.genres,
                averageScore = entry.anime.averageScore,
                nextAiringAt = entry.airingAt
            )
        }.toMutableList()

        // Ensure Recently Updated features premier hits if sparse
        if (recentlyUpdatedList.size < 5) {
            val highProfileRecent = backupAnimeApi.curatedBlockbusterHits.filter { 
                it.status == "RELEASING" || it.id == 21 || it.id == 185874 || it.id == 169755 || it.id == 151807 || it.id == 154587
            }
            for (hp in highProfileRecent) {
                val key = backupAnimeApi.extractFranchiseKey(hp.englishTitle ?: hp.title)
                if (recentlyUpdatedList.none { backupAnimeApi.extractFranchiseKey(it.englishTitle ?: it.title) == key }) {
                    recentlyUpdatedList.add(hp)
                }
            }
        }

        if (airingTodayList.isNotEmpty() || recentlyUpdatedList.isNotEmpty()) {
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
