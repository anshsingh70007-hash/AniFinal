# AniFlow revival — session 2 (2026-11-07) hand-off

Nothing is committed. All work is uncommitted on `main`. The final tree builds (`assembleStandardDebug`), all 30 unit tests
pass (`testStandardDebugUnitTest`), and steps 1–12 were checked on the TV emulator (`Television_720p`). `keystore/` and `keystore.properties` must stay gitignored. Don't touch the
updater's signature check.

## Done (steps 1–8)
1. **Koto navigation sounds:** `ui/sound/Sfx.kt`, hooked in `MainActivity`, with toggles in TV/Phone Settings (`SettingsStore.navSounds`). They are muted in the player through `Sfx.MuteWhileShown()`.
2. **Folded-play logo and launch animation:** brand resources.
3. **TV focus lands without pressing OK:** `ui/focus/FocusHealer.kt` (`FocusHealerRoot`, `Modifier.primaryFocus`).
4. **Purple theme pass:** `ui/tv/home/TvTokens.kt`, plus rewrites of `TvBrowseScreen` and `TvLibraryScreen`.
5. **Seasons/OVA/ONA/movies in the detail screen:** `Franchise.kt`, the AniList franchise walker, `FranchiseSection.kt` and the TvDetail franchise rows.
6. **Trailers and hero art:** `data/remote/MediaExtras.kt` (Kitsu fallback trailer and wide cover), a rewritten `ui/tv/home/TrailerHost.kt` (fails over refused ids, survives renderer crashes) and `PosterStage` in `TvHome.kt` for titles with no banner.
7. **No background playback after leaving the player:** `EmbedPlayer.kt` pauses through JS on pause/stop/dispose and tears down the WebView; `RedesignTvHomeScreen.BackgroundTrailerPlayer` got a lifecycle observer. Checked on the emulator with `dumpsys audio`.
8. **TV search no longer drops typed characters:** local query state in `TvBrowseScreen`.

## Confirmed bugs fixed this session
- `TrailerHost`: the first trailer never started. `play()` returned early because `currentId == id`; this is now fixed in `onReady`.
- TV search lost keystrokes. This was a stale-state race.
- `RedesignTvHomeScreen` trailer WebView had no pause on background.

