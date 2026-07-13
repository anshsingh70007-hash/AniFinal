package com.example.aniflow.data

import com.example.aniflow.data.model.*

interface EpisodeProvider {
    val id: ProviderId
    suspend fun findSeries(identity: AnimeIdentity): EpisodeLookupResult
    suspend fun episodes(slug: String): List<Episode>
    suspend fun resolve(request: EpisodeRequest): ProviderPlaybackResult
}
