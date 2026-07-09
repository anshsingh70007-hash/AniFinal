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

    // Search
    fun searchAnime(query: String, page: Int = 1): Flow<SearchPage>

    // Detail
    fun getAnimeDetail(id: Int): Flow<Anime?>
    suspend fun getEpisodes(animeId: Int, title: String): List<Episode>
    suspend fun getStreamingSources(episodeId: String): EpisodeSourcesResponse
    suspend fun checkUpdates(): AppUpdateInfo?
    suspend fun refreshSchedule(): Pair<List<AiringAnime>, List<Anime>>
}
