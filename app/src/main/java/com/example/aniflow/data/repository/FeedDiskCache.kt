package com.example.aniflow.data.repository

import android.content.Context
import com.example.aniflow.data.NetworkModule
import com.example.aniflow.data.model.AiringAnime
import com.example.aniflow.data.model.Anime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class HomeFeedCache(
    val trending: List<Anime> = emptyList(),
    val popular: List<Anime> = emptyList(),
    val seasonal: List<Anime> = emptyList(),
    val airingToday: List<AiringAnime> = emptyList(),
    val topRated: List<Anime> = emptyList(),
    val upcoming: List<Anime> = emptyList(),
    val recentlyUpdated: List<Anime> = emptyList(),
    val actionAnime: List<Anime> = emptyList(),
    val romanceAnime: List<Anime> = emptyList(),
    val timestamp: Long = 0L
)

class FeedDiskCache(private val context: Context) {
    private val json = NetworkModule.json
    private val cacheFile: File
        get() = File(context.filesDir, "feed_cache_v2.json")

    private var inMemoryCache: HomeFeedCache? = null

    suspend fun loadCache(): HomeFeedCache? = withContext(Dispatchers.IO) {
        try {
            inMemoryCache?.let { return@withContext it }
            if (!cacheFile.exists()) return@withContext null
            val text = cacheFile.readText()
            if (text.isBlank()) return@withContext null
            val loaded = json.decodeFromString<HomeFeedCache>(text)
            inMemoryCache = loaded
            loaded
        } catch (e: Exception) {
            android.util.Log.e("FeedDiskCache", "Failed to read feed cache from disk", e)
            null
        }
    }

    suspend fun saveCache(cache: HomeFeedCache) = withContext(Dispatchers.IO) {
        try {
            inMemoryCache = cache
            val serialized = json.encodeToString(HomeFeedCache.serializer(), cache)
            val tempFile = File(context.filesDir, "feed_cache_v2.json.tmp")
            tempFile.writeText(serialized)
            if (tempFile.renameTo(cacheFile)) {
                android.util.Log.d("FeedDiskCache", "Saved feed cache to disk (${cache.trending.size} trending, ${cache.popular.size} popular)")
            } else {
                cacheFile.writeText(serialized)
                tempFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("FeedDiskCache", "Failed to write feed cache to disk", e)
        }
    }

    suspend fun updateSection(
        trending: List<Anime>? = null,
        popular: List<Anime>? = null,
        seasonal: List<Anime>? = null,
        airingToday: List<AiringAnime>? = null,
        topRated: List<Anime>? = null,
        upcoming: List<Anime>? = null,
        recentlyUpdated: List<Anime>? = null,
        actionAnime: List<Anime>? = null,
        romanceAnime: List<Anime>? = null
    ) = withContext(Dispatchers.IO) {
        val current = loadCache() ?: HomeFeedCache()
        val updated = current.copy(
            trending = if (trending != null && trending.isNotEmpty()) trending else current.trending,
            popular = if (popular != null && popular.isNotEmpty()) popular else current.popular,
            seasonal = if (seasonal != null && seasonal.isNotEmpty()) seasonal else current.seasonal,
            airingToday = if (airingToday != null && airingToday.isNotEmpty()) airingToday else current.airingToday,
            topRated = if (topRated != null && topRated.isNotEmpty()) topRated else current.topRated,
            upcoming = if (upcoming != null && upcoming.isNotEmpty()) upcoming else current.upcoming,
            recentlyUpdated = if (recentlyUpdated != null && recentlyUpdated.isNotEmpty()) recentlyUpdated else current.recentlyUpdated,
            actionAnime = if (actionAnime != null && actionAnime.isNotEmpty()) actionAnime else current.actionAnime,
            romanceAnime = if (romanceAnime != null && romanceAnime.isNotEmpty()) romanceAnime else current.romanceAnime,
            timestamp = System.currentTimeMillis()
        )
        saveCache(updated)
    }

    fun findAnimeById(id: Int): Anime? {
        val c = inMemoryCache ?: return null
        return (c.trending + c.popular + c.seasonal + c.topRated + c.upcoming + c.recentlyUpdated + c.actionAnime + c.romanceAnime)
            .find { it.id == id }
    }
}
