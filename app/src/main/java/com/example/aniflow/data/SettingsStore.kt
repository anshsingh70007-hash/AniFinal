package com.example.aniflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_preferences")

class SettingsStore(
    private val context: Context,
    private val dataStore: DataStore<Preferences>? = null
) {
    private val activeDataStore: DataStore<Preferences> = dataStore ?: context.settingsDataStore

    private val qualityKey = stringPreferencesKey("quality_preference")
    private val languageKey = stringPreferencesKey("language_preference")
    private val providerKey = stringPreferencesKey("provider_preference")
    private val autoSkipIntroKey = booleanPreferencesKey("auto_skip_intro")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val defaultSpeedKey = floatPreferencesKey("default_playback_speed")
    private val autoPlayNextKey = booleanPreferencesKey("auto_play_next_episode")
    private val feedbackOnboardingShownKey = booleanPreferencesKey("feedback_onboarding_shown_v184")

    val feedbackOnboardingShown: Flow<Boolean> = activeDataStore.data.map { prefs ->
        prefs[feedbackOnboardingShownKey] ?: false
    }

    val qualityPreference: Flow<String> = activeDataStore.data.map { prefs ->
        prefs[qualityKey] ?: "auto"
    }

    val languagePreference: Flow<String> = activeDataStore.data.map { prefs ->
        prefs[languageKey] ?: "sub"
    }

    val providerPreference: Flow<String> = activeDataStore.data.map { prefs ->
        prefs[providerKey] ?: "anilight"
    }

    val autoSkipIntro: Flow<Boolean> = activeDataStore.data.map { prefs ->
        prefs[autoSkipIntroKey] ?: false
    }

    val themeMode: Flow<String> = activeDataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "system"
    }

    val defaultPlaybackSpeed: Flow<Float> = activeDataStore.data.map { prefs ->
        prefs[defaultSpeedKey] ?: 1.0f
    }

    val autoPlayNextEpisode: Flow<Boolean> = activeDataStore.data.map { prefs ->
        prefs[autoPlayNextKey] ?: true
    }

    suspend fun setQuality(quality: String) {
        activeDataStore.edit { prefs ->
            prefs[qualityKey] = quality
        }
    }

    suspend fun setLanguage(lang: String) {
        activeDataStore.edit { prefs ->
            prefs[languageKey] = lang
        }
    }

    suspend fun setProvider(provider: String) {
        activeDataStore.edit { prefs ->
            prefs[providerKey] = provider
        }
    }

    suspend fun setAutoSkipIntro(skip: Boolean) {
        activeDataStore.edit { prefs ->
            prefs[autoSkipIntroKey] = skip
        }
    }

    private val checkUpdatesKey = booleanPreferencesKey("check_updates_startup")
    private val lastUpdateCheckTimeKey = longPreferencesKey("last_update_check_time")
    private val dismissedVersionCodeKey = intPreferencesKey("dismissed_version_code")

    val checkUpdatesStartup: Flow<Boolean> = activeDataStore.data.map { prefs ->
        prefs[checkUpdatesKey] ?: true
    }

    val lastUpdateCheckTime: Flow<Long> = activeDataStore.data.map { prefs ->
        prefs[lastUpdateCheckTimeKey] ?: 0L
    }

    val dismissedVersionCode: Flow<Int> = activeDataStore.data.map { prefs ->
        prefs[dismissedVersionCodeKey] ?: 0
    }

    suspend fun setCheckUpdatesStartup(enabled: Boolean) {
        activeDataStore.edit { prefs ->
            prefs[checkUpdatesKey] = enabled
        }
    }

    suspend fun setLastUpdateCheckTime(time: Long) {
        activeDataStore.edit { prefs ->
            prefs[lastUpdateCheckTimeKey] = time
        }
    }

    suspend fun setDismissedVersionCode(code: Int) {
        activeDataStore.edit { prefs ->
            prefs[dismissedVersionCodeKey] = code
        }
    }

    suspend fun setThemeMode(mode: String) {
        activeDataStore.edit { prefs ->
            prefs[themeModeKey] = mode
        }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        activeDataStore.edit { prefs ->
            prefs[defaultSpeedKey] = speed
        }
    }

    suspend fun setAutoPlayNextEpisode(enabled: Boolean) {
        activeDataStore.edit { prefs ->
            prefs[autoPlayNextKey] = enabled
        }
    }

    suspend fun setFeedbackOnboardingShown(shown: Boolean) {
        activeDataStore.edit { prefs ->
            prefs[feedbackOnboardingShownKey] = shown
        }
    }

    private val navSoundsKey = booleanPreferencesKey("nav_sounds")

    /** Koto navigation sounds (ui/sound/Sfx). On by default. */
    val navSounds: Flow<Boolean> = activeDataStore.data.map { prefs -> prefs[navSoundsKey] ?: true }

    suspend fun setNavSounds(enabled: Boolean) {
        activeDataStore.edit { it[navSoundsKey] = enabled }
    }

    private val heroTrailersKey = booleanPreferencesKey("hero_trailers")

    /** Trailer previews behind the TV home billboard. On by default. */
    val heroTrailers: Flow<Boolean> = activeDataStore.data.map { prefs -> prefs[heroTrailersKey] ?: true }

    suspend fun setHeroTrailers(enabled: Boolean) {
        activeDataStore.edit { it[heroTrailersKey] = enabled }
    }

    private val recentSearchesKey = stringPreferencesKey("recent_searches")

    /** Queries that led somewhere (a result was opened), newest first. Stays on this device. */
    val recentSearches: Flow<List<String>> = activeDataStore.data.map { prefs -> decodeRecent(prefs[recentSearchesKey]) }

    suspend fun addRecentSearch(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.length < 2) return
        activeDataStore.edit { prefs ->
            val rest = decodeRecent(prefs[recentSearchesKey]).filterNot { it.equals(q, ignoreCase = true) }
            prefs[recentSearchesKey] = (listOf(q) + rest).take(MAX_RECENT).joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        activeDataStore.edit { it.remove(recentSearchesKey) }
    }

    private fun decodeRecent(raw: String?): List<String> = raw?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private companion object { const val MAX_RECENT = 6 }
}
