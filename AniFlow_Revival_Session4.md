# AniFlow revival, session 4 (2026-11-11): new phone UI, 1.9.3

Phone only. TV screens are untouched except the shared "new episodes" fix below.
Design source: motion board v7 (claude.ai artifact Gv7vfkAhWziyQboQ6c2rFX).

## What was built

| Area | Files | Notes |
|---|---|---|
| Motion tokens + haptics | `ui/phone/motion/PhoneMotion.kt` | spring/snappy/smooth springs, glide/arrive curves, `pressScale`, `rememberReduceMotion`, `Haptics` with API fallbacks |
| Shell | `ui/phone/PhoneShell.kt` | top bar (avatar → Settings, For You/Series/Films on Home, title elsewhere, search), capsule tab bar that shrinks on scroll, resume bar, bottom fade; `LocalPhoneBottomInset` for screen padding |
| Home | `ui/phone/PhoneHomeScreen.kt` | featured banner (parallax, Play/Resume), sections in panels; Series/Films hide rows with < 4 titles after filtering |
| Hold to move | `ui/phone/home/SectionReorder.kt` | 450 ms hold on a header lifts, drag swaps past neighbour centres, edge auto-scroll; grip = Move up/down (also a11y actions); order in `SettingsStore.homeOrder` |
| Detail sheet | `ui/phone/PhoneSheet.kt`, `Navigation.kt` | slides up on glide, page behind sinks; pull down to dismiss with iOS-style velocity projection |
| Player | `ui/player/PlayerChrome.kt` | phone ±10 s: icon turns, "+10 s" rises (shadowed) |
| Fix | `data/model/WatchHistoryEntry.kt` `freshEpisodes` | NEW badge only once your episode is finished. Was "1179 new episodes" mid-E1 of ONE PIECE (phone card and TV caption). Unit test added. |

The old standard phone branch inside the `Scaffold` in `MainScreen.kt` is now unreachable (only the redesign flavour
uses that Scaffold). Delete it with the flavour collapse (P2).

## Tested on the emulator (Medium_Phone, API 36)

- Real user path: 1.8.8 (release-signed) → live 1.9.2 from GitHub (sha256 matched) → local 1.9.3 release, all `adb install -r`.
  Watchlist and history survived; no crash in logcat.
- Hold-and-drag reorder (lift, dim, swap), order persists across a force-stop. Segments. Compact bar on scroll and
  tap-to-expand. Discover + search button raises the keyboard. Library / Schedule / Settings layouts. Detail open,
  pull-down dismiss, system BACK. Resume bar → player resumes. ±10 s seeks and animates.
- Unit tests: 65 pass. Lint: 0 errors.

## Not verified

- Haptic feel (emulator has no motor). Real-device check recommended.
- The in-app update from 1.9.2 → 1.9.3 (needs the GitHub release to exist). The adb path above covers the install itself.
- Small phones / large fonts, and Android 7–9 devices.

## Release state

`dist/update/AniFlow-1.9.3-58.apk` + `update.json` built by `tools/update_channel.py publish` (all checks ok).
Notes: `dist/notes-1.9.3.txt`, `dist/release-1.9.3.md`. **Not published**: the GitHub release, `verify --live`,
`app_update.json` and the commit/push wait for the owner's OK.

## Second pass (same day): Home rows and broken playback

Owner report: "Fresh episodes shows old anime", other rows too, and Gintama plus many others show a player error.

| # | Finding | Evidence | Class | Fix |
|---|---|---|---|---|
| 1 | "Fresh episodes" came from anilight's `recentlyAddedEpisodes`, its **upload** feed: Squid Girl (2010), Ghost in the Shell SAC 2nd GIG (2004), Kabaneri movie (2019) | `DefaultAnimeRepository.getRecentlyUpdated` (old code) + `/api/homepage` dump | BUG | `AniListApi.getFreshEpisodes()`: `airingSchedules` of the last 72 h, newest first, popularity ≥ 2500, no adult/music. Falls back to the week schedule. Stale disk copies are filtered. |
| 2 | "Trending now" = anilight's trending, which mixes in Black Clover (2017), Bleach (2004) | same dump | UX PROBLEM | AniList `TRENDING_DESC` (community activity across sites) ×50, keep airing or released this year/last (`isCurrentAnime`, unit tested), top 24. anilight trending (filtered) is the fallback. |
| 3 | Action / Romance rows = all-time popularity, so they repeated "Popular all time" | `getAnimeByGenre` POPULARITY_DESC; romance went through `getAnimeByGenre("Romance")` in `MainScreenViewModel` | UX PROBLEM | `getTrendingInGenre`: trending in genre, started in the last 3 years, popularity > 8000; not-yet-aired dropped. |
| 4 | "Airing this season" could list shows that haven't started | `getSeasonal` | BUG | NOT_YET_RELEASED filtered. |
| 5 | MegaPlay files many sequels only under the **MAL** id; the app asked by AniList id only. 30 of 191 popular titles failed (all Gintama sequels, Demon Slayer, HxH 2011, AoT S2/S3, MHA S2-4, DAN DA DAN, JJK 0…) | sweep of `megaplay.buzz/stream/{ani,mal}/…` page titles | BUG | `PlayerViewModel.embedFallback`: per episode+audio, try AniList id → MAL id → same-format prequel's MAL id with its episode count added (split-cour Part 2 = Part 1 eps 13+). The route that loads becomes the show's first choice; one that fails loses it. `#epN` on offset URLs keeps progress saved to the real episode. |

