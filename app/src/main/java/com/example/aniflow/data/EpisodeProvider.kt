package com.example.aniflow.data

import com.example.aniflow.data.model.*

interface EpisodeProvider {
    val id: ProviderId
    suspend fun findSeries(identity: AnimeIdentity): SeriesMatchResult
    suspend fun getEpisodes(seriesId: ProviderSeriesId): EpisodeLookupResult
    suspend fun resolve(request: EpisodeRequest): PlaybackResult
}
