# AniFlow revival — session 1: player stabilisation, updater fix, TV home plan

**Date:** 2026-11-06 · **Baseline:** v1.8.8 (versionCode 54) on `main` · Nothing committed.

## Where the tree actually stood

`CLAUDE.md` says "nothing in the source has been changed yet". That is stale. P0.1–P0.8 landed on `main`
(see `AniFlow_Phases/P0_STATUS_AND_FINISH.md`), and then 1.8.7/1.8.8 added things on top that undo parts of it:

| Finding | Evidence | Class |
|---|---|---|
| 22.9 MB of media back in `res/raw` (Bleach trailer MP4 11.9 MB + OST MP3 11.1 MB) | `res/raw/`, used by `BleachBgmManager.kt:49`, `RedesignTvHomeScreen.kt:949` | PERFORMANCE PROBLEM |
| Release signing fell back to the **debug key** when `keystore.properties` is missing | `app/build.gradle.kts:58` (fixed) | SECURITY PROBLEM |
| Every update dialog became a "Maintenance" dialog with no Install button for anyone on versionCode ≥ 53 | `MainScreen.kt:768-769` `isMaintenance = !isPreviousVersionUser \|\| …` (fixed) | **BUG** |
| 1.8.6 and earlier are debug-signed; 1.8.7+ are release-signed | `apksigner`: 1.8.6 = `CN=Android Debug`, 1.8.8 = `CN=AniFlow` (`b9b1424a…1650`, same as `keystore/aniflow-release.jks`) | BUG (unfixable in code) |

**About the last row:** anyone still on ≤1.8.6 **cannot** update to 1.8.7+ in place. Android refuses an APK signed
with a different key, and no code change can get around that. Those users have to uninstall once and then reinstall.
From 1.8.7 onwards, updates install over each other normally. **Back up `keystore/` and `keystore.properties` outside
this repo.** If you lose them, you can never ship an update again.

Measured release APK: **26,765,175 bytes**. About 22.9 MB of that is the two Bleach files. Without them it would be
roughly 4 MB.

## What I changed (all compiled: `assembleStandardDebug`, `testStandardDebugUnitTest`, `assembleStandardRelease` green)

### Player: root causes of the crashes and the quality "shifter hell"

| # | Problem | Evidence (pre-change) | Fix |
|---|---|---|---|
| 1 | **Bandwidth counted twice → ABR oscillation.** The meter was given to the player (`setBandwidthMeter`) *and* to the HTTP factory (`setTransferListener`). ExoPlayer already attaches the player's meter to every media DataSource, so each byte was reported twice. That makes the link look ~2× faster than it is: the player picks a variant it can't sustain, stalls, drops quality, climbs again, repeat. | `PlayerScreen.kt:154` + `:218` | Removed `setTransferListener`. The meter is now `DefaultBandwidthMeter.getSingletonInstance`, so the next episode starts at the bitrate it learned, not the cold-start guess. |
| 2 | **Every quality change flushed the buffer.** `applyVideoQualityOverride` did `seekTo(currentPosition)` after each parameter change. It was also called from **every** `onTracksChanged`. The result was a full rebuffer on each switch, plus repeated re-selection. It also set min = max video size *and* an override, which conflict. | `PlayerScreen.kt:1190-1254`, `:265` | Rewritten. Auto clears all overrides. A fixed height pins the closest supported variant with one `TrackSelectionOverride`, with **no seek**: ExoPlayer switches at the next segment and keeps what is already buffered. `onTracksChanged` only binds a pending fixed choice to the tracks of the *current* item (stale overrides from the previous item are detected). |
| 3 | **OOM risk on a 1 GB TV.** The buffer was 25–60 s forward + 30 s back, with `prioritizeTimeOverSizeThresholds = true`, which **turns off the byte cap**. A 1080p stream could hold about 90 MB of Java heap in the media buffer alone. | `PlayerScreen.kt:176-185` | `buildMemoryAwareLoadControl`: byte cap enforced at heap/5 (16–64 MB). Constrained devices (low-RAM or heap ≤ 256 MB) get 15–30 s forward and 5 s back. Starts after 2.5 s of buffer (was 5 s). |
| 4 | **A forced rebuffer near the end of most episodes.** A "proactive" refresh swapped `selectedSource` after 20 min of playback, which tears down and re-prepares the player. | `PlayerViewModel.kt:129-170` | Deleted. Expired URLs show up as a 403/410, and failover already handles that. |
| 5 | **A Wi-Fi blip switched servers.** Any fatal error went straight to failover. ExoPlayer's default segment retry count is 3. | `PlayerScreen.kt:261` | Load retries raised to 6. A connection drop or timeout on a server that was already playing retries **the same server in place** once. Behind-live-window jumps to the live edge. Only after that does it fail over. |
| 6 | **Failover ping-ponged A→B→A.** The cooldown map was written but never read. | `PlayerViewModel.kt:180` (B11) | Failover candidates skip endpoints that failed in the last 30 s. |
| 7 | **The wrong episode could start playing.** `reResolveSources` slept up to 10 s and then wrote the source with no generation guard. | B7 | It now bumps `generationId`, registers as `resolutionJob`, and checks the generation after the delay and after the network call. |
| 8 | **Empty source list → permanent black screen.** | B8, `PlayerViewModel.kt:352` | Shows an error and runs failover. |
| 9 | **Failover/re-resolve could restart the episode from 0.** It relied on `exoPlayer.currentPosition` after a fatal error. | `PlayerScreen.kt:313` | New `resumeAtMs`, set by failover/re-resolve and consumed once when the next source is prepared. |
| 10 | **Progress was never saved for users who tapped the controls.** | B10 | The save timer is a separate effect keyed only on the player. |
| 11 | **Garbage duration and a collapsed seek bar.** | B14 | `duration.coerceAtLeast(0)`. |
| 12 | **The "Auto-play next" toggle was ignored.** | B12 | `SharingStarted.Eagerly`. |

