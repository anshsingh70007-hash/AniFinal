# AniFlow update channel

AniFlow updates itself from **its own signed update channel**, hosted on **GitHub Releases** of
`anshsingh70007-hash/AniFinal`. Each release carries two files: the APK and a signed `update.json`. Code:
`data/update/UpdateChannel.kt` (check), `utils/AppUpdater.kt` (download + install), `utils/InstallResultReceiver.kt`
(the system's install answer), `tools/update_channel.py` (publish). Tests: `UpdateChannelTest` (19 cases).

## What was wrong before

| # | Problem | Evidence | Class |
|---|---|---|---|
| 1 | Every install read one file on GitHub. A GitHub outage, rate limit or DMCA takedown of the repo would cut every user off from updates at once. | old `UpdateConfig.UPDATE_JSON_URL` | ARCHITECTURAL PROBLEM |
| 2 | That file was unsigned. Anyone able to change it could switch every install into maintenance or force a "must update" lock. The APK signer check stopped malware, but not that lockout. | old `DefaultAnimeRepository.checkUpdates` | SECURITY PROBLEM |
| 3 | Maintenance had no end time, and also triggered on `versionCode >= 99999` or a version name containing "Maintenance". | old `MainScreen.kt:636`, `:805` | BUG |
| 4 | Nothing stopped you publishing a build that couldn't update itself again: a missing key, missing URL, or raised minSdk would strand those users permanently. | n/a | MISSING FEATURE |

## How it holds up

- **Signed manifest.** `update.json` is signed offline with ECDSA P-256. The public keys are compiled into the app, so a hacked, stale, mis-cached or seized mirror can't make an install do anything.
- **Mirrors.** The app asks every mirror in `UpdateConfig.MANIFEST_MIRRORS`; the newest validly signed answer wins. Today there is one (GitHub), so this protects only once a second is added.
- **No replay.** Each manifest has a rising `sequence`, and an install never accepts one lower than the highest it has seen.
- **The APK is pinned by size and SHA-256** from the signed manifest, and must also carry the release signing certificate, the right package and exactly the manifest's version code. Only then is Android asked to install it.
- **Device check.** An update needing a newer Android than the device has is never offered, rather than offered and then failing.
- **Maintenance switches itself off** at the end time you give it (72 h maximum).
- **Staged rollout.** Ship to 5%, then 25%, then 100%, or halt. A manual "Check for updates" in Settings still offers the update to people outside the rollout, because they asked.
- **The publish tool refuses** debuggable builds, the wrong signer, a non-increasing versionCode, an APK missing either trusted key, and an APK with none of the configured mirrors compiled in. That last check catches "this build could never update again" before anyone installs it.
- **Format compatibility.** Unknown manifest fields are ignored, and a manifest from a newer schema is skipped, not misread. Old installs keep working as the format grows.

## Keys: back these up offline today

| What | Where | If lost | If leaked |
|---|---|---|---|
| APK signing key | `keystore/aniflow-release.jks` + `keystore.properties` | **You can never update installed apps again** (an Android rule; no workaround) | Someone could build APKs that install over AniFlow; rotate via a new package |
| Update key k1 | `keystore/update-keys/k1.pem` | Sign with k2 (already trusted), then ship a build that adds a new spare | They can fake rollout or maintenance flags but **cannot install anything** (the APK signer check still applies). Ship a build without k1, sign with k2 |
| Update key k2 (spare) | `keystore/update-keys/k2.pem` | Ship a build with a new spare | Same as k1 |

Copy the whole `keystore/` folder to a USB stick and to one more place. Note that this repo is inside **OneDrive**, so the
private keys are also being synced to Microsoft's cloud. That counts as a backup, but if your Microsoft account is
compromised, so are the keys. Consider moving `keystore/` outside OneDrive. Gradle reads `keystore.properties`, and the
tool reads `keystore/update-keys/`, so update those paths if you move it.

## Hosting (since 2026-11-08)

The only mirror compiled into the app (`UpdateConfig.MANIFEST_MIRRORS`, `tools/update_channel.json`):

`https://github.com/anshsingh70007-hash/AniFinal/releases/latest/download/update.json`

`latest/download/` always points at the release marked **Latest**, and the APK is fetched from the same release by
file name. Every redirect hop is HTTPS; `AppUpdater` follows redirects itself and refuses any non-HTTPS hop.

- **Single host.** If GitHub or the repo goes away, updates stop. A second mirror (a domain you own, ~$10/yr) can be
  added in any release; it must be compiled in *before* it's needed. Cloudflare/Netlify were tried first and never went live.
- **Package identity is `com.example.aniflow`** (standard flavour). The old `.redesign` 1.8.x app is a separate app; it
  can't update in place. Its users reinstall and lose local lists.
- The welcome clips are **inside the APK** (`res/raw`, 1080p, ~81 MB of the ~85 MB APK). Nothing streams from the host.

## Every release

Ask the owner before creating the GitHub release or pushing to `main` (publishing is public).

```bat
:: 1. bump versionCode and versionName in app/build.gradle.kts, then build, test, lint
gradlew.bat assembleStandardDebug testStandardDebugUnitTest lintStandardDebug assembleStandardRelease
:: 2. write notes.txt (users see it). apksigner needs JAVA_HOME (Android Studio jbr) on PATH.
python tools\update_channel.py publish app\build\outputs\apk\standard\release\app-standard-release.apk --notes-file notes.txt
:: 3. publish both files as the new Latest release
gh release create v<ver> dist\update\AniFlow-<ver>-<code>.apk dist\update\update.json --repo anshsingh70007-hash/AniFinal --target main --title "AniFlow <ver>" --notes-file release.md --latest
python tools\update_channel.py verify --live
:: 4. point app_update.json (for 1.8.x installs) at the new APK and push it to main with the code
```

- **Rollout / halt / maintenance** change only `update.json`: run `set ...`, then
  `gh release upload v<current> dist\update\update.json --clobber`, then `verify --live`.
- **Halt a bad release:** `set --halt`, upload. Nobody else gets prompted.
- **Rollback:** Android can't install an older versionCode over a newer one. Rebuild the last good code with a
  **higher** versionCode and publish that.
- **Maintenance:** `set --maintenance "Back at 6 PM" --hours 3`, then `set --clear-maintenance` (or let it expire).
- **Force an update:** `publish ... --min-version <code>`. Installs below that code get no "Later" button.
- **When users see it:** the app checks on a cold start, and Settings → "Check for updates" checks on demand.
  TVs keep the app in memory, so a TV may not see a new release until the app restarts. (A check-on-reopen was
  written and declined on 2026-11-08.)

## How installs happen

1.9.1+ installs through a `PackageInstaller` session; `InstallResultReceiver` starts the system confirm screen. Android
asks once per device to allow AniFlow to "install unknown apps". `ACTION_VIEW` is only a fallback. **1.9.0 is broken on
TVs**: its button opens a browser (most TVs have none), so 1.9.0 TV users must install a newer APK by hand once.

## Old installs

`app_update.json` on `main` is read by the 1.8.x app. It points at the newest APK on GitHub Releases with notes saying
it's a new app. Update it with every release.

## Limits, stated plainly

- Android always shows its own install confirmation. Silent installs need Play, root or device-owner mode.
- A build that crashes on startup can't fix itself. Staged rollout plus `--halt` is the defence.
- Checked end to end on the TV emulator: 54 → 1.9.0 found and downloaded under R8 and reached the system installer.
  The final "allow unknown apps → installed" step was not exercised by Claude (it's a security setting).