## Step 9: Netflix-style player (done, checked on the TV emulator)
`ui/player/EmbedPlayer.kt` (logic) + `ui/player/PlayerChrome.kt` (visuals). MegaPlay's page is only the video surface: JW
Player's own controls are switched off, and every action goes through JW's public API (`play/pause/seek/setPlaybackRate/
setCurrentQuality/setCurrentCaptions`, plus `.click()` on the page's own skip buttons). Stream URLs are never read.
- **Checked on the emulator:**
  - Controls show on start and auto-hide after 5 s.
  - DOWN shows the controls with focus already on Play/Pause, so OK is never needed just to find the UI.
  - With the controls hidden, LEFT/RIGHT seek with a ±10 s badge and a slim bar.
  - TV labels expand on focus.
  - The Episodes drawer opens on the current episode, and BACK puts focus back on the button that opened it.
  - Captions lift while the controls are up.
  - HOME silences audio (`dumpsys audio` 0).
  - Resume works across launches.
- **Self-healing:**
  - 25 s load watchdog; 20 s stall watchdog (time paused doesn't count).
  - Up to 3 reloads per episode, each resuming at the last position.
  - Dub missing (HTTP 4xx or an `Error` page): switches to Sub, carrying the position.
  - Renderer crash: rebuilds the WebView (`onRenderProcessGone` returns true).
  - Offline: the watchdogs wait, then resume.
  - A missing WebView gets a Failed card.
- **Features:**
  - Skip Intro/Outro.
  - Up Next card with a 10 s countdown (only in the last 10 s, or on end, and only with autoplay on).
  - "Still watching?" after 3 auto-advances with no input.
  - End and Failed cards: Retry / Try Dub / Try HD player / Back.
  - Speed, subtitles and quality survive reloads and new episodes.
  - "Ends at h:mm", adjusted for speed.
  - Phone: gesture layer (tap toggles; double-tap left/right third seeks ±10 s; left-half drag = brightness, right-half drag = volume), forced landscape and immersive mode, all restored on exit.
- **Not verified:** the phone path on a real phone (only the TV emulator was available). Check landscape, gestures and the brightness restore.
- The progress-save episode now comes from the URL (`/stream/ani/{id}/{ep}/`) through `rememberUpdatedState`.

## Step 10: Smart suggestions (done)
- **`data/Suggestions.kt`** (pure, unit-tested in `SuggestionsTest`) builds a genre taste profile from history.
  - Weights: about a 2-week half-life on recency, plus how deep into the show you got.
  - Score = 65% genre affinity + 35% AniList score.
- **`MainScreenViewModel.observeSuggestions`:**
  - Fetches details for the 3 most recent shows, once per ViewModel, after a 2.5 s delay (AniList rate limit).
  - Rebuilds only when *which* shows were watched changes, never on the 15 s progress saves.
  - Builds **Top Picks for You** and **Because You Watched X**.
  - Excludes anything already in history, plus other seasons of watched shows (matched by the first two title words).
- **Where it shows:** both rows sit after Continue Watching on the TV home (`TvHome`) and the phone home (`PhoneHomeScreen`).
- **TV detail "More Like This"** (`Suggestions.moreLikeThis`) is re-ranked by genre overlap with AniList's own order. It drops franchise members (they have their own rows) and shows you've finished.

## Step 11: Security and self-healing (done)
- **Fixed:**
  - **BUG** (`AniLightProvider.kt`): `withTimeout` inside `try { } catch { if CancellationException rethrow }` meant one slow server's timeout failed its `async`, and `awaitAll` then threw away every other server's result. Now uses `withTimeoutOrNull`.
  - **BUG:** read-then-write that wasn't atomic in `WatchHistoryStore.saveProgress/removeHistory`, `WatchlistStore.add/remove` and `UserFeedbackStore.saveFeedback`. Racing saves dropped each other. Each now does its read-modify-write in one `DataStore.edit`.
  - **Data-loss risk:** the self-heal regex `\{[^{}]+\}` could not match entries with nested objects (`Anime` has some), and then overwrote the store with the lossy result. `StoreSalvage.kt` now salvages element by element, and an empty salvage never overwrites the original blob.
  - **SECURITY:**
    - Raw watchlist, history and update-manifest JSON is no longer logged.
    - The updater allows one download at a time (`AtomicBoolean`) into a fixed filename (`versionName` is remote input), with a 250 MB cap. Signature verification is untouched.
    - Backup rules now include only the four user DataStores (watchlist, history, settings, feedback). Caches and provider mappings are excluded.
    - `.gitignore` now also covers `*.jks`, `*.keystore` and `*.p12`.
  - **RedesignTvHomeScreen trailer:** `onRenderProcessGone` added; the YouTube id is sanitized before going into page JS.
  - **Context:** the stores are built on `applicationContext`.
- **New self-healing:**
  - `data/CrashGuard.kt`: 3 crashes in 10 min resets the rebuildable caches on next start (cache dir, detail/feed caches, provider mappings). User data is never touched.
  - `MainActivity.onTrimMemory` clears Coil's memory cache when memory runs low.
  - `MainScreenViewModel` reloads empty home rows when the network comes back.
- **Tests:**
  - **TEST POLLUTION:** `PlayerViewModel.nativeDownUntil` is process-wide by design. One test's embed fallback made 3 later tests skip native playback. The test setup now calls `PlayerViewModel.resetNativeBackoff()`.
  - `testMonotonicCooldownMilestones` now accepts the embed fallback, which has been the intended behaviour since native sources went down in Nov 2026.
- **Not done (potential, unconfirmed):**
  - No `corruptionHandler` on the DataStores. The salvage path covers bad JSON, not a corrupt protobuf file.
  - No total request timeout or body cap on the Ktor client (connect 8 s / socket 15 s already).
  - Main-thread disk IO in `ListingCorrectionManager` / `DetailDiskCache` constructors was not measured.

## Overnight pass (2026-11-08): what changed, and how it was checked
Builds green. 38/38 unit tests pass. Lint: 0 errors, 78 warnings (none from tonight's code). Every item marked
**seen** was driven and screenshotted on the emulators: TV 720p, and a 1080x2400 phone.

**Home feeds stay current with no manual edits.**
- `currentCour()` (`AniListApi.kt:675`) maps December to FALL. It used to map it to WINTER and show last January's line-up. Covered by a test.
- Season shelves ("Best of Summer 2026", "Coming Soon") come from that function, so they roll over by themselves.
- `MainScreenViewModel.refreshIfStale()` (`:429`, called from `MainScreen.kt:135` on resume) reloads the home rows silently once they are 30 minutes old.
- The `.first()` bug documented above `:429` meant rows with a cache never refreshed from the network. It is fixed.
- **BUG** "Airing Today" used a UTC day (`now - now % 86400`). It now uses the local day (`AniListApi.getAiringToday`). **seen**
- Phone Browse used to open on all-time classics, because its list was built before Trending loaded. It is now rebuilt on every visit with no query (`MainScreenViewModel.setTab`). **seen**

**New sections**
- **Schedule** on TV (rail) and phone (bottom nav), sharing `ui/schedule/ScheduleModel.kt`. **seen**
  - Day chips, a pulsing NOW divider, live countdowns ("in 2h 13m", then "LIVE · just out", then "Out now"), and times in the device's 12/24 h format.
  - AniLight's schedule API only serves the current Mon–Sun week, so `DefaultAnimeRepository.getWeekSchedule()` fills the rest from AniList `getAiringRange` (cached 30 min, deduped by media + episode).
- **Library** on both platforms has My List and History tabs. History shows a progress bar, "Episode N · Xm left" and "2 hours ago". It opens on whichever tab has content. **seen**
- **Recent searches** (new tonight) on TV and phone. **seen** on both.
  - A query is saved only when a result is opened from it: 6 kept, deduped, stored on the device.
  - Code: `SettingsStore.recentSearches`, `MainScreenViewModel.rememberSearch()`.
  - On TV the chips sit above the results, so typing with the remote is needed only once per title. Test: `RecentSearchesTest`.
- The phone home "Airing Today" row has countdowns and a "Full schedule ›" link to the Schedule tab. **seen**

**Redesigns**
- **Settings** on both platforms: a stats header, grouped cards, and `ui/settings/SettingsController.kt` shared by phone and TV. TV settings are full-bleed and fit 720p. **seen**
- **Phone spotlight hero** (`PhoneHomeScreen.SpotlightPager`): a sharp portrait cover with a glow shadow, plus title, score · format · year · episodes, and genre pills. **seen**
  - The banner is now a blurred backdrop with parallax. It used to be a center crop of a 4.75:1 banner, which showed a random third of the art.
  - Auto-advance waits 6 s, restarts after a manual swipe, and never fights a finger.
  - The parallax offset is read in the draw phase, not in composition. Lint caught the first version.
- **Phone detail**
  - The episode list marks watched episodes ✓ and highlights the current one with "Continue · 22m left" and a progress bar. TV already had this. **seen**
  - The meta line is "Studio • ★ 87%" with no "Unknown Studio / --%" filler. RELEASING titles get an "EP 7 in 6d 16h" chip. **seen**
  - The play button reads "Resume Ep N" and shrinks instead of pushing ♥/★ off-screen. That was a real overflow, caught on the emulator.
  - The never-reached `TvEpisodesGrid` was deleted (TV routes to `TvDetailScreen`, `Navigation.kt:89`).
- Phone search has a clear (✕) button. The placeholder is "Search anime by title…", because AniList search does not match studios or characters, so the old copy overpromised.
- "User's Choice" is now "Your Picks", and the onboarding says notes stay on the device (they do).

**TV**
- **Voice search**: a mic key on the search keyboard, shown only when a recogniser is installed.
  - It is pinned to Google TV's own recogniser, because Google TTS also claims the intent and caused a chooser.
  - The manifest has the `<queries>` entry this needs.
  - The recogniser launch is **seen**. Recognition itself could not be tested: the emulator has no mic or account.
- The remote's **Search key** opens Search with the keyboard focused, from any tab. **seen**
- **Hero trailer**: a paused or stalled YouTube iframe used to leave a pause glyph or a black hero. The backdrop now sits above the WebView and only fades out while the video is really playing. Stalls are caught after 3.5 s (`TrailerHost.kt`). Watched for 30 s. **seen**

**Player**
- Settings › Default speed is applied to the embed player (`EmbedPlayer.kt:415`). It used to start every episode at 1x. A speed picked in the player wins for the session. Auto-skip intro follows the setting (`:583`).

**Offline pass (phone, Wi-Fi and data off, cold start). All seen.**
- Home renders from cache.
- **BUG** Offline search for "naruto" said "Type at least 2 characters…".
  - Cause: the repository folds network failures into an empty page (`DefaultAnimeRepository.kt:441`), so the UI cannot tell offline from no match.
  - Fix: the copy is now true for both cases ("No matches for “naruto”" / "…or check your connection"), on phone and TV.
  - A real fix needs `searchAnime` to surface errors (an `AniResult`, P1).
- **UX** A failed episode lookup used to replace the whole phone detail page with a bare error, even though the title was cached.
  - Now the page stays up, with an inline "Couldn't load episodes · Retry" card, as on TV.
  - Uses `viewModel.anime` and a `when` guard (`DetailScreen.kt`). Retry after reconnecting restores the list.
- The player's `CenterCard` was a fixed 520dp, wider than a portrait phone (~411dp). It is now capped at 520dp with 20dp gutters.
- The phone detail banner is soft and dim, like the hero, instead of a hard random crop.
- Phone home rows share one `SectionHeader` (accent bar, title, optional action). The emoji prefixes are gone ("🔥 Trending Now" and so on); they were inconsistent and read as amateur. **seen**

**Not verified (needs the real OnePlus TV / a phone)**
- Voice recognition end to end.
- Trailer behaviour on the real TV's WebView.
- Schedule density at 1080p.
- The phone player in landscape on real hardware.
- The TV player's error card after the width cap. The TV emulator's ethernet cannot be turned off from adb, so it was not triggered.

## Lint (now 0 errors, 78 warnings)
`lintStandardDebug` found 17 errors, all in code that predates the player/suggestions/hardening work. All are fixed:
- **BUG** `DefaultAnimeRepository` "Airing Today": `java.time` needs API 26 while minSdk is 24, so it crashed on Android 7 devices. It now uses `Calendar`.
- `AdBlocker` data sources got `@OptIn(UnstableApi)`.
- `MainActivity.dispatchKeyEvent` got `@SuppressLint("RestrictedApi")`. That one is a lint false positive.
- `PlayerScreen:536` now uses collected state instead of `StateFlow.value` during composition.
- The drive colon in `local.properties` is escaped.

## Still to do
- Test the phone player on a real phone (landscape, gestures, brightness restore).
- Run a release build (`assembleStandardRelease`) before shipping.
- Commit in reviewable chunks: player, suggestions, hardening, earlier steps 1–8, then the overnight pass (schedule, library, search, hero, settings). Nothing is committed yet.
- APK: the debug build is 43.3 MB, the same as before this session.
