package com.example.aniflow.data

import com.example.aniflow.data.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URLEncoder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Serializable
data class AniLightTitle(
    val english: String? = null,
    val romaji: String? = null,
    val native: String? = null
)

@Serializable
data class AniLightCoverImage(
    val large: String? = null,
    val extraLarge: String? = null
)

@Serializable
data class AniLightAnimeItem(
    val id: Int,
    val slug: String,
    val title: AniLightTitle,
    val coverImage: AniLightCoverImage? = null,
    val anilistId: Int? = null
)

@Serializable
data class AniLightWatchResponse(
    val id: Int? = null,
    val episodes: List<AniLightEpisode> = emptyList(),
    val servers: AniLightServers? = null
)

@Serializable
data class AniLightEpisode(
    val number: Int,
    val title: String? = null,
    val description: String? = null,
    val img: String? = null
)

@Serializable
data class AniLightServers(
    val dubProviders: List<AniLightServerItem> = emptyList(),
    val subProviders: List<AniLightServerItem> = emptyList()
)

@Serializable
data class AniLightServerItem(
    val id: String,
    val tip: String? = null,
    val default: Boolean = false
)

@Serializable
data class AniLightSourcesResponse(
    val sources: List<AniLightSource> = emptyList(),
    val tracks: List<AniLightTrack> = emptyList()
)

@Serializable
data class AniLightSource(
    val url: JsonElement,
    val quality: String? = null,
    val type: String? = null
)

@Serializable
data class AniLightTrack(
    val url: String? = null,
    val file: String? = null,
    val label: String? = null,
    val lang: String? = null,
    val kind: String? = null,
    val default: Boolean = false
)

class AniLightProvider(private val client: HttpClient) : EpisodeProvider {
    override val id: ProviderId = ProviderId.ANILIGHT
    
    private val json = NetworkModule.json
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val baseUrl = "https://api.anilight.live/api"

    private val KNOWN_SERVER_ORDER = listOf(
        ServerId("light"),
        ServerId("misa"),
        ServerId("near"),
        ServerId("raye"),
        ServerId("rem"),
        ServerId("ryu"),
        ServerId("meg")
    )

    // Watch cache map: slug -> Pair(WatchResponse, entry time)
    private val watchCache = mutableMapOf<String, Pair<AniLightWatchResponse, Long>>()
    private val CACHE_TTL_MS = 300_000L // 5 minutes

    private fun getProviderPriority(server: ServerId): Int {
        val index = KNOWN_SERVER_ORDER.indexOf(server)
        return if (index >= 0) {
            KNOWN_SERVER_ORDER.size - index
        } else {
            0
        }
    }

    private fun cleanTitleForSearch(title: String): String {
        return title
            .replace(Regex("(?i):.*"), "")
            .replace(Regex("(?i)Season\\s*\\d+"), "")
            .replace(Regex("(?i)Part\\s*\\d+"), "")
            .replace(Regex("(?i)TV"), "")
            .replace(Regex("(?i)Uncensored"), "")
            .replace(Regex("(?i)Specials?"), "")
            .trim()
    }

    private fun extractYear(title: String): Int? {
        val match = Regex("\\b(19|20)\\d{2}\\b").find(title)
        return match?.value?.toIntOrNull()
    }

    private fun extractSeasonNumber(title: String): Int? {
        val lower = title.lowercase()
        val match1 = Regex("season\\s*(\\d+)").find(lower)
        if (match1 != null) return match1.groupValues[1].toIntOrNull()
        val match2 = Regex("(\\d+)(st|nd|rd|th)\\s*season").find(lower)
        if (match2 != null) return match2.groupValues[1].toIntOrNull()
        val match3 = Regex("\\bs(\\d+)\\b").find(lower)
        if (match3 != null) return match3.groupValues[1].toIntOrNull()
        return null
    }

    private fun calculateSimilarity(titleA: String, titleB: String): Double {
        val cleanA = titleA.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split("\\s+".toRegex()).filter { it.isNotEmpty() }.toSet()
        val cleanB = titleB.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split("\\s+".toRegex()).filter { it.isNotEmpty() }.toSet()
        if (cleanA.isEmpty() && cleanB.isEmpty()) return 1.0
        if (cleanA.isEmpty() || cleanB.isEmpty()) return 0.0
        val intersect = cleanA.intersect(cleanB)
        return (2.0 * intersect.size) / (cleanA.size + cleanB.size)
    }

