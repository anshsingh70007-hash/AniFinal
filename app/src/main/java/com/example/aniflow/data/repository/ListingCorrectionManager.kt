package com.example.aniflow.data.repository

import android.content.Context
import android.util.Log
import com.example.aniflow.data.NetworkModule
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class EpisodeCorrection(
    val provider: String = "ANILIGHT",
    val slug: String
)

@Serializable
data class ListingCorrections(
    val version: Int = 1,
    val lastUpdated: String = "",
    val episodeCorrections: Map<String, EpisodeCorrection> = emptyMap(),
    val titleSlugOverrides: Map<String, String> = emptyMap()
)

/**
 * Manages dynamic remote listing and episode corrections pulled silently from GitHub.
 * Enables fixing anime listings, missing anime, or broken episode links on the fly
 * without bothering the user with update popups or requiring APK updates for listing fixes.
 */
class ListingCorrectionManager(
    private val context: Context,
    private val client: HttpClient
) {
    private val TAG = "ListingCorrectionMgr"
    private val json = NetworkModule.json
    private val cacheFile by lazy { File(context.cacheDir, "listing_corrections_cache.json") }

    @Volatile
    private var cachedCorrections: ListingCorrections = loadCached()

    companion object {
        const val CORRECTIONS_URL = "https://raw.githubusercontent.com/anshsingh70007-hash/AniFinal/main/listing_corrections.json"
    }

    private fun loadCached(): ListingCorrections {
        return try {
            if (cacheFile.exists()) {
                json.decodeFromString<ListingCorrections>(cacheFile.readText())
            } else {
                ListingCorrections()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading cached corrections: ${e.message}")
            ListingCorrections()
        }
    }

    suspend fun syncRemoteCorrections() = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$CORRECTIONS_URL?t=${System.currentTimeMillis()}") {
                header("User-Agent", "AniFlow/1.8.7")
            }
            if (response.status == HttpStatusCode.OK) {
                val text = response.bodyAsText()
                val parsed = json.decodeFromString<ListingCorrections>(text)
                cachedCorrections = parsed
                try {
                    cacheFile.writeText(text)
                } catch (_: Exception) {}
                Log.d(TAG, "Successfully synced remote listing corrections (v${parsed.version}, ${parsed.episodeCorrections.size} overrides)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote listing corrections sync skipped: ${e.message}")
        }
    }

    fun getCorrection(anilistId: Int): EpisodeCorrection? {
        return cachedCorrections.episodeCorrections[anilistId.toString()]
    }

    fun getCorrectionByTitle(title: String): EpisodeCorrection? {
        val normalized = title.trim().lowercase()
        for ((k, v) in cachedCorrections.titleSlugOverrides) {
            val keyNorm = k.trim().lowercase()
            if (keyNorm == normalized || normalized.contains(keyNorm)) {
                return EpisodeCorrection(slug = v)
            }
        }
        return null
    }
}
