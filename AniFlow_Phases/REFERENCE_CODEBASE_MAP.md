# Reference — codebase map and verified facts

Everything here was verified by reading files or running commands on 2026-08-23, at the state
described in `P0_STATUS_AND_FINISH.md`. Line numbers move; symbol names and shapes are the stable
part. Re-grep before relying on a line number.

## Module and toolchain

Single Gradle module `:app`. AGP 9.0.1, Gradle wrapper 9.1.0, Kotlin 2.3.20, `jvmToolchain(17)`,
minSdk 24, compile/targetSdk 36, versionCode 51, versionName 1.8.6, `applicationId
com.example.aniflow`. Compose BOM 2026.03.01, Material3. Navigation3 `nav3Core = 1.0.1` plus
`lifecycle-viewmodel-navigation3 = 2.10.0`. Media3 1.5.1 (exoplayer, exoplayer-hls, ui). Ktor 3.0.3.
kotlinx.serialization 1.7.3. Coil 3.0.4. DataStore Preferences 1.1.1. **No Room, no Hilt/Koin, no DI
container.** Product flavours `standard` / `redesign` on dimension `ui` (being deleted in P2).

Installed SDK on this machine has only `platforms/android-37.0` and `build-tools/36.0.0`, and no
`cmdline-tools`, despite `compileSdk = 36`. Builds succeed anyway; don't "fix" it.

## Source tree (63 Kotlin files, ~19k LOC)

```
com.example.aniflow
├── MainActivity.kt          37 LOC — provides LocalDeviceType, locks TV to landscape
├── Navigation.kt            NavDisplay + entryProvider; 3 keys; entry decorators (P0.1)
├── NavigationKeys.kt        @Serializable Main / Detail(animeId) / Player(animeId, episodeNumber)
├── DeviceType.kt            enum PHONE|TV + DeviceDetector.detect(context), 4 signals, cached
├── data/                    2,123 LOC — providers, stores, failover, self-healing
│   ├── model/               311 LOC — Anime, AiringAnime, Episode, WatchHistoryEntry, …
│   ├── remote/AniListApi.kt 449 LOC — AniList GraphQL
│   └── repository/          658 LOC — AnimeRepository interface + DefaultAnimeRepository
├── theme/                   156 LOC — Color.kt, Theme.kt, Type.kt  ← P1 replaces this
├── ui/main/                 1,555 LOC — MainScreen.kt (host + tab switch), MainScreenViewModel.kt
├── ui/phone/                1,229 LOC — PhoneHome/Browse/Library/SettingsScreen.kt
├── ui/tv/                   1,167 LOC — TvHome/Browse/Library/SettingsScreen.kt + components/
├── ui/redesign/             3,553 LOC — parallel UI tree  ← P2 deletes all of it
├── ui/detail/               864 LOC — DetailScreen.kt + DetailViewModel.kt
├── ui/player/               2,242 LOC — PlayerScreen.kt (1,180) + PlayerViewModel.kt + 4 selectors
└── utils/                   229 LOC — AppUpdater.kt, UpdateConfig.kt
```

`ui/HomeScreen.kt`, `ui/BrowseScreen.kt`, `ui/LibraryScreen.kt`, `ui/SettingsScreen.kt` (1,222 LOC
of dead code) were deleted in P0.6. `ui/redesign/components/AmbientBackground.kt` and
`IntroOverlay.kt` were deleted in P0.5. `res/raw/` (two intro MP4s, 52.5 MB) is gone.

## Data layer contracts (exact, do not guess)

```kotlin
interface AnimeRepository {
    fun getTrending(): Flow<List<Anime>>          // + getPopular, getSeasonal, getTopRated,
    fun getAiringToday(): Flow<List<AiringAnime>> //   getUpcoming, getRecentlyUpdated,
    fun getAnimeByGenre(genre: String): Flow<List<Anime>>  // getActionAnime, getRomanceAnime
    fun searchAnime(query: String, page: Int = 1): Flow<SearchPage>
    fun getAnimeDetail(id: Int): Flow<Anime?>     // ← null-as-error; P1 replaces with AniResult
    suspend fun getEpisodes(identity: AnimeIdentity): EpisodeLookupResult
    suspend fun getEpisodesBySlug(provider: ProviderId, slug: ProviderSeriesId): EpisodeLookupResult
    suspend fun getStreamingSources(request: EpisodeRequest): PlaybackResult
    suspend fun checkUpdates(): AppUpdateInfo?
    suspend fun refreshSchedule(): Pair<List<AiringAnime>, List<Anime>>
    suspend fun checkUrlStatus(url: String, headers: Map<String, String>): Int
}

interface EpisodeProvider {
    val id: ProviderId
    suspend fun findSeries(identity: AnimeIdentity): SeriesMatchResult
    suspend fun getEpisodes(seriesId: ProviderSeriesId): EpisodeLookupResult
    suspend fun resolve(request: EpisodeRequest): PlaybackResult
}
```