    private fun scoreCandidate(candidate: ProviderSearchResult, identity: AnimeIdentity): Double {
        if (candidate.anilistId != null && candidate.anilistId == identity.anilistId) {
            return 10.0 // Authoritative match
        }

        // Format validation
        val lowerCandidate = candidate.title.lowercase()
        val isMovieCandidate = lowerCandidate.contains("movie") || lowerCandidate.contains("film")
        val isMovieIdentity = identity.format?.uppercase() == "MOVIE"
        if (isMovieCandidate != isMovieIdentity) {
            return 0.0 // Format mismatch
        }

        // Season validation
        val identitySeason = extractSeasonNumber(identity.englishTitle ?: "") ?: extractSeasonNumber(identity.title)
        val candidateSeason = extractSeasonNumber(candidate.title)
        if (identitySeason != null && candidateSeason != null && identitySeason != candidateSeason) {
            return 0.0 // Season mismatch
        }

        val romajiScore = calculateSimilarity(candidate.title, identity.title)
        val englishScore = identity.englishTitle?.let { calculateSimilarity(candidate.title, it) } ?: 0.0
        val nativeScore = identity.nativeTitle?.let { calculateSimilarity(candidate.title, it) } ?: 0.0

        var bestScore = maxOf(romajiScore, englishScore, nativeScore)

        // Year validation
        val yearInCandidate = extractYear(candidate.title)
        if (identity.seasonYear != null && yearInCandidate != null) {
            if (identity.seasonYear != yearInCandidate) {
                bestScore *= 0.5 // Penalty for mismatching year
            }
        }

        return bestScore
    }

    override suspend fun findSeries(identity: AnimeIdentity): SeriesMatchResult {
        val titlesToTry = mutableListOf<String>()
        identity.englishTitle?.let { titlesToTry.add(it) }
        titlesToTry.add(identity.title)

        val searchTitles = mutableListOf<String>()
        for (t in titlesToTry) {
            if (!searchTitles.contains(t) && t.isNotEmpty()) {
                searchTitles.add(t)
            }
            val cleaned = cleanTitleForSearch(t)
            if (cleaned.isNotEmpty() && !searchTitles.contains(cleaned)) {
                searchTitles.add(cleaned)
            }
        }

        val allResults = mutableListOf<ProviderSearchResult>()
        coroutineScope {
            val jobs = searchTitles.map { searchTitle ->
                async {
                    search(searchTitle)
                }
            }
            allResults.addAll(jobs.awaitAll().flatten().distinctBy { it.slug })
        }

        if (allResults.isEmpty()) return SeriesMatchResult.NotFound

        val scored = allResults.map { it to scoreCandidate(it, identity) }
            .sortedByDescending { it.second }

        val exactIdMatch = scored.find { it.second >= 10.0 }?.first
        if (exactIdMatch != null) {
            return SeriesMatchResult.Matched(
                provider = id,
                seriesId = ProviderSeriesId(exactIdMatch.slug),
                confidence = 1.0,
                evidence = MappingEvidence(
                    providerTitle = exactIdMatch.title,
                    anilistTitle = identity.title,
                    matchedBy = "EXACT_ID",
                    confidenceScore = 1.0
                )
            )
        }

        val (bestCandidate, bestScore) = scored.first()
        if (bestScore < 0.50) {
            return SeriesMatchResult.NotFound
        }

        // Apply strict confidence threshold & ambiguity margin
        if (scored.size > 1) {
            val (secondCandidate, secondScore) = scored[1]
            if (bestScore < 0.85 || (bestScore - secondScore) < 0.15) {
                val ambiguousCandidates = scored.filter { it.second >= 0.50 }.map { it.first }
                return SeriesMatchResult.Ambiguous(ambiguousCandidates)
            }
        } else {
            if (bestScore < 0.85) {
                return SeriesMatchResult.Ambiguous(listOf(bestCandidate))
            }
        }

        return SeriesMatchResult.Matched(
            provider = id,
            seriesId = ProviderSeriesId(bestCandidate.slug),
            confidence = bestScore,
            evidence = MappingEvidence(
                providerTitle = bestCandidate.title,
                anilistTitle = identity.title,
                matchedBy = "HIGH_CONFIDENCE_SCORE",
                confidenceScore = bestScore
            )
        )
    }

