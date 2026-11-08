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
    private val backupHistoryKey = stringPreferencesKey("history_json_backup")
    private val scope = CoroutineScope(Dispatchers.IO)

    val historyFlow: Flow<List<WatchHistoryEntry>> = context.watchHistoryDataStore.data.map { preferences ->
        val jsonStr = preferences[historyKey] ?: "[]"
        try {
            json.decodeFromString<List<WatchHistoryEntry>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            scope.launch {
                repairAndLoadHistory(jsonStr)
            }
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
            repairAndLoadHistory(jsonStr)
        }
    }

    private suspend fun repairAndLoadHistory(corruptedJson: String): List<WatchHistoryEntry> {
        android.util.Log.e("WatchHistoryStore", "Self-Heal: Watch history JSON corrupted! Attempting recovery (${corruptedJson.length} chars)")
        try {
            context.watchHistoryDataStore.edit { prefs ->
                prefs[backupHistoryKey] = corruptedJson
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val salvaged = salvageList<WatchHistoryEntry>(json, corruptedJson)
        // Nothing recoverable: keep the original blob (and its backup) rather than overwrite it with [].
        if (salvaged.isEmpty()) return salvaged

        try {
            saveHistory(salvaged)
            android.util.Log.d("WatchHistoryStore", "Self-Heal: Recovered ${salvaged.size} history entries.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return salvaged
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
            mutate { list -> (listOf(entry) + list.filter { it.animeId != animeId }).take(50) }
        }
    }

    /**
     * Read-modify-write inside one DataStore transaction. Two saves racing (player tick + exit save)
     * used to read the same list, and the later write dropped the earlier one.
     */
    private suspend fun mutate(change: (List<WatchHistoryEntry>) -> List<WatchHistoryEntry>) {
        val healed = getHistoryList()   // repairs a corrupted blob first
        context.watchHistoryDataStore.edit { prefs ->
            val current = prefs[historyKey]
                ?.let { runCatching { json.decodeFromString<List<WatchHistoryEntry>>(it) }.getOrNull() }
                ?: healed
            prefs[historyKey] = json.encodeToString(change(current))
        }
    }

    private suspend fun saveHistory(items: List<WatchHistoryEntry>) {
        val jsonStr = json.encodeToString(items)
        context.watchHistoryDataStore.edit { preferences ->
            preferences[historyKey] = jsonStr
        }
    }

    fun removeHistory(animeId: Int) {
        scope.launch { mutate { list -> list.filter { it.animeId != animeId } } }
    }

    fun clearHistory() {
        scope.launch {
            saveHistory(emptyList())
        }
    }
}
