package com.example.aniflow.data.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.example.aniflow.data.MappingEvidence

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
value class ProviderSeriesId(val value: String)

@JvmInline
@Serializable
value class ProviderEpisodeId(val value: String)

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
    val hlsHeights: List<Int> = emptyList(),
    val expiryTimeMs: Long? = null
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
    InvalidatedMapping,
    TemporarilyUnavailable,
    ContractChanged,
    IdentityMismatch
}

sealed interface ProviderStatus {
    object Selected : ProviderStatus
    object Loading : ProviderStatus
    object Available : ProviderStatus
    object CircuitOpen : ProviderStatus
    object Unconfigured : ProviderStatus
    object IdentityMismatch : ProviderStatus
}

@Serializable
sealed interface PlaybackResult {
    @Serializable
    @SerialName("native_sources")
    data class NativeSources(
        val provider: ProviderId,
        val sources: List<SourceEndpoint>,
        val subtitles: List<SubtitleTrack> = emptyList()
    ) : PlaybackResult

    @Serializable
    @SerialName("embed_only")
    data class EmbedOnly(
        val provider: ProviderId,
        val embedUrl: String
    ) : PlaybackResult

    @Serializable
    @SerialName("not_found")
    data class NotFound(val provider: ProviderId) : PlaybackResult

    @Serializable
    @SerialName("rate_limited")
    data class RateLimited(val provider: ProviderId, val retryAfterDurationMs: Long? = null) : PlaybackResult

    @Serializable
    @SerialName("temporarily_unavailable")
    data class TemporarilyUnavailable(val provider: ProviderId) : PlaybackResult

    @Serializable
    @SerialName("contract_changed")
    data class ContractChanged(val provider: ProviderId) : PlaybackResult

    @Serializable
    @SerialName("identity_mismatch")
    data class IdentityMismatch(val provider: ProviderId) : PlaybackResult

    @Serializable
    @SerialName("error")
    data class Error(val provider: ProviderId, val errorType: PlaybackErrorType, val message: String) : PlaybackResult
}

@Serializable
sealed interface EpisodeLookupResult {
    @Serializable
    @SerialName("matched")
    data class Matched(val provider: ProviderId, val seriesId: ProviderSeriesId, val episodes: List<Episode>) : EpisodeLookupResult

    @Serializable
    @SerialName("ambiguous")
    data class Ambiguous(val candidates: List<ProviderSearchResult>) : EpisodeLookupResult

    @Serializable
    @SerialName("notFound")
    data object NotFound : EpisodeLookupResult

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : EpisodeLookupResult
}

@Serializable
sealed interface SeriesMatchResult {
    @Serializable
    @SerialName("matched")
    data class Matched(
        val provider: ProviderId,
        val seriesId: ProviderSeriesId,
        val confidence: Double,
        val evidence: MappingEvidence
    ) : SeriesMatchResult
    
    @Serializable
    @SerialName("ambiguous")
    data class Ambiguous(val candidates: List<ProviderSearchResult>) : SeriesMatchResult
    
    @Serializable
    @SerialName("notFound")
    data object NotFound : SeriesMatchResult
}

@Serializable
data class ProviderSearchResult(
    val provider: ProviderId,
    val title: String,
    val slug: String,
    val posterUrl: String,
    val anilistId: Int? = null
)

@Serializable
data class HlsVariant(
    val url: String,
    val height: Int?,
    val bandwidth: Long?,
    val codecs: String?,
    val frameRate: Float?
)