    override suspend fun getEpisodes(seriesId: ProviderSeriesId): EpisodeLookupResult {
        val episodes = getEpisodeList(seriesId.value)
        return if (episodes.isNotEmpty()) {
            EpisodeLookupResult.Matched(id, seriesId, episodes)
        } else {
            EpisodeLookupResult.NotFound
        }
    }

    override suspend fun resolve(request: EpisodeRequest): PlaybackResult {
        val watchData = getWatchDataCached(request.seriesSlug)
            ?: return PlaybackResult.Error(id, PlaybackErrorType.Network, "Failed to load watch details.")

        val servers = watchData.servers
        val targetAudio = request.audioType

        val serversToTry = if (targetAudio == AudioType.SUB) {
            val defaultId = servers?.subProviders?.find { it.default }?.id
            (listOfNotNull(defaultId) + (servers?.subProviders?.map { it.id } ?: emptyList())).distinct()
        } else {
            val defaultId = servers?.dubProviders?.find { it.default }?.id
            (listOfNotNull(defaultId) + (servers?.dubProviders?.map { it.id } ?: emptyList())).distinct()
        }

        if (serversToTry.isEmpty()) {
            return PlaybackResult.Error(
                id,
                PlaybackErrorType.NoSources,
                "No servers available for audio type ${targetAudio.name}"
            )
        }

        val resolvedEndpoints = java.util.Collections.synchronizedList(mutableListOf<SourceEndpoint>())
        val resolvedSubtitles = java.util.Collections.synchronizedList(mutableListOf<SubtitleTrack>())

        val semaphore = Semaphore(4)

        supervisorScope {
            val jobs = serversToTry.map { provId ->
                async {
                    semaphore.withPermit {
                        try {
                            withTimeout(5000L) {
                                val sourcesUrl = "$baseUrl/sources?id=${watchData.id}&epNum=${request.episodeNumber}&type=${targetAudio.name.lowercase()}&providerId=$provId"
                                val response = client.get(sourcesUrl) {
                                    header("User-Agent", userAgent)
                                }
                                if (response.status == HttpStatusCode.OK) {
                                    val res = json.decodeFromString<AniLightSourcesResponse>(response.bodyAsText())
                                    if (res.sources.isNotEmpty()) {
                                        processSourcesResponse(res, targetAudio, provId, request.episodeId, resolvedEndpoints, resolvedSubtitles)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            android.util.Log.w("AniLightProvider", "Failed to resolve server $provId", e)
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        if (resolvedEndpoints.isEmpty()) {
            return PlaybackResult.Error(
                id,
                PlaybackErrorType.NoSources,
                "All resolved servers failed to return playable sources."
            )
        }

        // Sort deterministically: priority descending, then alphabetical server ID
        val sorted = resolvedEndpoints.sortedWith(
            compareByDescending<SourceEndpoint> { it.priority }
                .thenBy { it.server.value }
        )

        return PlaybackResult.NativeSources(
            provider = id,
            sources = sorted,
            subtitles = resolvedSubtitles.distinctBy { it.url }.toList()
        )
    }

    private suspend fun getWatchDataCached(slug: String): AniLightWatchResponse? {
        val now = System.currentTimeMillis()
        synchronized(watchCache) {
            val cached = watchCache[slug]
            if (cached != null && now - cached.second < CACHE_TTL_MS) {
                return cached.first
            }
        }

        val response = retryWithBackoff(retries = 3) {
            val res = client.get("$baseUrl/watch/$slug") {
                header("User-Agent", userAgent)
            }
            if (res.status == HttpStatusCode.OK) {
                json.decodeFromString<AniLightWatchResponse>(res.bodyAsText())
            } else null
        }

        if (response != null) {
            synchronized(watchCache) {
                watchCache[slug] = Pair(response, System.currentTimeMillis())
            }
        }
        return response
    }

    suspend fun search(query: String): List<ProviderSearchResult> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val response = client.get("$baseUrl/search?q=$encoded") {
                header("User-Agent", userAgent)
            }
            if (response.status == HttpStatusCode.OK) {
                val list = json.decodeFromString<List<AniLightAnimeItem>>(response.bodyAsText())
                list.map { item ->
                    val title = item.title.english ?: item.title.romaji ?: item.title.native ?: "Unknown Title"
                    val poster = item.coverImage?.large ?: item.coverImage?.extraLarge ?: ""
                    ProviderSearchResult(
                        provider = id,
                        title = title,
                        slug = item.slug,
                        posterUrl = poster,
                        anilistId = item.anilistId
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("AniLightProvider", "Search failed for '$query'", e)
            emptyList()
        }
    }

    suspend fun getEpisodeList(showId: String): List<Episode> {
        return try {
            val watchData = getWatchDataCached(showId)
            if (watchData != null) {
                val animeId = watchData.id ?: 0
                watchData.episodes.map { ep ->
                    val episodeId = "anilight:$showId|$animeId|${ep.number}"
                    Episode(
                        id = episodeId,
                        name = ep.title ?: "Episode ${ep.number}",
                        number = ep.number,
                        description = ep.description ?: "",
                        thumbnail = ep.img ?: ""
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("AniLightProvider", "GetEpisodeList failed for slug '$showId'", e)
            emptyList()
        }
    }

    private suspend fun <T> retryWithBackoff(retries: Int = 3, initialDelayMs: Long = 1000L, block: suspend () -> T?): T? {
        var currentDelay = initialDelayMs
        for (i in 0 until retries) {
            try {
                val res = block()
                if (res != null) return res
            } catch (e: Exception) {
                if (i == retries - 1) throw e
            }
            delay(currentDelay)
            currentDelay *= 2
        }
        return null
    }

    private suspend fun processSourcesResponse(
        res: AniLightSourcesResponse,
        audioType: AudioType,
        provId: String,
        episodeId: String,
        resolvedSources: MutableList<SourceEndpoint>,
        resolvedSubtitles: MutableList<SubtitleTrack>
    ) {
        // Map subtitles
        res.tracks.forEach { track ->
            val subtitleUrl = track.file ?: track.url ?: ""
            if (subtitleUrl.isNotEmpty()) {
                val mappedSubUrl = mapSubtitlesUrl(subtitleUrl)
                val lang = track.lang ?: track.label ?: "English"
                val label = track.label ?: lang
                synchronized(resolvedSubtitles) {
                    if (resolvedSubtitles.none { it.url == mappedSubUrl }) {
                        resolvedSubtitles.add(SubtitleTrack(url = mappedSubUrl, lang = lang, label = label))
                    }
                }
            }
        }

        val subTrackUrl = res.tracks.firstOrNull { (it.file ?: it.url ?: "").isNotEmpty() }?.let { it.file ?: it.url }

        // Map sources
        for (src in res.sources) {
            val urls = extractUrls(src.url)
            val quality = src.quality ?: "Auto"
            val isM3U8 = urls.any { it.contains(".m3u8") } || src.type == "hls"

            for (rawUrl in urls) {
                val mappedUrls = if (rawUrl.contains("/cachesub/")) {
                    val folder = rawUrl.substringAfter("/cachesub/").substringBefore("/")
                    if (folder.isNotEmpty() && folder != rawUrl) {
                        val rawHost = try { android.net.Uri.parse(rawUrl).host } catch (e: Exception) { null }
                        val subHost = subTrackUrl?.let { try { android.net.Uri.parse(it).host } catch (e: Exception) { null } }
                        val finalHost = rawHost ?: subHost ?: "ani10.nukitashi.top"
                        listOf("https://$finalHost/$folder/index.m3u8")
                    } else {
                        decryptUrl(rawUrl, provId)
                    }
                } else {
                    decryptUrl(rawUrl, provId)
                }
                for (finalUrl in mappedUrls) {
                    val referrer = "https://anilight.live"
                    
                    val cleanResolution = when {
                        quality.contains("1080") -> 1080
                        quality.contains("720") -> 720
                        quality.contains("480") -> 480
                        quality.contains("360") -> 360
                        else -> -1
                    }

                    val policy = if (cleanResolution > 0) {
                        QualityPolicy.FixedHeight(cleanResolution)
                    } else if (quality.contains("Auto", ignoreCase = true) || quality.contains("Adaptive", ignoreCase = true)) {
                        QualityPolicy.Auto
                    } else {
                        QualityPolicy.MaxAvailable
                    }

                    val isHlsStream = isM3U8 ||
                        finalUrl.contains(".m3u8", ignoreCase = true) ||
                        finalUrl.contains("index.txt", ignoreCase = true) ||
                        src.type.equals("hls", ignoreCase = true)

                    val streamType = if (isHlsStream) StreamType.HLS else StreamType.PROGRESSIVE
                    val serverId = ServerId(provId.lowercase())

                    val headers = mapOf(
                        "Referer" to referrer,
                        "Origin" to referrer,
                        "User-Agent" to userAgent
                    )

                    val hlsHeights = if (isHlsStream && policy is QualityPolicy.Auto) {
                        HlsManifestNormalizer.normalize(client, finalUrl, headers)
                            .mapNotNull { it.height }
                            .distinct()
                            .sortedDescending()
                    } else {
                        emptyList()
                    }

                    // Deterministic composite key: provider/episodeId/server/audioType/role
                    val endpointId = "${id.name}_${episodeId}_${serverId.value}_${audioType.name}".lowercase()

                    synchronized(resolvedSources) {
                        resolvedSources.add(
                            SourceEndpoint(
                                id = endpointId,
                                provider = id,
                                server = serverId,
                                audioType = audioType,
                                streamType = streamType,
                                url = finalUrl,
                                headers = headers,
                                priority = getProviderPriority(serverId),
                                qualityPolicy = policy,
                                declaredResolution = if (cleanResolution > 0) cleanResolution else null,
                                episodeId = episodeId,
                                hlsHeights = hlsHeights
                            )
                        )
                    }
                }
            }
        }
    }

    private fun extractUrls(element: JsonElement): List<String> {
        return when (element) {
            is JsonArray -> element.mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonPrimitive -> listOfNotNull(element.contentOrNull)
            else -> emptyList()
        }
    }

    private fun decryptUrl(url: String, providerId: String): List<String> {
        if (providerId == "raye" && url.isNotEmpty()) {
            try {
                val key = "aproxy2026".toByteArray(Charsets.UTF_8)
                val plaintext = (url + "\u0000https://kwik.cx").toByteArray(Charsets.UTF_8)
                val ciphertext = ByteArray(plaintext.size)
                for (i in plaintext.indices) {
                    ciphertext[i] = (plaintext[i].toInt() xor key[i % key.size].toInt()).toByte()
                }
                val b64 = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE).trimEnd('=')
                val m = "https://cdn.animex.su/stream/$b64/index.txt"
                val finalUrl = "$baseUrl/lb/raye/proxy?url=${URLEncoder.encode(m, "UTF-8")}"
                return listOf(finalUrl)
            } catch (e: Exception) {
                android.util.Log.e("AniLightProvider", "Raye decryption failed", e)
            }
        }

        if (providerId == "ryu" && url.isNotEmpty()) {
            return listOf("$baseUrl/proxy/ryu?url=${URLEncoder.encode(url, "UTF-8")}")
        }

        return listOf(mapProxyUrl(url, providerId))
    }

    private fun mapProxyUrl(url: String, providerId: String): String {
        if (url.startsWith("/lb/") || url.contains("/proxy")) {
            return if (url.startsWith("/")) "$baseUrl$url" else url
        }

        val encoded = URLEncoder.encode(url, "UTF-8")
        return when (providerId) {
            "near" -> "$baseUrl/lb/near/proxy?url=$encoded"
            "misa", "misora" -> "$baseUrl/lb/misa/proxy?url=$encoded"
            "raye" -> "$baseUrl/lb/raye/proxy?url=$encoded"
            "ryu" -> "$baseUrl/proxy/ryu?url=$encoded"
            else -> "$baseUrl/proxy?url=$encoded"
        }
    }

    private fun mapSubtitlesUrl(url: String): String {
        if (url.startsWith("/lb/") || url.contains("/proxy")) {
            return if (url.startsWith("/")) "$baseUrl$url" else url
        }
        val encoded = URLEncoder.encode(url, "UTF-8")
        return "$baseUrl/proxy/captions?url=$encoded"
    }

    suspend fun getSchedule(): List<AniLightScheduleEntry> {
        return try {
            val response = client.get("$baseUrl/schedule") {
                header("User-Agent", userAgent)
            }
            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<List<AniLightScheduleEntry>>(response.bodyAsText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("AniLightProvider", "Failed to fetch schedule", e)
            emptyList()
        }
    }
}

@Serializable
data class AniLightScheduleEntry(
    val id: Int,
    val episode: Int,
    val airingAt: Long,
    val anime: AniLightScheduleAnime
)

@Serializable
data class AniLightScheduleAnime(
    val id: Int,
    val slug: String,
    val anilistId: Int? = null,
    val title: AniLightTitle,
    val coverImage: AniLightCoverImage? = null,
    val status: String? = null,
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    val episodes: Int? = null,
    val nextAiringEpisode: AniLightNextAiringEpisode? = null
)

@Serializable
data class AniLightNextAiringEpisode(
    val episode: Int,
    val airingAt: Long
)
