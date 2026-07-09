package com.example.aniflow.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aniflow.data.*
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.isActive

class MainScreenViewModel(
    private val repository: AnimeRepository,
    private val watchlistStore: WatchlistStore,
    private val watchHistoryStore: WatchHistoryStore,
    private val context: android.content.Context
) : ViewModel() {

    private val settingsStore = SettingsStore(context)

    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    private val _trending = MutableStateFlow<List<Anime>>(emptyList())
    val trending = _trending.asStateFlow()

    private val _popular = MutableStateFlow<List<Anime>>(emptyList())
    val popular = _popular.asStateFlow()

    private val _seasonal = MutableStateFlow<List<Anime>>(emptyList())
    val seasonal = _seasonal.asStateFlow()

    private val _airingToday = MutableStateFlow<List<AiringAnime>>(emptyList())
    val airingToday = _airingToday.asStateFlow()

    private val _topRated = MutableStateFlow<List<Anime>>(emptyList())
    val topRated = _topRated.asStateFlow()

    private val _upcoming = MutableStateFlow<List<Anime>>(emptyList())
    val upcoming = _upcoming.asStateFlow()

    private val _recentlyUpdated = MutableStateFlow<List<Anime>>(emptyList())
    val recentlyUpdated = _recentlyUpdated.asStateFlow()

    private val _actionAnime = MutableStateFlow<List<Anime>>(emptyList())
    val actionAnime = _actionAnime.asStateFlow()

    private val _romanceAnime = MutableStateFlow<List<Anime>>(emptyList())
    val romanceAnime = _romanceAnime.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Anime>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _watchlist = MutableStateFlow<List<Anime>>(emptyList())
    val watchlist = _watchlist.asStateFlow()

    private val _history = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    val history = _history.asStateFlow()

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    init {
        loadData()
        observeWatchlist()
        observeHistory()
        setupSearchDebounce()
        checkForUpdates()
        startPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(15 * 60 * 1000L) // 15 minutes
                try {
                    val (airing, recent) = repository.refreshSchedule()
                    _airingToday.value = airing
                    _recentlyUpdated.value = recent
                    android.util.Log.d("MainScreenViewModel", "Periodic schedule refresh successful: ${airing.size} airing, ${recent.size} recent")
                } catch (e: Exception) {
                    android.util.Log.e("MainScreenViewModel", "Periodic schedule refresh failed", e)
                }
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun skipUpdate() {
        val info = _updateInfo.value
        if (info != null) {
            viewModelScope.launch {
                settingsStore.setDismissedVersionCode(info.versionCode)
            }
        }
        _updateInfo.value = null
    }

    private var isCheckingUpdates = false

    fun checkForUpdates(force: Boolean = false) {
        if (isCheckingUpdates) return
        isCheckingUpdates = true
        viewModelScope.launch {
            try {
                if (!force) {
                    val autoCheckEnabled = settingsStore.checkUpdatesStartup.first()
                    if (!autoCheckEnabled) return@launch
                }

                // Wait for network connection to establish on startup if not forced
                if (!force) {
                    kotlinx.coroutines.delay(3000L)
                }

                var retries = 2
                var info: AppUpdateInfo? = null
                while (retries > 0 && info == null && isActive) {
                    try {
                        info = repository.checkUpdates()
                    } catch (e: Exception) {
                        android.util.Log.w("MainScreenViewModel", "Update check failed, retries remaining: $retries", e)
                    }
                    if (info == null) {
                        retries--
                        if (retries > 0 && isActive) {
                            kotlinx.coroutines.delay(5000L)
                        }
                    }
                }

                if (info != null) {
                    // Update check succeeded, save timestamp
                    settingsStore.setLastUpdateCheckTime(System.currentTimeMillis())

                    val currentVersionCode = try {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode.toInt()
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode
                        }
                    } catch (e: Exception) {
                        1
                    }
                    android.util.Log.d("MainScreenViewModel", "checkForUpdates final: info=$info, current=$currentVersionCode")
                    
                    val dismissedCode = settingsStore.dismissedVersionCode.first()
                    if (info.versionCode > currentVersionCode && info.versionCode != dismissedCode) {
                        _updateInfo.value = info
                    }
                } else {
                    android.util.Log.e("MainScreenViewModel", "Update check failed after all retries")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainScreenViewModel", "Error in checkForUpdates", e)
            } finally {
                isCheckingUpdates = false
            }
        }
    }


    private fun observeHistory() {
        viewModelScope.launch {
            watchHistoryStore.historyFlow.collect { list ->
                _history.value = list
            }
        }
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            watchlistStore.watchlistFlow.collect { list ->
                _watchlist.value = list
            }
        }
    }

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre = _selectedGenre.asStateFlow()

    private val _hasNextPage = MutableStateFlow(false)
    val hasNextPage = _hasNextPage.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading = _isSearchLoading.asStateFlow()

    private var currentPage = 1

    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.length >= 2) {
                        _selectedGenre.value = null // clear genre when typing query
                        currentPage = 1
                        _hasNextPage.value = false
                        performSearch(query, 1, reset = true)
                    } else if (_selectedGenre.value == null) {
                        _searchResults.value = emptyList()
                        _hasNextPage.value = false
                        currentPage = 1
                    }
                }
        }
    }

    private suspend fun performSearch(query: String, page: Int, reset: Boolean) {
        if (_isSearchLoading.value && reset) return
        _isSearchLoading.value = true
        try {
            val searchPage = repository.searchAnime(query, page).first()
            if (reset) {
                _searchResults.value = searchPage.results
            } else {
                _searchResults.value = _searchResults.value + searchPage.results
            }
            _hasNextPage.value = searchPage.hasNextPage
            currentPage = searchPage.currentPage
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSearchLoading.value = false
        }
    }

    fun loadNextSearchPage() {
        val query = _searchQuery.value
        if (query.length < 2 || !_hasNextPage.value || _isSearchLoading.value) return
        viewModelScope.launch {
            performSearch(query, currentPage + 1, reset = false)
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Critical path: load in two batches to avoid rate limiting
                // Batch 1: Trending + Airing (most visible)
                val batch1 = listOf(
                    launch {
                        try {
                            _trending.value = repository.getTrending().first()
                        } catch (e: Exception) {
                            android.util.Log.e("MainScreenViewModel", "Error loading trending in loadData", e)
                        }
                    },
                    launch {
                        try {
                            _airingToday.value = repository.getAiringToday().first()
                        } catch (e: Exception) {
                            android.util.Log.e("MainScreenViewModel", "Error loading airingToday in loadData", e)
                        }
                    }
                )
                batch1.joinAll()

                // Batch 2: Popular + Seasonal
                val batch2 = listOf(
                    launch {
                        try {
                            _popular.value = repository.getPopular().first()
                        } catch (e: Exception) {
                            android.util.Log.e("MainScreenViewModel", "Error loading popular in loadData", e)
                        }
                    },
                    launch {
                        try {
                            _seasonal.value = repository.getSeasonal().first()
                        } catch (e: Exception) {
                            android.util.Log.e("MainScreenViewModel", "Error loading seasonal in loadData", e)
                        }
                    }
                )
                batch2.joinAll()
                _isLoading.value = false // Let user interact early!

                // If trending still looks like fallback data, retry once after a delay
                if (_trending.value.size <= 3 && _trending.value.firstOrNull()?.id == 1535) {
                    android.util.Log.w("MainScreenViewModel", "Trending appears to be fallback data, retrying...")
                    kotlinx.coroutines.delay(3000L)
                    val retryBatch1 = listOf(
                        launch {
                            try {
                                _trending.value = repository.getTrending().first()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        launch {
                            try {
                                _airingToday.value = repository.getAiringToday().first()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                    retryBatch1.joinAll()
                    
                    kotlinx.coroutines.delay(1000L)
                    val retryBatch2 = listOf(
                        launch {
                            try {
                                _popular.value = repository.getPopular().first()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        launch {
                            try {
                                _seasonal.value = repository.getSeasonal().first()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                    retryBatch2.joinAll()
                }

                // Background loading for non-critical lists (staggered)
                kotlinx.coroutines.delay(500L)
                launch {
                    try {
                        _recentlyUpdated.value = repository.getRecentlyUpdated().first()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                launch {
                    try {
                        _topRated.value = repository.getTopRated().first()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                kotlinx.coroutines.delay(500L)
                launch {
                    try {
                        _upcoming.value = repository.getUpcoming().first()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                launch {
                    try {
                        _actionAnime.value = repository.getActionAnime().first()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                kotlinx.coroutines.delay(500L)
                launch {
                    try {
                        _romanceAnime.value = repository.getAnimeByGenre("Romance").first()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    fun onGenreSelected(genre: String?) {
        _selectedGenre.value = genre
        if (genre != null) {
            _searchQuery.value = "" // clear search input when selecting genre
            currentPage = 1
            _hasNextPage.value = false
            viewModelScope.launch {
                _isSearchLoading.value = true
                try {
                    val list = repository.getAnimeByGenre(genre).first()
                    _searchResults.value = list
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isSearchLoading.value = false
                }
            }
        } else {
            _searchResults.value = emptyList()
            _hasNextPage.value = false
            currentPage = 1
        }
    }


    fun setTab(index: Int) {
        _currentTab.value = index
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleWatchlist(anime: Anime) {
        viewModelScope.launch {
            if (watchlistStore.isBookmarked(anime.id)) {
                watchlistStore.removeFromWatchlist(anime.id)
            } else {
                watchlistStore.addToWatchlist(anime)
            }
        }
    }
}
