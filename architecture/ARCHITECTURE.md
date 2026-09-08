# AniFlow Architecture Reference Manual

> **NOTICE FOR AI AGENTS & DEVELOPERS:**
> This document is the primary architectural source of truth for AniFlow. Reading this file gives you complete structural, data-flow, and behavioral understanding of the entire application without needing to read dozens of individual source files.

---

## 1. System Overview & Technology Stack

AniFlow is a dual-form-factor anime streaming application designed to run seamlessly from a **single APK** on both **Android Mobile (touchscreen)** and **Android TV (D-pad remote control)**.

### Core Stack (Baseline v1.8.6 / v1.8.7)
- **Module Structure**: Single Gradle module `:app`
- **Language**: Kotlin 2.2+ (`jvmToolchain(17)`), targeting Android SDK 36 (minSdk 24)
- **UI Framework**: Jetpack Compose (BOM 2026.03.01) + Material3
- **Navigation**: Navigation3 (`rememberNavBackStack`, `NavDisplay`, `entryProvider`) with `rememberSaveableStateHolderNavEntryDecorator` and `rememberViewModelStoreNavEntryDecorator`
- **Video Engine**: Media3 1.5.1 ExoPlayer + HLS extension + custom `DefaultLoadControl`
- **Networking**: Ktor 3.0.3 (CIO engine) with ContentNegotiation & kotlinx.serialization
- **Image Loading**: Coil 3 (Compose extension with crossfade)
- **Persistence**: AndroidX DataStore Preferences (JSON serialized blobs)
- **Dependency Injection**: Explicit constructor injection via `remember` / manual container (no Dagger/Hilt/Koin)

---

## 2. Product Flavor & Target Variant

### Primary Product Focus: The Redesign Variant
- The **Redesign** variant (`applicationIdSuffix = ".redesign"`, `versionNameSuffix = "-redesign"`) is the active, polished product line.
- Redesign screens are located in `com.example.aniflow.ui.redesign.*`.
- Flavour selection is detected dynamically via `context.packageName.endsWith(".redesign")`.

---

## 3. Architecture Blueprint & Package Map

