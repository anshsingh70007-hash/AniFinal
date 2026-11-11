package com.example.aniflow.data.remote

import com.example.aniflow.data.model.FranchiseEntry
import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.SearchPage
import com.example.aniflow.data.NetworkModule
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.async
import java.util.Calendar
import java.util.LinkedHashMap

/** Process-wide AniList circuit breaker. Open = skip AniList entirely and use fallbacks. */
object AniListHealth {
    @Volatile private var openUntilMs = 0L
    fun isAvailable(): Boolean = System.currentTimeMillis() >= openUntilMs
    fun trip(forMs: Long, reason: String) {
        openUntilMs = System.currentTimeMillis() + forMs
        android.util.Log.w("AniListApi", "AniList circuit OPEN for ${forMs / 1000}s ($reason)")
    }
    fun recordSuccess() { openUntilMs = 0L }
}

private val requestScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

class AniListApi(private val client: HttpClient) {
    private val json = NetworkModule.json

    private val cache = object : LinkedHashMap<String, Pair<Long, JsonObject>>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, JsonObject>>?): Boolean {
            return size > 50
        }
    }
    private val CACHE_TTL_MS = 5 * 60 * 1000L
    private val inFlightRequests = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<JsonObject?>>()

    private val MEDIA_FIELDS = """
        id
        title { romaji english native }
        coverImage { extraLarge large }
        bannerImage
        description
        episodes
        format
        averageScore
        genres
        status
        season
        seasonYear
        studios(isMain: true) { nodes { name } }
        trailer { id site thumbnail }
        nextAiringEpisode { episode airingAt }
        idMal
    """.trimIndent()

    private suspend fun queryAniList(query: String, variables: JsonObject): JsonObject? {
        val cacheKey = "$query|$variables"
        synchronized(cache) {
            val cached = cache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
                return cached.second
            }
        }
        // B24: the shared request used to run in the *first caller's* scope, so cancelling that
        // caller (leaving a screen) cancelled everyone awaiting the same query. It now runs in a
        // process scope; callers only cancel their own await.
        val deferred = synchronized(inFlightRequests) {
            inFlightRequests.getOrPut(cacheKey) {
                requestScope.async {
                    val result = queryAniListFromApi(query, variables)
                    if (result != null) {
                        synchronized(cache) { cache[cacheKey] = System.currentTimeMillis() to result }
                    }
                    synchronized(inFlightRequests) { inFlightRequests.remove(cacheKey) }
                    result
                }
            }
        }
        return deferred.await()
    }

    /**
     * Fail fast. When AniList is down or rate-limiting, the old loop could block a screen for
     * ~95 s (3 x 30 s socket timeouts) or ~180 s (3 x 60 s Retry-After sleeps). Now: at most two
     * 8 s attempts, then [AniListHealth] trips and every AniList call returns null instantly
     * until the cool-down ends — callers fall straight through to disk cache / AniLight / Kitsu.
     */
    private suspend fun queryAniListFromApi(query: String, variables: JsonObject): JsonObject? {
        if (!AniListHealth.isAvailable()) return null
        for (attempt in 1..2) {
            val outcome: Any? = try {
                kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    val response: HttpResponse = client.post("https://graphql.anilist.co") {
                        contentType(ContentType.Application.Json)
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        setBody(buildJsonObject {
                            put("query", query)
                            put("variables", variables)
                        })
                    }
                    when {
                        response.status == HttpStatusCode.OK ->
                            json.parseToJsonElement(response.bodyAsText()).jsonObject
                        response.status.value == 429 -> {
                            val retryAfterS = response.headers["Retry-After"]?.toLongOrNull() ?: 30L
                            AniListHealth.trip(retryAfterS.coerceIn(5L, 90L) * 1000L, "429")
                            null
                        }
                        response.status.value >= 500 -> response.status.value
                        else -> {
                            android.util.Log.e("AniListApi", "Failed with status: ${response.status}")
                            null
                        }
                    }
                } ?: "timeout"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AniListApi", "Attempt $attempt failed: ${e.message}")
                "error"
            }
            when (outcome) {
                is JsonObject -> { AniListHealth.recordSuccess(); return outcome }
                null -> return null                                  // 429 / 4xx: no point retrying
                else -> if (attempt == 1) kotlinx.coroutines.delay(700L)   // 5xx, timeout, I/O
            }
        }
        AniListHealth.trip(45_000L, "unreachable")
        return null
    }

    suspend fun getTrending(page: Int = 1, perPage: Int = 15): List<Anime> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
        }
        val response = queryAniList(query, vars)
        return parseAniListMedia(response)
    }

    /**
     * Episodes that actually went out in the last [hours], newest first, one card per show.
     * That is what "fresh episodes" means. anilight.live's recentlyAddedEpisodes is its catalogue
     * backfill (Shaman King 2001 and Squid Girl 2010 showed up the week they were uploaded).
     * The long tail nobody follows (popularity < [minPopularity]) is left out.
     */
    suspend fun getFreshEpisodes(hours: Int = 72, minPopularity: Int = 2500, maxPages: Int = 3): List<Anime> {
        val now = System.currentTimeMillis() / 1000
        val query = """
            query (${'$'}page: Int, ${'$'}from: Int, ${'$'}to: Int) {
              Page (page: ${'$'}page, perPage: 50) {
                pageInfo { hasNextPage }
                airingSchedules (airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME_DESC) {
                  media {
                    $MEDIA_FIELDS
                    isAdult
                    popularity
                  }
                }
              }
            }
        """.trimIndent()
        val out = LinkedHashMap<Int, Anime>()
        for (page in 1..maxPages) {
            val vars = buildJsonObject {
                put("page", page)
                put("from", JsonPrimitive(now - hours * 3600L))
                put("to", JsonPrimitive(now))
            }
            val pageObj = queryAniList(query, vars)?.get("data")?.jsonObject?.get("Page")?.jsonObject ?: break
            pageObj["airingSchedules"]?.jsonArray?.forEach { el ->
                val media = el.jsonObject["media"]?.jsonObject ?: return@forEach
                if (media["isAdult"]?.jsonPrimitive?.booleanOrNull == true) return@forEach
                if (media["format"]?.jsonPrimitive?.contentOrNull == "MUSIC") return@forEach
                if ((media["popularity"]?.jsonPrimitive?.intOrNull ?: 0) < minPopularity) return@forEach
                val id = media["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
                if (id !in out) runCatching { mapMediaObjToAnime(media) }.getOrNull()?.let { out[id] = it }
            }
            if (pageObj["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.booleanOrNull != true) break
        }
        return out.values.toList()
    }

    /**
     * What people are watching in [genre] right now: AniList's trending score (community activity
     * over the last few days), limited to shows from the last three years with a real audience.
     * The all-time popularity sort just repeated "Popular all time" (Attack on Titan, Demon Slayer…).
     */
    suspend fun getTrendingInGenre(genre: String, perPage: Int = 20): List<Anime> {
        val since = (Calendar.getInstance().get(Calendar.YEAR) - 3) * 10000
        val query = """
            query (${'$'}genre: String, ${'$'}perPage: Int, ${'$'}since: FuzzyDateInt) {
              Page (page: 1, perPage: ${'$'}perPage) {
                media (type: ANIME, genre_in: [${'$'}genre], sort: [TRENDING_DESC, POPULARITY_DESC], startDate_greater: ${'$'}since, popularity_greater: 8000, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("genre", genre)
            put("perPage", perPage)
            put("since", since)
        }
        return parseAniListMedia(queryAniList(query, vars))
    }

    /**
     * The direct PREQUEL of [id] in the same format, as (MAL id, episode count). Streaming sites
     * often file a split-cour "Part 2" as episodes 13+ of Part 1 (Attack on Titan Season 3 Part 2
     * is MAL 35760 episodes 13-22 on MegaPlay). Null when there is none, or it lacks either value.
     */
    suspend fun getSamePrequel(id: Int): Pair<Int, Int>? = runCatching {
        val query = """
            query (${'$'}id: Int) {
              Media (id: ${'$'}id, type: ANIME) {
                format
                relations { edges { relationType(version: 2) node { type format idMal episodes } } }
              }
            }
        """.trimIndent()
        val media = queryAniList(query, buildJsonObject { put("id", id) })
            ?.get("data")?.jsonObject?.get("Media")?.jsonObject ?: return@runCatching null
        val format = media["format"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
        media["relations"]?.jsonObject?.get("edges")?.jsonArray?.firstNotNullOfOrNull { e ->
            val edge = e.jsonObject
            val n = edge["node"]?.jsonObject
            if (edge["relationType"]?.jsonPrimitive?.contentOrNull != "PREQUEL" || n == null ||
                n["type"]?.jsonPrimitive?.contentOrNull != "ANIME" || n["format"]?.jsonPrimitive?.contentOrNull != format
            ) return@firstNotNullOfOrNull null
            val mal = n["idMal"]?.jsonPrimitive?.intOrNull ?: return@firstNotNullOfOrNull null
            val eps = n["episodes"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return@firstNotNullOfOrNull null
            mal to eps
        }
    }.getOrNull()

    suspend fun getPopular(page: Int = 1, perPage: Int = 15): List<Anime> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, sort: POPULARITY_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
        }
        val response = queryAniList(query, vars)
        return parseAniListMedia(response)
    }

    suspend fun getSeasonal(page: Int = 1, perPage: Int = 15): List<Anime> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        // Broadcast cours (Jan/Apr/Jul/Oct), which is how AniList files a show's season. The old
        // Dec->WINTER mapping asked for WINTER of the *current* year every December, so "Airing
        // This Season" showed the previous January's lineup for a whole month.
        val season = currentCour(month)
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}season: MediaSeason, ${'$'}seasonYear: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, sort: POPULARITY_DESC, season: ${'$'}season, seasonYear: ${'$'}seasonYear, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            put("season", season)
            put("seasonYear", year)
        }
        val response = queryAniList(query, vars)
        return parseAniListMedia(response)
    }

    suspend fun getAiringToday(): List<AiringAnime> {
        // The viewer's own calendar day. `now - now % 86400` was the UTC day, which in India
        // runs 05:30 to 05:30 and dropped the late-night simulcasts people care about most.
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStart = cal.timeInMillis / 1000
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return getAiringRange(dayStart, cal.timeInMillis / 1000, maxPages = 2)
    }

    /**
     * Every episode airing in [fromSec, toSec), soonest first, paged 50 at a time. Adult titles,
     * music videos and the long tail nobody watches (popularity < 600) are dropped so the result
     * reads like a simulcast list rather than a database dump.
     */
    suspend fun getAiringRange(fromSec: Long, toSec: Long, maxPages: Int = 6): List<AiringAnime> {
        val query = """
            query (${'$'}page: Int, ${'$'}airingAtGreater: Int, ${'$'}airingAtLesser: Int) {
              Page (page: ${'$'}page, perPage: 50) {
                pageInfo { hasNextPage }
                airingSchedules (airingAt_greater: ${'$'}airingAtGreater, airingAt_lesser: ${'$'}airingAtLesser, sort: TIME) {
                  airingAt
                  episode
                  media {
                    id
                    isAdult
                    format
                    popularity
                    title { romaji english }
                    coverImage { large }
                  }
                }
              }
            }
        """.trimIndent()
        val airingList = mutableListOf<AiringAnime>()
        for (page in 1..maxPages) {
            val vars = buildJsonObject {
                put("page", page)
                put("airingAtGreater", JsonPrimitive(fromSec))
                put("airingAtLesser", JsonPrimitive(toSec))
            }
            val pageObj = queryAniList(query, vars)?.get("data")?.jsonObject?.get("Page")?.jsonObject ?: break
            try {
                pageObj["airingSchedules"]?.jsonArray?.forEach { element ->
                    val obj = element.jsonObject
                    val media = obj["media"]?.jsonObject ?: return@forEach
                    if (media["isAdult"]?.jsonPrimitive?.booleanOrNull == true) return@forEach
                    if (media["format"]?.jsonPrimitive?.contentOrNull == "MUSIC") return@forEach
                    if (maxPages > 1 && (media["popularity"]?.jsonPrimitive?.intOrNull ?: 0) < 600) return@forEach
                    airingList.add(
                        AiringAnime(
                            mediaId = media["id"]?.jsonPrimitive?.int ?: 0,
                            title = media["title"]?.jsonObject?.get("english")?.jsonPrimitive?.contentOrNull
                                ?: media["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.content ?: "Airing Anime",
                            coverImageUrl = media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content ?: "",
                            airingAt = obj["airingAt"]?.jsonPrimitive?.long ?: 0L,
                            episode = obj["episode"]?.jsonPrimitive?.int ?: 0
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (pageObj["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.booleanOrNull != true) break
        }
        return airingList
    }

    suspend fun getTopRated(page: Int = 1, perPage: Int = 15): List<Anime> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, sort: SCORE_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
        }
        val response = queryAniList(query, vars)
        return parseAniListMedia(response)
    }

    suspend fun getUpcoming(page: Int = 1, perPage: Int = 15): List<Anime> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}before: FuzzyDateInt) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, status: NOT_YET_RELEASED, startDate_lesser: ${'$'}before, sort: POPULARITY_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        // Within the next ~12 months, recomputed per request: no announced-for-2029 titles.
        val cal = Calendar.getInstance()
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            put("before", (cal.get(Calendar.YEAR) + 1) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + 31)
        }
        val response = queryAniList(query, vars)
        return parseAniListMedia(response)
    }

    /**
     * The highest-rated titles of the current year (of last year until April, while the new year
     * has too few scored shows). The year comes from the clock on every call, so the row rolls
     * over by itself. A popularity floor keeps 40-vote outliers out.
     */
    suspend fun getBestOfYear(perPage: Int = 20): Pair<Int, List<Anime>> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR) - if (cal.get(Calendar.MONTH) < Calendar.APRIL) 1 else 0
        val query = """
            query (${'$'}perPage: Int, ${'$'}year: Int) {
              Page (page: 1, perPage: ${'$'}perPage) {
                media (type: ANIME, seasonYear: ${'$'}year, format_in: [TV, ONA, MOVIE], popularity_greater: 8000, sort: SCORE_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("perPage", perPage)
            put("year", year)
        }
        return year to parseAniListMedia(queryAniList(query, vars))
    }

    suspend fun getRecentlyUpdated(page: Int = 1, perPage: Int = 15): List<Anime> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, sort: UPDATED_AT_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
        }
        val response = queryAniList(query, vars)
        return parseAniListMedia(response)
    }

    suspend fun getAnimeByGenre(genre: String, page: Int = 1, perPage: Int = 15): List<Anime> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}genre: String) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, genre_in: [${'$'}genre], sort: POPULARITY_DESC, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            put("genre", genre)
        }
        val response = queryAniList(query, vars)
        return parseAniListMedia(response)
    }

    suspend fun searchAnime(query: String, page: Int = 1, perPage: Int = 50): SearchPage {
        val graphQuery = """
            query (${'$'}query: String, ${'$'}page: Int, ${'$'}perPage: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage currentPage }
                media (search: ${'$'}query, type: ANIME, isAdult: false) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("query", query)
            put("page", page)
            put("perPage", perPage)
        }
        val response = queryAniList(graphQuery, vars)
        val animeList = parseAniListMedia(response)
        var hasNextPage = false
        var currentPage = page
        try {
            response?.get("data")?.jsonObject?.get("Page")?.jsonObject?.get("pageInfo")?.jsonObject?.let { info ->
                hasNextPage = info["hasNextPage"]?.jsonPrimitive?.boolean ?: false
                currentPage = info["currentPage"]?.jsonPrimitive?.int ?: page
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return SearchPage(results = animeList, hasNextPage = hasNextPage, currentPage = currentPage)
    }

    suspend fun getAnimeDetail(id: Int): Anime? {
        val query = """
            query (${'$'}id: Int) {
              Media (id: ${'$'}id) {
                $MEDIA_FIELDS
                recommendations(sort: RATING_DESC, perPage: 10) {
                  nodes {
                    mediaRecommendation {
                      id
                      title { romaji english }
                      coverImage { extraLarge large }
                      bannerImage
                      averageScore
                      episodes
                      genres
                      status
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("id", id)
        }
        val response = queryAniList(query, vars)
        return try {
            response?.get("data")?.jsonObject?.get("Media")?.jsonObject?.let { mapMediaObjToAnime(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseAniListMedia(response: JsonObject?): List<Anime> {
        val animeList = mutableListOf<Anime>()
        try {
            response?.get("data")?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray?.forEach { element ->
                try {
                    animeList.add(mapMediaObjToAnime(element.jsonObject))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return animeList
    }

    private fun mapMediaObjToAnime(mediaObj: JsonObject): Anime {
        val titleObj = mediaObj["title"] as? JsonObject
        val coverObj = mediaObj["coverImage"] as? JsonObject
        val genresList = (mediaObj["genres"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
        val studiosList = (mediaObj["studios"] as? JsonObject)?.get("nodes") as? JsonArray
        val studioName = if (studiosList != null && studiosList.isNotEmpty()) {
            (studiosList[0] as? JsonObject)?.get("name")?.jsonPrimitive?.content
        } else null

        val trailerObj = mediaObj["trailer"] as? JsonObject
        val trailerSite = trailerObj?.get("site")?.jsonPrimitive?.contentOrNull
        val trailerId = trailerObj?.get("id")?.jsonPrimitive?.contentOrNull
        val trailerUrl = if (trailerSite == "youtube" && trailerId != null) {
            "https://www.youtube-nocookie.com/embed/$trailerId?autoplay=1&mute=1&controls=0&showinfo=0&loop=1&playlist=$trailerId&modestbranding=1&rel=0&disablekb=1&origin=https://www.youtube-nocookie.com"
        } else if (trailerSite == "dailymotion" && trailerId != null) {
            "https://www.dailymotion.com/embed/video/$trailerId?autoplay=1&mute=1&controls=0&queue-enable=0"
        } else null

        val recommendationsList = mutableListOf<Anime>()
        try {
            val recsObj = mediaObj["recommendations"] as? JsonObject
            recsObj?.get("nodes") as? JsonArray
            (recsObj?.get("nodes") as? JsonArray)?.forEach { nodeElement ->
                val recMedia = (nodeElement as? JsonObject)?.get("mediaRecommendation") as? JsonObject
                if (recMedia != null) {
                    recommendationsList.add(mapMediaObjToAnime(recMedia))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val format = mediaObj["format"]?.jsonPrimitive?.contentOrNull

        val nextAiringObj = mediaObj["nextAiringEpisode"] as? JsonObject
        val nextAiringEp = nextAiringObj?.get("episode")?.jsonPrimitive?.intOrNull
        val nextAiringAt = nextAiringObj?.get("airingAt")?.jsonPrimitive?.longOrNull

        return Anime(
            id = mediaObj["id"]?.jsonPrimitive?.int ?: 0,
            title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("romaji")?.jsonPrimitive?.content ?: "Anime Title",
            englishTitle = titleObj?.get("english")?.jsonPrimitive?.contentOrNull,
            romajiTitle = titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull,
            coverImage = coverObj?.get("extraLarge")?.jsonPrimitive?.contentOrNull
                ?: coverObj?.get("large")?.jsonPrimitive?.content ?: "",
            bannerImage = mediaObj["bannerImage"]?.jsonPrimitive?.contentOrNull,
            description = mediaObj["description"]?.jsonPrimitive?.contentOrNull,
            episodes = mediaObj["episodes"]?.jsonPrimitive?.intOrNull,
            format = format,
            averageScore = mediaObj["averageScore"]?.jsonPrimitive?.intOrNull,
            genres = genresList,
            status = mediaObj["status"]?.jsonPrimitive?.content ?: "FINISHED",
            season = mediaObj["season"]?.jsonPrimitive?.contentOrNull,
            seasonYear = mediaObj["seasonYear"]?.jsonPrimitive?.intOrNull,
            studioName = studioName,
            nextAiringEpisode = nextAiringEp,
            nextAiringAt = nextAiringAt,
            trailerUrl = trailerUrl,
            recommendations = recommendationsList,
            idMal = mediaObj["idMal"]?.jsonPrimitive?.intOrNull
        )
    }

    // ---------------------------------------------------------------- franchise (seasons/movies/OVAs)

    private val franchiseCache = java.util.concurrent.ConcurrentHashMap<Int, List<FranchiseEntry>>()

    private val FRANCHISE_NODE =
        "id type format status episodes startDate { year month day } title { romaji english } coverImage { large }"

    private val trailerCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /**
     * YouTube trailer id of each AniList media in [ids], one batched request. Every AniList media is
     * one season, movie or OVA, so an id's trailer is that entry's own PV — never a sibling season's.
     * Ids with no YouTube trailer are absent. Empty when AniList is unavailable.
     */
    suspend fun getTrailers(ids: List<Int>): Map<Int, String> {
        val want = ids.distinct().filter { it > 0 }.take(50)
        val missing = want.filter { !trailerCache.containsKey(it) }
        if (missing.isNotEmpty()) {
            val query = "query (${'$'}ids: [Int]) { Page(perPage: 50) { media(id_in: ${'$'}ids, type: ANIME) { id trailer { id site } } } }"
            val vars = buildJsonObject { put("ids", JsonArray(missing.map { JsonPrimitive(it) })) }
            val media = queryAniList(query, vars)?.get("data")?.jsonObject?.get("Page")?.jsonObject?.get("media") as? JsonArray
            media?.forEach { el ->
                val m = el as? JsonObject ?: return@forEach
                val id = m["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
                val t = m["trailer"] as? JsonObject ?: return@forEach
                if (t["site"]?.jsonPrimitive?.contentOrNull != "youtube") return@forEach
                val yt = t["id"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@forEach
                if (MediaExtras.YOUTUBE_ID.matches(yt)) trailerCache[id] = yt
            }
        }
        return want.mapNotNull { id -> trailerCache[id]?.let { id to it } }.toMap()
    }

    /**
     * Newest episode already out for each still-airing media in [ids]: the one before its next
     * scheduled episode. Finished or not-yet-aired shows are absent. One batched request; empty
     * when AniList is unavailable.
     */
    suspend fun getLatestAired(ids: List<Int>): Map<Int, Int> {
        val want = ids.distinct().filter { it > 0 }.take(50)
        if (want.isEmpty()) return emptyMap()
        val query = "query (${'$'}ids: [Int]) { Page(perPage: 50) { media(id_in: ${'$'}ids, type: ANIME, status: RELEASING) { id nextAiringEpisode { episode } } } }"
        val vars = buildJsonObject { put("ids", JsonArray(want.map { JsonPrimitive(it) })) }
        val media = queryAniList(query, vars)?.get("data")?.jsonObject?.get("Page")?.jsonObject?.get("media") as? JsonArray
            ?: return emptyMap()
        return media.mapNotNull { el ->
            val m = el as? JsonObject ?: return@mapNotNull null
            val id = m["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val next = (m["nextAiringEpisode"] as? JsonObject)?.get("episode")?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            (next - 1).takeIf { it > 0 }?.let { id to it }
        }.toMap()
    }

    private val SIDE_RELATIONS = listOf("SIDE_STORY", "SUMMARY", "SPIN_OFF", "ALTERNATIVE", "COMPILATION", "CONTAINS", "PARENT")

    /**
     * Every season, movie, OVA, ONA and special around [rootId].
     *
     * Walks AniList relations breadth-first, one batched request per level. Only SEQUEL and
     * PREQUEL links are followed (plus PARENT when the start is itself a movie/OVA/special), so
     * the walk never drifts into crossovers (CHARACTER), adaptations or unrelated OTHER links.
     * Side stories, recaps, spin-offs and alternate versions are listed but never expanded.
     * Returns an empty list when AniList is unavailable; only complete walks are cached.
     */
    suspend fun getFranchise(rootId: Int): List<FranchiseEntry> {
        franchiseCache[rootId]?.let { return it }
        val (entries, complete) = kotlinx.coroutines.withTimeoutOrNull(25_000L) { walkFranchise(rootId) }
            ?: return emptyList()
        if (complete && entries.isNotEmpty()) {
            franchiseCache[rootId] = entries
            entries.filter { it.mainLine }.forEach { franchiseCache.putIfAbsent(it.id, entries) }
        }
        return entries
    }

    private suspend fun walkFranchise(rootId: Int): Pair<List<FranchiseEntry>, Boolean> {
        val nodes = LinkedHashMap<Int, JsonObject>()      // every anime seen, by id
        val expanded = LinkedHashSet<Int>()
        val seq = HashMap<Int, MutableSet<Int>>()         // undirected sequel/prequel graph
        val inbound = HashMap<Int, MutableSet<String>>()  // side relation types pointing at an id
        var parentOfRoot: Int? = null
        var complete = true
        var frontier = listOf(rootId)
        while (frontier.isNotEmpty()) {
            if (expanded.size >= 30) { complete = false; break }
            val next = LinkedHashSet<Int>()
            for (batch in frontier.filter { it !in expanded }.distinct().chunked(10)) {
                expanded += batch
                val q = buildString {
                    append("query {")
                    batch.forEachIndexed { i, id ->
                        append(" m$i: Media(id: $id, type: ANIME) { $FRANCHISE_NODE relations { edges { relationType(version: 2) node { $FRANCHISE_NODE } } } }")
                    }
                    append(" }")
                }
                val data = queryAniList(q, buildJsonObject { })?.get("data") as? JsonObject
                if (data == null) { complete = false; continue }
                batch.indices.forEach { i ->
                    val m = data["m$i"] as? JsonObject ?: return@forEach
                    val id = m["id"]?.jsonPrimitive?.intOrNull ?: return@forEach
                    nodes[id] = m
                    val format = m["format"]?.jsonPrimitive?.contentOrNull
                    val edges = ((m["relations"] as? JsonObject)?.get("edges") as? JsonArray) ?: return@forEach
                    for (e in edges) {
                        val eo = e as? JsonObject ?: continue
                        val type = eo["relationType"]?.jsonPrimitive?.contentOrNull ?: continue
                        val node = eo["node"] as? JsonObject ?: continue
                        if (node["type"]?.jsonPrimitive?.contentOrNull != "ANIME") continue
                        val nid = node["id"]?.jsonPrimitive?.intOrNull ?: continue
                        when {
                            type == "SEQUEL" || type == "PREQUEL" -> {
                                seq.getOrPut(id) { mutableSetOf() } += nid
                                seq.getOrPut(nid) { mutableSetOf() } += id
                                nodes.putIfAbsent(nid, node)
                                if (nid !in expanded) next += nid
                            }
                            type == "PARENT" && id == rootId && format !in setOf("TV", "TV_SHORT") -> {
                                if (parentOfRoot == null) parentOfRoot = nid
                                nodes.putIfAbsent(nid, node)
                                if (nid !in expanded) next += nid
                            }
                            type in SIDE_RELATIONS -> {
                                inbound.getOrPut(nid) { mutableSetOf() } += type
                                nodes.putIfAbsent(nid, node)
                            }
                        }
                    }
                }
            }
            frontier = next.toList()
        }
        if (nodes[rootId] == null) return emptyList<FranchiseEntry>() to false

        // Main line: the sequel/prequel chain through the root, or through the root's parent
        // when the root is a side movie/OVA with no chain of its own.
        val anchor = parentOfRoot?.takeIf { seq[rootId].isNullOrEmpty() } ?: rootId
        val main = LinkedHashSet<Int>()
        val stack = ArrayDeque(listOf(anchor))
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (main.add(id)) seq[id]?.forEach { if (it !in main) stack.addLast(it) }
        }

        fun title(o: JsonObject): String {
            val t = o["title"] as? JsonObject
            return t?.get("english")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: t?.get("romaji")?.jsonPrimitive?.contentOrNull ?: "Untitled"
        }
        fun date(o: JsonObject, k: String) = ((o["startDate"] as? JsonObject)?.get(k))?.jsonPrimitive?.intOrNull
        val relationRank = SIDE_RELATIONS.withIndex().associate { (i, r) -> r to i }

        val raw = nodes.map { (id, o) ->
            FranchiseEntry(
                id = id,
                title = title(o),
                format = o["format"]?.jsonPrimitive?.contentOrNull,
                status = o["status"]?.jsonPrimitive?.contentOrNull,
                episodes = o["episodes"]?.jsonPrimitive?.intOrNull,
                coverImage = ((o["coverImage"] as? JsonObject)?.get("large"))?.jsonPrimitive?.contentOrNull,
                startYear = date(o, "year"), startMonth = date(o, "month"), startDay = date(o, "day"),
                mainLine = id in main,
                label = "",
                relation = if (id in main) null else (inbound[id]?.minByOrNull { relationRank[it] ?: 99 } ?: "PARENT"),
            )
        }.sortedBy { it.sortKey }

        val mainTitles = raw.filter { it.mainLine }.map { it.title }
        val prefix = commonWordPrefix(mainTitles)
        var season = 0
        val labelled = raw.map { e ->
            val tvLike = e.format in setOf("TV", "TV_SHORT", "ONA")
            if (e.mainLine && tvLike) season++
            e.copy(label = franchiseLabel(e, prefix, if (e.mainLine && tvLike) season else null))
        }
        return labelled.take(80) to complete
    }

    private val wordRegex = Regex("[\\p{L}\\p{N}']+")

    /** Number of leading words (case/punctuation-insensitive) shared by all [titles]; 0 if fewer than 2. */
    private fun commonWordPrefix(titles: List<String>): Int {
        if (titles.size < 2) return 0
        val words = titles.map { t -> wordRegex.findAll(t).map { it.value.lowercase() }.toList() }
        var n = 0
        while (words.all { it.size > n && it[n] == words[0][n] }) n++
        return n
    }

    private fun franchiseLabel(e: FranchiseEntry, prefixWords: Int, seasonNumber: Int?): String {
        val matches = wordRegex.findAll(e.title).toList()
        val rest = if (prefixWords in 1..matches.size) {
            e.title.substring(matches[prefixWords - 1].range.last + 1)
                .trim().trimStart(':', '-', '–', '—', '|', ',', '/', '.').trim()
        } else e.title
        val roman = mapOf("II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7, "VIII" to 8)
        return when {
            rest.isEmpty() && seasonNumber != null -> "Season $seasonNumber"
            rest.isEmpty() -> e.formatLabel.ifEmpty { e.title }
            rest.toIntOrNull() != null -> "Season ${rest.toInt()}"
            roman[rest.uppercase()] != null -> "Season ${roman[rest.uppercase()]}"
            else -> rest.replaceFirstChar { it.uppercase() }
        }
    }
}

/** The anime broadcast season (cour) a calendar month belongs to. */
internal fun currentCour(month: Int): String = when (month) {
    Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH -> "WINTER"
    Calendar.APRIL, Calendar.MAY, Calendar.JUNE -> "SPRING"
    Calendar.JULY, Calendar.AUGUST, Calendar.SEPTEMBER -> "SUMMER"
    else -> "FALL"
}
