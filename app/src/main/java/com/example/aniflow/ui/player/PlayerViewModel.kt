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
                val primarySource = sources.sources.firstOrNull { it.isM3U8 } ?: sources.sources.firstOrNull()
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
        val currentSource = selectedSource.value ?: return
        failedSources.add(currentSource.url)
        val isCurrentDub = currentSource.quality.contains("DUB", ignoreCase = true)
        val sources = streamingSources.value?.sources ?: return
        val nextSource = sources.find { src ->
            src.url !in failedSources &&
            src.quality.contains("DUB", ignoreCase = true) == isCurrentDub
        }
        
        if (nextSource != null) {
            android.util.Log.d("PlayerViewModel", "Playback failed with $errorName. Trying next source: ${nextSource.quality}")
            selectedSource.value = nextSource
        } else {
            errorMessage.value = "Playback error: $errorName. All matching servers failed."
            hasError.value = true
        }
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
        failedSources.clear()
        selectedSource.value = source
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