```
com.example.aniflow
│
├── MainActivity.kt               # Entry Activity; detects DeviceType, locks TV to landscape, sets EdgeToEdge
├── Navigation.kt                 # NavDisplay with Navigation3 back stack, entryDecorators, and route mapping
├── NavigationKeys.kt             # Typed serializable nav routes: Main, Detail(animeId), Player(animeId, episodeNumber)
├── DeviceType.kt                 # Enum (PHONE, TV) and DeviceDetector (touchscreen, leanback, UI mode signals)
│
├── data/                         # Data Layer
│   ├── CircuitBreaker.kt         # Failure-rate tracking & cooldown for streaming providers
│   ├── ProviderRegistry.kt      # Registry of enabled providers & circuit breaker states
│   ├── SelfHealingEngine.kt      # Background health monitor for providers and cached links
│   ├── SettingsStore.kt          # User preferences: language (sub/dub), quality, default speed, auto-skip
│   ├── WatchHistoryStore.kt      # Saved playback progress, timestamps, per-anime resume positions
│   ├── WatchlistStore.kt         # Bookmarked anime list
│   ├── UserFeedbackStore.kt      # Device-local feedback store (saved recommendations & user reviews)
│   ├── AniLightProvider.kt       # Primary provider integration (api.anilight.live + XOR decryption)
│   ├── MiruroProvider.kt         # Fallback provider scraping megaplay.buzz
│   ├── AnikotoProvider.kt        # Fallback provider scraping megaplay.buzz
│   │
│   ├── model/                    # Domain & Data Models
│   │   ├── Anime.kt              # Anime identity, titles, formats, episodes, banner/cover URLs
│   │   ├── Episode.kt            # Episode metadata, number, title, thumbnail URL
│   │   ├── StreamingSource.kt    # Typed streaming models: AudioType, QualityPolicy, SourceEndpoint, PlaybackResult
│   │   ├── ProviderStatus.kt     # Enum: Selected, Loading, Available, CircuitOpen, IdentityMismatch
│   │   └── AppUpdateInfo.kt      # In-app update metadata (versionCode, url, forceUpdate, notes)
│   │
│   ├── remote/                   # Network & Remote APIs
│   │   ├── AniListApi.kt         # GraphQL queries against anilist.co (Trending, Popular, Search, Details)
│   │   └── NetworkModule.kt      # Process-global Ktor HttpClient instances
│   │
│   └── repository/               # Repositories
│       ├── AnimeRepository.kt    # Interface for anime metadata and streaming source resolution
│       └── DefaultAnimeRepository.kt # Repository implementation with in-memory caching and fuzzy matching
│
├── ui/                           # Presentation Layer
│   ├── main/                     # Main Hub & TV Top Bar
│   │   ├── MainScreen.kt         # Main container, bottom capsule bar (phone) / top nav bar (TV), back handlers
│   │   └── MainScreenViewModel.kt# Loads Home rails, search debouncing, tab coordination, update checks
│   │
│   ├── detail/                   # Detail Screen (Standard variant)
│   │   ├── DetailScreen.kt       # Anime synopsis, episode list, feedback, recommendations
│   │   └── DetailViewModel.kt    # Detail state management and mapping confirmation
│   │
│   ├── redesign/                 # Redesign Variant UI (Active Production)
│   │   ├── RedesignDetailScreen.kt # Glassmorphic detail view, chunked episodes (100s), feedback overlay
│   │   ├── RedesignPhoneHomeScreen.kt # Phone Home: Hero trailer banner, Continue Watching, genre rails
│   │   ├── RedesignTvHomeScreen.kt    # TV Home: 10-foot hero with trailer player, overscan margins, D-pad rails
│   │   ├── RedesignPhoneBrowseScreen.kt # Phone Browse: Search bar, genre filter chips, 3-column poster grid
│   │   ├── RedesignTvBrowseScreen.kt  # TV Browse: Search bar, genre chips, A-Z letter filter, adaptive grid
│   │   ├── RedesignPhoneLibraryScreen.kt# Watchlist display on mobile
│   │   ├── RedesignTvLibraryScreen.kt # Watchlist display on TV
│   │   │
│   │   ├── components/           # Reusable Redesign Components
│   │   │   └── GlassCard.kt      # Glassmorphic card container with border highlights
│   │   │
│   │   └── theme/                # Redesign Design System
│   │       ├── GlassTokens.kt    # Spacing, padding, overscan margins, color constants (GlowCyan, GlowPurple)
│   │       └── GlassModifiers.kt # Modifiers: glassSurface(), focusGlow()
│   │
│   ├── player/                   # Video Player
│   │   ├── PlayerScreen.kt       # Media3 ExoPlayer UI, gesture overlays, TV controls row, key event handling
│   │   ├── PlayerViewModel.kt    # Stream resolution, parallel checks, failover controller, position saving
│   │   └── components/
│   │       ├── AdvancedServerProviderSelector.kt # Multi-provider and multi-server selector dialog
│   │       ├── QualitySelector.kt                # Resolution picker (Auto, Best, 1080p, 720p, 480p, 360p)
│   │       ├── SubtitleSelector.kt               # Subtitle track selector dialog
│   │       └── SpeedSelector.kt                  # Session playback speed selector (0.5x to 2.0x)
│   │
│   └── tv/                       # TV Specific legacy/shared components
│       ├── TvSettingsScreen.kt   # TV settings menu (language, speed, update checks, clear cache)
│       └── components/TvSideNavRail.kt # TvTopNavBar and TvSideNavRail implementations
│
└── utils/                        # Utilities
    ├── AdBlocker.kt              # URL filter for known ad hosts and popup trackers
    └── AppUpdater.kt             # Cryptographically secure APK downloader with SHA-256 cert verification
```

---

## 4. End-to-End Data Flows

### A. Metadata Pipeline (AniList GraphQL)
1. `AniListApi.kt` executes GraphQL queries against `https://graphql.anilist.co`.
2. Home screen queries fetch: Trending Now, Popular All Time, Seasonal Hits, Airing Today, Top Rated, Upcoming, Recently Updated, Action Anime, Romance Anime.
3. Every Anime has a canonical AniList `id: Int`, which serves as the universal identity key throughout the app.
4. Search queries are debounced by 300ms in `MainScreenViewModel.kt`.

### B. Streaming Provider Pipeline & Join Logic
1. AniFlow uses AniList IDs, but external providers use slugged titles or provider-specific IDs.
2. `DefaultAnimeRepository.kt` joins metadata with streams:
   - Takes AniList `title` (romaji/english) and queries `AniLightProvider.kt`.
   - Fuzzy match confidence is calculated via Dice coefficient. Matches $\ge 0.85$ are accepted.
   - Verified slug matches are cached in `ProviderMappingStore` with a 7-day TTL.
3. `AniLightProvider.kt` connects to `api.anilight.live`, decrypts stream URLs using the XOR key `"aproxy2026"`, and produces native HLS/MP4 streams.
4. Fallbacks: `MiruroProvider` and `AnikotoProvider` wrap `megaplay.buzz` for stream failover (they do not fabricate episode lists).

### C. Typed Stream Architecture
Streams are strictly decoupled from resolution and UI formatting:
- **`AudioType`**: Strongly-typed enum (`SUB`, `DUB`).
- **`QualityPolicy`**: Typed sealed interface:
  - `QualityPolicy.Auto`: Player adapts based on network bandwidth.
  - `QualityPolicy.MaxAvailable`: Player selects highest available bitrate/height.
  - `QualityPolicy.FixedHeight(height: Int)`: Player locks to exact height (e.g. 1080, 720, 480).
