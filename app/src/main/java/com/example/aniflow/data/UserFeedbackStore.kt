package com.example.aniflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.aniflow.data.model.Anime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

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

private val Context.userFeedbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_feedback_preferences")

/**
 * On-device store for the user's own notes about an anime.
 *
 * This used to GET/PUT a single shared document on the public, unauthenticated
 * `api.restful-api.dev`, which meant any user could read, overwrite or wipe every other
 * user's notes and inject arbitrary text and image URLs into the app. That backend is gone;
 * feedback is now device-local until there is a real authenticated service to sync against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedbackStore(private val context: Context) {
    private val json = NetworkModule.json
    private val feedbackKey = stringPreferencesKey("feedback_json")
    private val backupFeedbackKey = stringPreferencesKey("feedback_json_backup")
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    private val refreshSignal = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }

    val feedbackListFlow: Flow<List<UserFeedback>> = refreshSignal.flatMapLatest {
        flow { emit(getLocalFeedbackList()) }
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
        android.util.Log.e("UserFeedbackStore", "Self-Heal: UserFeedback JSON corrupted! Attempting recovery.")
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
        val updated = getLocalFeedbackList().toMutableList()
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

        // Newest feedback first.
        updated.sortByDescending { it.timestamp }
        saveLocalFeedbackList(updated)
        refreshSignal.emit(Unit)
    }

    suspend fun clearAll() {
        saveLocalFeedbackList(emptyList())
        refreshSignal.emit(Unit)
    }

    fun getFeedbackForAnimeFlow(animeId: Int): Flow<String?> {
        return feedbackListFlow.map { list ->
            list.firstOrNull { it.anime.id == animeId }?.feedback
        }
    }
}
