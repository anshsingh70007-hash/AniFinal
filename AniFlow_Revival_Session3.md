# AniFlow revival, session 3 (2026-11-08): update channel, GitHub, 1.9.0 → 1.9.2

Read this before touching the updater, first run, the update dialog or releases. Runbook: `AniFlow_Update_Channel.md`.

## Where things stand

| | |
|---|---|
| Live version | **1.9.2 (57)**, the Latest release on github.com/anshsingh70007-hash/AniFinal |
| `main` on GitHub | `85c9ab2`, same tree as local `revival/session2-overnight` (`4bac94d`) |
| Package | `com.example.aniflow` (standard flavour). The redesign flavour (`.redesign`) still exists in Gradle, unused |
| Signing | release key `keystore/aniflow-release.jks` (signer `b9b1424a…1650`), update keys `keystore/update-keys/k1,k2.pem` — all gitignored, must stay so |
| Build | `assembleStandardDebug testStandardDebugUnitTest lintStandardDebug assembleStandardRelease` all pass at 1.9.2 |

`main` on GitHub was **wiped and force-pushed** on the owner's instruction (old history gone). Each release since is a
`git commit-tree` of the local tree on top of the remote `main` (normal push, no force), so local and remote commit
hashes differ but trees match. Check with `git rev-parse <a>^{tree} <b>^{tree}`.

## What changed, in order

1. **Signed update channel** (`data/update/UpdateChannel.kt`, `tools/update_channel.py`): ECDSA-signed manifest, replay
   protection, rollout, self-expiring maintenance, APK pinned by size + SHA-256 + signer. Old unsigned
   `publish_update.py` deleted.
2. **Hosting moved to GitHub Releases** after Cloudflare/Netlify never went live. Single mirror:
   `.../AniFinal/releases/latest/download/update.json`; APK next to it.
3. **Welcome clips bundled in the APK** (`res/raw/first_run_clip.mp4` 1080p landscape, `first_run_clip_portrait.mp4`
   9:16), played via `RawResourceDataSource`. They used to stream from a host that didn't exist, so the TV showed
   nothing. Owner said APK size doesn't matter (~85 MB).
4. **1.9.0 (55)**: first public build of `com.example.aniflow`. Its TV update button opened a browser → "no app can do
   this". Known broken on TVs; those users install a newer APK by hand once.
5. **1.9.1 (56)**: `AppUpdater` installs through a `PackageInstaller` session + `utils/InstallResultReceiver.kt`
   (manifest receiver, not exported); `ACTION_VIEW` only as fallback. Update dialog (`ui/main/MainScreen.kt`) grabs TV
   focus with retries, traps it (`focusProperties onExit cancelFocusChange` + `focusGroup`), scrolls on 720p; primary
   button installs in-app. **Removed the "Introducing Your Picks" pop-up** (`FeedbackOnboardingScreen` and the
   `feedback_onboarding_shown_v184` setting). The "Your Picks" home row stays.
6. **1.9.2 (57)**: the welcome (clip + Create account / Log in / Continue) only ran when
   `firstInstallTime == lastUpdateTime`, so anyone who got 1.9.x as an update never saw it. Now once per device until
   finished, key `firstRunDone_v2` (`ui/brand/AniFlowBrand.kt`). Tested on the TV emulator: 1.9.1 → 1.9.2 update shows
   it; Continue dismisses it for good.
7. `app_update.json` on `main` (read by old 1.8.x installs) points at the newest APK each release.

## Declined or not done (don't redo without asking)

- **Check for updates on app reopen** (TVs keep the app in memory, so the cold-start check can miss new releases for
  days). Written, built, then the owner said throw it away. Workaround: Settings → "Check for updates".
- **Accounts**: Create account / Log in buttons only show "switch on in an upcoming update". Needs a backend; the
  owner must create a Supabase project and send URL + anon key.
- Final "allow unknown apps → installed" step was never exercised by Claude on the emulator (security setting).

## Lessons from this session

- **Test the path real users take**, not just a fresh install. The first-run gate and the 1.9.0 installer both passed
  fresh-install checks and failed for people updating. For anything first-run or update related, test
  *install previous live APK → update to new build → launch*.
- Don't call something "by design" when it hides a feature from the owner's own device; check whether the design still
  fits (here the package was new, so "fresh install only" excluded everyone who updated).
- Release APK in `app/build/outputs` can be stale or a throwaway test build; rebuild right before `publish`.
- PowerShell 5.1 `Out-File` adds a BOM; write files with `UTF8Encoding $false`. Run Gradle from PowerShell, not
  `cmd //c` from bash (silently does nothing). apksigner needs `JAVA_HOME` on PATH.
