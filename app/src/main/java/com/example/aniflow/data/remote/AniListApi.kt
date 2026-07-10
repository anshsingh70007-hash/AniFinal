package com.example.aniflow.data.remote

import com.example.aniflow.data.model.Anime
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.SearchPage
import com.example.aniflow.data.NetworkModule
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.util.Calendar
import java.util.LinkedHashMap

class AniListApi(private val client: HttpClient) {
    private val json = NetworkModule.json

    private val cache = object : LinkedHashMap<String, Pair<Long, JsonObject>>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, JsonObject>>?): Boolean {
            return size > 50
        }
    }
    private val CACHE_TTL_MS = 5 * 60 * 1000L

    private val MEDIA_FIELDS = """
        id
        title { romaji english }
        coverImage { large }
        bannerImage
        description
        episodes
        averageScore
        genres
        status
        season
        seasonYear
        studios(isMain: true) { nodes { name } }
    """.trimIndent()

    private suspend fun queryAniList(query: String, variables: JsonObject): JsonObject? {
        val cacheKey = "$query|$variables"
        synchronized(cache) {
            val cached = cache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
                return cached.second
            }
        }
        val result = queryAniListFromApi(query, variables)
        if (result != null) {
            synchronized(cache) {
                cache[cacheKey] = System.currentTimeMillis() to result
            }
        }
        return result
    }

    private suspend fun queryAniListFromApi(query: String, variables: JsonObject): JsonObject? {
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                val response: HttpResponse = client.post("https://graphql.anilist.co") {
                    contentType(ContentType.Application.Json)
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setBody(buildJsonObject {
                        put("query", query)
                        put("variables", variables)
                    })
                }
                android.util.Log.d("AniListApi", "Response status: ${response.status} (attempt $attempt)")
                if (response.status == HttpStatusCode.OK) {
                    val responseBody = response.bodyAsText()
                    return json.parseToJsonElement(responseBody).jsonObject
                } else if (response.status.value == 429) {
                    // Rate limited — wait before retrying
                    android.util.Log.w("AniListApi", "Rate limited (429), waiting before retry $attempt")
                    kotlinx.coroutines.delay(attempt * 2000L)
                    continue
                } else {
                    android.util.Log.e("AniListApi", "Failed with status: ${response.status}, body: ${response.bodyAsText()}")
                    return null
                }
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("AniListApi", "Attempt $attempt failed", e)
                if (attempt < 3) {
                    kotlinx.coroutines.delay(attempt * 1500L)
                }
            }
        }
        android.util.Log.e("AniListApi", "All 3 attempts failed for query", lastException)
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
        val season = when (month) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "WINTER"
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "SPRING"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "SUMMER"
            else -> "FALL"
        }
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}season: MediaSeason, ${'$'}seasonYear: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, sort: SCORE_DESC, season: ${'$'}season, seasonYear: ${'$'}seasonYear, isAdult: false) {
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
        val now = System.currentTimeMillis() / 1000
        val dayStart = now - (now % 86400)
        val dayEnd = dayStart + 86400
        val query = """
            query (${'$'}page: Int, ${'$'}airingAtGreater: Int, ${'$'}airingAtLesser: Int) {
              Page (page: ${'$'}page, perPage: 20) {
                airingSchedules (airingAt_greater: ${'$'}airingAtGreater, airingAt_lesser: ${'$'}airingAtLesser, sort: TIME) {
                  airingAt
                  episode
                  media {
                    id
                    title { romaji english }
                    coverImage { large }
                  }
                }
              }
            }
        """.trimIndent()
        val vars = buildJsonObject {
            put("page", 1)
            put("airingAtGreater", JsonPrimitive(dayStart))
            put("airingAtLesser", JsonPrimitive(dayEnd))
        }
        val response = queryAniList(query, vars)
        val airingList = mutableListOf<AiringAnime>()
        try {
            response?.get("data")?.jsonObject?.get("Page")?.jsonObject?.get("airingSchedules")?.jsonArray?.forEach { element ->
                val obj = element.jsonObject
                val media = obj["media"]?.jsonObject ?: return@forEach
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
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC, isAdult: false) {
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
                animeList.add(mapMediaObjToAnime(element.jsonObject))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return animeList
    }

    private fun mapMediaObjToAnime(mediaObj: JsonObject): Anime {
        val titleObj = mediaObj["title"]?.jsonObject
        val coverObj = mediaObj["coverImage"]?.jsonObject
        val genresList = mediaObj["genres"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val studiosList = mediaObj["studios"]?.jsonObject?.get("nodes")?.jsonArray
        val studioName = if (studiosList != null && studiosList.isNotEmpty()) {
            studiosList[0].jsonObject["name"]?.jsonPrimitive?.content
        } else null

        return Anime(
            id = mediaObj["id"]?.jsonPrimitive?.int ?: 0,
            title = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("romaji")?.jsonPrimitive?.content ?: "Anime Title",
            englishTitle = titleObj?.get("english")?.jsonPrimitive?.contentOrNull,
            coverImage = coverObj?.get("large")?.jsonPrimitive?.content ?: "",
            bannerImage = mediaObj["bannerImage"]?.jsonPrimitive?.contentOrNull,
            description = mediaObj["description"]?.jsonPrimitive?.contentOrNull,
            episodes = mediaObj["episodes"]?.jsonPrimitive?.intOrNull,
            averageScore = mediaObj["averageScore"]?.jsonPrimitive?.intOrNull,
            genres = genresList,
            status = mediaObj["status"]?.jsonPrimitive?.content ?: "FINISHED",
            season = mediaObj["season"]?.jsonPrimitive?.contentOrNull,
            seasonYear = mediaObj["seasonYear"]?.jsonPrimitive?.intOrNull,
            studioName = studioName
        )
    }
}