Also: `setAllowCrossProtocolRedirects(true)`, and timeouts set to 15 s.

### Updater
- `MainScreen.kt:768`: maintenance is now driven only by the remote JSON (`maintenance`, versionCode ≥ 99999, or
  "Maintenance" in the name). 1.8.7 users get a real Install button again.
- `app/build.gradle.kts`: no silent debug-key fallback.
- `AppUpdater` hardening from P0.3 is intact and was not touched: HTTPS on every hop, signer check, no downgrade.

## Not done / not verified

- **Nothing was tested on a device.** Everything above was compiled and read. Nothing was played.
- The flavour split, design system, Room, and the TV rail (P1–P4) are untouched.
- `lintStandardDebug` has not been run.
- `findSeries` in the Miruro/Anikoto providers still fabricates a match (known since P0; it belongs to the provider cleanup).

## Device checklist (phone + OnePlus Y1S)

| # | Test | Pass |
|---|---|---|
| 1 | Play an HLS episode on Auto for 5 min | Quality settles. It does not keep bouncing up and down |
| 2 | Switch Auto → 480 → 1080 → Auto mid-episode | No black screen and no restart from 0. The picture changes within a few seconds and the buffer stays filled |
| 3 | Turn Wi-Fi off for 5 s mid-episode, then on | Same server resumes at the same spot. No "switching server" |
| 4 | Watch a full episode on the Y1S | No crash, and no rebuffer around minute 20 |
| 5 | Next episode starts | Starts at a sensible quality straight away, not 360p |
| 6 | Turn Auto-play next off, finish an episode | It stops |
| 7 | On 1.8.7: Settings → check for updates (once 1.8.9 is published) | Shows "Update Available" + Install, not "Maintenance" |

## The Netflix-style TV home: proposal (not built yet)

What you described: the app opens like Netflix, the focused series expands and plays its trailer, and moving
away puts it back. That is the right model for the Y1S. Below is the design. It uses an original AniFlow look
inside that genre, not Netflix's branding or assets.

**Layout**
- **Collapsed left rail** with icons. It slides open with labels when focused, and content parallax-shifts with it.
- **Billboard** across the top ~55%. It follows focus: 500 ms after focus settles on a card, the backdrop
  cross-fades to that series, with title, score, year, episode count, genres and a 2-line synopsis.
  After a further ~1.5 s idle, the trailer fades in behind the text, muted by default (a toggle unmutes it).
  Moving focus away pauses the trailer and fades back to the still. Nothing loads for a card the user is just
  scrolling past.
- **Rows** of 16:9 cards: Continue Watching (with a progress bar), Trending, Airing now, Top 10 (rank numerals), genres.
  The focused card springs to 1.1× with a glow. After about 700 ms of dwell it widens into an info panel.
  Focus stays at a fixed x-position (pivot scrolling), and every row restores its focus when you come back to it.
- **Card → Detail** as a shared-element transition. **Launch** is an 800 ms vector logo reveal, skippable,
  with no video file. The old intro MP4s were the 52 MB problem.

**What it costs on a 1 GB TV (constraints I'll design to)**
- Trailers are YouTube, played in a WebView (`RedesignTvHomeScreen.kt:1145`). A WebView costs about
  100–150 MB and 1–2 s to start. So there will be **one** trailer surface for the whole app, created on
  first use and reused. That's why the trailer plays in the billboard, not inside every card.
  On low-RAM devices it falls back to a slow Ken Burns pan of the backdrop, with a setting to force real trailers.
- Animations only change `graphicsLayer` scale/alpha/translation, so they never trigger relayout. No blur
  (`RenderEffect` is expensive on this class of GPU). Gradients are static brushes. Images are decoded at
  card size. Lazy rows use stable keys.
- Built on `androidx.tv:tv-material` (re-added, as planned for P4). It replaces both `TvHomeScreen.kt` and
  `RedesignTvHomeScreen.kt` (2,400 LOC), so the TV ends up with one home screen, not three.

**Ordering (blunt):** the roadmap says P1 → P2 → P4. Building this TV home first, as the single TV path, is
the better trade. It's what you'll see every day, it deletes two screens instead of adding a third, and it
only needs the small slice of P1 tokens it actually uses. The phone side then follows the same tokens in P2.

**Decision needed:** the Bleach tribute (`BleachVfx.kt` 782 LOC, `BleachBgmManager`, 22.9 MB of media,
background music on the TV home). I'd delete it, or move the trailer to a streamed URL. It is 85% of the APK
and runs a MediaPlayer plus a video on the screen the Y1S has to render most smoothly.
