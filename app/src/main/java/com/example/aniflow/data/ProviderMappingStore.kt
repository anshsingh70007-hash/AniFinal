package com.example.aniflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.aniflow.data.model.ProviderId
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private val Context.providerMappingDataStore: DataStore<Preferences> by preferencesDataStore(name = "provider_mapping_preferences")

@Serializable
data class MappingEvidence(
    val providerTitle: String,
    val anilistTitle: String,
    val matchedBy: String, // "EXACT_ID" or "HIGH_CONFIDENCE_SCORE" or "USER_CONFIRMED"
    val confidenceScore: Double,
    val providerVersion: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ProviderMapping(
    val provider: ProviderId,
    val anilistId: Int,
    val slug: String,
    val evidence: MappingEvidence
)

class ProviderMappingStore(private val context: Context) {
    private val json = NetworkModule.json

    companion object {
        private const val CURRENT_SCHEMA_VERSION = 1
        private val SCHEMA_VERSION_KEY = intPreferencesKey("schema_version")
    }

    private fun getMappingKey(provider: ProviderId, anilistId: Int): Preferences.Key<String> {
        return stringPreferencesKey("mapping:${provider.name}:${anilistId}")
    }

    suspend fun getMapping(provider: ProviderId, anilistId: Int): ProviderMapping? {
        val prefs = context.providerMappingDataStore.data.first()
        val version = prefs[SCHEMA_VERSION_KEY] ?: 0
        if (version != CURRENT_SCHEMA_VERSION) {
            clearMappings()
            return null
        }
        val key = getMappingKey(provider, anilistId)
        val jsonStr = prefs[key] ?: return null
        return try {
            json.decodeFromString<ProviderMapping>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun setMapping(mapping: ProviderMapping) {
        context.providerMappingDataStore.edit { prefs ->
            prefs[SCHEMA_VERSION_KEY] = CURRENT_SCHEMA_VERSION
            val key = getMappingKey(mapping.provider, mapping.anilistId)
            val jsonStr = json.encodeToString(mapping)
            prefs[key] = jsonStr
        }
    }

    suspend fun invalidateMapping(provider: ProviderId, anilistId: Int) {
        context.providerMappingDataStore.edit { prefs ->
            val key = getMappingKey(provider, anilistId)
            prefs.remove(key)
        }
    }

    suspend fun clearMappings() {
        context.providerMappingDataStore.edit { prefs ->
            prefs.clear()
            prefs[SCHEMA_VERSION_KEY] = CURRENT_SCHEMA_VERSION
        }
    }
}
