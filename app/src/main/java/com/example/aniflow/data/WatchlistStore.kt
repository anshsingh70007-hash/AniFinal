package com.example.aniflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.aniflow.data.model.Anime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.watchlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "watchlist_preferences")

class WatchlistStore(private val context: Context) {
    private val json = NetworkModule.json
    private val watchlistKey = stringPreferencesKey("watchlist_json")

    val watchlistFlow: Flow<List<Anime>> = context.watchlistDataStore.data.map { preferences ->
        val jsonStr = preferences[watchlistKey] ?: "[]"
        try {
            json.decodeFromString<List<Anime>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun getWatchlistList(): List<Anime> {
        val preferences = context.watchlistDataStore.data.first()
        val jsonStr = preferences[watchlistKey] ?: "[]"
        return try {
            json.decodeFromString<List<Anime>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addToWatchlist(anime: Anime) {
        val current = getWatchlistList().toMutableList()
        if (current.none { it.id == anime.id }) {
            current.add(anime)
            saveWatchlist(current)
        }
    }

    suspend fun removeFromWatchlist(animeId: Int) {
        val current = getWatchlistList().toMutableList()
        val updated = current.filter { it.id != animeId }
        saveWatchlist(updated)
    }

    private suspend fun saveWatchlist(items: List<Anime>) {
        val jsonStr = json.encodeToString(items)
        context.watchlistDataStore.edit { preferences ->
            preferences[watchlistKey] = jsonStr
        }
    }

    suspend fun isBookmarked(animeId: Int): Boolean {
        return getWatchlistList().any { it.id == animeId }
    }

    fun isBookmarkedFlow(animeId: Int): Flow<Boolean> {
        return watchlistFlow.map { list -> list.any { it.id == animeId } }
    }

    suspend fun clearWatchlist() {
        saveWatchlist(emptyList())
    }
}
