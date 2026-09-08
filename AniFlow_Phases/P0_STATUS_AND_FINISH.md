# P0 — status, every change made, and what is left

**State:** P0.1 – P0.8 are code complete. `assembleStandardDebug` → **BUILD SUCCESSFUL**.
`testStandardDebugUnitTest` → **BUILD SUCCESSFUL**. Nothing is committed; all changes are working-tree
(some deletions are staged). `lintStandardDebug`, the signed release build, the APK size measurement
and all on-device validation are **outstanding** — see "What is left" at the bottom.

Nothing below needs re-doing. Read it so you don't undo it.

## Corrections to CLAUDE.md and the audit (report these; they are wrong as written)

1. **P0.1's "make `onBack` honour the `Int` count it currently discards" is not a real task on
   nav3 1.0.1.** `NavDisplay`'s `onBack` is `Function0<Unit>` (verified with `javap` on
   `navigation3-ui-1.0.1.aar`). There is no `Int`. That half of P0.1 was correctly skipped.
2. **The decorator is `rememberSaveableStateHolderNavEntryDecorator()`**, not
   `rememberSavedStateNavEntryDecorator()` as CLAUDE.md says.
3. **`fontSize` literals are 229, not 245** (the audit counted before the P0 deletions).
4. **The `.redesign` package check is at 12 sites, not 13** (same reason).
5. **P0.5's "56 MB APK → ~6 MB" needs re-measuring, not repeating.** The 52.5 MB of MP4s are gone,
   but nobody has built a release APK since. State the measured number, not the predicted one.

## P0.1 — entry-scoped ViewModels (the wrong-anime bug)

`Navigation.kt`. Added imports `androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator`
and `androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator`, then:

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Without these, every NavEntry shares the Activity's ViewModelStore and
    // SaveableStateRegistry: Detail -> Detail keeps a single DetailViewModel, so
    // popping back leaves the previous entry showing the newer anime's state.
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
    ),
    entryProvider = entryProvider { … }
)
```

`libs.androidx.lifecycle.viewmodel.navigation3` **must stay** in `app/build.gradle.kts` — it is what
provides the ViewModel decorator. It is annotated with a comment saying so.

## P0.2 — no more fabricated episodes

`data/MiruroProvider.kt` and `data/AnikotoProvider.kt` each had a byte-identical block that returned
24 hardcoded episodes with **no network call at all**. Both replaced with:

```kotlin
override suspend fun getEpisodes(seriesId: ProviderSeriesId): EpisodeLookupResult {
    // MegaPlay exposes no episode-list endpoint. This used to fabricate 24 episodes
    // with no network call at all, which surfaced phantom episodes as real ones.
    return EpisodeLookupResult.Error(
        "Miruro/MegaPlay has no episode list endpoint; it can only resolve a stream for an episode that is already known."
    )
}
```

That made the backup-provider loop in `data/repository/DefaultAnimeRepository.kt` unreachable (it
iterated `listOf(ProviderId.MIRURO, ProviderId.ANIKOTO)` looking for a `Matched`), so it was deleted:

```kotlin
if (primaryResult is EpisodeLookupResult.Matched) {
    return primaryResult
}

// No episode-list fallback exists: MIRURO and ANIKOTO both wrap MegaPlay, which has
// no episode-list endpoint. They stay registered for stream resolution/failover only.
// Surface the primary provider's failure instead of inventing episodes.
return primaryResult
```

**Deliberately left alone:** `findSeries` in both providers still fabricates a match. It is not
reached from the `getEpisodes` path. It is out of P0 scope and belongs with the provider cleanup in
P5. Say so rather than silently fixing it.

## P0.3 — real release signing + hardened updater

**Keystore.** Generated with the JBR's keytool: `keystore/aniflow-release.jks`, alias `aniflow`,
RSA 4096, SHA384withRSA, 10,950 days, `CN=AniFlow, OU=AniFlow, O=AniFlow, L=Unknown, ST=Unknown,
C=IN`. Credentials in `keystore.properties` at the repo root. **Both are gitignored**
(`/keystore/`, `/keystore.properties`). Harmeet must back them up outside the repo — losing them
means he can never ship an update to installed users again.

`app/build.gradle.kts` — the debug-signing line is gone; signing is now conditional so a fresh clone
still builds:

```kotlin
import java.util.Properties
…
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
  if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile")
  ?.let { rootProject.file(it).exists() } == true

