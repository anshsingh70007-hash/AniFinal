# AniFlow — working context for Claude

Read this file first, then read `AniFlow_Phase0_Audit.md` in this same directory. The audit is the
source of truth for what is wrong with this app. **Do not re-derive it** — it was produced from a
full read of the codebase and it costs a lot of tokens to reproduce.

## What this project is

AniFlow is an anime streaming app targeting **Android phone AND Android TV from one APK**. It works
today but was built fast and is structurally messy. The owner (Harmeet) has commissioned a
founder-level rebuild: audit → vision → phased implementation. He wants a serious, premium product
on both form factors, using https://anilight.live/ as a *benchmark, not a template*.

## Stack (v1.8.6 / versionCode 51)

Single Gradle module `:app`. Kotlin 2.3.x, `jvmToolchain(17)`, minSdk 24, compile/targetSdk 36.
Jetpack Compose (BOM 2026.03.01) + Material3. **Navigation3** (`rememberNavBackStack`, `NavDisplay`,
`entryProvider`) — *not* NavHost. Media3 1.5.1 ExoPlayer + HLS. Ktor 3.0.3. kotlinx.serialization.
Coil 3. DataStore Preferences. **No Room, no Hilt/Koin, no DI container.** ~19k LOC Kotlin.

Metadata comes from AniList GraphQL (`data/remote/AniListApi.kt`). Streams come from
`api.anilight.live` (`AniLightProvider`), with Miruro/Anikoto as byte-identical duplicate fallbacks.
The two systems are joined by fuzzy title matching cached in `ProviderMappingStore` (7-day TTL).
**AniList IDs are the identity key throughout.**

## Locked decisions (do not relitigate)

1. **One UI code path. The `standard`/`redesign` product-flavour split is being deleted.** It was
   never a real flavour split — the flavour source sets contain only an unused `config.xml`, and
   selection is a runtime `context.packageName.endsWith(".redesign")` check repeated in **13 places
   across 11 files**. Both APKs ship both UI trees. Keep the standard screens as the single path,
   promote the three genuinely better redesign elements into them (capsule bottom nav, trailer hero,
   episode chunking), delete the rest. If a "premium visuals" mode is ever wanted, it ships as a
   runtime token swap (`LocalAniFlowStyle`), not a second screen tree.
2. **Room will replace the DataStore JSON blobs** for watchlist, watch history/progress and the
   metadata cache, in phase P3. Needs a one-shot importer so existing users don't lose their lists.
3. Navigation3 stays. Fix its decorators, `onBack`, deep links and tab-in-backstack instead.
4. Manual `AppContainer` for construction — **not** Hilt. This app is too small to justify it.

## How Harmeet wants this run

- **Phase by phase.** Audit → proposal → implement ONE phase → validate → next. Never a large rewrite
  in one operation. Reasons he gave: lower token spend, fewer regressions, easier review, cleaner
  commits. Each phase must end with a tree that builds.
- **Evidence or silence.** Every claim of breakage needs `file:line`. Keep *confirmed* bugs separate
  from *potential* risks. He rejects assumptions presented as facts.
- **Classify every finding**: BUG / ARCHITECTURAL PROBLEM / UX PROBLEM / PERFORMANCE PROBLEM /
  SECURITY PROBLEM / TECH DEBT / MISSING FEATURE / UNNECESSARY FEATURE / DESIGN OPPORTUNITY.
- **Be blunt.** He has explicitly invited you to tell him when one of his own decisions is bad. Do
  not preserve bad architecture because it exists. Bias toward deleting code over keeping it.
- **Token frugality is a hard requirement.** Don't re-read unchanged files. Don't dump whole files.
  Prefer targeted grep. Delegate wide sweeps to subagents so findings, not file contents, land in
  context. Think deeply, reply densely and briefly.
- **Don't over-engineer.** No unnecessary abstractions, no new libraries without a reason, no
  refactoring for aesthetics.
- Long deliverables go in Markdown files in the repo root, not in chat messages.

## Build and verify

```bat
gradlew.bat assembleStandardDebug
gradlew.bat testStandardDebugUnitTest
gradlew.bat lintStandardDebug
```

Gradle wrapper 9.1.0. `local.properties` currently points at a *previous developer's* SDK path
(`sdk.dir=C:/Users/RG/AppData/Local/Android/Sdk`) — fix it to the real local SDK before building.
Known-broken already, before any of your changes: `app/src/androidTest/.../MainScreenTest.kt` does
not compile (it calls `MainScreen(List<String>)`). Fix or delete it in P0.

