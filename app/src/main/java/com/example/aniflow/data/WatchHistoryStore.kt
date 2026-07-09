package com.example.aniflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.aniflow.data.model.WatchHistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

private val Context.watchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "watch_history_preferences")

class WatchHistoryStore(private val context: Context) {
    private val json = NetworkModule.json
    private val historyKey = stringPreferencesKey("history_json")
    private val scope = CoroutineScope(Dispatchers.IO)

    val historyFlow: Flow<List<WatchHistoryEntry>> = context.watchHistoryDataStore.data.map { preferences ->
        val jsonStr = preferences[historyKey] ?: "[]"
        try {
            json.decodeFromString<List<WatchHistoryEntry>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getHistoryList(): List<WatchHistoryEntry> {
        val preferences = context.watchHistoryDataStore.data.first()
        val jsonStr = preferences[historyKey] ?: "[]"
        return try {
            json.decodeFromString<List<WatchHistoryEntry>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getProgress(animeId: Int): WatchHistoryEntry? {
        return getHistoryList().firstOrNull { it.animeId == animeId }
    }

    fun saveProgress(
        animeId: Int,
        title: String,
        coverImage: String,
        episodeNumber: Int,
        episodeName: String,
        progressMs: Long,
        durationMs: Long
    ) {
        scope.launch {
            val currentList = getHistoryList().toMutableList()
            currentList.removeAll { it.animeId == animeId }
            
            val entry = WatchHistoryEntry(
                animeId = animeId,
                title = title,
                coverImage = coverImage,
                episodeNumber = episodeNumber,
                episodeName = episodeName,
                progressMs = progressMs,
                durationMs = durationMs,
                lastWatchedTime = System.currentTimeMillis()
            )
            currentList.add(0, entry)
            
            val trimmedList = currentList.take(50)
            saveHistory(trimmedList)
        }
    }

    private suspend fun saveHistory(items: List<WatchHistoryEntry>) {
        val jsonStr = json.encodeToString(items)
        context.watchHistoryDataStore.edit { preferences ->
            preferences[historyKey] = jsonStr
        }
    }

    fun removeHistory(animeId: Int) {
        scope.launch {
            val currentList = getHistoryList().toMutableList()
            currentList.removeAll { it.animeId == animeId }
            saveHistory(currentList)
        }
    }

    fun clearHistory() {
        scope.launch {
            saveHistory(emptyList())
        }
    }
}