- **`SourceEndpoint`**: Represents a physical stream URL, its server identity, headers, and audio type.

---

## 5. Video Player Architecture (Media3 / ExoPlayer)

### Initialization & LoadControl
- Built inside `PlayerScreen.kt` using `remember(animeId) { ExoPlayer.Builder(...) }`.
- `DefaultLoadControl`:
  - Min buffer: 25,000 ms
  - Max buffer: 60,000 ms
  - Buffer for playback: 5,000 ms
  - Buffer for playback after rebuffer: 10,000 ms
  - Back-buffer: 30,000 ms (allows instant rewinds without network hit)

### Resolution Locking (Invariant Rule 3)
When changing resolution in `applyVideoQualityOverride`:
1. Track overrides of type `TRACK_TYPE_VIDEO` are cleared on `trackSelectionParameters`.
2. `setMinVideoSize(w, h)` and `setMaxVideoSize(w, h)` are set to the target format dimensions.
3. A `TrackSelectionOverride` is added for the matching track group and index.
4. If the player is in `STATE_READY`, `exoPlayer.seekTo(exoPlayer.currentPosition)` is executed to immediately flush the pre-buffered lower-resolution chunks.

### Playback Speed (Invariant Rule 8)
- Changes in the player controls modify only `exoPlayer.setPlaybackSpeed(speed)` and `viewModel.playbackSpeed.value`.
- They are strictly session-only and never write default speed updates to `SettingsStore`.

### Failover & Proactive Refresh
- `PlaybackFailoverController.kt` handles transient errors without restarting the episode from 0.
- `PlayerViewModel.kt` runs a 20-minute proactive background refresh for live CDN tokens.

---

## 6. Navigation & Back Handling Architecture

### Navigation3 Integration
- Uses `rememberNavBackStack(Main)` and `NavDisplay`.
- **Mandatory Entry Decorators**:
  - `rememberSaveableStateHolderNavEntryDecorator()`: Preserves scroll state.
  - `rememberViewModelStoreNavEntryDecorator()`: Prevents shared ViewModel instances when navigating `Detail -> Detail`. Popping back restores the original anime's state cleanly.

### Back Handling Hierarchy
- **Player**: `BackHandler` checks `controlsVisible`. If controls are visible, it closes controls. If controls are hidden, it pops back to Detail.
- **Detail**: Back arrow / remote BACK pops back to Main.
- **Main (TV)**: If focus is inside the content grid, pressing BACK moves focus back to `TvTopNavBar` without quitting. If already on `TvTopNavBar` at sub-tab (Browse/Library/Settings), BACK switches to Home tab (tab 0).
- **Main (Phone)**: If on sub-tab (1, 2, 3), BACK switches to Home (tab 0). If on Home (tab 0), BACK arms a 2-second "Press back again to exit" toast.

---

## 7. Android TV vs Phone Remote Interaction Model

| Aspect | Android TV (Remote Control) | Android Phone (Touchscreen) |
|---|---|---|
| **Form Factor** | 10-foot viewing distance, landscape locked | 1-foot viewing distance, portrait + auto-landscape in player |
| **Input Device** | 5-way D-pad (Up, Down, Left, Right, Center/Select) + Back | Capacitive Multi-touch & Gestures |
| **Focus System** | Explicit `focusGlow()`, `focusRequester()`, `onFocusChanged` | No focus indicators; touch ripples / glass surface depression |
| **Modal Overlays** | **Strict Focus Locking**: Background has `focusProperties { canFocus = false }` + blur | Scrim overlay with tap-outside to dismiss |
| **Player Scrubbing** | D-pad Left/Right jumps $\pm 10$s; bottom row has dedicated `-10s` and `+10s` buttons | Double-tap left/right screen half ($\pm 10$s) + scrub slider |
| **Controls Auto-Hide**| Vanishes after 5s **only if** user is idle; any key press resets the timer | Tapping video toggles visibility |
| **Screen Wakefulness**| `keepScreenOn = true` on `PlayerView` prevents screensaver | `keepScreenOn = true` prevents display sleep |

---

## 8. Glassmorphic Design System Guidelines

- **Backgrounds**: Highly opaque slate surfaces (`Color(0xFF0F0E17).copy(alpha = 0.98f)`) for dialogs and cards to prevent text overlap.
- **Blur**: `Modifier.blur(20.dp)` on background container while modal overlays are active.
- **Scrim**: `Color.Black.copy(alpha = 0.85f)` dark dimming overlay.
- **Glow & Highlights**:
  - `GlassTokens.GlowCyan`: `Color(0xFF00E5FF)`
  - `GlassTokens.GlowPurple`: `Color(0xFF8A2BE2)`
  - Focused cards scale by $1.02\times - 1.05\times$ with cyan glow borders.