**Build after every phase and do not report a phase as done until it compiles.** Never say a change
is "verified" when you have only read it — say what you statically checked and what still needs a
compile. Before renaming or deleting any symbol, grep every call site first.

## Where the work stands

Phase 0 (discovery + audit) is **complete** — see `AniFlow_Phase0_Audit.md`, 21 sections. The next
action is **P0**, below. Nothing in the source has been changed yet.

## P0 — the immediate task list

Nothing here is a redesign. All of it is "the app currently lies to users, is unsafe, or is bloated."
Full detail and rationale is in §18 of the audit; the confirmed-bug table is §3.

| # | Change | Fixes |
|---|---|---|
| P0.1 | Add `rememberViewModelStoreNavEntryDecorator()` + `rememberSavedStateNavEntryDecorator()` to the `NavDisplay` in `Navigation.kt`; make `onBack` honour the `Int` count it currently discards | **Detail→Detail→back shows the WRONG anime** — `DetailViewModel` is Activity-scoped because there are no `entryDecorators` |
| P0.2 | Delete the fake-episode fabrication in `MiruroProvider.kt:46-60` (and its twin in `AnikotoProvider`); make provider failure return an error | The repo currently returns **24 episodes that do not exist**, with no network call, as if real. Worst trust failure in the app |
| P0.3 | Generate a real release keystore, remove `signingConfig = signingConfigs.getByName("debug")` from `app/build.gradle.kts`; harden `AppUpdater` — HTTPS only, no downgrade on redirect, verify the downloaded APK's signing certificate before install | Release builds are debug-signed; the updater is a supply-chain hole |
| P0.4 | Remove `UserFeedbackStore`'s write to the public unauthenticated `api.restful-api.dev` document | Any user can clobber it |
| P0.5 | Delete the two intro MP4s in `res/raw` (35.2 MB + 17.3 MB) and `IntroOverlay`; delete `filmGrainOverlay` (allocates ~1–3k objects **per frame, forever, full-screen, on TV too**) and `AmbientBackground` | 56 MB APK → ~6 MB; removes the single worst perf defect |
| P0.6 | Delete dead code: `ui/HomeScreen.kt`, `ui/BrowseScreen.kt`, `ui/LibraryScreen.kt`, `ui/SettingsScreen.kt` (1,222 LOC, provably unreferenced). Remove 9 never-imported deps: haze, haze-materials, lottie-compose, orbital, konfetti-compose, palette, tv-foundation, tv-material, lifecycle-viewmodel-navigation3 — **except** re-add `tv-material` in P4, and note removing `lifecycle-viewmodel-navigation3` conflicts with P0.1, so keep that one | Tech debt; build back to green |
| P0.7 | Add `BackHandler`s — sub-tab → Home, Home → confirm-to-exit, TV rail-aware. There is currently only **one** `BackHandler` in the entire app (the player), so BACK from any sub-tab exits | Navigation bugs |
| P0.8 | Remove `usesCleartextTraffic="true"` from the manifest, or scope it to a `network_security_config` listing only the hosts that genuinely need it | Security |

P0.1 needs `androidx.lifecycle:lifecycle-viewmodel-navigation3` — so do P0.1 *before* the dependency
pruning in P0.6, and keep that artifact.

**P0 validation, to run on a real device:** install and do Detail→Detail→back (must show the correct
anime); force a provider failure (must show an error, not 24 phantom episodes); check the APK size;
press BACK from every screen including sub-tabs.

## After P0

P1 design system (`ui/design` tokens + `AniFlowTheme(deviceType)` with a TV type multiplier; replace
245 hardcoded `fontSize` literals; kill the global `var … by mutableStateOf` theme colours; introduce
`AniResult<T>` and one `UiState<T>`; `AppContainer`). P2 flavour collapse + new phone IA
(Home / Discover / Library / Profile, Search as a destination) + deep links. P3 Room offline-first.
P4 TV as a first-class product (`androidx.tv.material3`, left nav rail, focus-driven hero, focus
checklist on every rail, Watch Next, voice search, transport keys). P5 player (MediaSession, PiP,
subtitles, silent server failover). P6 growth (downloads, airing notifications, AniList sync).

Sections 13–20 of the audit hold the product vision, the target package tree, the navigation design,
the mobile and TV strategies, complexity estimates, and ten product ideas. Read them before starting
P1 — the design system in P1 is what every later phase builds on.

