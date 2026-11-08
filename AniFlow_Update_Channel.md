# AniFlow update channel: shipping updates without GitHub

AniFlow updates itself from **its own signed update channel**: a few static files you upload to any HTTPS host, ideally two
or more independent ones. GitHub isn't involved. Code: `data/update/UpdateChannel.kt` (check),
`utils/AppUpdater.kt` (download + install), `tools/update_channel.py` (publish). Tests: `UpdateChannelTest` (19 cases).

## What was wrong before

| # | Problem | Evidence | Class |
|---|---|---|---|
| 1 | Every install read one file on GitHub. A GitHub outage, rate limit or DMCA takedown of the repo would cut every user off from updates at once. | old `UpdateConfig.UPDATE_JSON_URL` | ARCHITECTURAL PROBLEM |
| 2 | That file was unsigned. Anyone able to change it could switch every install into maintenance or force a "must update" lock. The APK signer check stopped malware, but not that lockout. | old `DefaultAnimeRepository.checkUpdates` | SECURITY PROBLEM |
| 3 | Maintenance had no end time, and also triggered on `versionCode >= 99999` or a version name containing "Maintenance". | old `MainScreen.kt:636`, `:805` | BUG |
| 4 | Nothing stopped you publishing a build that couldn't update itself again: a missing key, missing URL, or raised minSdk would strand those users permanently. | n/a | MISSING FEATURE |

## How it holds up

- **Signed manifest.** `update.json` is signed offline with ECDSA P-256. The public keys are compiled into the app, so a hacked, stale, mis-cached or seized mirror can't make an install do anything.
- **Several mirrors.** The app asks every mirror in `UpdateConfig.MANIFEST_MIRRORS`; the newest validly signed answer wins. The APK is fetched from each mirror in turn. One host down means nothing to users.
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

## One-time setup (needs you: hosts are accounts in your name)

Chosen hosts, already compiled into 1.9.0 (both names were free on 2026-11-08):

| Mirror | URL | Role |
|---|---|---|
| Cloudflare Pages | `https://aniflow-updates.pages.dev/update.json` | Primary. No bandwidth cap. Serves the APK and the welcome clips. |
| Netlify | `https://aniflow-updates.netlify.app/update.json` | Independent fallback. Its free tier **pauses a site that exceeds its bandwidth allowance**, so the app never streams clips from it, and fetches the APK from it only if Cloudflare fails. |

**The project names must be exactly `aniflow-updates`.** If either host gives you a different name (for example
Cloudflare adds a suffix when a name is taken), stop and tell me. The URLs are compiled into the app, so they must match.

1. **Cloudflare:** dash.cloudflare.com → Workers & Pages → Create → Pages → **Upload assets** → project name
   `aniflow-updates` → drop the `dist/update` folder → Deploy.
2. **Netlify:** app.netlify.com → Add new site → **Deploy manually** → drop the same `dist/update` folder. Then
   Site configuration → Change site name → `aniflow-updates`.
3. `python tools\update_channel.py verify --live`. Both mirrors must say ok.
4. Do a real update test on a device. Only then distribute the 1.9.0 APK. From then on, every update flows through the channel.

Later, a domain you own (around $10 a year) pointed at Cloudflare is the strongest setup. Add it as a **third** mirror
in a release; remove a `*.dev`/`*.app` mirror only once every install in the field knows the domain.

**Size limit:** Cloudflare Pages refuses the whole upload if any single file is over 25 MiB. The publish tool checks
this. The welcome clips were re-encoded to fit.

## Every release

```bat
:: 1. bump versionCode (and versionName) in app/build.gradle.kts
gradlew.bat assembleStandardRelease
:: 2. write notes.txt (users see it), then:
python tools\update_channel.py publish app\build\outputs\apk\standard\release\app-standard-release.apk --notes-file notes.txt --rollout 10
:: 3. upload the whole dist\update\ folder to BOTH mirrors (Cloudflare: project → Create deployment; Netlify: Deploys → drag folder)
python tools\update_channel.py verify --live
:: 4. a day later, if nothing broke:
python tools\update_channel.py set --rollout 100     (then upload and verify again)
```

- **Halt a bad release:** `set --halt`, upload. Nobody else gets prompted.
- **Rollback:** Android can't install an older versionCode over a newer one. Rolling back means rebuilding the last good
  code with a **higher** versionCode and publishing that. This is why the staged rollout matters.
- **Maintenance:** `set --maintenance "Back at 6 PM" --hours 3`, then `set --clear-maintenance` when done (or let it expire).
- **Force an update** (for example after a security fix): `publish ... --min-version <code>`. Installs below that code get no "Later" button.

## Moving existing 1.8.x installs onto the channel

Builds up to 1.8.8 still read `app_update.json` from GitHub. Before you stop using GitHub, change that file **once**,
setting its `updateUrl` to the first channel build's APK on your new host. The old updater accepts any HTTPS URL and
still checks the signing certificate. After those users update, GitHub is no longer involved. (Not done: it's a push to
`main`, which needs your go-ahead.)

## Limits, stated plainly

- Android always shows its own install confirmation. Silent installs need Play, root or device-owner mode.
- A build that crashes on startup can't fix itself. Staged rollout plus `--halt` is the defence: ship to a few first.
- Checked so far: unit tests and the publish tool against a real release APK. **Not yet checked end to end on a device
  against a live host**, because no host exists yet. Do that on step 4 of the setup, before distributing.
