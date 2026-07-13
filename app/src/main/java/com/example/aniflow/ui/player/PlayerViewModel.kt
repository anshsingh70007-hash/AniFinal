package com.example.aniflow.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PlayerViewModel(
    private val repository: AnimeRepository,
    private val watchHistoryStore: WatchHistoryStore,
    private val settingsStore: SettingsStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    
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
    
    val selectedServer = MutableStateFlow<String?>(null)
    val selectedAudioType = MutableStateFlow<AudioType>(AudioType.SUB)
    val selectedSource = MutableStateFlow<SourceEndpoint?>(null)
    val selectedSubtitle = MutableStateFlow<SubtitleTrack?>(null)
    val selectedVideoQuality = MutableStateFlow("auto")

    val selectedQualityPolicy = selectedVideoQuality.map { getQualityPolicyFromString(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, QualityPolicy.Auto)

    val availableHeightsForCurrentEndpoint = MutableStateFlow<List<Int>>(emptyList())
    val observedHeight = MutableStateFlow<Int?>(null)

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

    // Centralized failover & cooldown controller using monotonic clock (nanotime)
    private val endpointCooldowns = mutableMapOf<String, Long>() // endpointId -> cooldownUntilNs
    private var stablePlaybackJob: Job? = null

    // Generation-based stale write protection
    private var generationId = 0L
    private var resolutionJob: Job? = null
    private var reResolveAttempt = 0

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
                // ignore
            }
        }
        viewModelScope.launch {
            try {
                val lang = settingsStore.languagePreference.first()
                selectedAudioType.value = if (lang.lowercase() == "dub") AudioType.DUB else AudioType.SUB
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun markEndpointFailed(endpointId: String) {
        val cooldownDurationNs = 30_000_000_000L // 30 seconds
        endpointCooldowns[endpointId] = System.nanoTime() + cooldownDurationNs
        android.util.Log.d("PlayerViewModel", "Cooldown applied to $endpointId")
    }

    private fun isEndpointCooldown(endpointId: String): Boolean {
        val until = endpointCooldowns[endpointId] ?: return false
        return System.nanoTime() < until
    }

    private fun clearCooldown(endpointId: String) {
        endpointCooldowns.remove(endpointId)
        android.util.Log.d("PlayerViewModel", "Cooldown cleared for $endpointId")
    }

    fun onFirstFrameRendered() {
        val current = selectedSource.value ?: return
        stablePlaybackJob?.cancel()
        stablePlaybackJob = viewModelScope.launch {
            delay(3000L) // 3 seconds stable play milestone
            clearCooldown(current.id)
            reResolveAttempt = 0
        }
    }

    fun onPlaybackStateChanged(playing: Boolean) {
        if (!playing) {
            stablePlaybackJob?.cancel()
        }
    }

    fun loadAnimeDetails(animeId: Int, episodeNumber: Int) {
        viewModelScope.launch {
            isLoading.value = true
            hasError.value = false
            try {
                val detail = repository.getAnimeDetail(animeId).first()
                anime.value = detail
                if (detail != null) {
                    val identity = AnimeIdentity(
                        anilistId = detail.id,
                        title = detail.title,
                        englishTitle = detail.englishTitle,
                        nativeTitle = null,
                        seasonYear = detail.seasonYear,
                        format = if (detail.episodes == 1) "MOVIE" else "TV"
                    )
                    val result = repository.getEpisodes(identity)
                    val eps = when (result) {
                        is EpisodeLookupResult.Matched -> result.episodes
                        else -> emptyList()
                    }
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
        
        currentPosition.value = 0L
        totalDuration.value = 0L
        isBuffering.value = false
        hasError.value = false
        errorMessage.value = ""
        streamingSources.value = null
        selectedSource.value = null
        selectedSubtitle.value = null
        availableHeightsForCurrentEndpoint.value = emptyList()
        observedHeight.value = null

        generationId++
        val currentGen = generationId

        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            isLoading.value = true
            hasError.value = false
            try {
                val currentAnime = anime.value ?: return@launch
                val request = EpisodeRequest(
                    provider = ProviderId.ANILIGHT,
                    seriesSlug = currentAnime.title,
                    episodeId = ep.id,
                    animeId = currentAnime.id,
                    episodeNumber = ep.number,
                    audioType = selectedAudioType.value
                )
                val result = repository.getStreamingSources(request)
                if (currentGen != generationId) return@launch

                if (result is ProviderPlaybackResult.Success) {
                    val sources = result.response
                    streamingSources.value = sources
                    
                    val targetAudio = selectedAudioType.value
                    val allSources = sources.sources
                    if (allSources.isNotEmpty()) {
                        var langSources = allSources.filter { it.audioType == targetAudio }
                        if (langSources.isEmpty()) {
                            val fallbackAudio = if (targetAudio == AudioType.SUB) AudioType.DUB else AudioType.SUB
                            langSources = allSources.filter { it.audioType == fallbackAudio }
                            selectedAudioType.value = fallbackAudio
                        }

                        val verifiedSource = withContext(ioDispatcher) {
                            val startTime = System.currentTimeMillis()
                            val deferreds = langSources.map { source ->
                                async {
                                    val isLive = try {
                                        withTimeoutOrNull(1500L) {
                                            val status = repository.checkUrlStatus(source.url, source.headers)
                                            android.util.Log.d("PlayerViewModel", "Checked server ${source.server.value} (${source.audioType}): status=$status in ${System.currentTimeMillis() - startTime}ms")
                                            status in 200..399
                                        } ?: false
                                    } catch (e: Exception) {
                                        false
                                    }
                                    if (isLive) source else null
                                }
                            }
                            deferreds.map { it.await() }.firstOrNull { it != null }
                        }

                        var chosenSource = verifiedSource
                        if (chosenSource == null) {
                            val fallbackAudio = if (selectedAudioType.value == AudioType.SUB) AudioType.DUB else AudioType.SUB
                            val fallbackSources = allSources.filter { it.audioType == fallbackAudio }
                            if (fallbackSources.isNotEmpty()) {
                                val verifiedFallback = withContext(ioDispatcher) {
                                    val deferreds = fallbackSources.map { source ->
                                        async {
                                            val isLive = try {
                                                withTimeoutOrNull(1500L) {
                                                    val status = repository.checkUrlStatus(source.url, source.headers)
                                                    status in 200..399
                                                } ?: false
                                            } catch (e: Exception) {
                                                false
                                            }
                                            if (isLive) source else null
                                        }
                                    }
                                    deferreds.map { it.await() }.firstOrNull { it != null }
                                }
                                if (verifiedFallback != null) {
                                    chosenSource = verifiedFallback
                                    selectedAudioType.value = fallbackAudio
                                }
                            }
                        }

                        if (chosenSource == null) {
                            chosenSource = langSources.firstOrNull()
                        }

                        if (currentGen != generationId) return@launch

                        val chosenServer = chosenSource?.server?.value
                        selectedServer.value = chosenServer

                        val initialSource = resolveSourceInternal(allSources, chosenServer, selectedAudioType.value, selectedVideoQuality.value)
                        selectedSource.value = initialSource

                        updateAvailableHeightsFromStaticSources(allSources, chosenServer, selectedAudioType.value)
                    }
                    
                    selectedSubtitle.value = sources.subtitles.firstOrNull { it.lang.lowercase() == "en" } ?: sources.subtitles.firstOrNull()
                } else if (result is ProviderPlaybackResult.Error) {
                    errorMessage.value = result.message
                    hasError.value = true
                }
            } catch (e: Exception) {
                if (currentGen == generationId) {
                    e.printStackTrace()
                    errorMessage.value = "Failed to fetch streaming sources."
                    hasError.value = true
                }
            } finally {
                if (currentGen == generationId) {
                    isLoading.value = false
                }
            }
        }
    }

    private fun reResolveSources(resumePositionMs: Long) {
        val index = currentEpisodeIndex.value
        val eps = episodeList.value
        if (index !in eps.indices) return
        val ep = eps[index]

        viewModelScope.launch {
            isLoading.value = true
            try {
                val delayMs = minOf(10000L, 1000L * Math.pow(2.0, reResolveAttempt.toDouble()).toLong() + (0..500).random())
                reResolveAttempt++
                delay(delayMs)

                val currentAnime = anime.value ?: return@launch
                val request = EpisodeRequest(
                    provider = ProviderId.ANILIGHT,
                    seriesSlug = currentAnime.title,
                    episodeId = ep.id,
                    animeId = currentAnime.id,
                    episodeNumber = ep.number,
                    audioType = selectedAudioType.value
                )
                val sourcesResult = repository.getStreamingSources(request)
                if (sourcesResult is ProviderPlaybackResult.Success) {
                    streamingSources.value = sourcesResult.response
                    val allSources = sourcesResult.response.sources
                    val matching = allSources.firstOrNull { it.server.value == selectedServer.value && it.audioType == selectedAudioType.value }
                        ?: allSources.firstOrNull()
                    
                    if (matching != null) {
                        selectedSource.value = matching
                        currentPosition.value = resumePositionMs
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Re-resolution failed", e)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun handlePlaybackError(error: androidx.media3.common.PlaybackException, currentPositionMs: Long) {
        val currentSource = selectedSource.value ?: return
        markEndpointFailed(currentSource.id)

        val cause = error.cause
        val responseCode = if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
            cause.responseCode
        } else {
            -1
        }

        if (responseCode == 401 || responseCode == 403 || responseCode == 410) {
            android.util.Log.d("PlayerViewModel", "HTTP $responseCode. Re-resolving URL...")
            reResolveSources(currentPositionMs)
            return
        }

        val isDecoderError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED

        if (isDecoderError) {
            val heights = availableHeightsForCurrentEndpoint.value
            val currentHeight = when (val policy = currentSource.qualityPolicy) {
                is QualityPolicy.FixedHeight -> policy.height
                else -> observedHeight.value ?: 1080
            }
            val lowerHeights = heights.filter { it < currentHeight }
            if (lowerHeights.isNotEmpty()) {
                val nextHeight = lowerHeights.first()
                android.util.Log.d("PlayerViewModel", "Decoder error: switching to $nextHeight")
                selectQualityByResolution(nextHeight.toString())
                return
            }
        }

        // Failover
        val allSources = streamingSources.value?.sources.orEmpty()
        val nextSource = allSources.firstOrNull { src ->
            !isEndpointCooldown(src.id) && src.audioType == selectedAudioType.value && src.server.value != selectedServer.value
        } ?: allSources.firstOrNull { src ->
            !isEndpointCooldown(src.id) && src.audioType != selectedAudioType.value
        } ?: allSources.firstOrNull { src ->
            !isEndpointCooldown(src.id)
        } ?: allSources.firstOrNull { src ->
            src.id != currentSource.id
        }

        if (nextSource != null) {
            android.util.Log.d("PlayerViewModel", "Failover switching to ${nextSource.server.value}")
            selectedServer.value = nextSource.server.value
            selectedAudioType.value = nextSource.audioType
            selectedSource.value = nextSource
            updateAvailableHeightsFromStaticSources(allSources, nextSource.server.value, nextSource.audioType)
        } else {
            errorMessage.value = "Playback failed: ${error.localizedMessage ?: "All servers cooled down."}"
            hasError.value = true
        }
    }

    fun updateAvailableHeightsFromStaticSources(sources: List<SourceEndpoint>, server: String?, audioType: AudioType) {
        if (server == null) return
        val serverSources = sources.filter { it.server.value == server && it.audioType == audioType }
        val hasAdaptive = serverSources.any { it.qualityPolicy is QualityPolicy.Auto }
        if (hasAdaptive) {
            // Also add HLS heights if parsed at provider level
            val hlsHeights = serverSources.flatMap { it.hlsHeights }.distinct().sortedDescending()
            if (hlsHeights.isNotEmpty()) {
                availableHeightsForCurrentEndpoint.value = hlsHeights
            } else {
                availableHeightsForCurrentEndpoint.value = listOf(1080, 720, 480, 360)
            }
        } else {
            val heights = serverSources.mapNotNull {
                when (val q = it.qualityPolicy) {
                    is QualityPolicy.FixedHeight -> q.height
                    else -> null
                }
            }.distinct().sortedDescending()
            availableHeightsForCurrentEndpoint.value = heights
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

    fun selectServerAndType(server: String, audioType: AudioType) {
        if (selectedServer.value == server && selectedAudioType.value == audioType) return
        selectedServer.value = server
        selectedAudioType.value = audioType
        hasError.value = false
        errorMessage.value = ""
        
        val allSources = streamingSources.value?.sources.orEmpty()
        val nextSource = resolveSourceInternal(allSources, server, audioType, selectedVideoQuality.value)
        if (nextSource != null) {
            selectedSource.value = nextSource
            updateAvailableHeightsFromStaticSources(allSources, server, audioType)
        }
    }

    fun selectQualityByResolution(resolution: String) {
        selectedVideoQuality.value = resolution
        viewModelScope.launch {
            try {
                settingsStore.setQuality(resolution)
            } catch (e: Exception) {
                // ignore
            }
        }
        
        val currentSource = selectedSource.value ?: return
        val server = selectedServer.value ?: return
        val audio = selectedAudioType.value
        val allSources = streamingSources.value?.sources.orEmpty()
        
        if (currentSource.qualityPolicy is QualityPolicy.Auto) {
            return
        }
        
        val targetPolicy = getQualityPolicyFromString(resolution)
        if (targetPolicy is QualityPolicy.FixedHeight) {
            if (currentSource.qualityPolicy is QualityPolicy.FixedHeight && currentSource.qualityPolicy.height == targetPolicy.height) {
                return
            }
            val nextSource = resolveSourceInternal(allSources, server, audio, resolution)
            if (nextSource != null && nextSource.url != currentSource.url) {
                selectedSource.value = nextSource
            }
        } else if (targetPolicy is QualityPolicy.Auto || targetPolicy is QualityPolicy.MaxAvailable) {
            val nextSource = resolveSourceInternal(allSources, server, audio, resolution)
            if (nextSource != null && nextSource.url != currentSource.url) {
                selectedSource.value = nextSource
            }
        }
    }

    private fun resolveSourceInternal(
        sources: List<SourceEndpoint>,
        server: String?,
        audioType: AudioType,
        resolution: String
    ): SourceEndpoint? {
        if (server == null || sources.isEmpty()) return null
        val serverSources = sources.filter { it.server.value == server && it.audioType == audioType }
        if (serverSources.isEmpty()) return null

        val targetPolicy = getQualityPolicyFromString(resolution)
        val exactMatch = serverSources.firstOrNull { it.qualityPolicy == targetPolicy }
        if (exactMatch != null) return exactMatch

        if (targetPolicy is QualityPolicy.FixedHeight) {
            val autoMatch = serverSources.firstOrNull { it.qualityPolicy is QualityPolicy.Auto }
            if (autoMatch != null) return autoMatch
        }

        return serverSources.firstOrNull()
    }

    fun getQualityPolicyFromString(str: String): QualityPolicy {
        return when {
            str.contains("1080") -> QualityPolicy.FixedHeight(1080)
            str.contains("720") -> QualityPolicy.FixedHeight(720)
            str.contains("480") -> QualityPolicy.FixedHeight(480)
            str.contains("360") -> QualityPolicy.FixedHeight(360)
            str.contains("auto") -> QualityPolicy.Auto
            else -> QualityPolicy.MaxAvailable
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
