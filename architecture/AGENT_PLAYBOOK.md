# AniFlow AI Agent Playbook & Rapid Onboarding

> **PURPOSE:** This document is optimized for LLM context windows (Gemini, Claude, GPT). It allows an agent to understand and safely modify AniFlow without wasting tens of thousands of tokens scanning the workspace.

---

## 1. Quick Build & Test Commands

On Windows PowerShell, Java is located in Android Studio's JBR. Always set `JAVA_HOME` before building:

```powershell
# Set Java environment (mandatory on this machine)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "C:\Program Files\Android\Android Studio\jbr\bin;$env:PATH"

# Check compilation (Redesign variant)
.\gradlew.bat compileRedesignDebugKotlin

# Run unit tests
.\gradlew.bat testRedesignDebugUnitTest

# Assemble APK
.\gradlew.bat assembleRedesignDebug
```

---

## 2. Unbreakable Invariants (Must Always Follow)

1. **Redesign is King**: All development, polish, and fixes target the **Redesign** variant (`1.8.1-redesign` and onward). Do not work on the legacy standard UI.
2. **Release Folder Hygiene**: Keep `releases/` clean. Only active versions stay in `releases/`. All older versions must live in `releases/backup/`.
3. **TV Focus Locking**: When displaying any dialog or modal overlay on TV:
   - Mark background layout: `Modifier.focusProperties { canFocus = false }` + `Modifier.blur(20.dp)`.
   - Set a `FocusRequester` on the default dialog button and trigger `.requestFocus()` inside a `LaunchedEffect`.
4. **ExoPlayer Quality Locking**: In `applyVideoQualityOverride`, set both `setMinVideoSize` and `setMaxVideoSize` on player parameters, add `TrackSelectionOverride`, and call `exoPlayer.seekTo(exoPlayer.currentPosition)` to flush pre-buffered video chunks.
5. **Playback Speed is Session-Only**: Changing speed in player controls adjusts `exoPlayer.setPlaybackSpeed(speed)` and `viewModel.playbackSpeed.value`. NEVER write default playback speed updates to `SettingsStore` during playback.
6. **Keep Video Surface Awake**: `PlayerView` must have `keepScreenOn = true` to prevent TV screensavers or mobile display sleep during video playback.
7. **Typed Streams Only**: Never parse or serialize stream resolution from string labels. Always use `AudioType` (`SUB`/`DUB`), `QualityPolicy` (`Auto`, `MaxAvailable`, `FixedHeight`), and `SourceEndpoint`.
8. **Never Invent Data**: Do not fabricate dummy episodes or silent fallbacks. If a provider fails, surface a typed error with retry affordances.

---

## 3. Fast File Index & Responsibility Map

| Task | Key File | Notes |
|---|---|---|
| **App Entry & Device Type** | `MainActivity.kt`, `DeviceType.kt` | Sets landscape on TV, provides `LocalDeviceType` |
| **App Navigation** | `Navigation.kt`, `NavigationKeys.kt` | Navigation3 backstack, decorators, routes |
| **TV Top Bar & Rail** | `ui/tv/components/TvSideNavRail.kt` | `TvTopNavBar` with item focus glow |
| **Main Screen & Tabs** | `ui/main/MainScreen.kt`, `MainScreenViewModel.kt` | BackHandler, update takeover, tab switching |
| **TV Home Screen** | `ui/redesign/RedesignTvHomeScreen.kt` | Hero banner, trailer player, horizontal anime rails |
| **Phone Home Screen** | `ui/redesign/RedesignPhoneHomeScreen.kt` | Continue watching, airing today, genre rails |
| **TV Browse & Search** | `ui/redesign/RedesignTvBrowseScreen.kt` | A-Z letter filter, genre chips, adaptive grid |
| **Phone Browse & Search**| `ui/redesign/RedesignPhoneBrowseScreen.kt` | Search textfield, genre chips, 3-column grid |
| **Detail Screen** | `ui/redesign/RedesignDetailScreen.kt` | Banner, 100-episode chunking, recommendations |
| **Video Player** | `ui/player/PlayerScreen.kt` | Media3 ExoPlayer, gestures, TV controls row |
| **Player State & Failover**| `ui/player/PlayerViewModel.kt` | Server selection, failover controller, progress save |
| **Player Dialogs** | `ui/player/components/*.kt` | Quality, Subtitle, Speed, Advanced Server selectors |
| **AniList API** | `data/remote/AniListApi.kt` | GraphQL queries for anime metadata |
| **Streaming Providers** | `data/AniLightProvider.kt`, `data/MiruroProvider.kt` | Primary and fallback stream extractors |
| **Local Stores** | `data/SettingsStore.kt`, `data/WatchHistoryStore.kt`, `data/WatchlistStore.kt` | DataStore Preferences persistence |
| **Glassmorphic Tokens** | `ui/redesign/theme/GlassTokens.kt`, `GlassModifiers.kt` | Glow, blur, surface modifiers |

---

## 4. Current State & Version Roadmap

- **Baseline Code**: v1.8.6 (versionCode 51)
- **Current Target**: v1.8.7 (versionCode 52)
- **Pending Tasks**:
  1. Move older APKs (`1.8.4`, `1.8.5`) to `releases/backup/`.
  2. Backup `1.8.6` to `releases/backup/AniFinal_1.8.6-redesign-backup.apk`.
  3. Bump version in `app/build.gradle.kts` to `1.8.7` (versionCode 52).
  4. Fix TV focus trapping in player selector dialogs (`SubtitleSelector`, `SpeedSelector`, `AdvancedServerProviderSelector`).
  5. Fix screen timeout / screensaver during playback by setting `keepScreenOn = true` on `PlayerView`.
  6. Fix A-Z search gate in `MainScreenViewModel.kt` (`query.length >= 2` -> `query.isNotEmpty()`).
  7. Add key reset to controls auto-hide timer so controls don't disappear while TV user is navigating.
