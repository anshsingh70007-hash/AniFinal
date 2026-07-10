package com.example.aniflow.data

import com.example.aniflow.data.model.Episode
import com.example.aniflow.data.model.EpisodeSourcesResponse
import com.example.aniflow.data.model.StreamingSource
import com.example.aniflow.data.model.SubtitleTrack
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlinx.coroutines.*

@Serializable
data class ProviderSearchResult(
    val title: String,
    val slug: String,
    val posterUrl: String
)

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
    val coverImage: AniLightCoverImage? = null
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

class AniLightProvider(private val client: HttpClient) {
    private val json = NetworkModule.json
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val baseUrl = "https://api.anilight.live/api"

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
                        title = title,
                        slug = item.slug,
                        posterUrl = poster
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
            val response = client.get("$baseUrl/watch/$showId") {
                header("User-Agent", userAgent)
            }
            if (response.status == HttpStatusCode.OK) {
                val watchData = json.decodeFromString<AniLightWatchResponse>(response.bodyAsText())
                val animeId = watchData.id ?: 0
                watchData.episodes.map { ep ->
                    // Construct a custom episode ID that carries slug, animeId, and epNum
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
            kotlinx.coroutines.delay(currentDelay)
            currentDelay *= 2
        }
        return null
    }

    suspend fun getStreamUrl(episodeId: String): EpisodeSourcesResponse {
        if (!episodeId.startsWith("anilight:")) {
            return EpisodeSourcesResponse(sources = emptyList())
        }
        
        try {
            val parts = episodeId.substringAfter("anilight:").split("|")
            if (parts.size < 3) return EpisodeSourcesResponse(sources = emptyList())
            val slug = parts[0]
            val animeId = parts[1]
            val epNum = parts[2].toIntOrNull() ?: 1
            
            // 1. Fetch servers list by hitting watch details
            val servers = retryWithBackoff(retries = 3) {
                val response = client.get("$baseUrl/watch/$slug") {
                    header("User-Agent", userAgent)
                }
                if (response.status == HttpStatusCode.OK) {
                    json.decodeFromString<AniLightWatchResponse>(response.bodyAsText()).servers
                } else null
            }
            
            // Default servers to try if fetch fails or servers is null
            val subDefault = servers?.subProviders?.find { it.default }?.id
            val subServers = (listOfNotNull(subDefault) + (servers?.subProviders?.map { it.id } ?: listOf("light", "misa", "near", "raye", "rem", "ryu", "meg"))).distinct()
            
            val dubDefault = servers?.dubProviders?.find { it.default }?.id
            val dubServers = (listOfNotNull(dubDefault) + (servers?.dubProviders?.map { it.id } ?: listOf("light", "misa", "near", "raye", "ryu", "meg"))).distinct()
            
            val resolvedSources = java.util.Collections.synchronizedList(mutableListOf<StreamingSource>())
            val resolvedSubtitles = java.util.Collections.synchronizedList(mutableListOf<SubtitleTrack>())
            val customHeaders = java.util.Collections.synchronizedMap(mutableMapOf<String, String>())
            
            // Run sub and dub fetching in parallel
            coroutineScope {
                val subJob = launch {
                    try {
                        coroutineScope {
                            subServers.map { provId ->
                                launch {
                                    try {
                                        kotlinx.coroutines.withTimeoutOrNull(5000L) {
                                            val sourcesUrl = "$baseUrl/sources?id=$animeId&epNum=$epNum&type=sub&providerId=$provId"
                                            val res = retryWithBackoff(retries = 1, initialDelayMs = 300L) {
                                                val sourcesResponse = client.get(sourcesUrl) {
                                                    header("User-Agent", userAgent)
                                                }
                                                if (sourcesResponse.status == HttpStatusCode.OK) {
                                                    json.decodeFromString<AniLightSourcesResponse>(sourcesResponse.bodyAsText())
                                                } else null
                                            }
                                            if (res != null && res.sources.isNotEmpty()) {
                                                processSourcesResponse(res, "sub", provId, resolvedSources, resolvedSubtitles, customHeaders)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("AniLightProvider", "Failed sub provider '$provId'", e)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AniLightProvider", "Error in sub provider fetching", e)
                    }
                }

                val dubJob = launch {
                    try {
                        coroutineScope {
                            dubServers.map { provId ->
                                launch {
                                    try {
                                        kotlinx.coroutines.withTimeoutOrNull(5000L) {
                                            val sourcesUrl = "$baseUrl/sources?id=$animeId&epNum=$epNum&type=dub&providerId=$provId"
                                            val res = retryWithBackoff(retries = 1, initialDelayMs = 300L) {
                                                val sourcesResponse = client.get(sourcesUrl) {
                                                    header("User-Agent", userAgent)
                                                }
                                                if (sourcesResponse.status == HttpStatusCode.OK) {
                                                    json.decodeFromString<AniLightSourcesResponse>(sourcesResponse.bodyAsText())
                                                } else null
                                            }
                                            if (res != null && res.sources.isNotEmpty()) {
                                                processSourcesResponse(res, "dub", provId, resolvedSources, resolvedSubtitles, customHeaders)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("AniLightProvider", "Failed dub provider '$provId'", e)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AniLightProvider", "Error in dub provider fetching", e)
                    }
                }

                joinAll(subJob, dubJob)
            }
            
            if (resolvedSources.isNotEmpty()) {
                // Deduplicate: keep only ONE source per quality+type combo
                // Collect other provider URLs as backupUrls
                val deduped = resolvedSources
                    .groupBy { it.quality }  // Groups like "1080p (SUB)", "720p (DUB)", etc.
                    .map { (_, sourcesForQuality) ->
                        val primary = sourcesForQuality.first()
                        val backups = sourcesForQuality.drop(1).map { it.url }
                        primary.copy(backupUrls = backups)
                    }
                
                // Sort: SUB first, then by resolution descending
                val qualityOrder = mapOf("1080p" to 4, "720p" to 3, "480p" to 2, "360p" to 1, "Auto" to 0)
                val sorted = deduped.sortedWith(
                    compareByDescending<StreamingSource> { src ->
                        if (src.quality.contains("(SUB)")) 1 else 0
                    }.thenByDescending { src ->
                        qualityOrder.entries.firstOrNull { (k, _) -> src.quality.contains(k) }?.value ?: 0
                    }
                )
                
                return EpisodeSourcesResponse(
                    sources = sorted,
                    subtitles = resolvedSubtitles.distinctBy { it.url }.toList(),
                    headers = customHeaders.toMap()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AniLightProvider", "GetStreamUrl failed for episodeId '$episodeId'", e)
        }
        
        return EpisodeSourcesResponse(sources = emptyList())
    }

    private fun processSourcesResponse(
        res: AniLightSourcesResponse,
        type: String,
        provId: String,
        resolvedSources: MutableList<StreamingSource>,
        resolvedSubtitles: MutableList<SubtitleTrack>,
        customHeaders: MutableMap<String, String>
    ) {
        synchronized(resolvedSources) {
            // Map subtitles
            res.tracks.forEach { track ->
                val subtitleUrl = track.file ?: track.url ?: ""
                if (subtitleUrl.isNotEmpty()) {
                    val mappedSubUrl = mapSubtitlesUrl(subtitleUrl)
                    val lang = track.lang ?: track.label ?: "English"
                    val label = track.label ?: lang
                    val exists = resolvedSubtitles.any { it.url == mappedSubUrl }
                    if (!exists) {
                        resolvedSubtitles.add(SubtitleTrack(url = mappedSubUrl, lang = lang, label = label))
                    }
                }
            }

            // Find first subtitle URL in this response to extract the CDN domain
            val subTrackUrl = res.tracks.firstOrNull { (it.file ?: it.url ?: "").isNotEmpty() }?.let { it.file ?: it.url }
                ?: resolvedSubtitles.firstOrNull()?.url

            // Map sources
            res.sources.forEach { src ->
                val urls = extractUrls(src.url)
                val quality = src.quality ?: "Auto"
                val isM3U8 = urls.any { it.contains(".m3u8") } || src.type == "hls"

                urls.forEach { rawUrl ->
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
                    mappedUrls.forEach { finalUrl ->
                        val referrer = "https://anilight.live"
                        customHeaders["Referer"] = referrer
                        customHeaders["Origin"] = referrer

                        val cleanResolution = when {
                            quality.contains("1080") -> "1080p"
                            quality.contains("720") -> "720p"
                            quality.contains("480") -> "480p"
                            quality.contains("360") -> "360p"
                            else -> "Auto"
                        }

                        val isHlsStream = isM3U8 ||
                            finalUrl.contains(".m3u8", ignoreCase = true) ||
                            finalUrl.contains("index.txt", ignoreCase = true) ||
                            src.type.equals("hls", ignoreCase = true)

                        resolvedSources.add(
                            StreamingSource(
                                url = finalUrl,
                                quality = "$cleanResolution - ${provId.uppercase()} (${type.uppercase()})",
                                isM3U8 = isHlsStream,
                                headers = mapOf(
                                    "Referer" to referrer,
                                    "Origin" to referrer,
                                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                )
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

    // --- Dynamic URL Decryption and Mapping Routines matching anilight.live ---

    private fun decryptUrl(url: String, providerId: String): List<String> {
        if (providerId == "raye" && url.isNotEmpty()) {
            try {
                val key = "aproxy2026".toByteArray(Charsets.UTF_8)
                val plaintext = (url + "\u0000https://kwik.cx").toByteArray(Charsets.UTF_8)
                val ciphertext = ByteArray(plaintext.size)
                for (i in plaintext.indices) {
                    ciphertext[i] = (plaintext[i].toInt() xor key[i % key.size].toInt()).toByte()
                }
                // Base64-url safe encoding without padding
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
