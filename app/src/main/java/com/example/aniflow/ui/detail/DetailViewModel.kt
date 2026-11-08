package com.example.aniflow.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aniflow.data.ProviderMapping
import com.example.aniflow.data.MappingEvidence
import com.example.aniflow.data.ProviderMappingStore
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.data.repository.resolveEpisodes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Success(val anime: Anime, val episodes: List<Episode>) : DetailUiState()
    data class Empty(val anime: Anime) : DetailUiState()
    data class Ambiguous(val anime: Anime, val candidates: List<ProviderSearchResult>) : DetailUiState()
    data class NotFound(val anime: Anime) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

class DetailViewModel(
    private val repository: AnimeRepository,
    private val providerMappingStore: ProviderMappingStore,
    private val watchHistoryStore: com.example.aniflow.data.WatchHistoryStore
) : ViewModel() {
    val uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val watchHistoryEntry = MutableStateFlow<WatchHistoryEntry?>(null)
    /** Every show in history, for "More Like This" (skip what you finished). */
    val allHistory = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    /** The show itself, available as soon as metadata loads - before episodes resolve - so the
     *  TV hero can render while the provider lookup is still running. */
    val anime = MutableStateFlow<Anime?>(null)
    private var currentId: Int? = null

    /** Seasons, movies, OVAs and specials of this show, loaded in parallel with the detail. */
    val franchise = MutableStateFlow<List<FranchiseEntry>>(emptyList())
    private var franchiseJob: Job? = null

    /** Season-by-season trailers (TV detail), each from its own AniList entry. */
    val trailers = MutableStateFlow<List<com.example.aniflow.data.model.TrailerClip>>(emptyList())
    private var trailersJob: Job? = null
    private var trailersFor: Pair<Int, Int>? = null

    private fun refreshTrailers() {
        val a = anime.value ?: return
        val f = franchise.value
        val key = a.id to f.size
        if (trailersFor == key) return
        trailersFor = key
        trailersJob?.cancel()
        trailersJob = viewModelScope.launch {
            val ids = (listOf(a.id) + f.filter { it.mainLine || it.format == "MOVIE" }.map { it.id })
            trailers.value = com.example.aniflow.data.model.TrailerClip.build(a, f, repository.getTrailers(ids))
        }
    }

    fun retry() { currentId?.let { loadAnimeDetails(it) } }
    
    private var loadJob: Job? = null
    private var historyJob: Job? = null

    fun loadAnimeDetails(animeId: Int) {
        currentId = animeId
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            watchHistoryStore.historyFlow.collect { historyList ->
                watchHistoryEntry.value = historyList.firstOrNull { it.animeId == animeId }
                allHistory.value = historyList
            }
        }

        if (franchise.value.none { it.id == animeId }) {
            franchiseJob?.cancel()
            franchiseJob = viewModelScope.launch { franchise.value = repository.getFranchise(animeId); refreshTrailers() }
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            uiState.value = DetailUiState.Loading
            try {
                repository.getAnimeDetail(animeId).collect { detail ->
                    if (detail == null) {
                        if (anime.value == null) uiState.value = DetailUiState.Error("Couldn't load this anime. Check your connection and retry.")
                        return@collect
                    }
                    anime.value = detail
                    refreshTrailers()
                    loadEpisodes(detail)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // a retry cancelled this load; don't paint "cancelled" over the new one
            } catch (e: Exception) {
                uiState.value = DetailUiState.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun loadEpisodes(anime: Anime) {
        // getAnimeDetail emits cache first, then network. Don't re-resolve episodes (a provider
        // round-trip) for the second emission if nothing that affects the list changed.
        val current = uiState.value
        if (current is DetailUiState.Success && current.anime.id == anime.id &&
            current.anime.nextAiringEpisode == anime.nextAiringEpisode && current.anime.status == anime.status
        ) {
            uiState.value = current.copy(anime = anime)
            return
        }
        uiState.value = when (val result = repository.resolveEpisodes(anime)) {
            is EpisodeLookupResult.Matched ->
                if (result.episodes.isEmpty()) DetailUiState.Empty(anime) else DetailUiState.Success(anime, result.episodes)
            is EpisodeLookupResult.Ambiguous -> DetailUiState.Ambiguous(anime, result.candidates)
            EpisodeLookupResult.NotFound -> DetailUiState.NotFound(anime)
            is EpisodeLookupResult.Error -> DetailUiState.Error(result.message)
        }
    }

    fun confirmMapping(anime: Anime, selectedCandidate: ProviderSearchResult) {
        viewModelScope.launch {
            uiState.value = DetailUiState.Loading
            try {
                val evidence = MappingEvidence(
                    providerTitle = selectedCandidate.title,
                    anilistTitle = anime.englishTitle ?: anime.title,
                    matchedBy = "USER_CONFIRMED",
                    confidenceScore = 1.0
                )
                val mapping = ProviderMapping(
                    provider = ProviderId.ANILIGHT,
                    anilistId = anime.id,
                    slug = selectedCandidate.slug,
                    evidence = evidence
                )
                providerMappingStore.setMapping(mapping)
                loadEpisodes(anime)
            } catch (e: Exception) {
                uiState.value = DetailUiState.Error("Failed to save mapping: ${e.localizedMessage}")
            }
        }
    }
}