android {
  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
      …
    }
  }
}
```

Note `import java.util.Properties` at the very top of the file: inside a `.gradle.kts` script,
fully-qualified `java.util.Properties()` does **not** resolve, because `java` binds to the Gradle
`java` extension accessor. That cost one failed build; don't repeat it.

**⚠️ Consequence Harmeet must be told, bluntly:** releases used to be signed with the *debug* key.
Every existing install carries that key. A release signed with the new key **cannot** be installed
over an existing install — users must uninstall first, losing their DataStore-backed watchlist and
history unless P3's importer ships first. The alternative — keep shipping debug-signed — is worse:
the debug keystore's password is literally `android`, so anyone can forge an "update" that the new
signature check would accept. This is a one-time, unavoidable break. It is his call whether to ship
it now or bundle it with P3's migration.

**`utils/AppUpdater.kt` rewritten.** Three defects fixed, public signature unchanged
(`downloadAndInstall(context, url, versionName, onProgress)`) so the 3 call sites in
`ui/main/MainScreen.kt`, `ui/phone/PhoneSettingsScreen.kt`, `ui/tv/TvSettingsScreen.kt` are untouched.

1. HTTPS enforced on **every hop**: `instanceFollowRedirects = false`, a manual loop capped at 5
   redirects, `requireHttps()` on the initial URL and on each resolved `Location` (relative
   redirects resolved with `URL(current, location)`).
2. **Signature verification before install** — `verifyApkOrThrow` compares SHA-256 digests of the
   downloaded archive's signing certs against the installed app's, checks the package name matches,
   and rejects version downgrades:

```kotlin
val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
val downloaded = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw SecurityException(…)
val installed = pm.getPackageInfo(context.packageName, flags)
// packageName equality, versionCode >= installed, then:
if (expected.isEmpty() || actual.isEmpty() || expected != actual)
    throw SecurityException("APK is signed by a different key")
