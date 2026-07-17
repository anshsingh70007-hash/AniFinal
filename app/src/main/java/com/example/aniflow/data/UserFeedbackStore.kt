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
            emptyList()
        }
    }

    val feedbackListFlow: Flow<List<UserFeedback>> = refreshSignal.flatMapLatest {
        flow {
            // First emit local cached feedbacks (offline-first, instant load)
            val cached = getLocalFeedbackList()
            emit(cached)

            // Then fetch from server and update local cache
            try {
                val response: HttpResponse = client.get(globalDocUrl)
                if (response.status == HttpStatusCode.OK) {
                    val resBody = response.body<GlobalFeedbackResponse>()
                    val serverList = resBody.data.list
                    saveLocalFeedbackList(serverList)
                    emit(serverList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If network fails, we fall back gracefully to the cache we already emitted
            }
        }
    }

    private suspend fun getLocalFeedbackList(): List<UserFeedback> {
        val preferences = context.userFeedbackDataStore.data.first()
        val jsonStr = preferences[feedbackKey] ?: "[]"
        return try {
            json.decodeFromString<List<UserFeedback>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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
            val response: HttpResponse = client.get(globalDocUrl)
            if (response.status == HttpStatusCode.OK) {
                val resBody = response.body<GlobalFeedbackResponse>()
                currentList = resBody.data.list
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

        // Send update to server
        try {
            client.put(globalDocUrl) {
                contentType(ContentType.Application.Json)
                setBody(GlobalFeedbackRequest(data = GlobalFeedbackData(list = updated)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Trigger flow collection to emit updated value instantly!
            refreshSignal.emit(Unit)
        }
    }

    fun getFeedbackForAnimeFlow(animeId: Int): Flow<String?> {
        return feedbackListFlow.map { list ->
            list.firstOrNull { it.anime.id == animeId }?.feedback
        }
    }
}
