package com.example.aniflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.aniflow.data.model.Anime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.call.*

@Serializable
data class FeedbackAnime(
    val id: Int,
    val title: String,
    val coverImage: String,
    val bannerImage: String? = null,
    val episodes: Int? = null
) {
    fun toAnime(): Anime {
        return Anime(
            id = id,
            title = title,
            englishTitle = null,
            coverImage = coverImage,
            bannerImage = bannerImage,
            description = null,
            episodes = episodes,
            averageScore = null,
            genres = emptyList(),
            status = "FINISHED",
            season = null,
            seasonYear = null,
            studioName = null,
            nextAiringEpisode = null,
            nextAiringAt = null,
            trailerUrl = null,
            recommendations = emptyList()
        )
    }
}

@Serializable
data class UserFeedback(
    val anime: FeedbackAnime,
    val feedback: String,
    val timestamp: Long
)

@Serializable
data class GlobalFeedbackData(
    val list: List<UserFeedback>
)

@Serializable
data class GlobalFeedbackResponse(
    val id: String,
    val name: String? = null,
    val data: GlobalFeedbackData
)

@Serializable
data class GlobalFeedbackRequest(
    val name: String = "aniflow_global_feedbacks",
    val data: GlobalFeedbackData
)

private val Context.userFeedbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_feedback_preferences")