```

   Signers come from `info.signingInfo?.apkContentsSigners` on API 28+, `info.signatures` below.
3. Staging moved from `getExternalFilesDir(DIRECTORY_DOWNLOADS)` to `File(context.cacheDir,
   "updates")` — external app dirs are writable by any app holding legacy storage permission, which
   is a swap-the-APK-between-download-and-install window. `res/xml/file_paths.xml` was narrowed to
   match: `<cache-path name="update_cache" path="updates/" />` (the old `external-files-path` entry
   is gone; `AppUpdater` is the only `FileProvider` user in the app — verified by grep).

Failures now delete the partial file and Toast a distinct message for `SecurityException`
("Update rejected: …") versus transport errors ("Download failed: …").

## P0.4 — UserFeedbackStore is device-local now

`data/UserFeedbackStore.kt` went from 335 to ~165 lines. Deleted: `globalDocUrl =
"https://api.restful-api.dev/objects/ff8081819d82fab6019f6fb7249673e0"`, every Ktor GET/PUT with its
retry/backoff, `GlobalFeedbackData` / `GlobalFeedbackResponse` / `GlobalFeedbackRequest`, and
`mergeFeedbackLists`. Kept `FeedbackAnime`, `UserFeedback`, the DataStore persistence and the
corrupted-blob repair path. `resetServer()` became `clearAll()`.

The whole read path was removed, not just the write: a document nobody writes is pointless to read,
and that document was attacker-writable **text and image URLs rendered in the app** — an injection
vector, not just a data-integrity problem.

Four tests in `app/src/test/.../MainScreenViewModelTest.kt` were deleted with it: `testJsonParsing`
(referenced the deleted `GlobalFeedbackResponse`), `testRealApiFetch`, `testRealApiPut`, and
`runResetServer` — which, when run, **wiped the live public document**. Replaced with two local
round-trip tests (`savedFeedback_isReadBack_fromLocalStore`, `clearAll_emptiesLocalStore`);
`initiallyLoading_isTrue`, `FakeContext` and `FakeAnimeRepository` were left as they were.

## P0.5 — the 52.5 MB and the per-frame allocator

Deleted: `res/raw/intro_first.mp4` (35,233,334 bytes), `res/raw/intro_second.mp4` (17,273,445 bytes),
the now-empty `res/raw/`, `ui/redesign/components/IntroOverlay.kt`,
`ui/redesign/components/AmbientBackground.kt`, and `Modifier.filmGrainOverlay()` from
`ui/redesign/theme/GlassModifiers.kt` (it drove a 150 ms `rememberInfiniteTransition` and allocated a
`List<Offset>` of up to 3,000 points **per frame, full-screen, forever, on TV too**).

`MainActivity.kt` shrank to 37 lines — no `IntroOverlay`, no `isRedesign` branch, no `appScale` /
`appAlpha` reveal animation. `LocalDeviceType` and the TV landscape lock are unchanged.

`AmbientBackground` had 4 real call sites. Its content lambda was `@Composable () -> Unit` (not
`BoxScope`-scoped), so a plain `Box` is a drop-in:

```kotlin
Box(modifier = Modifier.fillMaxSize().background(PrimaryDark)) { … }
```

applied at `RedesignDetailScreen.kt:137`, `RedesignPhoneBrowseScreen.kt:61`,
`RedesignPhoneHomeScreen.kt:82`, `RedesignPhoneLibraryScreen.kt:24` (that last file also needed
`import androidx.compose.foundation.background`). Dead imports were stripped from
`RedesignTvBrowseScreen.kt`, `RedesignTvHomeScreen.kt`, `RedesignTvLibraryScreen.kt`. In
`ui/main/MainScreen.kt` the TV branch's `if (isRedesign) AmbientBackground(…) else Box(…)` collapsed
to the single `Box`. No replacement component was invented — these screens are deleted in P2 anyway.

`MainScreenViewModel.kt` had one leftover reference to `AppLoader` (declared in the deleted
`IntroOverlay.kt`) at the old line 320: `com.example.aniflow.ui.redesign.components.AppLoader
.setLoaded(true)`. Deleted — it only gated the intro overlay. This was the last compile error.

## P0.6 — dead code and dead dependencies

Deleted (1,222 LOC, every declared symbol grepped individually first, all zero external hits):
`ui/HomeScreen.kt`, `ui/BrowseScreen.kt`, `ui/LibraryScreen.kt`, `ui/SettingsScreen.kt`. Note
`SpotlightPager` and `ContinueWatchingCard` also exist in `ui/phone/PhoneHomeScreen.kt:231` and
`:476` — those are the live copies.

Also deleted: `app/src/androidTest/.../MainScreenTest.kt`, an unmodified project template that called
`MainScreen(listOf("Sample1", …))` and asserted `"Hello Sample1!"`. It never compiled. It was the only
file in `androidTest`; the androidTest dependencies were left in place for when real instrumented
tests arrive.

Removed from `app/build.gradle.kts` **and** `gradle/libs.versions.toml` (aliases + version refs) after
verifying 0 occurrences of every package prefix in `app/src`: `haze`, `haze-materials`
(`dev.chrisbanes.haze`), `lottie-compose` (`com.airbnb.lottie`), `orbital` (`com.skydoves.orbital`),
`konfetti-compose` (`nl.dionsegijn`), `androidx-palette` (`androidx.palette`), `tv-foundation`,
`tv-material` (`androidx.tv`). Kept `lifecycle-viewmodel-navigation3` per CLAUDE.md — P0.1 needs it.

**This broke the build in a way worth remembering:** `androidx.compose.material.icons` stopped
resolving in 8 files, because `material-icons-core` was arriving transitively through
`androidx.tv:tv-material` and Material3 in BOM 2026.03.01 no longer pulls it. Fixed by making it
explicit:

```kotlin
implementation(libs.androidx.compose.material3)
// Icons: material3 no longer pulls material-icons-core transitively, and it used to arrive via
// androidx.tv:tv-material (removed here as unused). Only the core icon set is used.
implementation("androidx.compose.material:material-icons-core")
```

When P4 re-adds `tv-material`, **keep that explicit line** — implicit transitives are how this broke.

## P0.7 — BackHandlers

Before this the app had exactly **one** `BackHandler` in 19k LOC (`PlayerScreen.kt:118`), so BACK from
any sub-tab quit the app.

`MainScreen` gained a parameter and one handler. The parameter exists because `NavDisplay`'s own back
handler is only enabled while `backStack.size > 1`, and an entry-level `BackHandler` is registered
*later* in the dispatcher's LIFO list, so it would otherwise swallow BACK during the
Home→Detail transition:

```kotlin
// MainScreen signature
isTopDestination: Boolean = true,   // false while another destination sits on top

