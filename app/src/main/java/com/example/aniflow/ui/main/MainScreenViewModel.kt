package com.example.aniflow.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aniflow.data.*
import com.example.aniflow.data.model.*
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.ui.redesign.theme.isBleachTybw
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

class MainScreenViewModel(
    private val repository: AnimeRepository,
    private val watchlistStore: WatchlistStore,
    private val watchHistoryStore: WatchHistoryStore,
    private val userFeedbackStore: UserFeedbackStore,
    private val context: android.content.Context
) : ViewModel() {

    private val settingsStore = SettingsStore(context)

    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    private val _userFeedbackList = MutableStateFlow<List<UserFeedback>>(emptyList())
    val userFeedbackList = _userFeedbackList.asStateFlow()

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

    /** Extra home rows: movies, last season, genre spotlights, best of the year. */
    private val _shelves = MutableStateFlow<List<com.example.aniflow.data.repository.HomeShelf>>(emptyList())
    val shelves = _shelves.asStateFlow()

    private val _weekSchedule = MutableStateFlow<List<com.example.aniflow.data.model.AiringAnime>>(emptyList())
    val weekSchedule = _weekSchedule.asStateFlow()
    /** True once a schedule fetch has finished with nothing to show, so the screen offers Retry. */
    private val _weekScheduleFailed = MutableStateFlow(false)
    val weekScheduleFailed = _weekScheduleFailed.asStateFlow()
    private var weekJob: Job? = null

    private var homeJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastHomeLoad = 0L

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

    /** AniList id -> newest episode out, for Continue Watching shows that are still airing. */
    private val _latestAired = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val latestAired = _latestAired.asStateFlow()

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    /** "Because you watched X": X's title and picks like it. Null until there is history. */
    private val _becauseYouWatched = MutableStateFlow<Pair<String, List<Anime>>?>(null)
    val becauseYouWatched = _becauseYouWatched.asStateFlow()

    /** Ranked by the on-device taste model (genres of what you watch); empty with no history. */
    private val _topPicks = MutableStateFlow<List<Anime>>(emptyList())
    val topPicks = _topPicks.asStateFlow()

    // Details of recently watched shows, fetched once per ViewModel (they carry genres + recs).
    private val seedDetails = java.util.concurrent.ConcurrentHashMap<Int, Anime>()

    // Self-heal: if the app opened offline (or rows failed), reload them the moment a network appears
    // instead of leaving empty rows until the next periodic refresh. Declared before init, which
    // registers it (properties below init are still null while it runs).
    private val connectivity = context.applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            if (!_isLoading.value && (_trending.value.isEmpty() || _popular.value.isEmpty() || _seasonal.value.isEmpty())) {
                android.util.Log.i("MainScreenViewModel", "Network back with empty rows: reloading")
                loadData()
            }
            if (_weekScheduleFailed.value) retryWeekSchedule()
        }
    }

    init {
        loadData()
        observeWatchlist()
        observeHistory()
        observeSuggestions()
        observeUserFeedback()
        setupSearchDebounce()
        checkForUpdates()
        startPeriodicRefresh()
        watchConnectivity()
    }

    private fun observeUserFeedback() {
        viewModelScope.launch {
            try {
                userFeedbackStore.feedbackListFlow.collect { list ->
                    _userFeedbackList.value = list.sortedByDescending { it.timestamp }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainScreenViewModel", "Failed to observe user feedback", e)
            }
        }
    }

    private fun watchConnectivity() {
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(15 * 60 * 1000L) // 15 minutes
                try {
                    val (airing, _) = repository.refreshSchedule()
                    // A failed refresh returns empty lists; never blank a row that has data.
                    if (airing.isNotEmpty()) _airingToday.value = airing
                    // Every row re-streams on its own every half hour (the repository's 10-minute
                    // caches make that a real network fetch), so a TV left on for days still
                    // shows this week's episodes and charts. Rows only ever swap to non-empty data.
                    if (System.currentTimeMillis() - lastHomeLoad >= HOME_STALE_MS) loadData(silent = true)
                    android.util.Log.d("MainScreenViewModel", "Periodic refresh: ${airing.size} airing")
                } catch (e: Exception) {
                    android.util.Log.e("MainScreenViewModel", "Periodic schedule refresh failed", e)
                }
                try {
                    userFeedbackStore.refresh()
                } catch (e: Exception) {
                    android.util.Log.e("MainScreenViewModel", "Periodic feedback refresh failed", e)
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
                // Check immediately on startup to enforce emergency maintenance and updates

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
                    // Maintenance was switched off (or ran out): let people back in.
                    if (_updateInfo.value?.maintenance == true && !info.maintenance) _updateInfo.value = null
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
                    android.util.Log.d("MainScreenViewModel", "checkForUpdates: dismissedCode=$dismissedCode")
                    if (info.maintenance) {
                        // Signed and time-limited (UpdateChannel): show it regardless of version.
                        _updateInfo.value = info
                    } else if (info.versionCode > currentVersionCode && (info.forceUpdate || info.versionCode != dismissedCode)) {
                        if (info.silentUpdate) {
                            android.util.Log.d("MainScreenViewModel", "checkForUpdates: Silent update (minor listing/episode fix). Skipping modal dialog to avoid bothering user.")
                            // Do NOT show takeover update modal to bother user. The dynamic fixes and background sync apply seamlessly.
                        } else {
                            android.util.Log.d("MainScreenViewModel", "checkForUpdates: Major/UI feature update. Setting updateInfo state!")
                            _updateInfo.value = info
                        }
                    } else {
                        android.util.Log.d("MainScreenViewModel", "checkForUpdates: Conditions not met. info.versionCode=${info.versionCode}, currentVersionCode=$currentVersionCode, dismissedCode=$dismissedCode")
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
        // Which of the shows you're watching have a newer episode out. Re-asked when the set of
        // shows changes; a few seconds' wait keeps it out of the cold-start burst of home rows.
        viewModelScope.launch {
            watchHistoryStore.historyFlow
                .map { list -> list.sortedByDescending { it.lastWatchedTime }.take(30).map { it.animeId }.sorted() }
                .distinctUntilChanged()
                .collectLatest { ids ->
                    if (ids.isEmpty()) { _latestAired.value = emptyMap(); return@collectLatest }
                    kotlinx.coroutines.delay(3_000)
                    val got = repository.getLatestAired(ids)
                    if (got.isNotEmpty() || _latestAired.value.isEmpty()) _latestAired.value = got
                }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSuggestions() {
        viewModelScope.launch {
            // Wait out the cold-start burst of home rows (AniList rate limit).
            kotlinx.coroutines.delay(2_500)
            // Only a change in *which* shows were watched recently matters, not the 15 s progress saves.
            val seeds = watchHistoryStore.historyFlow
                .map { list -> list.sortedByDescending { it.lastWatchedTime }.take(3) }
                .distinctUntilChanged { a, b -> a.map { it.animeId } == b.map { it.animeId } }
            combine(seeds, _trending, _popular, _topRated, _seasonal) { recent, t, p, r, se -> recent to (t + p + r + se) }
                .debounce(1_200)
                .collectLatest { (recent, pool) -> rebuildSuggestions(recent, pool) }
        }
    }

    private suspend fun rebuildSuggestions(recent: List<WatchHistoryEntry>, pool: List<Anime>) {
        if (recent.isEmpty()) {
            _becauseYouWatched.value = null
            _topPicks.value = emptyList()
            return
        }
        for (h in recent) {
            if (seedDetails.containsKey(h.animeId)) continue
            val d = try {
                kotlinx.coroutines.withTimeoutOrNull(10_000) { repository.getAnimeDetail(h.animeId).filterNotNull().firstOrNull() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (d != null) seedDetails[h.animeId] = d
        }
        val seen = (_history.value.map { it.animeId } + recent.map { it.animeId }).toSet()
        val watched = recent.mapNotNull { h -> seedDetails[h.animeId]?.let { it to h } }
        val taste = Suggestions.genreProfile(watched)

        val lead = watched.firstOrNull { it.first.recommendations.isNotEmpty() }?.first
        val because = lead?.let { l ->
            val stem = titleStem(l.title)
            Suggestions.moreLikeThis(l, emptySet(), seen, taste)
                .filter { stem.isEmpty() || titleStem(it.title) != stem }   // sequels/spin-offs have their own rows
                .take(15)
        }.orEmpty()
        _becauseYouWatched.value = if (lead != null && because.size >= 4) (lead.englishTitle ?: lead.title) to because else null

        // Other seasons of shows you're already on aren't "picks"; their franchise rows cover them.
        val watchedStems = watched.map { titleStem(it.first.title) }.filter { it.isNotEmpty() }.toSet()
        val recPool = (watched.flatMap { it.first.recommendations } + pool).filter { titleStem(it.title) !in watchedStems }
        _topPicks.value = if (taste.isEmpty()) emptyList()
        else Suggestions.rank(recPool, taste, seen + because.map { it.id }.toSet(), limit = 20)
    }

    /** First two words, lowercased: "Black Clover: Sword of…" and "Black Clover" share "black clover". */
    private fun titleStem(title: String): String =
        title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.take(2).joinToString(" ")

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
    private var searchJob: Job? = null

    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        _selectedGenre.value = null // clear genre when typing query
                        currentPage = 1
                        _hasNextPage.value = false
                        searchJob?.cancel()
                        searchJob = viewModelScope.launch {
                            performSearch(query.trim(), 1, reset = true)
                        }
                    } else if (_selectedGenre.value == null) {
                        searchJob?.cancel()
                        _hasNextPage.value = false
                        currentPage = 1
                        loadDefaultBrowseAnime()
                    }
                }
        }
    }

    private suspend fun performSearch(query: String, page: Int, reset: Boolean) {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSearchLoading.value = false
        }
    }

    fun loadNextSearchPage() {
        val query = _searchQuery.value
        if (query.length < 2 || !_hasNextPage.value || _isSearchLoading.value) return
        searchJob = viewModelScope.launch {
            performSearch(query, currentPage + 1, reset = false)
        }
    }

    private suspend fun prioritizeBleachTybw(list: List<Anime>): List<Anime> {
        if (list.isEmpty()) return list
        val bleachCalamityIndex = list.indexOfFirst { 
            it.id == 185874 || ((it.englishTitle ?: it.title).contains("Calamity", ignoreCase = true) && it.isBleachTybw()) 
        }
        if (bleachCalamityIndex >= 0) {
            val bleachItem = list[bleachCalamityIndex]
            val mutable = list.toMutableList()
            mutable.removeAt(bleachCalamityIndex)
            mutable.add(0, bleachItem)
            return mutable
        }
        val anyBleachIndex = list.indexOfFirst { it.isBleachTybw() || it.id in listOf(185874, 169755, 116674) }
        if (anyBleachIndex >= 0) {
            val mutable = list.toMutableList()
            mutable.removeAt(anyBleachIndex)
            val part4Anime = repository.getAnimeDetail(185874).first()
            if (part4Anime != null) {
                mutable.add(0, part4Anime)
                return mutable
            }
        }
        return try {
            val bleachAnime = repository.getAnimeDetail(185874).first()
            if (bleachAnime != null) {
                listOf(bleachAnime) + list.filter { it.id !in listOf(185874, 169755, 116674) }
            } else {
                list
            }
        } catch (e: Exception) {
            list
        }
    }

    /**
     * Every section streams on its own: the repository emits the disk cache first (instant) and
     * the network result after it. Previously the home waited behind one spinner for the slowest
     * of four sections, and seven sections used `.first()` — which takes the cached emission and
     * cancels the flow, so whenever a cache existed those rows never refreshed from the network.
     */
    /** The home is visible again (app resumed): refresh if the rows are older than half an hour. */
    fun refreshIfStale() {
        if (lastHomeLoad != 0L && System.currentTimeMillis() - lastHomeLoad >= HOME_STALE_MS) loadData(silent = true)
    }

    private fun loadData(silent: Boolean = false) {
        if (homeJob?.isActive == true) return
        lastHomeLoad = System.currentTimeMillis()
        homeJob = viewModelScope.launch {
            if (!silent) _isLoading.value = true
            val primary = listOf(
                streamSection("trending", repository.getTrending(), _trending),
                streamSection("airing", repository.getAiringToday(), _airingToday),
                streamSection("popular", repository.getPopular(), _popular),
                streamSection("seasonal", repository.getSeasonal(), _seasonal),
            )
            // Secondary rows: a small stagger keeps the cold start under AniList's rate limit.
            kotlinx.coroutines.delay(400L)
            val secondary = listOf(
                streamSection("recentlyUpdated", repository.getRecentlyUpdated(), _recentlyUpdated),
                streamSection("topRated", repository.getTopRated(), _topRated),
                streamSection("upcoming", repository.getUpcoming(), _upcoming),
                streamSection("action", repository.getActionAnime(), _actionAnime),
                streamSection("romance", repository.getRomanceAnime(), _romanceAnime),
                streamSection("shelves", repository.getHomeShelves(), _shelves),
            )
            weekJob = loadWeekSchedule()
            (primary + secondary + listOfNotNull(weekJob)).joinAll()
            _isLoading.value = false
        }
    }

    private fun <T> kotlinx.coroutines.CoroutineScope.streamSection(
        name: String,
        source: kotlinx.coroutines.flow.Flow<List<T>>,
        target: MutableStateFlow<List<T>>,
    ) = launch {
        try {
            source.collect { list ->
                if (list.isNotEmpty()) {
                    target.value = list
                    _isLoading.value = false   // first real data anywhere ends the spinner
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("MainScreenViewModel", "Section $name failed", e)
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.loadWeekSchedule() = launch {
        _weekScheduleFailed.value = false
        streamSection("week", repository.getWeekSchedule(), _weekSchedule).join()
        _weekScheduleFailed.value = _weekSchedule.value.isEmpty()
    }

    fun retryWeekSchedule() {
        if (weekJob?.isActive == true) return
        weekJob = viewModelScope.loadWeekSchedule()
    }

    fun onGenreSelected(genre: String?) {
        searchJob?.cancel()
        _selectedGenre.value = genre
        if (genre != null) {
            _searchQuery.value = "" // clear search input when selecting genre
            currentPage = 1
            _hasNextPage.value = false
            searchJob = viewModelScope.launch {
                _isSearchLoading.value = true
                try {
                    val list = repository.getAnimeByGenre(genre).first()
                    _searchResults.value = list
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isSearchLoading.value = false
                }
            }
        } else {
            currentPage = 1
            _hasNextPage.value = false
            loadDefaultBrowseAnime()
        }
    }

    fun setTab(index: Int) {
        _currentTab.value = index
        // Rebuild the no-query list on every visit: the first one is usually built at start-up,
        // before Trending has arrived, and would otherwise stay on the all-time classics.
        if (index == 1 && _searchQuery.value.isBlank() && _selectedGenre.value == null) {
            loadDefaultBrowseAnime()
        }
        if (index == 4 && _weekScheduleFailed.value) retryWeekSchedule()
    }

    private fun loadDefaultBrowseAnime() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearchLoading.value = true
            try {
                // What's hot this week first, then this season, then the all-time favourites.
                val list = (_trending.value + _seasonal.value + _popular.value).distinctBy { it.id }.ifEmpty {
                    repository.getPopular().first()
                }
                if (_searchQuery.value.isBlank() && _selectedGenre.value == null) {
                    _searchResults.value = list
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                _isSearchLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    val recentSearches: StateFlow<List<String>> = settingsStore.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Called when a search result is opened: the query was worth keeping. */
    fun rememberSearch() {
        val q = _searchQuery.value
        if (q.isNotBlank()) viewModelScope.launch { settingsStore.addRecentSearch(q) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { settingsStore.clearRecentSearches() }
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

    private companion object {
        const val HOME_STALE_MS = 30 * 60 * 1000L
    }
}
