package com.example.aniflow.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aniflow.data.ProviderMapping
import com.example.aniflow.data.MappingEvidence
import com.example.aniflow.data.ProviderMappingStore
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
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
    private val providerMappingStore: ProviderMappingStore
) : ViewModel() {
    val uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    
    private var loadJob: Job? = null

    fun loadAnimeDetails(animeId: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            uiState.value = DetailUiState.Loading
            try {
                repository.getAnimeDetail(animeId).collect { detail ->
                    if (detail == null) {
                        uiState.value = DetailUiState.Error("Anime details not found.")
                        return@collect
                    }
                    loadEpisodes(detail)
                }
            } catch (e: Exception) {
                uiState.value = DetailUiState.Error("Network error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun loadEpisodes(anime: Anime) {
        val identity = AnimeIdentity(
            anilistId = anime.id,
            title = anime.title,
            englishTitle = anime.englishTitle,
            nativeTitle = null,
            seasonYear = anime.seasonYear,
            format = if (anime.episodes == 1) "MOVIE" else "TV"
        )
        val result = repository.getEpisodes(identity)
        when (result) {
            is EpisodeLookupResult.Matched -> {
                if (result.episodes.isEmpty()) {
                    uiState.value = DetailUiState.Empty(anime)
                } else {
                    uiState.value = DetailUiState.Success(anime, result.episodes)
                }
            }
            is EpisodeLookupResult.Ambiguous -> {
                uiState.value = DetailUiState.Ambiguous(anime, result.candidates)
            }
            is EpisodeLookupResult.NotFound -> {
                uiState.value = DetailUiState.NotFound(anime)
            }
            is EpisodeLookupResult.Error -> {
                uiState.value = DetailUiState.Error(result.message)
            }
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
