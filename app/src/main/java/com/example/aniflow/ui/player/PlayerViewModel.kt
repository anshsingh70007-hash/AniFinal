package com.example.aniflow.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val repository: AnimeRepository,
    private val watchHistoryStore: WatchHistoryStore,
    private val settingsStore: SettingsStore
) : ViewModel() {
    
    init {
        viewModelScope.launch {
            try {
                playbackSpeed.value = settingsStore.defaultPlaybackSpeed.first()
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Failed to get default playback speed", e)
            }
        }
        viewModelScope.launch {
            try {
                selectedVideoQuality.value = settingsStore.qualityPreference.first()
            } catch (e: Exception) {
                // ignore — default "auto" is fine
            }
        }
    }
    
    val anime = MutableStateFlow<Anime?>(null)
    val episodeList = MutableStateFlow<List<Episode>>(emptyList())
    val currentEpisodeIndex = MutableStateFlow(-1)
    val streamingSources = MutableStateFlow<EpisodeSourcesResponse?>(null)
    val isLoading = MutableStateFlow(true)
    val hasError = MutableStateFlow(false)
    val errorMessage = MutableStateFlow("")
    val isBuffering = MutableStateFlow(false)
    
    val currentPosition = MutableStateFlow(0L)
    val totalDuration = MutableStateFlow(0L)
    val isPlaying = MutableStateFlow(false)
    val playbackSpeed = MutableStateFlow(1.0f)
    
    val selectedSource = MutableStateFlow<StreamingSource?>(null)
    val selectedSubtitle = MutableStateFlow<SubtitleTrack?>(null)
    val selectedVideoQuality = MutableStateFlow("auto")

    val autoSkipIntro = settingsStore.autoSkipIntro.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val defaultPlaybackSpeed = settingsStore.defaultPlaybackSpeed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1.0f
    )

    val autoPlayNextEpisode = settingsStore.autoPlayNextEpisode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val isEpisodeSwitch = MutableStateFlow(false)

    private val failedSources = mutableSetOf<String>()
    private var lastErrorTimestamp = 0L
    private val ERROR_COOLDOWN_MS = 2000L  // Minimum 2s between source switches

    fun loadAnimeDetails(animeId: Int, episodeNumber: Int) {
        viewModelScope.launch {
            isLoading.value = true
            hasError.value = false
            try {
                val detail = repository.getAnimeDetail(animeId).first()
                anime.value = detail
                if (detail != null) {
                    val eps = repository.getEpisodes(detail.id, detail.title)
                    episodeList.value = eps
                    val index = eps.indexOfFirst { it.number == episodeNumber }.coerceAtLeast(0)
                    currentEpisodeIndex.value = index
                    loadStreamingSourcesForIndex(index)
                } else {
                    errorMessage.value = "Failed to load anime details."
                    hasError.value = true
                    isLoading.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.value = "Failed to load anime details: ${e.localizedMessage}"
                hasError.value = true
                isLoading.value = false
            }
        }
    }

    fun loadStreamingSourcesForIndex(index: Int) {
        val eps = episodeList.value
        if (index !in eps.indices) return
        val ep = eps[index]
        
        // Reset player state on episode change
        currentPosition.value = 0L
        totalDuration.value = 0L
        isBuffering.value = false
        hasError.value = false
        errorMessage.value = ""
        streamingSources.value = null
        selectedSource.value = null
        selectedSubtitle.value = null
        
        viewModelScope.launch {
            isLoading.value = true
            hasError.value = false
            failedSources.clear()
            try {
                val sources = repository.getStreamingSources(ep.id)
                streamingSources.value = sources
                val primarySource = pickInitialSource(sources.sources)
                selectedSource.value = primarySource
                selectedSubtitle.value = sources.subtitles.firstOrNull { it.lang.lowercase() == "en" } ?: sources.subtitles.firstOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.value = "Failed to fetch streaming sources."
                hasError.value = true
            } finally {
                isLoading.value = false
            }
        }
    }

    fun handlePlaybackError(errorName: String) {
        val now = System.currentTimeMillis()
        if (now - lastErrorTimestamp < ERROR_COOLDOWN_MS) {
            errorMessage.value = "Playback error: $errorName. Tap to retry."
            hasError.value = true
            return
        }
        lastErrorTimestamp = now

        val currentSource = selectedSource.value ?: return
        failedSources.add(currentSource.url)

        // 1. Try backup URLs for the same quality tier
        val backupUrl = currentSource.backupUrls.firstOrNull { it !in failedSources }
        if (backupUrl != null) {
            android.util.Log.d("PlayerViewModel", "Trying backup URL for ${currentSource.quality}")
            selectedSource.value = currentSource.copy(
                url = backupUrl,
                backupUrls = currentSource.backupUrls.filter { it != backupUrl }
            )
            return
        }

        // 2. Fall back to another server/quality (Auto first, then same language)
        val allSources = streamingSources.value?.sources.orEmpty()
        val preferSub = isSubSource(currentSource)
        val nextSource = allSources.firstOrNull { src ->
            src.url !in failedSources &&
                src.quality.contains("Auto", ignoreCase = true) &&
                isSubSource(src) == preferSub
        } ?: allSources.firstOrNull { src ->
            src.url !in failedSources && isSubSource(src) == preferSub
        } ?: allSources.firstOrNull { src -> src.url !in failedSources }

        if (nextSource != null) {
            android.util.Log.d("PlayerViewModel", "Falling back to ${nextSource.quality}")
            selectedSource.value = nextSource
            selectedVideoQuality.value = parseResolutionLabel(nextSource.quality)
            return
        }

        errorMessage.value = "Playback error: $errorName. All servers failed."
        hasError.value = true
    }

    fun playNextEpisode() {
        val nextIndex = currentEpisodeIndex.value + 1
        if (nextIndex in episodeList.value.indices) {
            isEpisodeSwitch.value = true
            currentEpisodeIndex.value = nextIndex
            loadStreamingSourcesForIndex(nextIndex)
        }
    }

    fun playPrevEpisode() {
        val prevIndex = currentEpisodeIndex.value - 1
        if (prevIndex in episodeList.value.indices) {
            isEpisodeSwitch.value = true
            currentEpisodeIndex.value = prevIndex
            loadStreamingSourcesForIndex(prevIndex)
        }
    }

    fun selectSource(source: StreamingSource) {
        if (selectedSource.value?.url == source.url) {
            selectedVideoQuality.value = parseResolutionLabel(source.quality)
            return
        }
        failedSources.clear()
        hasError.value = false
        errorMessage.value = ""
        selectedSource.value = source
        selectedVideoQuality.value = parseResolutionLabel(source.quality)
    }

    /** Update quality preference — ExoPlayer handles track switching via LaunchedEffect in PlayerScreen. */
    fun selectQualityByResolution(resolution: String) {
        selectedVideoQuality.value = resolution
        viewModelScope.launch {
            try {
                settingsStore.setQuality(resolution)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun pickInitialSource(sources: List<StreamingSource>): StreamingSource? {
        if (sources.isEmpty()) return null
        // Prefer adaptive Auto/Adaptive streams — they are the most reliable across providers
        val autoSub = sources.firstOrNull { (it.quality.contains("Auto", ignoreCase = true) || it.quality.contains("Adaptive", ignoreCase = true)) && isSubSource(it) }
        val autoAny = sources.firstOrNull { it.quality.contains("Auto", ignoreCase = true) || it.quality.contains("Adaptive", ignoreCase = true) }
        return autoSub ?: autoAny ?: sources.first()
    }

    private fun findSourceForResolution(
        sources: List<StreamingSource>,
        resolution: String,
        preferSub: Boolean
    ): StreamingSource? {
        val typeTag = if (preferSub) "(SUB)" else "(DUB)"
        return sources.firstOrNull { src ->
            src.quality.contains(typeTag, ignoreCase = true) &&
                parseResolutionLabel(src.quality).equals(resolution, ignoreCase = true)
        }
    }

    private fun isSubSource(source: StreamingSource): Boolean {
        return source.quality.contains("(SUB)", ignoreCase = true) ||
            !source.quality.contains("(DUB)", ignoreCase = true)
    }

    private fun parseResolutionLabel(quality: String): String {
        return when {
            quality.contains("1080", ignoreCase = true) -> "1080p"
            quality.contains("720", ignoreCase = true) -> "720p"
            quality.contains("480", ignoreCase = true) -> "480p"
            quality.contains("360", ignoreCase = true) -> "360p"
            quality.contains("Auto", ignoreCase = true) || quality.contains("Adaptive", ignoreCase = true) -> "auto"
            else -> "auto"
        }
    }

    fun selectSubtitle(subtitle: SubtitleTrack?) {
        selectedSubtitle.value = subtitle
    }

    fun saveProgress(animeId: Int, progressMs: Long, durationMs: Long, episode: Episode? = null) {
        val currentAnime = anime.value ?: return
        val ep = episode ?: episodeList.value.getOrNull(currentEpisodeIndex.value) ?: return
        if (progressMs > 0 && durationMs > 0) {
            watchHistoryStore.saveProgress(
                animeId = animeId,
                title = currentAnime.title,
                coverImage = currentAnime.coverImage,
                episodeNumber = ep.number,
                episodeName = ep.name,
                progressMs = progressMs,
                durationMs = durationMs
            )
        }
    }

    suspend fun getSavedProgress(animeId: Int): Long {
        return watchHistoryStore.getProgress(animeId)?.progressMs ?: 0L
    }

    suspend fun getSavedProgressEntry(animeId: Int): WatchHistoryEntry? {
        return watchHistoryStore.getProgress(animeId)
    }
}