Rows kept all-time on purpose (their titles say so): Popular all time, Highest rated, Blockbuster Movies, Spotlight shelves.

**Reddit:** not wired. Reddit's Data API needs OAuth, a registered app and, for commercial use, an agreement; every install
would share one client's rate limit; there is no backend to cache it. AniList's trending score is the community signal
used instead, and it updates itself. A Reddit-fed ranking needs a small server first (future, with accounts).

**Playback after the fix:** 26 of the 30 sweep failures resolve (24 by MAL id, AoT S3 Part 2 and Final Chapters Special 2
by prequel numbering). Still unplayable, because MegaPlay has no file for them at all: Made in Abyss: Mezameru Shinpi,
Rascal Does Not Dream of a Dear Friend, GROTESQQQUE, To You in the Beyond (all 2026 films). No app change can fix those.

Tested on the emulator (release build over the previous install): Home rows (Trending, Fresh episodes, Action, Romance
current); Gintama Season 3 E1 → Next → E2 play; AoT S3 Part 2 E1 plays and is labelled E1. Unit tests + lint (0 errors).
Not tested on the device: AoT Final Chapters Special 2 (MegaPlay page checked from the desktop only); TV Home rendering of the new rows.

1.9.3 was rebuilt with these fixes (still versionCode 58: it was never published; `dist/update/update.json` was reset to the
live 1.9.2 manifest before re-running `publish`).

## Third pass: phone and TV home must match

Owner rule: **phone and TV show the same content, names and order; only the UI differs.** They had drifted: phone said
"Recently updated" on the live 1.9.2 while TV said "Fresh Episodes", TV had no Airing today / Your picks, TV showed only the
top 10 highest rated, and the titles and order differed (TV "All-Time Popular", "Adrenaline: Action", "Heart-Racing Romance").

Fix: `ui/home/HomeRowSpec.kt` is the single list of row keys, titles and default order. `PhoneHomeScreen` and `TvHome`
both build from it and sort by it (the phone's drag-to-reorder still applies on top). TV keeps its own drawing: rank
numerals on the first 10 of Highest rated, and Airing today as poster cards.

Tested: phone (emulator-5554) and TV (Television_720p, release build installed over live 1.9.2) list the same rows in the
same order under the same names. Your picks shows only where the person has given feedback (none on the TV emulator).

## Fourth pass (2026-11-11): anime colour + TV picture

**TV softness: root cause (ARCHITECTURAL, not a code slip).** The old crisp picture was native ExoPlayer on a `SurfaceView`, so the TV's own video engine scaled and sharpened it. Since native sources died, playback is MegaPlay's page inside a WebView. That draws video into the app UI layer, which Android TV renders at ≤1080p (often lower) and then scales like a screenshot. The video path is bypassed.

**Fix:** on TV, once playing, the player arms a one-shot `requestFullscreen()` and sends a synthetic tap, which is the user gesture fullscreen requires (`EmbedPlayer.kt`, `JS_ARM_FULLSCREEN`, `WebHolder.tap`). Chromium hands over its fullscreen view (`onShowCustomView` → `WebHolder.showCustom`, non-focusable so Compose keeps every key). In fullscreen, with no CSS filter on the video, Chromium may promote the video to a hardware overlay that the TV's picture engine processes.
- Verified on the TV emulator (API 36, WebView 156): fullscreen view attaches; chrome, pause, Quality sheet and next episode all work; BACK hides chrome, then leaves the player; no leftover views.
- **Not verifiable on the emulator:** whether a given TV actually promotes the video to an overlay. There's no overlay hardware here (no extra SurfaceFlinger layer appears). Needs a real 4K TV.
- Bug found and fixed while testing: on Android 13+, WebView registers its own back callback while fullscreen, so the first BACK only left fullscreen. `WebHolder.claimBack()` registers ours after it at `PRIORITY_OVERLAY` and forwards to the Activity's dispatcher. It's released in `removeCustom()`.

**Anime colour** (`SettingsStore.animeColor`, toggle in phone + TV Settings): CSS `filter: contrast(1.06) saturate(1.12) brightness(1.01)` on the page's `<video>` only. Default **on for phone, off for TV**, because any CSS filter forces the composited path and would undo the TV fix above. A TV user who turns it on trades the TV engine's sharpening for the grade.

Not done: CAS line sharpening (research doc §2); an Anime colour switch inside the player's sheet. No "4K" label, because the source is 1080p.
