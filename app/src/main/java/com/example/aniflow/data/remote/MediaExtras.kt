package com.example.aniflow.data.remote

import com.example.aniflow.data.NetworkModule
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Second source for the two things AniList is weakest at: trailers and wide artwork.
 *
 * Kitsu maps AniList ids to its own entries, which carry their own YouTube trailer id (often a
 * different upload, so it still plays when AniList's is missing or embed-blocked) and a wide
 * cover (3360x800, vs AniList's 1900x400 banner). Fetched lazily — only when AniList's own
 * trailer or banner is missing or fails — one request per title per process, deduped in flight.
 *
 * YouTube thumbnails were evaluated as hero art and rejected: maxresdefault is nearly always a
 * custom thumbnail with "本PV"/"Official Trailer" text, and the auto frames carry subtitles.
 */
object MediaExtras {
    data class Extras(val youtubeId: String?, val cover: String?)

    /** YouTube ids are 11 chars of [A-Za-z0-9_-]; anything else never reaches a WebView. */
    val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{6,20}$")

    private val cache = ConcurrentHashMap<Int, Extras>()
    private val inFlight = ConcurrentHashMap<Int, Deferred<Extras?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cached(anilistId: Int): Extras? = cache[anilistId]

    suspend fun get(anilistId: Int): Extras? {
        cache[anilistId]?.let { return it }
        val job = inFlight.getOrPut(anilistId) {
            scope.async { fetch(anilistId).also { inFlight.remove(anilistId) } }
        }
        return try {
            job.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetch(id: Int): Extras? = withTimeoutOrNull(9_000L) {
        try {
            val url = "https://kitsu.app/api/edge/mappings?filter[externalSite]=anilist/anime" +
                "&filter[externalId]=$id&include=item&fields[anime]=youtubeVideoId,coverImage"
            val response = NetworkModule.client.get(url) { header("Accept", "application/vnd.api+json") }
            if (response.status.value != 200) return@withTimeoutOrNull null
            val body = response.bodyAsText()
            if (body.length > 256_000) return@withTimeoutOrNull null
            val root = NetworkModule.json.parseToJsonElement(body).jsonObject
            val anime = (root["included"] as? JsonArray)
                ?.firstOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "anime" } as? JsonObject
            val attrs = anime?.get("attributes") as? JsonObject
            val yt = attrs?.get("youtubeVideoId")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { YOUTUBE_ID.matches(it) }
            val cover = (attrs?.get("coverImage") as? JsonObject)?.let { c ->
                listOf("large", "original").firstNotNullOfOrNull { k -> c[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://") } }
            }
            // A definitive answer (including "Kitsu has nothing") is cached; network errors are not.
            Extras(yt, cover).also { cache[id] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("MediaExtras", "kitsu $id: ${e.message}")
            null
        }
    }
}