`EpisodeLookupResult` is a sealed interface: `Matched` / `Ambiguous` / `NotFound` /
`Error(message)`. Providers: `AniLightProvider` (real, `api.anilight.live`), `MiruroProvider` and
`AnikotoProvider` (both thin wrappers over MegaPlay, **stream resolution only** — their
`getEpisodes` returns `Error` since P0.2). Support: `ProviderRegistry`, `ProviderMappingStore`
(fuzzy AniList-title → provider-slug cache, 7-day TTL), `PlaybackFailoverController`,
`CircuitBreaker`, `SelfHealingEngine`, `AdBlocker`, `HlsManifestNormalizer`, `NetworkModule`.

**AniList IDs are the identity key throughout the app.** Metadata is AniList GraphQL; streams are
provider slugs joined to AniList IDs by `ProviderMappingStore`.

## Persistence today (all DataStore Preferences, all JSON blobs — P3 replaces)

| Store | Key shape | API |
|---|---|---|
| `WatchlistStore` | one JSON string = whole `List<Anime>` | `watchlistFlow`, `addToWatchlist`, `removeFromWatchlist`, `isBookmarked`, `isBookmarkedFlow`, `clearWatchlist` |
| `WatchHistoryStore` | one JSON string = whole `List<WatchHistoryEntry>` | `historyFlow`, `getHistoryList`, `getProgress(animeId)`, `saveProgress(...)`, `removeHistory`, `clearHistory` |
| `UserFeedbackStore` | one JSON string = whole `List<UserFeedback>` | `feedbackListFlow`, `saveFeedback`, `getFeedbackForAnimeFlow`, `clearAll` (device-local since P0.4) |
| `SettingsStore` | individual preference keys — **correct use of DataStore, keep it** | `themeMode`, `feedbackOnboardingShown`, quality/subtitle prefs |
| `ProviderMappingStore` | JSON blob keyed by AniList id | mapping cache, 7-day TTL |

Every list store re-serialises the entire list on every write (lost updates under concurrency) and
re-parses it on every emission. Each has a `repairAndLoad…` path that salvages a corrupted blob —
evidence the blob approach has already failed in production.

Models (`data/model/`), all `@Serializable`:

```kotlin
data class Anime(id: Int, title: String, englishTitle: String?, coverImage: String,
    bannerImage: String?, description: String?, episodes: Int?, format: String?,
    averageScore: Int?, genres: List<String>, status: String = "FINISHED", season: String?,
    seasonYear: Int?, studioName: String?, nextAiringEpisode: Int?, nextAiringAt: Long?,
    trailerUrl: String?, recommendations: List<Anime>)
data class AiringAnime(mediaId: Int, title: String, coverImageUrl: String, airingAt: Long, episode: Int)
data class Episode(id: String, name: String, number: Int, description: String?, thumbnail: String?)
data class WatchHistoryEntry(animeId: Int, title: String, coverImage: String, episodeNumber: Int,
    episodeName: String, progressMs: Long, durationMs: Long, lastWatchedTime: Long)
```

## Theme today (P1 rewrites this)

`theme/Color.kt` declares **seven top-level `var … by mutableStateOf(Color)`**: `PrimaryDark`,
`PrimaryDarker`, `SurfaceCard`, `SurfaceBorder`, `TextPrimary`, `TextSecondary` (+ constants
`PrimaryAccent`, `PrimaryAccentLight`, `SecondaryAccent`, `TertiaryAccent`, `SuccessGreen`,
`WarningAmber`, `TextTertiary`). `theme/Theme.kt`'s `AniFlowTheme` **assigns to those globals during
composition** based on `SettingsStore.themeMode` (`"amoled"` vs anything else), then builds a
`darkColorScheme` from them. That is process-global mutable state written from a composable: two
themed subtrees are impossible, and a recomposition ordering change silently repaints the app.

`fontSize = N.sp` literals in `app/src/main`: **229** (verified count, `grep -ro 'fontSize = [0-9]*\.sp'`).
The audit's "245" predates the P0 deletions.

## The flavour check (P2 deletes)

`context.packageName.endsWith(".redesign")` appears at **12 sites in 11 files** (verified):