// Navigation.kt, entry<Main>
isTopDestination = backStack.size == 1
```

```kotlin
val activity = LocalActivity.current
val navBarFocusRequester = remember { FocusRequester() }
var navBarFocused by remember { mutableStateOf(false) }
var exitArmed by remember { mutableStateOf(false) }
LaunchedEffect(exitArmed) { if (exitArmed) { delay(2_000); exitArmed = false } }

BackHandler(enabled = isTopDestination) {
    when {
        // On TV, BACK from the content grid should land on the nav bar, not quit.
        deviceType == DeviceType.TV && !navBarFocused ->
            runCatching { navBarFocusRequester.requestFocus() }
        currentTab != 0 -> viewModel.setTab(0)
        exitArmed -> activity?.finish()
        else -> {
            exitArmed = true
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
}
```

Rail-awareness needed a way to pull focus back onto the nav bar, so `TvTopNavBar` in
`ui/tv/components/TvSideNavRail.kt` gained an optional requester that it attaches to the **selected**
item, and its start-up auto-focus now resolves through the same value (calling `requestFocus()` on an
unattached `FocusRequester` throws, so this is not optional):

```kotlin
fun TvTopNavBar(
    selectedIndex: Int,
    items: List<Pair<ImageVector, String>>,
    onSelect: (Int) -> Unit,
    selectedItemFocusRequester: FocusRequester? = null   // caller pulls focus back onto the bar
) {
    …
    focusRequester = if (index == selectedIndex && selectedItemFocusRequester != null)
        selectedItemFocusRequester else focusRequesters[index],
    …
    LaunchedEffect(Unit) {
        if (selectedIndex in items.indices) {
            runCatching { (selectedItemFocusRequester ?: focusRequesters[selectedIndex]).requestFocus() }
        }
    }
}
```

The wrapping `Box` in `MainScreen`'s TV branch tracks `.onFocusChanged { navBarFocused = it.hasFocus }`.
Full focus-location tracking on TV is deliberately deferred to P4, where the rail replaces this bar.

## P0.8 — cleartext traffic

`AndroidManifest.xml`: `android:usesCleartextTraffic="true"` removed (line 21 of the old file);
`android:networkSecurityConfig="@xml/network_security_config"` kept. The config was wide open
(`cleartextTrafficPermitted="true"`) and is now closed:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
</network-security-config>
```

Justification, verified by grep: the only `http://` string left in `app/src/main` is a scheme *check*
in `HlsManifestNormalizer.kt:96`. Every endpoint in the source is HTTPS — `graphql.anilist.co`,
`api.anilight.live`, `anilight.live`, `megaplay.buzz`, `cdn.animex.su`, `s4.anilist.co`, `kwik.cx`,
`picsum.photos`, `raw.githubusercontent.com`, `www.dailymotion.com`, `www.youtube.com`,
`www.youtube-nocookie.com`. There is no local HTTP proxy (no `ServerSocket`, no `127.0.0.1`).

**Residual risk, stated honestly:** stream URLs are resolved at runtime and a provider *could* return
an `http://` CDN URL, which will now fail instead of playing. That is the correct trade — and
`PlaybackFailoverController` already exists to move to another server. The config file carries a
comment telling the next person to add a single `<domain-config>` for that one host rather than
reopening `base-config`. If Harmeet reports "some servers stopped working after this build", that is
where to look first.

## What is left (do this first, before touching P1)

Four things. None of them is a code change yet — they are the verification P0 has not had.

**1. `./gradlew.bat lintStandardDebug`.** Never run on this tree. Expect noise from the 229 `fontSize`
literals and from the deleted-flavour leftovers. Triage rule: fix `Error` severity, report `Warning`
severity, do not start a lint-cleanliness project — that is not P0.

**2. `./gradlew.bat assembleStandardRelease`.** Never run. This is the **first minified build of this
app** (`isMinifyEnabled` was already true, but nobody built release since these changes). R8 will
exercise paths debug never did:
- `proguard-rules.pro` currently keeps `com.example.aniflow.data.**`, Ktor and kotlinx.serialization
  serializers. Check the generated serializers survive — a `SerializationException` at runtime for a
  model that works in debug is the signature of an R8 strip.
- `AppUpdater`'s reflection-free, so it is safe, but `getPackageArchiveInfo` needs the APK to be
  readable from cache — that is a runtime check, not a build one.
- If R8 fails, add the narrowest `-keep` that fixes it and write down which class needed it.

**3. Measure the APK.** Do not repeat the audit's "~6 MB" — it is a prediction. Build release, then:

```bash
ls -l app/build/outputs/apk/standard/release/app-standard-release.apk
```

Report the measured bytes next to the old 56 MB. The 52.5 MB of MP4s are provably gone; the rest of
the delta is R8 + resource shrinking and nobody has measured it.

**4. Give Harmeet this on-device checklist.** He does all device testing himself; do not ask him to
run gradle.

| # | Test | Pass condition | Guards |
|---|---|---|---|
| 1 | Home → Detail(A) → recommendation → Detail(B) → BACK | Shows **A**, not B | P0.1 |
| 2 | Open an anime the provider cannot match (obscure/unaired) | An error message; **never** a 24-episode list | P0.2 |
| 3 | BACK from Browse / Library / Settings tab | Returns to Home | P0.7 |
| 4 | BACK on Home once, then again within 2 s | Toast, then exit | P0.7 |
| 5 | TV: BACK from the content grid | Focus lands on the nav bar, app stays open | P0.7 |
| 6 | Play any episode, switch server, switch quality, switch subtitle | All still work | P0.8 regression |
| 7 | Settings → check for updates | Downloads and installs, or shows a *distinct* rejection message | P0.3 |
| 8 | Feedback: submit, kill the app, reopen | Feedback still there, and nothing is fetched from the internet | P0.4 |
| 9 | Cold start | No intro video, no 2 s reveal animation — straight into Home | P0.5 |

**Test 7 caveat he must be told before he tries it:** the currently-installed build is debug-signed.
A release APK signed with the new keystore **cannot install over it**, and the new signature check
would reject it as "signed by a different key" — which is the check working correctly, not a bug. To
test the update path end-to-end he needs two consecutively-versioned builds *both* signed with the new
keystore, installed in that order. Do not "fix" the signature check to make test 7 pass.

**Then stop.** P0 ends here. `P1_DESIGN_SYSTEM.md` is the next session, not the next hour.