@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedbackStore(private val context: Context) {
    private val json = NetworkModule.json
    private val client = NetworkModule.client
    private val feedbackKey = stringPreferencesKey("feedback_json")
    private val backupFeedbackKey = stringPreferencesKey("feedback_json_backup")
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    private val globalDocUrl = "https://api.restful-api.dev/objects/ff8081819d82fab6019f6fb7249673e0"

    private val refreshSignal = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }

    private val localFeedbackFlow: Flow<List<UserFeedback>> = context.userFeedbackDataStore.data.map { preferences ->
        val jsonStr = preferences[feedbackKey] ?: "[]"
        try {
            json.decodeFromString<List<UserFeedback>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            scope.launch {
                repairAndLoadFeedback(jsonStr)
            }
            emptyList()
        }
    }

    val feedbackListFlow: Flow<List<UserFeedback>> = refreshSignal.flatMapLatest {
        flow {
            // First emit local cached feedbacks (offline-first, instant load)
            val cached = getLocalFeedbackList()
            emit(cached)

            // Then fetch from server and update local cache with retry backoff
            var success = false
            var attempt = 1
            while (!success && attempt <= 4) {
                try {
                    val response: HttpResponse = client.get(globalDocUrl) {
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    }
                    if (response.status == HttpStatusCode.OK) {
                        val bodyText = response.bodyAsText()
                        val parsed = try {
                            json.decodeFromString<GlobalFeedbackResponse>(bodyText)
                        } catch (e: Exception) {
                            android.util.Log.e("UserFeedbackStore", "Failed to parse server response: $bodyText", e)
                            null
                        }
                        if (parsed != null) {
                            val serverList = parsed.data.list
                            val latestCached = getLocalFeedbackList()
                            val mergedList = mergeFeedbackLists(latestCached, serverList)
                            saveLocalFeedbackList(mergedList)
                            emit(mergedList)
                            success = true
                        }
                    } else {
                        android.util.Log.w("UserFeedbackStore", "Server returned status: ${response.status} (attempt $attempt)")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UserFeedbackStore", "Failed to fetch feedback from server (attempt $attempt)", e)
                }
                if (!success && attempt < 4) {
                    kotlinx.coroutines.delay(attempt * 3000L) // backoff: 3s, 6s, 9s
                }
                attempt++
            }
        }
    }

    private fun mergeFeedbackLists(local: List<UserFeedback>, server: List<UserFeedback>): List<UserFeedback> {
        val now = System.currentTimeMillis()
        val serverMap = server.associateBy { it.anime.id }
        val merged = mutableListOf<UserFeedback>()

        // Add all server items
        merged.addAll(server)

        // For local items not on server, keep them if they are very recent (within 15 seconds)
        // For local items on server, keep the one with the latest timestamp
        for (localItem in local) {
            val serverItem = serverMap[localItem.anime.id]
            if (serverItem == null) {
                if (now - localItem.timestamp < 15000L) {
                    merged.add(localItem)
                }
            } else {
                if (localItem.timestamp > serverItem.timestamp) {
                    merged.remove(serverItem)
                    merged.add(localItem)
                }
            }
        }

        return merged.sortedByDescending { it.timestamp }
    }

    fun refresh() {
        scope.launch {
            refreshSignal.emit(Unit)
        }
    }

    private suspend fun getLocalFeedbackList(): List<UserFeedback> {
        val preferences = context.userFeedbackDataStore.data.first()
        val jsonStr = preferences[feedbackKey] ?: "[]"
        return try {
            json.decodeFromString<List<UserFeedback>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            repairAndLoadFeedback(jsonStr)
        }
    }

    private suspend fun repairAndLoadFeedback(corruptedJson: String): List<UserFeedback> {
        android.util.Log.e("UserFeedbackStore", "Self-Heal: UserFeedback JSON corrupted! Attempting recovery. Raw: $corruptedJson")
        try {
            context.userFeedbackDataStore.edit { prefs ->
                prefs[backupFeedbackKey] = corruptedJson
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val salvaged = mutableListOf<UserFeedback>()
        try {
            val pattern = java.util.regex.Pattern.compile("\\{[^{}]+\\}")
            val matcher = pattern.matcher(corruptedJson)
            while (matcher.find()) {
                val candidate = matcher.group()
                try {
                    val entry = json.decodeFromString<UserFeedback>(candidate)
                    salvaged.add(entry)
                } catch (e: Exception) {
                    // Ignore non-parseable items
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            saveLocalFeedbackList(salvaged)
            android.util.Log.d("UserFeedbackStore", "Self-Heal: Recovered ${salvaged.size} user feedback entries.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return salvaged
    }

    private suspend fun saveLocalFeedbackList(list: List<UserFeedback>) {
        val jsonStr = json.encodeToString(list)
        context.userFeedbackDataStore.edit { preferences ->
            preferences[feedbackKey] = jsonStr
        }
    }

    suspend fun saveFeedback(anime: Anime, feedbackText: String) {
        // Fetch current list from server first to be in sync
        var currentList = emptyList<UserFeedback>()
        try {
            val response: HttpResponse = client.get(globalDocUrl) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            if (response.status == HttpStatusCode.OK) {
                val bodyText = response.bodyAsText()
                val parsed = try {
                    json.decodeFromString<GlobalFeedbackResponse>(bodyText)
                } catch (e: Exception) {
                    android.util.Log.e("UserFeedbackStore", "Failed to parse server response in save: $bodyText", e)
                    null
                }
                if (parsed != null) {
                    currentList = mergeFeedbackLists(getLocalFeedbackList(), parsed.data.list)
                } else {
                    currentList = getLocalFeedbackList()
                }
            } else {
                currentList = getLocalFeedbackList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentList = getLocalFeedbackList()
        }

        val updated = currentList.toMutableList()
        updated.removeAll { it.anime.id == anime.id }
        if (feedbackText.isNotBlank()) {
            val flatAnime = FeedbackAnime(
                id = anime.id,
                title = anime.title,
                coverImage = anime.coverImage,
                bannerImage = anime.bannerImage,
                episodes = anime.episodes
            )
            updated.add(UserFeedback(anime = flatAnime, feedback = feedbackText, timestamp = System.currentTimeMillis()))
        }

        // Sort by timestamp descending so newest feedback shows first
        updated.sortByDescending { it.timestamp }

        // Update local cache
        saveLocalFeedbackList(updated)

        // Send update to server with retry loop
        var putSuccess = false
        var putAttempt = 1
        while (!putSuccess && putAttempt <= 3) {
            try {
                val response = client.put(globalDocUrl) {
                    contentType(ContentType.Application.Json)
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setBody(GlobalFeedbackRequest(data = GlobalFeedbackData(list = updated)))
                }
                if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
                    putSuccess = true
                } else {
                    android.util.Log.w("UserFeedbackStore", "PUT failed with status: ${response.status} (attempt $putAttempt)")
                }
            } catch (e: Exception) {
                android.util.Log.e("UserFeedbackStore", "PUT failed (attempt $putAttempt)", e)
            }
            if (!putSuccess && putAttempt < 3) {
                kotlinx.coroutines.delay(putAttempt * 2000L)
            }
            putAttempt++
        }

        // Trigger flow collection to emit updated value instantly!
        refreshSignal.emit(Unit)
    }

    suspend fun resetServer() {
        try {
            client.put(globalDocUrl) {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setBody(GlobalFeedbackRequest(data = GlobalFeedbackData(list = emptyList())))
            }
            saveLocalFeedbackList(emptyList())
            refreshSignal.emit(Unit)
            android.util.Log.d("UserFeedbackStore", "Feedback section reset successfully")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getFeedbackForAnimeFlow(animeId: Int): Flow<String?> {
        return feedbackListFlow.map { list ->
            list.firstOrNull { it.anime.id == animeId }?.feedback
        }
    }
}
