package com.example.aniflow.data.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class AudioType {
    SUB, DUB
}

@Serializable
enum class ProviderId {
    ANILIGHT, ANIKOTO, MIRURO
}

@JvmInline
@Serializable
value class ServerId(val value: String)

@Serializable
enum class StreamType {
    HLS, DASH, PROGRESSIVE, UNKNOWN
}

@Serializable
sealed class QualityPolicy {
    @Serializable
    @SerialName("auto")
    data object Auto : QualityPolicy()

    @Serializable
    @SerialName("fixed")
    data class FixedHeight(val height: Int) : QualityPolicy()

    @Serializable
    @SerialName("max")
    data object MaxAvailable : QualityPolicy()
}

@Serializable
data class SourceEndpoint(
    val id: String, // deterministic stable key
    val provider: ProviderId,
    val server: ServerId,
    val audioType: AudioType,
    val streamType: StreamType,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val priority: Int = 0,
    val qualityPolicy: QualityPolicy,
    val declaredResolution: Int? = null,
    val episodeId: String,
    val role: String? = null,
    val hlsHeights: List<Int> = emptyList()
) {
    val isM3U8: Boolean get() = streamType == StreamType.HLS
}

@Serializable
data class SubtitleTrack(
    val url: String,
    val lang: String,
    val label: String
)

@Serializable
data class EpisodeSourcesResponse(
    val sources: List<SourceEndpoint>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val headers: Map<String, String>? = null
)

@Serializable
data class AnimeIdentity(
    val anilistId: Int,
    val malId: Int? = null,
    val title: String,
    val englishTitle: String? = null,
    val nativeTitle: String? = null,
    val seasonYear: Int? = null,
    val format: String? = null // MOVIE, TV, etc.
)

@Serializable
data class EpisodeRequest(
    val provider: ProviderId,
    val seriesSlug: String,
    val episodeId: String,
    val animeId: Int,
    val episodeNumber: Int,
    val audioType: AudioType
)

@Serializable
enum class PlaybackErrorType {
    NoProviderMatch,
    NoEpisodes,
    NoSources,
    Network,
    ProviderChanged,
    RateLimited,
    InvalidatedMapping
}

@Serializable
sealed class ProviderPlaybackResult {
    @Serializable
    @SerialName("success")
    data class Success(val response: EpisodeSourcesResponse) : ProviderPlaybackResult()

    @Serializable
    @SerialName("embed")
    data class EmbedOnly(val embedUrl: String) : ProviderPlaybackResult()

    @Serializable
    @SerialName("error")
    data class Error(val errorType: PlaybackErrorType, val message: String) : ProviderPlaybackResult()
}

@Serializable
sealed class EpisodeLookupResult {
    @Serializable
    @SerialName("matched")
    data class Matched(val provider: ProviderId, val slug: String, val episodes: List<Episode>) : EpisodeLookupResult()

    @Serializable
    @SerialName("ambiguous")
    data class Ambiguous(val provider: ProviderId, val candidates: List<ProviderSearchResult>) : EpisodeLookupResult()

    @Serializable
    @SerialName("notFound")
    data object NotFound : EpisodeLookupResult()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : EpisodeLookupResult()
}

@Serializable
data class ProviderSearchResult(
    val title: String,
    val slug: String,
    val posterUrl: String,
    val anilistId: Int? = null
)
