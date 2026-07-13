package com.example.aniflow.data

import com.example.aniflow.data.model.*

class AnikotoProvider : EpisodeProvider {
    override val id: ProviderId = ProviderId.ANIKOTO

    override suspend fun findSeries(identity: AnimeIdentity): EpisodeLookupResult {
        return EpisodeLookupResult.NotFound
    }

    override suspend fun episodes(slug: String): List<Episode> {
        return emptyList()
    }

    override suspend fun resolve(request: EpisodeRequest): ProviderPlaybackResult {
        return ProviderPlaybackResult.Error(
            errorType = PlaybackErrorType.NoSources,
            message = "Anikoto provider is currently dormant."
        )
    }
}