```
Navigation.kt:39                              ui/main/MainScreen.kt:122, :629, :965
ui/phone/PhoneSettingsScreen.kt:35            ui/tv/TvSettingsScreen.kt:39
ui/tv/components/TvSideNavRail.kt:109         ui/player/PlayerScreen.kt:136
ui/player/components/AdvancedServerProviderSelector.kt:45   …/QualitySelector.kt:36
…/SpeedSelector.kt:34                         …/SubtitleSelector.kt:35
```

The flavour source sets contain only an unused `config.xml`. Both APKs ship both UI trees.

## Navigation3 1.0.1 — verified API facts

Established by extracting the AARs from `~/.gradle/caches/modules-2/files-2.1` and running `javap`,
because `CLAUDE.md` and the audit are wrong on two points:

- `NavDisplay`'s `onBack` parameter is `kotlin.jvm.functions.Function0<kotlin.Unit>` — i.e.
  `() -> Unit`. **There is no `Int` count to honour.** Any instruction to "make `onBack` honour the
  `Int`" is a no-op on this version.
- The runtime decorator is `rememberSaveableStateHolderNavEntryDecorator()`, **not**
  `rememberSavedStateNavEntryDecorator()`.
- 1.0.1's default `entryDecorators` is `listOf(rememberSaveableStateHolderNavEntryDecorator())`, and
  `SceneSetupNavEntryDecorator` is applied internally by `NavDisplay`. So passing exactly
  `listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator())`
  is correct and sufficient.
- `navigation3-ui` does **not** depend on `lifecycle-viewmodel-navigation3`; the ViewModel decorator
  was genuinely absent, which is what made `DetailViewModel` Activity-scoped.
- `NavDisplay` registers its own back handler enabled only while `backStack.size > 1`. A
  `BackHandler` inside an entry composable is registered *later* than NavDisplay's, so it wins while
  it is enabled — which is why `MainScreen` takes `isTopDestination: Boolean` (P0.7) instead of
  registering unconditionally.

## Player (P5 works here)

`ui/player/PlayerScreen.kt` (~1,180 LOC) owns the `ExoPlayer` instance directly
(`ExoPlayer.Builder(context, renderersFactory)`), renders it through `AndroidView { PlayerView(ctx) }`,
and is the **only** place in the app that had a `BackHandler` before P0.7 (`PlayerScreen.kt:118`).
`PlayerViewModel` (~1,010 LOC) already carries: provider status, endpoint cooldown/failure marking,
`reResolveSources`, `classifyPlaybackException` → `PlaybackErrorType`, `handlePlaybackError`,
`selectProviderManual`, `selectServerAndType`, `selectAudioType`, `selectQualityByResolution`,
`selectSubtitle`, `playNextEpisode` / `playPrevEpisode`, `saveProgress`, `getSavedProgress(Entry)`.
So failover logic exists; what is missing is MediaSession, PiP, and making failover silent.

## Gotchas that will bite you

- **`material-icons-core` is an explicit dependency now.** Compose Material3 in BOM 2026.03.01 does
  not pull it transitively; it used to arrive via `androidx.tv:tv-material`, which P0.6 removed. The
  app uses only core icons (`Icons.Default/Rounded.{Home,Search,Settings,Favorite,FavoriteBorder,
  Star,PlayArrow,Close,Info}` + `automirrored.rounded.ArrowBack`). If you re-add `tv-material` in P4,
  keep the explicit `material-icons-core` line anyway — implicit transitives are how this broke.
- **`theme/*` uses wildcard imports everywhere** (`import com.example.aniflow.theme.*`). Renaming a
  colour will fail at ~40 call sites at once with no import to guide you. Grep the symbol first.
- `ui/redesign/theme/GlassModifiers.kt` and `GlassTokens.kt` are imported by *non-redesign* files
  (`ui/main/MainScreen.kt` imports `glassSurface`, `darkGlassSurface`, `focusGlow`, `GlassTokens`).
  The two trees are not cleanly separated — P2 must untangle this, not just delete `Redesign*.kt`.
- `MainScreenViewModel` is the god-object for all Home/Browse/Library state (14 `StateFlow`s) and
  holds the tab index (`_currentTab`, `setTab(index)`), which is why the tab is not in the back stack.
- `AppUpdater` is the app's supply chain: it downloads and installs APKs. Treat every change to it as
  security-relevant. `UpdateConfig.UPDATE_JSON_URL` points at a **public GitHub raw JSON**, so the
  update URL is attacker-influenceable if that repo is ever compromised — hence the signature check.
