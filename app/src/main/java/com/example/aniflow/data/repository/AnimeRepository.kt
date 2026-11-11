package com.example.aniflow.data.repository

import com.example.aniflow.data.model.*
import kotlinx.coroutines.flow.Flow

interface AnimeRepository {
    // Homepage sections
    fun getTrending(): Flow<List<Anime>>
    fun getPopular(): Flow<List<Anime>>
    fun getSeasonal(): Flow<List<Anime>>
    fun getAiringToday(): Flow<List<AiringAnime>>
    fun getTopRated(): Flow<List<Anime>>
    fun getUpcoming(): Flow<List<Anime>>
    fun getRecentlyUpdated(): Flow<List<Anime>>
    fun getActionAnime(): Flow<List<Anime>>
    fun getRomanceAnime(): Flow<List<Anime>>
    fun getAnimeByGenre(genre: String): Flow<List<Anime>>
    /** Extra home rows (movies, last season, genre spotlights, best of the year). */
    fun getHomeShelves(): Flow<List<HomeShelf>> = kotlinx.coroutines.flow.flowOf(emptyList())
    /** This week's simulcast airings (AniList ids), oldest first. */
    fun getWeekSchedule(): Flow<List<AiringAnime>> = kotlinx.coroutines.flow.flowOf(emptyList())

    // Search
    fun searchAnime(query: String, page: Int = 1): Flow<SearchPage>

    // Detail
    fun getAnimeDetail(id: Int): Flow<Anime?>

    /** Seasons, movies, OVAs, ONAs and specials of [id]'s franchise; empty when unavailable. */
    suspend fun getFranchise(id: Int): List<FranchiseEntry> = emptyList()

    /** Same-format prequel of [id] as (MAL id, episodes), for streaming sites that file a Part 2 under Part 1. */
    suspend fun getSamePrequel(id: Int): Pair<Int, Int>? = null

    /** AniList id -> that entry's own YouTube trailer id; empty when unavailable. */
    suspend fun getTrailers(ids: List<Int>): Map<Int, String> = emptyMap()

    /** AniList id -> newest episode already out, for shows still airing; empty when unavailable. */
    suspend fun getLatestAired(ids: List<Int>): Map<Int, Int> = emptyMap()
    
    // Updated repository contracts
    suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult
    suspend fun getEpisodesBySlug(provider: ProviderId, slug: ProviderSeriesId): EpisodeLookupResult
    suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult
    
    suspend fun checkUpdates(): AppUpdateInfo?
    suspend fun refreshSchedule(): Pair<List<AiringAnime>, List<Anime>>
    suspend fun checkUrlStatus(url: String, headers: Map<String, String>): Int
}

/** A titled home row. [late] shelves go near the bottom of the home, the rest mid-page. */
data class HomeShelf(val key: String, val title: String, val items: List<Anime>, val late: Boolean = false)
