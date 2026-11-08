# AniFlow — Phase 0: Discovery & Audit

**Date:** 2026-08-22 · **Baseline:** v1.8.6 (versionCode 51) · **Scope:** ~19,000 LOC Kotlin, single `:app` module

---

## Method, and what I could not verify

Every finding below carries `file:line` evidence and one of these labels: **BUG** (confirmed misbehaviour, with reasoning), **RISK** (plausible, needs a device to confirm), **ARCHITECTURAL PROBLEM**, **UX PROBLEM**, **PERFORMANCE PROBLEM**, **SECURITY PROBLEM**, **TECH DEBT**, **MISSING FEATURE**, **UNNECESSARY FEATURE**, **DESIGN OPPORTUNITY**. I read every Kotlin file in `app/src`, the Gradle and manifest config, the resource tree, and the ProGuard rules.

Two honest limits:

**I cannot compile this project.** The Linux environment I work in has no Android SDK (`local.properties` points at `C:/Users/RG/AppData/Local/Android/Sdk`, a Windows path from a previous developer's machine) and outbound network is allowlist-blocked — `dl.google.com` and `repo1.maven.org` both return 403, so Gradle cannot resolve the Android plugin or any dependency. **You will need to be the build runner.** After each implementation phase I will hand you a specific command (`./gradlew assembleStandardDebug`) and ask you to paste any failure. I will compensate by grepping every call site before renaming or deleting anything, and I will never tell you a change "builds" — only what I checked statically.

**I could not see AniLight.** Web fetches to `anilight.live` are blocked by the same allowlist, and the site has essentially no search-index footprint — no reviews, no screenshot threads. I will not invent a design critique of a site I have not seen. What I do have is arguably better for architectural purposes: your own `AniLightProvider.kt` is a working integration against `api.anilight.live`, and an API contract is a reliable fingerprint of a product model. Section 21 separates what that confirms from what remains unverified.

---

## 1. Current architecture summary

One Gradle module. Kotlin 2.3, Compose (BOM 2026.03.01), Material3, **Navigation3** (`rememberNavBackStack` + `NavDisplay`, not `NavHost`), Media3 1.5.1 (ExoPlayer + HLS), Ktor 3.0.3, kotlinx.serialization, Coil 3, DataStore Preferences. No Room. No DI framework. minSdk 24 / target 36.

**Layering as it exists.** There is a recognisable shape — `data/model`, `data/remote`, `data/repository`, `ui/*` with ViewModels — but no domain layer, and the boundaries leak in both directions. `MainScreenViewModel.kt:320` calls `ui.redesign.components.AppLoader.setLoaded(true)`: a shared ViewModel reaching into flavour-specific UI. `PhoneSettingsScreen.kt:23` imports `redesign.theme.glassSurface`. `DetailScreen.kt:462` writes `viewModel.uiState.value` from the composable, because `uiState` is a public `MutableStateFlow` (`DetailViewModel.kt:29`).

**Object graph: there is none.** `NetworkModule` is an `object` holding two process-global `HttpClient`s. Everything else is constructed ad hoc in composables. `SettingsStore` alone is instantiated in five places — `Theme.kt:20`, `Navigation.kt:33`, `MainScreen.kt:114`, `PlayerScreen.kt:88`, `DefaultAnimeRepository.kt:20` — each building its own duplicate `.data.map` flow chain over the same file. `DefaultAnimeRepository.kt:18` hardcodes `NetworkModule.client`, so the repository cannot be substituted in a test.

**Data flow.** Metadata comes from AniList GraphQL (`data/remote/AniListApi.kt`). Streams come from `api.anilight.live` (`AniLightProvider`), with `MiruroProvider` and `AnikotoProvider` as fallbacks — and those two are **byte-identical duplicates of each other** (`AnikotoProvider.kt:62-121` == `MiruroProvider.kt:62-121`), both scraping `megaplay.buzz`. The two worlds are joined by fuzzy title matching (Dice coefficient at `AniLightProvider.kt:144-151`, accepted at ≥0.85), cached in `ProviderMappingStore` with a 7-day TTL. AniList IDs are the identity key, which is the right choice.

**Flavours.** `standard` and `redesign` on dimension `ui`. But `app/src/standard/` and `app/src/redesign/` contain **only an unused `config.xml`** — there is no Kotlin in either source set. The flavour is selected by a runtime string check, `context.packageName.endsWith(".redesign")`, repeated in **13 places across 11 files**. Consequently both APKs compile and ship both complete UI trees.

**Caching.** Three uncoordinated in-memory layers: an LRU-50 with a 5-minute TTL in `AniListApi.kt:19-24`, seven hand-rolled `cachedX`/`lastXFetchTime` field pairs in `DefaultAnimeRepository.kt:28-49` at 10 minutes, and a watch-map cache in `AniLightProvider.kt:106` at 5 minutes. **Nothing metadata-related is persisted to disk.** Every cold start refetches the entire home screen.

**What is genuinely good, and I want to say this before the rest.** `DeviceType.kt`'s four-signal detection is thoughtful. The leanback manifest is correct — `LEANBACK_LAUNCHER` filter, `android:banner`, touchscreen and leanback both marked not-required. `StreamingSource.kt:130-188` defines a real typed result model (`PlaybackResult`, `EpisodeLookupResult`) with a proper error taxonomy. `CircuitBreaker.kt` is correctly implemented. The player's position polling is properly scoped (`PlayerScreen.kt:1071-1128`) so a 250 ms tick does not recompose the tree — that is a subtlety most people get wrong. Search debounce is correct (300 ms, min 2 chars). There are 26 real unit tests covering the failover logic. Nav3 keys are `@Serializable`, so the back stack genuinely survives process death. Someone was thinking.

---

## 2. Major problems found

Twelve things, ordered by how much they hurt.

**1 · The flavour split is a fiction that doubles the codebase.** `ARCHITECTURAL PROBLEM.` Two complete UI trees, ~700 LOC of near-identical duplication on the TV surface alone (Browse is ~65% identical, Library ~73%), nine `isRedesign` branches inside `MainScreen.kt` alone, and neither APK is smaller because both ship everything. The redesign is also only 4-of-6 surfaces complete — Settings falls back to `PhoneSettingsScreen` (`MainScreen.kt:514`) and the Player is shared. No user can ever compare the two, because the flavour is fixed at install time by package name. Every bug in this document exists in two places.

**2 · 51 MB of intro video in `res/raw`.** `PERFORMANCE PROBLEM.` `intro_first.mp4` is 35 MB, `intro_second.mp4` is 17 MB. Total `res/` is 51 MB of a 56 MB APK; `classes.dex` is 2.67 MB. Because they are referenced behind a *runtime* flavour check (`IntroOverlay.kt:66-67`), `shrinkResources` cannot strip them, so the standard flavour ships 51 MB of video it never plays. And the intro is **unskippable** — 2,000 ms hold plus video, 5,000 ms bypass timeout, `pointerInput` consuming all input, no BackHandler, no key handler, and no "seen" flag persisted. Floor 2.8 s, ceiling 7.8 s, **on every cold start, forever.**

**3 · Two of three providers fabricate episodes.** `BUG.` `MiruroProvider.kt:46-60` and `AnikotoProvider.kt:46-60` return exactly 24 invented episodes with no network call. `DefaultAnimeRepository.kt:327-337` returns that as `Matched`. Three consequences: users see a fake 24-episode list for any unmatched anime; `EpisodeLookupResult.Ambiguous` can therefore never reach the UI, making the entire user-disambiguation screen dead code; and `resolve` is then called with an AniList ID against a service keyed on HiAnime IDs, so playback is guaranteed to fail. **The app lies to the user and then fails.**

**4 · Navigation3 has no entry decorators, so the wrong anime appears on back.** `BUG.` `NavDisplay` at `Navigation.kt:40` passes no `entryDecorators`, so `rememberViewModelStoreNavEntryDecorator` is absent — it is never a default, it must be added explicitly. `viewModel {}` in `DetailScreen.kt:62` therefore resolves against the *Activity* store. Tap a recommendation (`Navigation.kt:68`) and `Detail(21)` and `Detail(20954)` share one `DetailViewModel`; `LaunchedEffect(animeId)` overwrites the shared `uiState`, and popping back shows the second anime's data. The required artifact is already on the classpath (`app/build.gradle.kts:103`) and never imported. Related: `onBack = { backStack.removeLastOrNull() }` discards the `Int` nav3 hands it, so a multi-entry predictive-back pops one entry.

**5 · The design system does not exist.** `ARCHITECTURAL PROBLEM.` `MaterialTheme.typography` is referenced **zero times** against **245 hardcoded `fontSize = N.sp` literals**. `MaterialTheme.colorScheme` is referenced once. `theme/Type.kt` is a complete, unused 10-style scale. 1,181 `.dp` literals, 244 inline `RoundedCornerShape(n)`, 55 hardcoded `Color(0x…)`, corner radii spanning 2/3/4/6/8/12/16/18/20/22/24 dp, and three different card widths for the same concept. Worse, `Color.kt:8-21` declares theme colours as **global `var … by mutableStateOf`** which `Theme.kt:26-40` writes to *from a composable body*: a snapshot write during composition, process-global, non-idempotent.

**6 · The TV app is a scaled-up phone app.** `UX PROBLEM.` There is no `dimens.xml`, no `values-television/`, no resource qualifier of any kind, and one phone `Typography` serving both form factors. Card titles are 13sp; score badges 9-11sp. `tv-foundation` and `tv-material` are declared and **imported zero times** — every focus affordance is hand-rolled. `focusRestorer()`, `pivotOffsets`, `focusGroup()` appear zero times each. Section 6 has the full list.

**7 · Back is broken app-wide.** `BUG.` There is exactly one `BackHandler` in 19,000 LOC, in the player (`PlayerScreen.kt:118`). `currentTab` is a plain `MutableStateFlow(0)` (`MainScreenViewModel.kt:24`) that is not on the back stack, so from Browse, Library or Settings, **BACK exits the app.** On Android TV that alone fails Google's app-quality requirements.

**8 · The updater is a supply-chain hole.** `SECURITY PROBLEM.` Release builds are signed with the **debug keystore** (`app/build.gradle.kts:35`), whose private key is public and identical on every machine. `AppUpdater.kt:38-62` fetches a server-supplied `updateUrl`, manually follows one redirect to an arbitrary `Location` **with no scheme check**, and installs the result via `FileProvider` + `REQUEST_INSTALL_PACKAGES` with no hash check, no signature check and no versionCode check — over a connection where `usesCleartextTraffic="true"` app-wide. Anyone who can influence that JSON, that redirect, or a cleartext hop can install a replacement app that upgrades over the real one.

**9 · The feedback system writes to a public, unauthenticated document.** `SECURITY PROBLEM.` `UserFeedbackStore.kt:89` GETs and PUTs to a single hardcoded `api.restful-api.dev` document. `saveFeedback` PUTs the **entire array**, so any user's client can read all users' feedback, overwrite the global list, or wipe it (`resetServer`, `:314`). No auth, no rate limit, no moderation. Around it sits a four-attempt retry loop, a 15-second merge heuristic and a regex JSON salvage routine — elaborate machinery on a toy endpoint.

**10 · A full-screen particle generator runs forever on both platforms.** `PERFORMANCE PROBLEM.` `filmGrainOverlay` (`GlassModifiers.kt:106-144`) drives a 150 ms infinite animation whose `drawWithContent` allocates a `java.util.Random` plus a `List<Offset>` of up to 3,000 points **per frame**, applied full-screen via `AmbientBackground.kt:100`. That is roughly 180,000 allocations per second and a permanently non-idle frame pipeline on a static screen — on a 1 GB Amlogic TV box as well as a phone. `AmbientBackground.kt:52-99` adds three more full-screen radial-gradient shaders rebuilt per frame. The visual payoff is a 0.04-alpha texture almost nobody will notice.

**11 · Failure is invisible, and fake content is shown instead.** `ARCHITECTURAL PROBLEM.` `queryAniListFromApi` returns `null` for HTTP errors, parse errors and exhausted retries alike (`AniListApi.kt:108,119`); callers turn null into an empty list, and `getFallbackAnimeList()` (`DefaultAnimeRepository.kt:377-413`) then serves **three hardcoded anime as if they were real content**. `MainScreenViewModel.kt:320` sniffs `id == 1535` to guess whether it is looking at fake data. There is no connectivity awareness anywhere despite `ACCESS_NETWORK_STATE` being declared, no error state and no retry on Home, Browse or Library, and every repository failure is swallowed into `Log.e`. Total network failure renders as a permanently blank, apparently-empty Home.

**12 · Episode lists are not virtualized.** `PERFORMANCE PROBLEM.` `DetailScreen.kt:487-535` renders `Column { episodes.forEach { … } }` inside a `verticalScroll`. A 1,100-episode series composes and measures 1,100 rows on the main thread. The redesign chunks at 100 but still uses `Column { forEach }`, so every chunk switch is a 100-card measure storm that loses scroll position. `TvEpisodesGrid` (`DetailScreen.kt:538-603`) has the same defect via `chunked(3)`.

---

## 3. Confirmed bugs

These misbehave by inspection — the reasoning is in the "why" column, not an assumption about runtime.

| # | Location | Bug | Why it definitely misbehaves |
|---|---|---|---|
| B1 | `Navigation.kt:40` | Detail→Detail→back shows the wrong anime | No `entryDecorators` ⇒ Activity-scoped `DetailViewModel` shared by both entries; `LaunchedEffect(animeId)` overwrites `uiState` |
| B2 | `MiruroProvider.kt:46-60`, `AnikotoProvider.kt:46-60` | 24 fabricated episodes returned as real | Function returns a hardcoded range with no network call; `DefaultAnimeRepository.kt:327-337` labels it `Matched` |
| B3 | `DefaultAnimeRepository.kt:287` | Good provider mappings deleted on every seasonal anime | Invalidates when `abs(found−expected)/expected > 0.50`; AniList `episodes` is the *planned* total (12) while the provider has only aired ones (3) ⇒ ratio 0.75 ⇒ delete, re-search, rewrite the identical slug. Costs 2-4 extra requests per detail open |
| B4 | `PhoneBrowseScreen.kt:126` + `MainScreenViewModel.kt:236` | Tapping any A–Z letter silently clears results | The letter sets the whole query to 1 char; the search gate requires ≥2 chars |
| B5 | `DetailScreen.kt:116-123` + `:448` | Feedback text field cannot be typed into | The `focusProperties { canFocus = false }` ancestor *contains* the dialog, so the `requestFocus()` at `:651` cannot succeed |
| B6 | `MainScreenViewModel.kt:24`, no `BackHandler` in `MainScreen` | BACK from Browse/Library/Settings exits the app | `currentTab` is not on the back stack and nothing intercepts back |
| B7 | `PlayerViewModel.kt:386-437` | Wrong episode can start playing | `reResolveSources` sleeps up to 10 s, then writes `streamingSources`/`selectedSource` with **no `generationId` guard and no `resolutionJob` registration** — the only write path in the class missing the guard every other path applies |
| B8 | `PlayerViewModel.kt:331` | Empty source list ⇒ permanent black screen, no error, no retry | `if (allSources.isNotEmpty())` has no `else`; state ends as `isLoading=false, hasError=false, selectedSource=null`. Reachable via `AdBlocker.shouldBlock` filtering at `DefaultAnimeRepository.kt:365` |
| B9 | `PlayerScreen.kt` (module-wide) | Screen sleeps mid-episode; TV screensaver fires | `keepScreenOn` / `FLAG_KEEP_SCREEN_ON` / `setWakeMode` appear **zero times** in `app/src/main`; Media3 does not do this for you |
| B10 | `PlayerScreen.kt:402-418` | Users who tap the controls never checkpoint progress | The 15 s save timer lives in `LaunchedEffect(exoPlayer, controlsVisible)` and resets `lastSaveTime` on every controls toggle |
| B11 | `PlayerViewModel.kt:180` | Failover retries known-dead endpoints | `isEndpointCooldown` is **never called**; the cooldown map is write-only, and `PlaybackFailoverController.kt:57-61` picks the first endpoint whose server merely differs, so A→B→A ping-pongs |
| B12 | `PlayerViewModel.kt:58-62` + `PlayerScreen.kt:232` | "Auto-play next episode" and "Auto-skip openings" toggles do nothing | `autoPlayNextEpisode` is `stateIn(WhileSubscribed)` that nothing collects and is read via `.value`, so it is permanently the initial `true`; `autoSkipIntro` is never consumed anywhere |
| B13 | `PlayerViewModel.kt:103,112,121,293` | User's speed/quality/language/provider preferences silently discarded | Every DataStore first-read is wrapped in `withTimeoutOrNull(100L)` — cold storage on a TV box loses the race |
| B14 | `PlayerScreen.kt:409,421` | Garbage duration and a collapsed seek bar | Raw `exoPlayer.duration` written without the `coerceAtLeast(0L)` used at `:227`; `C.TIME_UNSET` is negative |
| B15 | `PlayerScreen.kt:427-432` | Controls vanish 5 s in while a TV user is still traversing | Auto-hide keys only on `controlsVisible`/`isPlaying`; no key press or click resets it. The same LEFT/RIGHT then means "seek" instead of "move focus" depending on invisible state |
| B16 | `MainScreen.kt:125`, `DetailScreen.kt:123`, `RedesignDetailScreen.kt:144`, `PlayerScreen.kt:571` | D-pad focus walks behind modals, including the force-update takeover | `focusProperties { canFocus = false }` on a plain `Box` with no `focusTarget`/`focusGroup` in the chain does not deactivate descendants |
| B17 | `TvSideNavRail.kt:139-143` + `Navigation.kt:40-101` | TV focus is thrown to the nav bar every time you close a title | The `Main` entry is disposed while `Detail` is on top; on return `LaunchedEffect(Unit) { requestFocus() }` re-fires |
| B18 | `RedesignTvHomeScreen.kt:579-603` | The hero's "Watch Now" button is decoration | It is neither `clickable` nor `focusable` — unreachable by remote and by touch |
| B19 | `WatchHistoryStore.kt:22`, `WatchlistStore.kt:20`, `UserFeedbackStore.kt:88` | One DataStore `IOException` crashes the process and permanently kills all later writes | `CoroutineScope(Dispatchers.IO)` with a plain `Job` and no exception handler; writes are fire-and-forget into it |
| B20 | `WatchlistStore.kt:83-87`, `WatchHistoryStore.kt:98-114` | Lost updates | Read-modify-write across two DataStore operations instead of transforming inside `edit {}`; a progress tick and a bookmark racing loses one |
| B21 | `WatchHistoryStore.kt:85,99` + `PlayerScreen.kt:361` | Watching ep 6 destroys ep 3's saved position, and cross-episode resume silently starts at 0 | One history entry per anime; the resume gate requires `savedEntry.episodeNumber == currentEpNum` |
| B22 | `PhoneHomeScreen.kt:179,191,203` | Three orphan section headers over blank space on network failure | Trending/Popular/Seasonal headers render unconditionally while the other six are `isNotEmpty()`-guarded |
| B23 | `app/src/androidTest/.../MainScreenTest.kt:17` | The instrumented test source set does not compile | Calls `MainScreen(FAKE_DATA: List<String>)`; the real signature (`MainScreen.kt:73`) takes `onItemClick/deviceType/repository/…`. `connectedCheck` has been dead for a long time |
| B24 | `AniListApi.kt:57-82` | Cross-request cancellation contagion, plus duplicate requests | The shared in-flight `Deferred` is created with the *first* caller's scope; if that caller is cancelled, later `await()`s throw `CancellationException` and cancel *their* scopes. `finally { remove(key) }` fires on the first awaiter, so later callers start a duplicate |
| B25 | `MainScreen.kt:309`, `TvSideNavRail.kt:175-177` | Nav indicator sits on the wrong tab in RTL | Raw `offset(x = …)` / raw `Offset` with `supportsRtl="true"` |
| B26 | all `items()` calls (23 sites) | Focus, scroll and animation state churn on every refresh; pagination re-diffs positionally | No `key =` on a single lazy list in the app |
| B27 | `res/drawable/ic_banner.png` | Blurry leanback launcher banner | A correct 320×180 asset placed in density-less `drawable/`, so it is treated as mdpi and upscaled 2× on an xhdpi TV |

---

## 4. Potential risks (not confirmed — need a device or a specific input to prove)

**Config-change survival is a house of cards.** `RISK.` The player is composition-owned (`remember(animeId) { ExoPlayer.Builder… }` at `PlayerScreen.kt:162-219`) and survives rotation *only* because `AndroidManifest.xml:29` declares `configChanges="orientation|screenSize|…"`. `uiMode`, `locale`, `density` and `fontScale` are **not** in that list, and `resizeableActivity="true"`. So a dark-mode toggle, a system font-size change, or a multi-window resize destroys the Activity, releases the player, and restarts from the last checkpoint. `rememberSaveable` and `SavedStateHandle` appear **zero times** in the entire app, so nothing else is saved either: tab, search query, scroll positions, selected episode chunk.

**A single shared `DefaultHttpDataSource.Factory` is mutated while loader threads are live.** `RISK.` `PlayerScreen.kt:143-149` builds one factory; `:314` rewrites its default `Referer`/`Origin` per source. Those headers then apply to every host — segments, keys, and subtitle files. Since VTT configs are attached to the MediaItem at `:336-350` after the video host's headers were set, subtitles from a different host get the wrong `Referer` and can 403 — and a subtitle load failure is a *fatal* player error, so it triggers a full server failover. `setAllowCrossProtocolRedirects` is never set, so the https→http redirects these CDNs commonly use fail fatally.

**`AdBlocker` turns one blocked URL into a dead episode.** `RISK.` `AdBlocker.kt:69-77` throws `AdBlockedException : IOException` from `open()`, which ExoPlayer treats as a fatal source error rather than a skippable segment. `blockedPaths` matches by substring (`path.contains("pagead")`). Separately, `AdBlocker.filterHeadersForHost` — the origin isolation it implements — is **never called from the playback path**, so that protection does not exist at runtime.

**The 20-minute proactive refresh can force a rebuffer near the end of nearly every episode.** `RISK.` `PlayerViewModel.kt:129-170` can reassign `selectedSource` (`:161`), retriggering `LaunchedEffect(selectedSource)` → `stop()/clearMediaItems()/prepare()/seekTo()`. No generation check there either. `handlePlaybackError` also early-returns when `selectedSource == null` (`:456`), swallowing errors inside that window.

**ProGuard rules are aggressive in the wrong places.** `RISK.` `proguard-rules.pro:28-30` enables `-overloadaggressively` and `-repackageclasses ''`, both discouraged and both hostile to reflection — including the class names kotlinx-serialization embeds in `rememberNavBackStack`'s saved state, which can make saved-state restore fail across builds. The serialization keeps also lack the modern `-if @Serializable class **` / `-keepclassmembers class <1>$Companion` pair, and `-keep class io.ktor.** { *; }` disables shrinking for the entire client. Three release APKs shipped successfully, so this is debt rather than a live crash — but it is the kind of debt that surfaces as an unreproducible release-only crash.

**`FocusRequester.requestFocus()` is called unguarded in five places** (`TvSideNavRail.kt:59,141`, `DetailScreen.kt:652`, `RedesignDetailScreen.kt:921`, `QualitySelector.kt:57`) while `MainScreen.kt:613-617` does guard it. On a not-yet-placed node this throws. `RISK (crash).`

**`DeviceType` misclassifies non-touch devices as TVs.** `RISK.` `DeviceType.kt:38-40` returns TV when `FEATURE_TOUCHSCREEN` is absent, so non-touch ARM Chromebooks, desktop-mode sessions and most emulators get D-pad UI *and* forced landscape (`MainActivity.kt:30-32`). It is also cached in a `@Volatile` static, provided through a `staticCompositionLocalOf`, with `configChanges` suppressing recreation — so folding a foldable or moving to an external display can never change the shell.

**Provider scraping is one markup change from silence.** `RISK.` `AnikotoProvider.kt:73` scrapes `data-id="([^"]+)"` from a megaplay HTML page, first match wins. `AniLightProvider.decryptUrl:595-618` hardcodes an XOR key (`"aproxy2026"`), a literal `https://kwik.cx` suffix, `cdn.animex.su`, seven server names, a `/cachesub/` path convention and a fallback host. Any rotation degrades silently to `NoSources`. `PlaybackErrorType.ContractChanged` exists in the model but nothing ever probes for it.

**Strict provider models will throw on one missing field.** `RISK.` `MegaPlayTrack.file/label/kind` are all required (`MiruroProvider.kt:26-30`), so one label-less subtitle track kills the whole resolve. `AniLightScheduleEntry` requires `id/episode/airingAt` (`:661-666`), so one malformed row empties the entire schedule, which then falls back to the three hardcoded fake anime.

**Buffer configuration is memory-aggressive for a 1 GB TV box.** `RISK.` `PlayerScreen.kt:170-179` uses 25 s/60 s with a 30 s back-buffer, `prioritizeTimeOverSizeThresholds(true)` and no `setTargetBufferBytes` cap. The emulator-only `MediaCodecSelector` sort at `:181-200` also ships in release.

**`allowBackup="true"` with untouched IDE template backup rules** (`backup_rules.xml`, `data_extraction_rules.xml`) means watch history and settings are backed up with no considered policy. Low severity, but it is an explicit decision nobody made.

---

## 5. UX / UI problems

**Home is nine identical rails with a hero on top.** Spotlight pager (260 dp, 5 items) → Continue Watching → Airing Today → User's Choice → then **eight visually indistinguishable 115 dp poster rails** (`PhoneHomeScreen.kt:180-226`). Nothing is personalized: "Action & Adventure" and "Romance Picks" are fixed genre queries (`MainScreenViewModel.kt:390,398`). There is no top bar, no search entry point, no profile, and no "view all" on any rail. Section titles are emoji-prefixed literal strings — untranslatable, and inconsistent between flavours. `DESIGN OPPORTUNITY`: the rails are the product's front door and they currently communicate "here is some anime" rather than "here is *your* anime".

**Loading, empty, error and offline states are mostly absent.**

| Screen | Skeleton | Spinner | Empty | Error | Retry | Offline |
|---|---|---|---|---|---|---|
| Home (both flavours) | ✗ | global only | ✗ | ✗ | ✗ | ✗ |
| Browse (both) | ✗ | ✓ | text only | ✗ | ✗ | ✗ |
| Library (both) | ✗ | ✗ | text only | ✗ | ✗ | ✗ |
| Settings | ✗ | ✓ | n/a | ✗ | ✗ | ✗ |
| Detail (both) | ✗ | ✓ | ✓ | ✓ | ✓ | ✗ |

Detail is the only screen in the app with an error path. There is exactly one skeleton implementation in the codebase (`ShimmerItem`, `ui/HomeScreen.kt:58`) and it lives in a dead file.

**Library is 55 lines.** One grid, one line of empty text (`PhoneLibraryScreen.kt:40`). No sort, no filter, no search, no count, no remove affordance, no watch progress. `MISSING FEATURE`: there is no watch-status model at all — nothing distinguishes watching, completed, on-hold or dropped. For an app whose retention loop *is* the library, this is the biggest single product gap.

**Detail is thinner than it looks.** It has banner, poster, title, studio, score, genres, Play/Continue, bookmark, feedback and a recommendations rail. It is missing synopsis expansion (hard `maxLines = 5` at `DetailScreen.kt:348`), characters and staff, trailer, related series, next-episode countdown, per-episode watched state — and **episode rows do not render the thumbnail the `Episode` model already exposes**, even though your provider returns per-episode `img`, `title` and `description`.

**Interaction feedback is switched off in places.** Nearly every interactive element is a `Box` + `clickable` with no `Role` and no semantics; the redesign nav explicitly disables indication (`MainScreen.kt:359-362`), so there is no ripple and no press feedback at all. Touch targets under 48 dp: detail genre chips ~28 dp, bookmark/feedback 44 dp, A–Z chips ~24 dp, dialog buttons 40 dp, redesign hero buttons 36 dp.

**No pull-to-refresh anywhere.** The only refresh path is a blind 15-minute timer (`MainScreenViewModel.kt:100`).

**Edge-to-edge is enabled and then defeated.** `enableEdgeToEdge()` at `MainActivity.kt:33`, but there are exactly three inset modifiers in the phone surface; Home/Browse/Library rely on `Scaffold` padding, so the "cinematic" 260/450 dp hero starts *below* the status bar — the opposite of the intent. `themes.xml:6` also sets `windowFullscreen=true`, which fights `enableEdgeToEdge`. No `imePadding()` anywhere.

**The redesign flavour is decoration, not improvement.** Same data, same ViewModel, same rails in the same order. What it adds: an unskippable 51 MB intro, a permanently animating film-grain overlay, three per-frame gradients, and `glassSurface` (`GlassModifiers.kt:50-59`) which on phones reduces to `clip + background(Color(0x1AFFFFFF))` — **no border, no blur**. That is glassmorphism faked with 10% white alpha over arbitrary artwork, which is exactly why 11sp `TextSubtle` is unreadable over bright banners. `Modifier.blur()` is a **no-op below API 31** and minSdk is 24, so on a large share of TV boxes the modals render over unblurred content; where it does work, `RedesignDetailScreen.kt:143` blurs behind an 85%-opaque black scrim — paying a full-screen RenderEffect for something invisible. Haze, Lottie, Orbital, Konfetti and Palette are all declared and **never imported**, so there is no real backdrop blur anywhere in the app.

Three redesign elements *are* genuinely better and should survive: the animated capsule bottom nav (`MainScreen.kt:281-409`), the 450 dp trailer-playing hero (on phone), and Detail's episode chunking.

**Accessibility.** `contentDescription = null` appears 44 times, including on every poster and on meaningful action icons. Icon-only controls carry a label but no state, so TalkBack announces "Bookmark" identically whether or not the item is bookmarked. `sp` is used consistently (good) but with `maxLines = 1` on 11–13sp text inside fixed-height containers, so at 200% font scale titles truncate to nothing and cards clip. Dialogs at `width(400.dp)` and `420.dp` overflow a 360 dp viewport. `TextTertiary #64748B` on `#0D0D1A` is ~4.3:1 — failing AA at the 12sp size it is used at.

**Localization is not possible today.** `strings.xml` contains **one** string; `stringResource` usage is **zero**; there are ~167 hardcoded UI literals.

---

## 6. Android TV problems

**Verdict first: this is a phone app with TV paint on it.** The evidence is structural rather than aesthetic. There is no `dimens.xml`, no `values-television/`, and one phone `Typography` serving both form factors, so card titles are 13sp and badges 9–11sp at a three-metre viewing distance — anything under ~18sp is marginal there. `grep androidx.tv app/src` returns **no matches**, so the two declared TV artifacts are unused and every focus affordance is a hand-rolled `onFocusChanged` + border + `graphicsLayer` scale. `focusRestorer()`, `pivotOffsets` and `focusGroup()` appear **zero times each**. The only `BackHandler` in the app is in the player. The hero is a hard-wired `trending.first()` that never follows focus, and its primary "Watch Now" button is not even clickable. Search is a phone `OutlinedTextField` with an A–Z strip as a workaround.

**Focus.**
- No focus restoration anywhere ⇒ leaving and returning to a rail always loses your place, and closing a title throws focus to the top nav bar (**B17**).
- `focusProperties { canFocus = false }` is a no-op as applied in four places (**B16**), so focus can walk behind the force-update takeover and behind dialogs.
- `DetailScreen.kt:449-454` renders a scrim `Box(...).clickable(onClick = {})` — a focusable node that does nothing, which becomes the only reachable target once the broken deactivation above fails. A genuine focus dead-end.
- `.clickable { }.focusable()` appears ~30 times; `clickable` already installs a focus target, so these chains carry two, with real traversal cost.
- `OutlinedTextField` on TV (`TvBrowseScreen.kt:56-73`) is a focus trap: once focused, LEFT/RIGHT moves the caret rather than escaping, and there is no `imeAction` or `onKeyEvent` escape hatch.
- `TvSideNavRail.kt:206,211` colour the focused and the selected tab identically (`isSelected || isFocused`), so moving focus along the nav bar looks like the tab already changed.
- `TvSideNavRail` itself (96 LOC) is **dead code** — `MainScreen.kt:144-153` uses `TvTopNavBar`. There is no side navigation in the shipped app.

**Traversal.** No `focusProperties` anywhere constrains direction. Traversal is 100% geometric, across rows of wildly different heights (a 320–380 dp spotlight band with a nested 0.6-width trailer box, 280×158 dp continue-watching cards, 150 dp posters), so where focus lands is unpredictable by construction.

**Rails.** Row titles exist (good). But no `key =`, no `contentPadding` on either library grid (so the 1.08× focus scale is clipped on edge items), and **no lead-margin / pivot scrolling** — without `pivotOffsets` the focused card at the right edge barely scrolls into view, instead of the "hold focus at one third of the screen" behaviour TV users expect. `TvBrowseScreen.kt:202-208` puts `LaunchedEffect(Unit) { onLoadMore() }` inside a grid sentinel that composes immediately and re-fires every time it scrolls back into view.

**Overscan.** The redesign defines `OVERSCAN_MARGIN_TV = 48` (`GlassTokens.kt:49`) and applies it correctly. The **standard flavour does not**: `MainScreen.kt:160` gives 24 dp and rails add 16 dp — roughly 2.5–4% at a 960 dp effective width, below the 5% safe area.

**Performance on a 1–2 GB Amlogic/Realtek box**, worst first:
1. `filmGrainOverlay` — a full-screen per-frame particle generator, forever (see §2).
2. `AmbientBackground.kt:52-99` — three full-screen animated radial gradients per frame.
3. `BackgroundTrailerPlayer` (`RedesignTvHomeScreen.kt:610-783`) — a **WebView with `javaScriptEnabled`, the YouTube iframe API, an injected JS bridge, autoplay and `LAYER_TYPE_HARDWARE`, living inside a `LazyColumn` item**, whose `update = { evaluateJavascript(…) }` fires on **every recomposition, i.e. every focus change**. On a Chromecast-class device this alone will stall the UI thread. It is also remote JS executing in an app with `usesCleartextTraffic="true"`.
4. `RedesignTvHomeScreen.kt:335-376` — two more infinite transitions recomputing `sin`/`cos` and allocating a new gradient per frame, for a **non-interactive decorative** button.
5. Unbounded Coil requests — no `ImageRequest`, no `.size()`, no `crossfade` anywhere; a full-resolution AniList banner decodes into a 150 dp slot.
6. Full-screen `blur(20.dp)` on TV content, which is simultaneously expensive on API 31+ and a silent no-op below it.
7. `.composed { }` in `glassSurface`/`focusGlow`/`premiumGlassPanel` defeats modifier skipping, so every focus change recomposes the whole card subtree.
8. `IntroOverlay` spins up a **second ExoPlayer** at launch on top of the app composition.

**Missing TV features.** `grep` for `SpeechRecognizer|RecognizerIntent|TvContract|WatchNext|PreviewChannel` returns **zero hits**: no voice search, no on-screen keyboard affordance, and no Watch Next / recommendation channel, so the app is invisible on the Google TV home row. Continue-watching does exist, but sits below the hero rather than being the first focused element. No D-pad long-press handling outside the player. `TvEpisodesGrid` is not lazy.

**Duplication.** `ui/tv` is 1,384 LOC, redesign TV 1,350 LOC, the detail pair 1,744 LOC. Normalized diffs: Browse ~65% identical, Library ~73% identical — roughly **700 duplicated LOC on the TV surface alone**, with the same pagination bug present in both copies. Settings is already shared by both flavours (`MainScreen.kt:197,236`), which proves the split is unnecessary.

---

## 7. Mobile problems

Most of the mobile-specific findings are in §5; what remains is specific to phone form factor and behaviour.

**Nothing is responsive.** Every card width and hero height is a fixed dp. `GridCells.Adaptive(110.dp)` in Browse is the only responsive layout in the entire phone surface. There is no `WindowSizeClass` usage and no `values-sw600dp`, and `DeviceType` has only PHONE and TV — so a 12-inch tablet or an unfolded foldable gets the 340×68 dp floating pill nav and phone-sized cards. Landscape adapts nowhere.

**Back and state restoration.** Beyond **B6** (back exits from a sub-tab): predictive back is not enabled (`android:enableOnBackInvokedCallback` is absent), and because `rememberSaveable`/`SavedStateHandle` are used zero times, process death loses the tab, the search query, every scroll position and the selected episode chunk — while `configChanges` masks rotation only for the four configs it lists.

**Gestures are thin.** Single-tap toggle and double-tap ±10 s exist in the player. Missing: swipe brightness/volume (`currentVolume`/`maxVolume` are computed at `PlayerScreen.kt:132-134` and never used), pinch/zoom, resize-mode toggle (hardcoded `RESIZE_MODE_FIT`), long-press fast-forward. Outside the player: no pull-to-refresh, no swipe actions in the library, no shared-element transition from card to detail.

**Missing posters render as blank holes.** No `placeholder` or `error` drawable on any of the 31 `AsyncImage` call sites.

**Spotlight titles truncate to one word.** `maxLines = 1` at 20sp on the hero title (`PhoneHomeScreen.kt:284`).

**Mismatched aspect ratios in adjacent rows.** `aspectRatio(0.7f)` posters next to a raw `height(180.dp)` continue-watching card.

---

## 8. Performance problems

Ordered by expected user-visible impact:

1. **Cold start** — 51 MB APK, an unskippable 2.8–7.8 s intro that spins up a second ExoPlayer, no baseline profile anywhere, and the platform `Theme.Material.NoActionBar` parent with no `windowBackground`, so a grey window flashes before Compose draws. This is the single most damaging thing to perceived quality, and it is almost entirely self-inflicted.
2. **`filmGrainOverlay` + three animated full-screen gradients** — a permanently non-idle frame pipeline with ~180k allocations/second on a screen that is visually static.
3. **A JS-enabled autoplay WebView inside a `LazyColumn` item on the TV home screen**, re-evaluating JS on every focus change.
4. **Unbounded image decoding** — no `.size()`, no `crossfade`, no shared `ImageLoader` config, across 31 call sites; full-resolution banners decoding into 46×64 dp thumbnails.
5. **Non-virtualized episode lists** (**§2.12**) — ANR class on long-running series.
6. **No disk cache for metadata** — every cold start refetches the entire home screen; nine sequential row loads in `MainScreenViewModel.loadData()`, one of which (`getAnimeByGenre("Romance")`) bypasses the cache entirely while an unreachable cached variant exists.
7. **`.composed { }` modifiers** on every card, defeating skipping; **no `key =`** on any lazy list, so every refresh churns identity.
8. **Recomposition-hostile theme** — colours are global snapshot `var`s written during composition, so every colour read in the app subscribes to process-global state.
9. **73 raw `Log.*`/`println` sites with no wrapper and no `BuildConfig.DEBUG` gating** — release builds log provider URLs, and `proguard-android-optimize` does not strip `Log.d`.
10. **Nine unused dependencies** shipped (haze, haze-materials, lottie-compose, orbital, konfetti-compose, palette, tv-foundation, tv-material, lifecycle-viewmodel-navigation3). Small next to the video, but free to remove.

**Dependency currency**, worth noting for a streaming app: media3 `1.5.1` → **1.10.1** (five minors of HLS, subtitle and DRM fixes), nav3 `1.0.1` → 1.1.4, datastore `1.1.1` → 1.2.1, lifecycle `2.10.0` → 2.11.0, compose BOM `2026.03.01` → 2026.06.01. `media3-session` is already in the version catalog and never wired — which is precisely why there is no MediaSession, no notification controls and no PiP.

---

## 9. Features worth adding

Each of these has to earn its place against one of: discovery, usability, retention, speed, personalization, playback, organization, convenience. I have written the justification, not just the name.

**Tier 1 — the app is incomplete without these.**

- **A watch-status library** (Watching / On-hold / Completed / Dropped / Plan to watch) with per-episode watched marks. *Retention + organization.* This is the loop that makes someone open the app tomorrow. Today there is a bookmark list and nothing else.
- **Per-episode resume, not per-anime** (fixes **B21**). *Usability.* The current model actively destroys information the user needs.
- **MediaSession + notification / lock-screen / Bluetooth controls, and Picture-in-Picture.** *Playback + convenience.* `media3-session` is already in the catalog. Their absence is the clearest signal that this is not yet a product.
- **Auto-play next episode with a countdown card, and working skip-intro/outro.** *Playback + retention.* Both settings toggles already exist in the UI and both are no-ops (**B12**). Binge-watching is the core use case; right now the app fights it.
- **New-episode notifications** driven by the `/schedule` feed you already consume, diffed against the watchlist. *Retention.* This is the single highest-leverage retention mechanic available to a native app, and your provider already returns `nextAiringEpisode.airingAt`.
- **Real error, empty and offline states with retry, everywhere.** *Usability.* Replacing `getFallbackAnimeList()` with the truth.

**Tier 2 — what makes it feel premium.**

- **Airing schedule / seasonal calendar** with live countdowns, as a first-class destination. *Discovery.* You already have the data; you currently render a single "Airing Today" rail from it.
- **Real filtering and sorting on Browse**: genre (multi-select), year, season, format (TV/Movie/OVA/ONA/Special), airing status, minimum score; sort by popularity, score, trending, newest, title. *Discovery.* AniList supports all of it in the same query you already send. This is where you decisively beat a title-only web search.
- **Sub/dub as a persistent global preference** that pre-selects everywhere rather than a per-play question. *Convenience.* Your `AudioType` model already supports it; nothing persists the choice.
- **Downloads for offline viewing**, with season-batch queueing and a Wi-Fi-only option. *Convenience.* Structurally impossible for a referrer-locked website. This is a defensible differentiator, not a nice-to-have.
- **Android TV Watch Next channel.** *Retention.* Puts AniFlow's continue-watching row on the TV home screen without the app being opened. Highest-leverage TV-only feature there is.
- **Richer detail page**: expandable synopsis, characters, staff, studio, trailer, related series, next-episode countdown, and **episode thumbnails you already fetch and discard**. *Discovery + usability.*
- **Voice search on TV**, plus recent searches and genre tiles so typing on a remote is rare. *Usability.*

**Tier 3 — signature features, once the foundation holds.**

- **On-device personalization.** Derive genre/tag/studio affinity from watch history and re-rank AniList results locally, with rails that *name their reason* ("Because you finished Frieren"). Private, offline, no backend, and it converts your dead generic rails into the reason someone chooses AniFlow.
- **AniList account sync** (two-way progress push). *Retention.* This is table stakes for the audience that cares, and it makes AniFlow part of their existing habit rather than a silo.
- **Predictive prefetch** — resolve and buffer the next episode during the credits; pre-warm `/watch/{slug}` for continue-watching items on app open. *Speed.* Your 5-minute watch cache is already the seed.
- **Cross-device resume** keyed by AniList ID rather than provider slug, so phone→TV handoff works even when the two devices resolve different servers.
- **Home-screen widget / Glance**: "airing today" plus a one-tap resume tile.

## 10. Features worth removing

- **The `redesign` build flavour.** `UNNECESSARY FEATURE.` Not the visual ambition — the *flavour mechanism*. See §16 for what replaces it.
- **The 51 MB intro.** `UNNECESSARY FEATURE.` If you want a launch animation, it is a 200 KB Lottie or a Compose transition, plays once ever, and is skippable.
- **`filmGrainOverlay` and the animated ambient gradients.** `UNNECESSARY FEATURE.` Enormous cost, imperceptible benefit.
- **`BackgroundTrailerPlayer` on TV.** Keep the concept on phone if you like it; a JS WebView in a lazy list on a TV box is indefensible.
- **`SelfHealingEngine`.** `UNNECESSARY FEATURE.` Its 5-minute cycle does two things: reset circuit breakers that are already self-healing after 60 s, and null out cache fields that are already TTL-checked at read. It is a polling loop whose observable output is a log line saying it succeeded.
- **The per-endpoint cooldown map** (`PlayerViewModel.kt:180`) — write-only, never read, and the controller ignores it. Either wire it or delete it; today it is worse than nothing because it looks like protection.
- **`UserFeedbackStore`'s shared public document.** Keep in-app feedback if you value it, but it must go to something you control, or nowhere. The current design lets any user delete everyone's feedback.
- **The `AdvancedServerProviderSelector`** on the TV surface. Seven named servers (`light`, `misa`, `raye`…) is developer vocabulary leaking into a couch UI. Nobody debugs CDNs with a remote. Keep it behind a developer setting on phone.
- **`repairAndLoad*` regex JSON salvage**, triplicated across three stores — ~250 lines defending against a corruption mode DataStore's atomic writes already prevent, using a regex (`\{[^{}]+\}`) that cannot match nested JSON and will happily overwrite good data with fragments.
- **1,222 LOC of provably dead screens**: `ui/HomeScreen.kt` (636), `ui/BrowseScreen.kt` (264), `ui/SettingsScreen.kt` (263), `ui/LibraryScreen.kt` (59) — zero call sites; they only still resolve because of a wildcard import. Plus `TvSideNavRail` (96 LOC). Rescue `ShimmerItem` first.
- **Nine unused dependencies**, and the duplicate `AnikotoProvider` (collapse it and `MiruroProvider` into one parameterized MegaPlay provider).

---

## 11. What should be rebuilt

| Area | Why rebuild rather than patch |
|---|---|
| **Theme / design tokens** | The current system inverts correctly-working Compose theming (global mutable colours written during composition) and is bypassed by 245 hardcoded font sizes. Patching each site without a target token set just moves the mess. Rebuild the tokens first, then migrate screens. |
| **The flavour mechanism** | 13 runtime `packageName` checks across 11 files cannot be incrementally improved into a clean split. Replace with one `BuildConfig` field or, better, a runtime style setting (§16). |
| **Library screen** | 55 lines with no data model behind it. There is nothing to preserve; it needs a watch-status model, then a real screen. |
| **Home composition** | Not the rails themselves — the *section model*. Today it is nine hardcoded calls in a ViewModel. It should be a declarative list of section descriptors so personalization, reordering and "view all" become data rather than code. |
| **`WatchHistoryStore` / `WatchlistStore` / `UserFeedbackStore`** | Single-JSON-blob-in-one-preference-key with non-atomic read-modify-write, unbounded growth (watchlist stores full `Anime` objects *including nested recommendations*), and a re-parse of the whole blob per flow emission per subscriber. This is what Room is for. Rebuilding these also fixes B19, B20 and B21 together. |
| **Episode list rendering** (both flavours, phone and TV) | The non-lazy `Column { forEach }` cannot be patched into virtualization; the surrounding scroll container has to change too. |
| **The reliability layer** | Four overlapping mechanisms (session circuit breaker, per-provider attempt counters, a write-only endpoint cooldown map, a 20-minute proactive refresh), of which the two most elaborate are inert or actively harmful. Collapse to: bounded attempts + a per-session endpoint blacklist + provider fallback + one user-visible "trying another server…" line. |
| **Error model** | `null` cannot represent "offline" vs "API down" vs "no results". A sealed result type has to replace it at the repository boundary, and the fake fallback list has to go with it. |
| **Instrumented tests** | The androidTest source set does not compile and has not for a long time. Start over with a small number of real tests. |

## 12. What should be preserved

Deliberately, and without redesign:

- **`DeviceType.kt`'s multi-signal detection** — fix the touchscreen heuristic, add a size class, keep the approach.
- **The leanback manifest configuration** — it is correct.
- **`StreamingSource.kt`'s typed result model** (`PlaybackResult`, `EpisodeLookupResult`, `PlaybackErrorType`). This is the best-designed thing in the codebase and everything else should be brought up to it.
- **`CircuitBreaker.kt`** — correct implementation; just give it one clear job.
- **AniList-ID-as-identity, and `ProviderMappingStore`.** The mapping cache is the right idea; only the invalidation heuristic (**B3**) is wrong.
- **`AniLightProvider`'s candidate scoring** (Dice + format/season/year heuristics, confidence margin). Fuzzy matching is unavoidable here and this implementation is thoughtful. It just needs to be allowed to say "I don't know" (**B2**).
- **The player's scoped position polling** (`ScopedProgressSlider`/`ScopedProgressText`) — a subtle thing done right.
- **Search debounce** (300 ms, min 2 chars) — correct; only the A–Z strip that violates it needs fixing.
- **The 26 existing unit tests**, and Ktor's `ktor-client-mock` setup.
- **`@Serializable` nav keys and `rememberNavBackStack`** — the nav foundation is right; only the decorators and the `onBack` arity are missing.
- **Navigation3 as the choice.** It is newer than NavHost but it is the direction the platform is going, the keys are already correct, and migrating away would cost more than fixing it.
- **Three redesign visual ideas**: the capsule bottom nav, the phone trailer hero, and episode chunking.
- **`HlsManifestNormalizer`'s variant parsing** — the approach is sound; it has specific bugs (query-string loss on relative URLs, multi-byte chunk splitting, `EXT-X-MEDIA` ignored) rather than a wrong design.

---

## 13. Proposed new AniFlow vision

**Positioning.** AniFlow's metadata is AniList's, and so is every competitor's. The catalog is not the product. What AniFlow can own is the three things a website structurally cannot: **it knows you, it works offline, and it lives on your TV.** Everything below follows from that.

> **AniFlow is the anime app that remembers where you are — on every screen in your house — and gets you back there in one press.**

**Three product principles, in priority order.**

1. **Resume is the primary action.** Not search, not browse. The first focusable element on TV and the first thing above the fold on phone is the episode you were watching, with its thumbnail, its remaining time, and one press to continue. Everything else is secondary navigation.
2. **Never show a lie.** No fabricated episode lists, no three hardcoded anime pretending to be trending. If we do not know, we say so, and we offer the user a way forward (retry, pick the right match, browse cached content).
3. **Two interaction philosophies, one product.** Shared data, models, repositories, persistence and domain logic. Divergent navigation, layout, density, focus and controls. Never a scaled phone layout on TV, never a duplicated codebase.

**Visual direction.** Cinematic and dark, with the artwork doing the work.

- **Colour.** A single dark ramp (near-black `#08080C` base, three elevated surface steps) with **one** accent used exclusively for focus, selection and the primary action — not sprinkled decoratively. Poster-derived accent (via Palette, a dependency you already ship unused) tinting *only* the detail hero, so each series feels like itself without fragmenting the app's identity. Real semantic colours for airing/success/error status. Light mode is not a priority; AMOLED-black as an option is.
- **Type.** One variable font family, a seven-step scale, with a **TV multiplier applied at the theme level** — the same `bodyMedium` resolves to 14sp on phone and 20sp on TV. This is the single change that would most improve TV readability, and it costs one function.
- **Surfaces.** Flat elevated surfaces with a 1 dp hairline border at 8% white — not fake glass. If real blur is wanted, do it in one place (the nav bar and dialog scrims), on API 31+, with a solid fallback. Blur is not a texture to apply everywhere.
- **Radius.** Four values only: 8 (chips, small controls), 12 (cards), 20 (sheets, dialogs), pill (nav, filter chips). Anything else is a bug.
- **Motion.** Two springs and three durations, defined once. Focus scale on TV is 1.06 with a border-glow, never a shadow. Content enters with a 150 ms fade+8 dp rise, staggered by 30 ms within a rail. **No infinite animations on any screen that is not actively being interacted with** — that rule alone kills the entire current performance problem class.
- **Cards.** One poster card component, one 16:9 episode/continue card component, one wide banner. Three shapes total, tokenized dimensions, phone and TV differing only in resolved size.

**What "premium" concretely means here**, since the word is doing a lot of work: cold start under 1.5 s to visible content; no blank screen ever (skeleton, cache, or an honest error with a retry); focus that never lands somewhere unexpected; a resume that is accurate to the second; and nothing on screen that the user cannot act on. Polish is the absence of small betrayals, not the presence of effects.

## 14. Proposed architecture

Deliberately modest. Single module until there is a reason otherwise — modularizing 19k LOC solo buys build-time complexity and nothing else.

```
com.example.aniflow            →  rename to a real applicationId
├── AniFlowApp.kt              Application; owns AppContainer
├── di/AppContainer.kt         ~50 lines: HttpClient, database, repositories,
│                              stores, DeviceInfo. One LocalAppContainer.
├── core/
│   ├── result/                AniResult<T> = Success | Loading | Error(AniError)
│   │                          AniError: Offline | RateLimited | Upstream |
│   │                                    ContractChanged | NotFound | Unknown
│   ├── network/               HttpClient config, retry policy, host allowlist
│   ├── connectivity/          NetworkMonitor (a Flow<Boolean>) — currently absent
│   └── log/                   AniLog: BuildConfig.DEBUG-gated, no-op in release
├── domain/                    Pure Kotlin, no Android imports
│   ├── model/                 Anime, Episode, StreamSource, WatchState,
│   │                          LibraryEntry, HomeSection
│   └── usecase/               GetHomeSections, ResolvePlayback, ResumePoint,
│                              RecommendFromHistory
├── data/
│   ├── metadata/              AniListApi + AnimeMetadataRepository
│   ├── playback/              ProviderRegistry, providers, mapping, failover
│   ├── local/                 Room: anime_cache, episode_cache, library,
│   │                          watch_progress, provider_mapping
│   └── prefs/                 SettingsStore (DataStore — the correct use)
└── ui/
    ├── design/                tokens + theme + shared primitives (see §14 note)
    ├── shared/                ViewModels + state, used by BOTH platforms
    ├── phone/                 phone composables only
    ├── tv/                    TV composables only, using androidx.tv.material3
    └── player/                shared player VM; phone/TV control overlays
```

**Key decisions, with reasons.**

- **Room replaces the JSON-blob DataStore for anything list-shaped.** Library, watch progress, and a metadata cache table. This single change fixes lost updates, unbounded blob growth, per-emission re-parsing, and gives you offline-first and cross-episode resume for free. DataStore stays for actual preferences, which is what it is for.
- **`AniResult<T>` at the repository boundary**, replacing `null`. Non-negotiable — it is the precondition for honest error states, and it is what makes "never show a lie" enforceable rather than aspirational.
- **A manual `AppContainer`, not Hilt.** One module, ~10 objects, one developer. Hilt would buy KSP build time and generated indirection to solve a problem a 50-line file solves. Revisit if you ever modularize.
- **ViewModels live in `ui/shared` and are platform-agnostic.** Phone and TV screens are thin, differing in layout and interaction only. This is what makes "one product, two interaction systems" real instead of a slogan.
- **`androidx.tv.material3` is actually used on the TV branch.** You already ship the artifact. Its `Surface`/`Card` carry correct focus semantics, scale and glow — adopting them deletes most of the hand-rolled focus code and most of §6's focus bugs at once.
- **Navigation3 stays**, with `rememberViewModelStoreNavEntryDecorator` added, `onBack` honouring its `Int`, deep links wired through `onNewIntent`, and the tab index moved into the back stack.
- **Offline-first read path**: UI observes Room; the repository refreshes into Room; the network is never on the critical path for showing something. Cold start renders from cache in one frame.

**Design system file layout** (referenced above as `ui/design`):

```
ui/design/
├── AniFlowTheme.kt        one entry point; DeviceType-aware; wraps
│                          material3 MaterialTheme AND androidx.tv.material3
├── tokens/Palette.kt      raw ramps only (Neutral0..1000, Accent, Cyan)
├── tokens/Colors.kt       @Immutable AniFlowColors + LocalAniFlowColors
│                          surface / surfaceRaised / surfaceOverlay,
│                          borderSubtle / borderFocus,
│                          textPrimary / Secondary / Tertiary,
│                          accent / accentMuted, statusAiring / Success / Error
├── tokens/Spacing.kt      xxs2 xs4 s8 m12 l16 xl24 xxl32 xxxl48
│                          + pagePadding / sectionGap resolved per DeviceType
├── tokens/Radius.kt       s8 m12 l20 pill
├── tokens/Motion.kt       durationFast/Medium/Slow + springSnappy/springSoft
├── tokens/Type.kt         one FontFamily, 7 steps, tvScale() multiplier
├── component/             PosterCard, EpisodeCard, HeroBanner, SectionHeader,
│                          FilterChip, Skeleton, EmptyState, ErrorState,
│                          MetadataRow, ProgressBadge
└── modifier/              focusScale(), hairlineSurface(), shimmer()
```

Two token sets (`standard`, `premium`) selected by a **runtime setting**, not a build flavour. TV differs only in *resolved* spacing and type scale — never a parallel component tree.

## 15. Proposed navigation

**Phone — four tabs, and I am removing one and adding one.**

| Tab | Contents | Why |
|---|---|---|
| **Home** | Resume rail first, then Airing Today with countdowns, then personalized rails that name their reason, then trending/seasonal. Search icon in the top bar. | Resume is the primary action (principle 1). |
| **Discover** | Browse with real filters and sort, genre tiles, seasonal calendar, and search as a full destination rather than a tab. | Merges today's Browse and Search, which are the same intent at different levels of specificity. Filtering is where you beat a website. |
| **Library** | Watch-status segments (Watching / Plan / Completed / On-hold / Dropped), plus Downloads, with sort and filter. | The retention loop. Today's bookmark grid becomes a real destination. |
| **Profile** | Settings, AniList account, playback preferences, downloads management, about/update. | Settings does not deserve a top-level tab in a four-tab bar; it belongs under an identity surface that also holds account sync. |

Detail and Player stay full-screen pushes. Back returns Discover→Home and never exits from a sub-tab. Deep links: `aniflow://anime/{anilistId}` and `aniflow://watch/{anilistId}/{episode}` — needed anyway for notification taps.

**TV — a left nav rail plus focus-driven rails.** Not a top bar.

- **Collapsed 72 dp icon rail on the left**, expanding to 240 dp with labels when focus enters it. LEFT from the first item of any rail opens it; RIGHT returns focus to where it was. This is the standard TV model and it beats a top bar because it never competes vertically with content and it is one predictable D-pad direction away.
- Destinations: **Resume · Home · Discover · Schedule · Library · Search · Settings**. Search gets its own destination with voice input; Schedule gets one because the calendar is a genuinely different browsing mode with a remote.
- **Home is a focus-driven hero.** A full-bleed backdrop that cross-fades to whatever card is focused, with title, metadata and Play/Add actions on the left third. The hero is *the* TV pattern and it is currently a static `trending.first()`.
- **First focus on launch is the resume card**, always. Not the nav, not the hero.
- Rails: `focusRestorer()` on every one, `key =` on every item, `pivotOffsets` so the focused card holds at ~⅓ from the left, 48 dp overscan margin, row titles at 20sp+.
- **BACK**: rail → previous destination → Home → confirm-to-exit. Never a silent exit.
- Detail on TV is a two-pane layout: metadata and actions left, episode grid right, with focus landing on the next unwatched episode.

## 16. Mobile strategy

**Collapse the two flavours into one product, and keep the visual ambition as a runtime setting.**

The evidence for collapsing is decisive: the flavours share the same source set, the same ViewModels and the same data layer; they differ only in decoration; they already leak into each other (`MainScreenViewModel.kt:320`, `PhoneSettingsScreen.kt:23`); the redesign covers only 4 of 6 surfaces; both APKs ship both trees; and because the flavour is fixed at install time by package name, **no user can ever compare them** — so the A/B rationale the split implies does not exist.

Concretely: keep the standard screens as the single code path, promote the three genuinely better redesign elements into them (capsule bottom nav, trailer hero, episode chunking), delete `ui/redesign/Redesign*` (~3,500 LOC), `IntroOverlay`, `AmbientBackground` and `filmGrainOverlay`, and drop the nine unused libraries. If you still want a "premium visuals" mode, ship it as `LocalAniFlowStyle` swapping token values on one code path — which is A/B-able, gives the user the choice, and removes the per-screen branch tax.

Mobile-specific commitments beyond that: `WindowSizeClass` so tablets and unfolded foldables get more columns and a nav rail instead of a floating pill; true edge-to-edge with the hero *under* the status bar and correct inset consumption; predictive back enabled; pull-to-refresh on Home and Library; gesture volume/brightness and long-press fast-forward in the player; shared-element transition from poster to detail hero; PiP on leaving the player; `SavedStateHandle` for tab, query and scroll.

## 17. Android TV strategy

**Rule: no TV screen is allowed to be a phone screen with different numbers.** Enforced by three structural decisions rather than discipline.

1. **`androidx.tv.material3` is the only component library on the TV branch.** Its `Surface`/`Card` carry focus scale, border and glow semantics correctly. Adopting them deletes the hand-rolled `onFocusChanged` + border + `graphicsLayer` pattern in ~30 places along with most of §6's focus defects.
2. **A TV type and spacing scale resolved at the theme level.** `AniFlowTheme` takes `DeviceType`; `bodyMedium` is 14sp on phone and 20sp on TV from the same call site. Minimums: body 18sp, card title 20sp, section header 24sp, hero title 40sp+. No text below 16sp exists on TV, ever.
3. **Focus discipline as a checklist applied to every rail**: `focusRestorer()`, `key =`, `pivotOffsets`, `contentPadding` ≥ the focus-scale overflow, 48 dp overscan, one `focusGroup()` per rail, explicit `focusProperties` where geometry is ambiguous.

Then the TV-native features: **Watch Next channel** publishing continue-watching to the Google TV home row; **voice search**; **D-pad long-press accelerating seek**; **keep-screen-on during playback**; **transport key handling** (`DPAD_CENTER`, `MEDIA_PLAY_PAUSE`, `FAST_FORWARD`, `REWIND`) regardless of overlay state; a focusable, scrubbable progress bar; auto-hide that resets on any input and never runs while a dialog is open; and **automatic, silent server failover** with a single "Trying another server…" line — the server picker leaves the TV UI entirely.

Performance rules for TV, treated as hard constraints rather than goals: no infinite animations, no WebView, no full-screen blur, no unbounded image decode, no non-lazy list of unbounded length, and a `setTargetBufferBytes` cap so a 1 GB box does not thrash.

## 18. Prioritized development roadmap

Ordered by risk retired per unit of work. Each phase is self-contained, ends with a buildable tree, and ends with an explicit validation step you run.

### P0 — Stop the bleeding (correctness, security, size)

Nothing here is a redesign. All of it is "the app is currently lying to users or exposing them."

| # | Change | Fixes |
|---|---|---|
| P0.1 | Add `rememberViewModelStoreNavEntryDecorator()` + `rememberSavedStateNavEntryDecorator()` to `NavDisplay`; make `onBack` respect its count | B1 wrong-anime bug, B2, B3 |
| P0.2 | Delete `MiruroProvider`/`AnikotoProvider` fake-episode fabrication; make provider failure return an error, not 24 phantom episodes | B4 — the worst user-facing bug |
| P0.3 | Generate a real release keystore; remove `signingConfig = debug`; harden `AppUpdater` (HTTPS-only, no redirect to non-HTTPS, verify signing certificate of the downloaded APK before install) | S1, S2 |
| P0.4 | Remove `UserFeedbackStore`'s public unauthenticated endpoint | S3 |
| P0.5 | Delete the two intro MP4s and `IntroOverlay`; remove `filmGrainOverlay` and `AmbientBackground` | 51 MB → APK ≈ 6 MB; P1 per-frame allocation |
| P0.6 | Delete `ui/HomeScreen.kt`, `BrowseScreen.kt`, `LibraryScreen.kt`, `SettingsScreen.kt` (1,222 LOC dead); remove the 9 unused dependencies; fix or delete `MainScreenTest.kt` | TD block, build back to green |
| P0.7 | Add `BackHandler`s: sub-tab → Home, Home → confirm exit; TV: rail-aware | B-nav block |
| P0.8 | Remove `usesCleartextTraffic`, or scope it to a `network_security_config` with the specific hosts that need it | S4 |

Validation: install both flavours, do Detail→Detail→back (must show the right anime), force a provider failure (must show an error, not 24 fake episodes), check APK size, run BACK from every screen.

### P1 — Foundations (design system + theme + state)

| # | Change |
|---|---|
| P1.1 | Build `ui/design` exactly as in §14: tokens, `AniFlowTheme(deviceType)`, the TV type multiplier, the 10 shared components |
| P1.2 | Replace all 245 hardcoded `fontSize` literals with typography tokens; delete the global `var … by mutableStateOf` theme colors in favour of a `CompositionLocal` |
| P1.3 | Introduce `AniResult<T>` at the repository boundary; replace `null`-means-error; surface real error/empty/loading states through one `UiState<T>` |
| P1.4 | `AppContainer` for construction; single `HttpClient`; single repository instance instead of one per composition |

Validation: build both flavours; visually diff Home/Detail/Player; confirm no screen constructs its own repository.

### P2 — One product, one code path (flavour collapse + navigation)

| # | Change |
|---|---|
| P2.1 | Promote the three good redesign elements into the standard screens; delete `ui/redesign/Redesign*`; remove all 13 `endsWith(".redesign")` checks; keep the flavours only if you want two applicationIds, otherwise remove the dimension |
| P2.2 | Implement the §15 phone IA: Home / Discover / Library / Profile; Search becomes a destination; tab state in `SavedStateHandle` |
| P2.3 | Deep links `aniflow://anime/{id}` and `aniflow://watch/{id}/{ep}` |

Validation: every route reachable, back stack correct from a cold deep link, no `redesign` string left in source.

### P3 — Data layer (offline-first)

| # | Change |
|---|---|
| P3.1 | Room for watchlist, watch history/progress, and a metadata cache; migrate from the DataStore JSON blobs with a one-shot importer |
| P3.2 | Repository reads flow from Room and refresh in the background; playback progress written continuously, not only on exit |
| P3.3 | Consolidate provider fallback into one honest failover path with the circuit breaker; delete `SelfHealingEngine` unless it earns its keep |

Validation: airplane mode — Home, Library and Detail still render from cache; kill the app mid-episode and resume within a second or two of the right position.

### P4 — TV as a first-class product

`androidx.tv.material3` adoption, the left rail, the focus-driven hero, the focus checklist on every rail, Watch Next, voice search, transport keys, D-pad seek acceleration, two-pane detail.

Validation: complete a full session — launch → resume → finish an episode → next episode → back to home — **using only a D-pad**, with no focus trap and no unreachable control.

### P5 — Player and playback quality

MediaSession + PiP, subtitle styling and selection, quality/audio selection that persists, gesture and long-press controls on phone, silent server failover, resume-position accuracy, and the player-overlay/auto-hide state machine rebuilt as an explicit state rather than a pile of booleans.

### P6 — Growth features

Downloads for offline, notifications for airing episodes, AniList two-way sync, recommendations that name their reason, and a real onboarding that seeds taste.

## 19. Estimated complexity per phase

| Phase | Scope | Files touched | Risk | Relative effort |
|---|---|---|---|---|
| P0 | 8 mechanical fixes + deletions | ~20, mostly deletions | **Low** — deleting dead code and adding decorators cannot regress much | 1 |
| P1 | New design system + token migration | ~40 (touch many, change little in each) | Medium — wide but shallow; typography diffs are visible | 3 |
| P2 | Flavour collapse + new IA | ~30, large deletions + new nav graph | **Highest** — this is where regressions hide | 4 |
| P3 | Room + offline-first | ~15 new, ~10 modified | Medium-high — migration correctness matters; users have existing data | 3 |
| P4 | TV rebuild | ~25 TV-only | Medium — isolated to the TV branch, but needs a real device | 4 |
| P5 | Player | ~10, but the two largest files in the repo | High — playback bugs are hard to reproduce | 3 |
| P6 | Growth features | new surfaces | Varies | 5+ |

P0 and P1 are the only phases I would do without pausing between them. P2 should be its own reviewed commit series.

## 20. My own ideas

These are product ideas, not cleanup. Each is something AniFlow can do that AniLight structurally cannot, because AniLight is a website and AniFlow is an app that lives on every screen in the house.

**1. One continuous session across devices.** The single most valuable thing a phone-plus-TV app can own. You start an episode on the phone on the bus, sit down, and the TV's first focused card is that episode at 14:32. This requires only what P3 already builds plus a tiny sync surface, and it is the feature that makes people stop using the website. It is also the concrete expression of the positioning thesis in §13.

**2. "Next up" as a first-class object, not a rail.** Instead of a Continue Watching row of posters, compute a single decision: *what should this person press play on right now?* Ranked over resume position, airing recency, and how close a series is to completion. On TV it is the hero. On phone it is one card above the fold with a play button. Most apps make you choose; the premium feeling comes from not having to.

**3. Airing countdowns that mean something.** AniList already returns `nextAiringEpisode`. Surface it as a live countdown on the poster of anything the user is watching, group "Airing Today" at the top of Home, and offer one notification per series at air time. This is cheap — the data is already in the response — and it is the thing seasonal watchers open an app for daily.

**4. Honest playback UI.** Never show a server picker unless the user asks for one; never show 24 episodes that do not exist; when a stream fails, say "trying another server" once and move on. Trust is a feature and it is the one AniFlow currently loses hardest (B4).

**5. Season-aware episode navigation.** The provider returns flat episode numbers; AniList knows about sequels and relations. Joining them lets the detail screen say "Season 2, Episode 3" and lets "next episode" cross a season boundary into the sequel entry. Nobody in this category does this well.

**6. A skip-intro button earned from data, not guessed.** Do not ship a hardcoded 90-second skip. Record where users manually seek in the first two minutes of each episode, and once there is a cluster, offer the skip. If there is no data, show nothing. Requires a tiny endpoint, and it is the single most-loved feature in this category.

**7. Downloads that are actually about the commute.** Not "download this episode" — "keep the next 3 unwatched episodes of what I'm watching on the device." Set once in Profile, and the app is always ready for a tunnel.

**8. A remote-first search that assumes typing is painful.** Voice first, then recent searches, then a genre/season grid — with the on-screen keyboard as the last resort rather than the default. On phone, the inverse.

**9. Make the "why" visible in recommendations.** Every rail should be able to name its reason: "Because you finished Vinland Saga", "Studio Bones", "Airing now, similar to your list". Unlabelled algorithmic rails feel arbitrary; labelled ones feel like the app knows you. AniList's relation and tag data is enough to do this without any ML.

**10. Two things I would deliberately *not* build**, because they cost more than they return: an in-app comment/social layer (moderation liability, low engagement, AniList already owns the social graph), and a second parallel visual "premium" theme maintained as separate screens (§16 — a token swap gets 90% of it for 5% of the cost).

## 21. Appendix — AniLight: what is confirmed vs. unverified

I could not load `https://anilight.live/` from this environment (egress blocked, and I did not attempt to work around it). So this section is deliberately split.

**Confirmed from AniFlow's own working integration** (`AniLightProvider.kt`, which successfully talks to `api.anilight.live`):

- The backend exposes an episode list per anime and a resolvable stream source per episode, addressed by its own internal ID — which is why AniFlow needs the fuzzy title match in `ProviderMappingStore` to bridge AniList IDs to it.
- Streams are HLS, and the manifests need normalization (`HlsManifestNormalizer`) plus ad-segment filtering (`AdBlocker`) — i.e. the upstream sources are ad-injected, which tells you the site's own player must do the same work.
- Multiple servers/qualities exist per episode, and they are not equally reliable — hence the failover controller. **This is the strongest inference available: the site's reliability model is "try another server," and AniFlow's opportunity is to do that silently where a website makes you click.**

**Unverified — must be checked before any design decision depends on it:** its actual layout, navigation, information hierarchy, home-page rail composition, filter capabilities, detail-page structure, player chrome, or how it handles continue-watching. I have not seen the site.

**What this means for the roadmap:** nothing in P0–P4 depends on AniLight's visual design. If you want a real comparative critique, send me screenshots of its home, browse, detail and player screens, or paste the DOM, and I will produce §21 properly as a "what it does better / worse / what we should take" table.

