package com.example.aniflow.data

import com.example.aniflow.data.model.*

class MiruroProvider : EpisodeProvider {
    override val id: ProviderId = ProviderId.MIRURO

    override suspend fun findSeries(identity: AnimeIdentity): EpisodeLookupResult {
        return EpisodeLookupResult.NotFound
    }

    override suspend fun episodes(slug: String): List<Episode> {
        return emptyList()
    }

    override suspend fun resolve(request: EpisodeRequest): ProviderPlaybackResult {
        return ProviderPlaybackResult.Error(
            errorType = PlaybackErrorType.NoSources,
            message = "Miruro provider is currently dormant."
        )
    }
}
