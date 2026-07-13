package com.example.aniflow.data

import com.example.aniflow.data.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class MiruroProvider(private val client: HttpClient) : EpisodeProvider {
    override val id: ProviderId = ProviderId.MIRURO
    private val json = NetworkModule.json

    @Serializable
    private data class MegaPlaySourcesResponse(
        val sources: MegaPlaySource? = null,
        val tracks: List<MegaPlayTrack> = emptyList()
    )

    @Serializable
    private data class MegaPlaySource(
        val file: String
    )

    @Serializable
    private data class MegaPlayTrack(
        val file: String,
        val label: String,
        val kind: String
    )

    override suspend fun findSeries(identity: AnimeIdentity): SeriesMatchResult {
        return SeriesMatchResult.Matched(
            provider = id,
            seriesId = ProviderSeriesId(identity.anilistId.toString()),
            confidence = 1.0,
            evidence = MappingEvidence(
                providerTitle = identity.title,
                anilistTitle = identity.title,
                matchedBy = "EXACT_ID",
                confidenceScore = 1.0
            )
        )
    }

    override suspend fun getEpisodes(seriesId: ProviderSeriesId): EpisodeLookupResult {
        val count = 24
        val eps = (1..count).map { num ->
            Episode(
                id = num.toString(),
                name = "Episode $num",
                number = num
            )
        }
        return EpisodeLookupResult.Matched(
            provider = id,
            seriesId = seriesId,
            episodes = eps
        )
    }

    override suspend fun resolve(request: EpisodeRequest): PlaybackResult {
        return try {
            val embedPageUrl = "https://megaplay.buzz/stream/ani/${request.animeId}/${request.episodeNumber}/${request.audioType.name.lowercase()}"
            val response = client.get(embedPageUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Referer", "https://megaplay.buzz/")
            }
            if (response.status != HttpStatusCode.OK) {
                return PlaybackResult.Error(id, PlaybackErrorType.NoSources, "Embed page returned status: ${response.status}")
            }
            val html = response.bodyAsText()
            val dataIdMatch = Regex("""data-id="([^"]+)"""").find(html)
            val dataId = dataIdMatch?.groupValues?.get(1)
                ?: return PlaybackResult.Error(id, PlaybackErrorType.NoSources, "Could not find data-id in embed page")

            val sourcesUrl = "https://megaplay.buzz/stream/getSources?id=$dataId"
            val sourcesResponse = client.get(sourcesUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Referer", "https://megaplay.buzz/")
                header("X-Requested-With", "XMLHttpRequest")
            }
            if (sourcesResponse.status != HttpStatusCode.OK) {
                return PlaybackResult.Error(id, PlaybackErrorType.NoSources, "getSources returned status: ${sourcesResponse.status}")
            }

            val sourcesText = sourcesResponse.bodyAsText()
            val sourcesData = json.decodeFromString<MegaPlaySourcesResponse>(sourcesText)
            val m3u8Url = sourcesData.sources?.file
                ?: return PlaybackResult.Error(id, PlaybackErrorType.NoSources, "No video file URL found in getSources response")

            val endpoint = SourceEndpoint(
                id = "${id.name.lowercase()}_megaplay_${request.animeId}_${request.episodeNumber}",
                provider = id,
                server = ServerId("MegaPlay"),
                audioType = request.audioType,
                streamType = StreamType.HLS,
                url = m3u8Url,
                headers = mapOf(
                    "Referer" to "https://megaplay.buzz/",
                    "Origin" to "https://megaplay.buzz"
                ),
                qualityPolicy = QualityPolicy.Auto,
                episodeId = request.episodeId
            )

            PlaybackResult.NativeSources(
                provider = id,
                sources = listOf(endpoint),
                subtitles = sourcesData.tracks.map {
                    SubtitleTrack(
                        url = it.file,
                        lang = it.label,
                        label = it.label
                    )
                }
            )
        } catch (e: Exception) {
            PlaybackResult.Error(id, PlaybackErrorType.Network, "Failed to resolve MegaPlay stream: ${e.message}")
        }
    }
}
