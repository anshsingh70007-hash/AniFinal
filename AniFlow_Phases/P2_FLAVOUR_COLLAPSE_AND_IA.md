# P2 — flavour collapse, glass extraction, new information architecture

**Prerequisite:** P1 is done and green (`ui/design/` exists, `AniFlowTheme(deviceType)` is the entry
point, `AppContainer` is wired). Read `P0_STATUS_AND_FINISH.md` and `P1_DESIGN_SYSTEM.md` first.

**This is the largest phase in the plan.** If you cannot finish it in one session, stop at a green
build after any numbered step in §Execution order and write down where you stopped. Do not leave a
broken tree.

## Correction to CLAUDE.md and to `REFERENCE_CODEBASE_MAP.md` — report this before you start

CLAUDE.md's locked decision says "delete the rest" of `ui/redesign`. **Taken literally, that does not
compile.** Verified with grep:

- `ui/redesign/theme/GlassModifiers.kt` (202 LOC) and `ui/redesign/theme/GlassTokens.kt` (50 LOC) are
  imported by **8 non-redesign files** at **~60 call sites**: `ui/main/MainScreen.kt`,
  `ui/phone/PhoneSettingsScreen.kt`, `ui/player/PlayerScreen.kt`,
  `ui/player/components/{AdvancedServerProviderSelector,QualitySelector,SpeedSelector,SubtitleSelector}.kt`,
  `ui/tv/components/TvSideNavRail.kt`, `ui/tv/TvSettingsScreen.kt`. The symbols are `glassSurface`,
  `darkGlassSurface`, `focusGlow`, `GlassTokens`.
- So `ui/redesign/theme/` is **not redesign code**. It is the app's de-facto surface-and-focus modifier
  library that happens to live in the wrong package. **Move it, don't delete it.**
- `ui/redesign/components/GlassCard.kt` (51 LOC) *is* redesign-only — used at 5 sites, all inside
  `ui/redesign`. Delete it.
- The reference map's "ui/redesign 3,553 LOC" is the seven `Redesign*Screen.kt` files.
  `find ui/redesign -name '*.kt' | xargs wc -l` totals **3,856**. The 303 LOC difference is
  `theme/` + `components/`. Use 3,553 as the delete figure and 252 as the move figure.

Also worth flagging honestly: CLAUDE.md says the redesign tree's three good ideas are "capsule bottom
nav, trailer hero, episode chunking". Two of the three are **already in the standard tree**:

| Element | Where it actually lives | Action |
|---|---|---|
| Capsule bottom nav | `ui/main/MainScreen.kt:295-440` — animated capsule with `darkGlassSurface(CircleShape)`. The Material `NavigationBar` at `:443-490` is the *other* branch of the same `if (isRedesign)`. | Keep the capsule, delete the `NavigationBar` branch. No porting needed. |
| Trailer hero | `ui/redesign/RedesignTvHomeScreen.kt:611 BackgroundTrailerPlayer` | **Genuine port.** TV only. |
| Episode chunking | `ui/redesign/RedesignDetailScreen.kt:126-129` (`chunkSize = 100`) + range chips at `:497-510`. `ui/detail/DetailScreen.kt:547` only has `episodes.chunked(3)`, which is a 3-per-row grid, not pagination. | **Genuine port.** |

So the "promote three elements" task is really two ports and one deletion. Say so.

## Confirmed problems this phase fixes

| # | Class | Evidence | Effect |
|---|---|---|---|
| 1 | ARCHITECTURAL | 12 `context.packageName.endsWith(".redesign")` checks in 11 files (list below) | Both APKs ship both UI trees. Every feature must be written twice. |
| 2 | TECH DEBT | `app/build.gradle.kts:31-41` — `flavorDimensions += "ui"`, two flavours whose source sets contain only an unused `config.xml` | A build-variant matrix (4 variants) that buys nothing. |
| 3 | ARCHITECTURAL | `ui/main/MainScreen.kt` is 1,104 LOC with **four** `when (currentTab)` blocks (`:198 :237 :509 :548`) — the phone/TV × standard/redesign cross product | Unmaintainable. Adding a tab means four edits. |
| 4 | UX | No deep links at all. `AndroidManifest.xml:25-41` has only MAIN/LAUNCHER and MAIN/LEANBACK_LAUNCHER. No `onNewIntent`. | Notifications (P6), Watch Next (P4) and share links have nowhere to land. |
| 5 | ARCHITECTURAL | Tab index lives in `MainScreenViewModel._currentTab` (`:24`, `setTab` at `:434`), not the back stack | Tab is not restorable, not deep-linkable, and BACK needs the P0.7 special case to work at all. |
| 6 | UX | Settings occupies a top-level tab (`MainScreen.kt:181`) while Search is buried inside Browse | Audit §15: wrong hierarchy for the actual usage pattern. |

The 12 flavour-check sites, verified — grep each before editing, line numbers move:

```
Navigation.kt:39                                    ui/main/MainScreen.kt:122, :629, :965
ui/phone/PhoneSettingsScreen.kt:35                  ui/tv/TvSettingsScreen.kt:39
ui/tv/components/TvSideNavRail.kt:109               ui/player/PlayerScreen.kt:136
ui/player/components/AdvancedServerProviderSelector.kt:45
ui/player/components/QualitySelector.kt:36          ui/player/components/SpeedSelector.kt:34
ui/player/components/SubtitleSelector.kt:35
```

**Resolve every one of them to the `true` (redesign) branch, not the `false` branch.** That is the
point of the locked decision: the redesign visual treatment is the one being kept; the *screen tree* is
what's being deleted. Check each site individually — in the player selectors the check usually only
picks a shape or a colour, so the fix is deleting the `if` and keeping one arm.

## §1 — Extract the glass modifiers (do this FIRST, it unblocks everything)

Move, with `git mv` so history survives:

```bash
git mv app/src/main/java/com/example/aniflow/ui/redesign/theme/GlassModifiers.kt \
       app/src/main/java/com/example/aniflow/ui/design/modifier/SurfaceModifiers.kt
git mv app/src/main/java/com/example/aniflow/ui/redesign/theme/GlassTokens.kt \
       app/src/main/java/com/example/aniflow/ui/design/tokens/SurfaceTokens.kt
```

Then in both files change `package com.example.aniflow.ui.redesign.theme` to
`com.example.aniflow.ui.design.modifier` / `…tokens`, and fix the 8 consumer files' imports:

```bash
grep -rl 'com.example.aniflow.ui.redesign.theme' app/src/main --include=*.kt
```

`GlassTokens`' hardcoded colours must be folded into the P1 token system rather than surviving as a
second palette — that is the whole point of P1. Read `SurfaceTokens.kt` and for each colour either map
it onto an `AniFlowColors` role or, if it has no equivalent (e.g. `GlowCyan`), add **one** role to
`AniFlowColors` (`accentGlow`) and delete the object. Do not leave two colour systems standing; that
is the defect P1 just removed, reintroduced under a new name.

Compile here. **Green before you delete anything.**

## §2 — Port the two elements that are actually worth keeping

Do this **before** deleting, while the source is still on disk.

### 2a. Episode range pagination → `ui/detail/DetailScreen.kt`

From `RedesignDetailScreen.kt:126-129` and `:497-510`. The shape:

```kotlin
// In DetailScreen, where the episode list is rendered (currently around :540-560)
val chunkSize = 100
val episodeChunks = remember(episodes) {
    if (episodes.size <= chunkSize) listOf(episodes) else episodes.chunked(chunkSize)
}
var selectedChunk by remember(episodes) { mutableStateOf(0) }

if (episodeChunks.size > 1) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = LocalAniFlowSpacing.current.pagePadding)
    ) {
        itemsIndexed(episodeChunks) { index, chunk ->
            val start = chunk.first().number
            val end = chunk.last().number
            RangeChip(
                label = "$start-$end",
                selected = index == selectedChunk,
                onClick = { selectedChunk = index }
            )
        }
    }
}
// then render episodeChunks[selectedChunk] instead of episodes
```

Two things to get right that the redesign version gets wrong:

1. `remember(episodes)` keys on the list, so navigating Detail→Detail resets the selected chunk. Keying
   on `Unit` leaves you on "1101-1200" for a 12-episode show.
2. Keep the existing `chunked(3)` grid **inside** the selected chunk. These are two different chunkings
   — 100 for pagination, 3 for the row layout. Do not collapse them.
3. **Also default `selectedChunk` to the chunk containing the resume episode**, not 0. `DetailViewModel`
   already exposes `watchHistoryEntry` (`DetailViewModel.kt:30`), so:
   `episodeChunks.indexOfFirst { c -> entry.episodeNumber in c.first().number..c.last().number }`.
   One Piece users should not scroll to chunk 11 every time. This is a small addition that makes the
   port worth doing at all.

### 2b. Trailer hero → `ui/tv/TvHomeScreen.kt`

From `RedesignTvHomeScreen.kt:611 BackgroundTrailerPlayer`. **Read that function before porting and
check three things**, because a background video player on TV is exactly where the app can regress into
the P0.5 class of defect:

- Does it release the `ExoPlayer` in an `onDispose`? If not, add it — a leaked player on the home screen
  survives navigation and keeps decoding.
- Is it muted and does it stop when the screen is not resumed? Use
  `LifecycleEventObserver` / `lifecycle.currentStateAsState()`, not just `DisposableEffect`.
- Does it debounce focus changes? Focus moves on every D-pad press; starting a video load per press is
  a stutter machine. Gate it behind ~600 ms of focus stability:
  `LaunchedEffect(focusedAnimeId) { delay(600); startTrailer(focusedAnimeId) }`.

`Anime.trailerUrl` already exists (`data/model/Anime.kt`), so no data work is needed. If the ported
function has no dispose/lifecycle handling, **write it in** and say in your report that you added it —
don't port a leak.

## §3 — Delete the second tree and the flavour split

Order matters: resolve the checks first (so nothing references the redesign screens), then delete.

**3a. Resolve the 12 checks.** `Navigation.kt:38-40` is the important one — deleting `isRedesign` there
removes the only reference to `RedesignDetailScreen`:

```kotlin
// Navigation.kt — delete the isRedesign val and collapse entry<Detail> to one call
entry<Detail> { detailKey ->
    DetailScreen(
        animeId = detailKey.animeId,
        …
    )
}
```

For `MainScreen.kt:122, :629, :965` the checks select between phone/TV standard and redesign screen
sets — collapse each `when (currentTab)` pair into one. That is what takes MainScreen from 1,104 LOC to
roughly half.

**3b. Delete.** Grep every symbol first (this is not optional — `FeedbackInputDialog` and
`AmbiguousSelectionDialog` exist in *both* trees, so a careless delete-by-name breaks
`DetailScreen.kt:606` and `:643`):

```bash
git rm app/src/main/java/com/example/aniflow/ui/redesign/Redesign*.kt
git rm app/src/main/java/com/example/aniflow/ui/redesign/components/GlassCard.kt
```

Then `find app/src/main/java/com/example/aniflow/ui/redesign -type d -empty -delete`.

Expected deletion: **3,604 LOC** (3,553 screens + 51 `GlassCard.kt`). If your number differs, find out
why before continuing.

**3c. Delete the flavour split.** `app/build.gradle.kts` — remove lines 31-41:

```kotlin
    flavorDimensions += "ui"
    productFlavors { … }
```

Then delete the flavour source sets: `app/src/standard/` and `app/src/redesign/` (each contains only an
unused `config.xml` — verify with `ls -R` before deleting).

**This changes every gradle task name.** After this the commands are:

```bash
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleRelease
```

Update `AniFlow_Phases/README.md`'s build section in the same commit, or the next session will run a
task that no longer exists. The debug APK moves to
`app/build/outputs/apk/debug/app-debug.apk`.

**Tell Harmeet about the applicationId consequence, bluntly:** the `redesign` flavour had
`applicationIdSuffix = ".redesign"`, so anyone with `com.example.aniflow.redesign` installed (his own
test builds — `releases/AniFinal_1.8.6-redesign.apk` exists in the repo) keeps a second, now-orphaned
app that will never update again. It has its own DataStore, so its watchlist is not migrated by P3's
importer either. He should uninstall it before P3 ships. Also note `applicationId` is still
`com.example.aniflow` — audit §14 wants that renamed to a real id, but renaming it is a
**breaking, uninstall-required change** on top of P0.3's signing-key break. Recommend doing both at the
same time, once, in a single release, rather than twice.

## §4 — Tab index into the back stack

> **Read `§4 CORRECTION` (after §6) before you write this code.** The version below is right about
> where the tab belongs and wrong about one consequence: replacing the top entry changes the NavKey,
> and a changed key means a new `NavEntry` — new `ViewModelStore`, dropped saveable state. The
> correction keeps everything §4 wants and removes that cost. Where they disagree, the correction wins.

Today: `MainScreenViewModel._currentTab` (`:24`) + `setTab(index)` (`:434`). Not restorable across
process death, not deep-linkable, and it is why P0.7's BackHandler needs the `currentTab != 0` special
case.

Change `NavigationKeys.kt` so the tab is part of the key:

```kotlin
package com.example.aniflow

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
enum class Tab { Home, Discover, Library, Profile }

@Serializable
data class Main(val tab: Tab = Tab.Home) : NavKey    // was: data object Main

@Serializable
data class Detail(val animeId: Int) : NavKey

@Serializable
data class Player(val animeId: Int, val episodeNumber: Int) : NavKey

@Serializable
data class Search(val initialQuery: String = "") : NavKey   // Search is a destination now (audit §15)
```

`Main` going from `data object` to `data class` breaks `rememberNavBackStack(Main)` — it becomes
`rememberNavBackStack(Main())`. Grep for `Main` as a bare reference; there are only a few
(`Navigation.kt:37`, `entry<Main>`).

Switching tabs then means replacing the top entry rather than pushing:

```kotlin
// Navigation.kt
entry<Main> { key ->
    MainScreen(
        tab = key.tab,
        onTabChange = { newTab ->
            // Replace, don't push: a 4-deep tab history is not what BACK should mean.
            backStack[backStack.lastIndex] = Main(newTab)
        },
        …
    )
}
```

`MainScreenViewModel` loses `_currentTab`, `currentTab` and `setTab`. That is a deletion, not a move —
grep `setTab` (P0.7's BackHandler calls it at `MainScreen.kt:142`) and rewrite that arm as
`onTabChange(Tab.Home)`. With the tab in the key, the P0.7 handler simplifies to:

```kotlin
BackHandler(enabled = isTopDestination) {
    when {
        deviceType == DeviceType.TV && !navBarFocused -> runCatching { navBarFocusRequester.requestFocus() }
        tab != Tab.Home -> onTabChange(Tab.Home)
        exitArmed -> activity?.finish()
        else -> { exitArmed = true; Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show() }
    }
}
```

**Do not** make each tab a separate `NavKey` pushed onto the stack. That gives you Home→Discover→Library
→BACK→Discover, which is not how bottom navigation behaves on Android and is not what audit §15 asks
for ("Back returns Discover→Home and never exits from a sub-tab").

## §5 — the new phone IA

Four tabs (audit §15): **Home · Discover · Library · Profile**. Mapping from today's four:

| Today | Becomes | Work |
|---|---|---|
| Home (`PhoneHomeScreen.kt`, 529 LOC) | **Home** | Reorder rails: Continue Watching first, then Airing Today, then Trending/Seasonal. `MainScreenViewModel` already exposes `history` and `airingToday`. |
| Browse (`PhoneBrowseScreen.kt`, 211 LOC) | **Discover** | Keep the genre chips + grid. Move the inline search field out to the new `Search` destination; leave a search icon that does `backStack.add(Search())`. |
| Library (`PhoneLibraryScreen.kt`, 55 LOC) | **Library** | 55 LOC is a bookmark grid, not a library. Add watch-status segments only if P3's schema is in place — **otherwise leave it and say so.** `WatchlistStore` has no status field, so segments would be fake. |
| Settings (`PhoneSettingsScreen.kt`, 434 LOC) | **Profile** | Rename the tab and icon (`Icons.Rounded.Person`), keep the screen. Do not restructure 434 LOC of settings in this phase. |

**Be blunt with Harmeet about Library:** audit §15 wants Watching/Plan/Completed/On-hold/Dropped
segments, and the current data model cannot express any of them — `WatchlistStore` stores a
`List<Anime>` with no status (see `REFERENCE_CODEBASE_MAP.md`). Building segments now means inventing a
status field in a DataStore JSON blob that P3 immediately migrates to Room. **Defer segments to P3** and
ship P2's Library as a renamed, tokenised bookmark grid. Delivering a fake five-segment UI where four
segments are always empty is worse than shipping one honest grid.

`Search` as a destination:

```kotlin
entry<Search> { key ->
    SearchScreen(
        initialQuery = key.initialQuery,
        viewModel = /* reuse MainScreenViewModel's search state, or a small SearchViewModel */,
        onAnimeClick = { backStack.add(Detail(it)) },
        onBack = { backStack.removeLastOrNull() }
    )
}
```

`MainScreenViewModel` already has the whole search pipeline: `searchQuery`, `searchResults`,
`isSearchLoading`, `hasNextPage`, `onSearchQueryChanged`, `loadNextSearchPage`, `onGenreSelected`, and a
300 ms debounce at `:229-248`. **Extract it into `SearchViewModel` rather than reusing the god-object** —
`MainScreenViewModel` has 14 `StateFlow`s and a 15-minute refresh loop (`:97-116`); a search screen that
drags all of that into an entry-scoped ViewModel will re-run `loadData()` on every search. That is a
concrete regression, not a style preference. Move lines `:57-61`, `:218-275`, `:409-431`, `:438-440`
into the new ViewModel.

## §6 — deep links

Manifest — add to the existing `<activity android:name=".MainActivity">` (`AndroidManifest.xml:25`):

```xml
<intent-filter android:autoVerify="false">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="aniflow" android:host="anime" />
    <data android:scheme="aniflow" android:host="watch" />
</intent-filter>
```

`aniflow://anime/{anilistId}` and `aniflow://watch/{anilistId}/{episode}`. A custom scheme, not
`https://`, because App Links need a verified domain and there isn't one — say that rather than adding
`autoVerify="true"` and shipping a link that silently never verifies.

`MainActivity` needs `onNewIntent`, and the back stack needs to be reachable from it. Keep it simple:
hold the intent in a `MutableStateFlow` on the Activity and consume it in `MainNavigation`.

```kotlin
// MainActivity
private val deepLinks = MutableStateFlow<NavKey?>(null)

override fun onCreate(savedInstanceState: Bundle?) {
    …
    deepLinks.value = parseDeepLink(intent)
    setContent { … MainNavigation(deepLink = deepLinks) … }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    deepLinks.value = parseDeepLink(intent)
}
```

```kotlin
// Navigation.kt — one place, so a malformed link cannot crash a screen
internal fun parseDeepLink(intent: Intent?): NavKey? {
    val uri = intent?.data ?: return null
    if (!uri.scheme.equals("aniflow", ignoreCase = true)) return null
    val segments = uri.pathSegments
    return when (uri.host) {
        "anime" -> segments.getOrNull(0)?.toIntOrNull()?.let { Detail(it) }
        "watch" -> {
            val id = segments.getOrNull(0)?.toIntOrNull()
            val ep = segments.getOrNull(1)?.toIntOrNull()
            if (id != null && ep != null) Player(id, ep) else null
        }
        else -> null
    }
}

// in MainNavigation
val pending by deepLink.collectAsStateWithLifecycle()
LaunchedEffect(pending) {
    val key = pending ?: return@LaunchedEffect
    // Always land on a stack that has Main underneath, so BACK from a notification tap
    // goes Home instead of closing the app.
    if (backStack.size == 1) backStack.add(key) else { backStack.clear(); backStack.add(Main()); backStack.add(key) }
    deepLink.value = null      // consume, or rotation re-navigates
}
```

The `deepLink.value = null` line is not optional. Without it, every configuration change re-runs the
navigation and the user cannot leave the deep-linked screen.

## §4 CORRECTION — the tab belongs in the key, but the key must not change on every tap

I wrote §4 the way the audit describes it, then checked it against the decorators P0.1 added. It has a
cost §4 does not mention, and you would discover it as "why does Home reload every time I come back
from Library".

**The mechanism.** `Navigation.kt` passes
`listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator())`.
Both decorators key their storage on the back-stack key. `Main(Tab.Home)` and `Main(Tab.Discover)` are
different values, so `backStack[lastIndex] = Main(newTab)` removes one key and adds another. By the
decorators' contract the departing key's `ViewModelStore` is cleared and its saveable state dropped, so
every tab tap would:

- re-create `MainScreenViewModel` → re-run `loadData()` → 4–8 AniList queries per tab switch, and
  restart the 15-minute refresh loop at `:97-116`
- lose every `LazyRow`/`LazyGrid` scroll position on the tab you left

**Confidence:** this is reasoned from the decorator contract, **not observed** — I have not run it. Verify
in one minute by putting `android.util.Log.d("VM", "init")` in `MainScreenViewModel`'s `init` and
switching tabs four times. Four log lines confirms it. If the stores turn out to survive, the fix below
is still correct and costs nothing.

**The fix — the key carries the *initial* tab, local saveable state carries the current one:**

```kotlin
@Serializable
data class Main(val startTab: Tab = Tab.Home) : NavKey    // "start", not "current"
```

```kotlin
// Navigation.kt
entry<Main> { key ->
    // Survives process death via the saveable-state decorator; does NOT change the key,
    // so the entry — and MainScreenViewModel, and every scroll position — is kept alive.
    var tab by rememberSaveable(key) { mutableStateOf(key.startTab) }
    MainScreen(
        tab = tab,
        onTabChange = { tab = it },
        …
    )
}
```

That satisfies everything §4 asked for:

| §4's goal | Met by |
|---|---|
| Tab out of the ViewModel | `_currentTab` / `currentTab` / `setTab` still get deleted. Unchanged. |
| Restorable across process death | `rememberSaveable` + `rememberSaveableStateHolderNavEntryDecorator`. |
| Deep-linkable | `backStack.add(Main(Tab.Library))` — the key still carries a tab. |
| BACK simplification | The `BackHandler` block in §4 is unchanged; `tab`/`onTabChange` are now local. |
| No 4-deep tab history | Nothing is pushed at all, so this is free. |

`rememberSaveable(key)` takes `key` as an input so that a *deep link* to `Main(Tab.Library)` while a
`Main(Tab.Home)` entry is already on the stack re-seeds the tab instead of being ignored. Keep it.

Everything else in §4 — the `Tab` enum, `Search` as a key, `rememberNavBackStack(Main())`, deleting
`setTab`, the `BackHandler` shape, "do not push a key per tab" — stands as written.

## §7 — cut `MainScreen.kt` down

**Do this after §3 and §4, not before.** Splitting a file that still contains the cross product just
gives you two files that still contain it.

`MainScreen.kt` is 1,104 LOC. The size is not accidental — it is one function holding a 2×2 matrix:

```
                 standard          redesign
        phone    when(tab) :198    when(tab) :237
        TV       when(tab) :509    when(tab) :548
```

§3 kills the redesign column (two of the four blocks go, and with them the `NavigationBar` arm at
`:443-490`). What is left is phone and TV, which are genuinely different layouts — a bottom capsule vs a
side rail — and belong in separate files:

```
ui/main/
├── MainScreen.kt          ← device dispatch + BackHandler + tab plumbing. Target < 200 LOC.
├── PhoneMainScaffold.kt   ← capsule bottom nav (ported from :295-440) + one when (tab)
├── TvMainScaffold.kt      ← TvSideNavRail + one when (tab)
└── MainScreenViewModel.kt ← minus _currentTab / currentTab / setTab
```

```kotlin
// MainScreen.kt — all that is left of the host
@Composable
fun MainScreen(
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    isTopDestination: Boolean,
    actions: MainActions,
    viewModel: MainScreenViewModel = viewModel { … },
) {
    val deviceType = LocalDeviceType.current
    // …the P0.7 BackHandler from §4 lives here, once, for both form factors…
    when (deviceType) {
        DeviceType.PHONE -> PhoneMainScaffold(tab, onTabChange, actions, viewModel)
        DeviceType.TV    -> TvMainScaffold(tab, onTabChange, actions, viewModel)
    }
}
```

Two things to decide by counting, not by taste:

1. **`MainActions`.** Grep the current signature first: `grep -n 'fun MainScreen' -A 20 ui/main/MainScreen.kt`.
   If it takes **six or fewer** lambdas, pass them through as plain parameters and skip the wrapper. If it
   takes more (it currently does — anime click, episode click, search, settings, update, feedback, …),
   declaring one `@Immutable data class MainActions(val onAnimeClick: (Int) -> Unit, …)` is cheaper than
   repeating a 12-parameter list in three files. `@Immutable` matters: without it, a data class of lambdas
   is treated as unstable and every scaffold recomposes on every parent recomposition.
2. **The tab list.** Labels are declared twice today (`:178-181` and `:356-359`) — that duplication is how
   they drift. One list, used by both scaffolds:

```kotlin
// ui/main/MainTabs.kt
internal data class TabItem(val tab: Tab, val label: String, val icon: ImageVector)

internal val MainTabItems = listOf(
    TabItem(Tab.Home,     "Home",     Icons.Rounded.Home),
    TabItem(Tab.Discover, "Discover", Icons.Rounded.Search),
    TabItem(Tab.Library,  "Library",  Icons.Rounded.Favorite),
    TabItem(Tab.Profile,  "Profile",  Icons.Rounded.Person),
)
```

`Icons.Rounded.Person` is in the **core** icon set, so the existing explicit
`material-icons-core` dependency covers it (see the gotcha in `REFERENCE_CODEBASE_MAP.md`). If it does
not resolve, **do not add `material-icons-extended`** to get one glyph — it is a very large dependency;
use `Icons.Rounded.AccountCircle` or ship a 24 dp vector in `res/drawable/`.

**LOC target, so you can tell whether the split actually helped:** the three files together should be
**under 600 LOC**. If they total 1,000, you moved code instead of removing the cross product — go back
and find the `when (tab)` you kept twice.

## Execution order

Compile after **every** numbered step. Steps 1–5 are pure subtraction; if you run out of session after
any of them the tree is strictly better than when you started. Steps 6–9 add behaviour and are the ones
that can leave the app half-migrated, so do not start 6 unless you can finish it.

| # | Step | Section | Green-build check |
|---|---|---|---|
| 1 | Move the glass modifiers, fold `GlassTokens` into `AniFlowColors`, fix 8 consumers' imports | §1 | `assembleStandardDebug` (flavours still exist here) |
| 2 | Port episode range pagination and the TV trailer hero | §2 | same |
| 3 | Resolve all 12 `endsWith(".redesign")` checks to the redesign arm | §3a | same — nothing should reference `Redesign*Screen` afterwards |
| 4 | `git rm` the 7 `Redesign*.kt` + `GlassCard.kt`, delete empty dirs | §3b | same. Expect ≈ −3,604 LOC |
| 5 | Delete `flavorDimensions`/`productFlavors` and `app/src/{standard,redesign}/` | §3c | **task names change here** — `assembleDebug` |
| 6 | `Tab` enum, `Main(startTab)`, `Search` key, delete `setTab`, new `BackHandler` | §4 + CORRECTION | `assembleDebug` |
| 7 | Split `MainScreen.kt` into host + two scaffolds + `MainTabs.kt` | §7 | `assembleDebug` |
| 8 | Rename tabs to Home/Discover/Library/Profile, extract `SearchViewModel`, add the `Search` destination | §5 | `assembleDebug` |
| 9 | Deep links: manifest filter, `parseDeepLink`, `onNewIntent`, consume-once `LaunchedEffect` | §6 | `assembleDebug` + `testDebugUnitTest` + `lintDebug` |

If you must stop early, stop **after step 5**. That is the phase's whole architectural payload — one UI
tree, one build variant — and it is the point after which the tree cannot regress back into two trees by
accident. Write where you stopped into this file under a `## STOPPED AT` heading with the step number and
the last command you ran.

## Build and verify

The two Windows environment traps from `README.md` still apply — no `java` on `PATH`, and `TMP`/`TEMP`
must be a short space-free path or Gradle dies with `Unable to establish loopback connection`:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export TMP="C:\\gradle_tmp"
export TEMP="C:\\gradle_tmp"
./gradlew.bat assembleDebug --console=plain
```

Before step 5 the task is `assembleStandardDebug`; after it, `assembleDebug`. **Do not guess the new
names — list them once:**

```bash
./gradlew.bat tasks --all --console=plain | grep -iE '^(assemble|test|lint)'
```

```bash
./gradlew.bat testDebugUnitTest --console=plain
```

```bash
./gradlew.bat lintDebug --console=plain
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (the `standard/` path segment is gone).
Update the build section of `AniFlow_Phases/README.md` and the `## Build and verify` block in
`CLAUDE.md` **in the same commit as step 5**, or the next session runs a task that no longer exists.

## Definition of done

Every line here is a command with an expected result. Run them; do not eyeball the tree.

```bash
# 1. no flavour checks left anywhere
grep -rn 'endsWith(".redesign")' app/src/main --include=*.kt          # → 0 hits

# 2. no references to the deleted package, imports included
grep -rn 'com.example.aniflow.ui.redesign' app/src --include=*.kt     # → 0 hits

# 3. the tree and the flavour source sets are gone
ls app/src/main/java/com/example/aniflow/ui/redesign 2>&1             # → No such file
ls app/src/standard app/src/redesign 2>&1                             # → No such file

# 4. one build variant dimension
grep -n 'flavorDimensions\|productFlavors\|applicationIdSuffix' app/build.gradle.kts   # → 0 hits

# 5. the tab is out of the ViewModel
grep -rn '_currentTab\|currentTab\|setTab' app/src/main --include=*.kt # → 0 hits

# 6. the cross product is gone: exactly two tab switches, one per form factor
grep -rn 'when (tab)' app/src/main/java/com/example/aniflow/ui/main/   # → exactly 2

# 7. the host shrank
wc -l app/src/main/java/com/example/aniflow/ui/main/MainScreen.kt      # → < 200
wc -l app/src/main/java/com/example/aniflow/ui/main/*.kt               # → total < 600 excl. the ViewModel

# 8. search is its own ViewModel, not the god-object
grep -rn 'searchQuery\|searchResults' app/src/main/java/com/example/aniflow/ui/main/MainScreenViewModel.kt  # → 0

# 9. deep links parse in one place
grep -rn 'parseDeepLink' app/src/main --include=*.kt                  # → declared once, called twice

# 10. measure what you deleted instead of quoting the estimate
git diff --stat main -- app/src/main/java/com/example/aniflow/ui/redesign
```

Then the three gradle tasks above, all green.

**Device checks are Harmeet's, not yours.** Write them down for him; do not ask him to run gradle:

1. Tap through all four tabs. Home does **not** re-show a loading spinner when you come back to it (this
   is the §4 CORRECTION working — if it does spin, the entry is being recreated).
2. From Discover, BACK → Home. From Home, BACK → Toast, BACK again → exits.
3. Open a 1,000+ episode anime (One Piece, AniList id 21) with a saved episode ~500. Detail opens on the
   range chip containing 500, not on `1-100`.
4. TV: focus a card on Home, wait ~1 s → trailer fades in muted. Move focus fast across 6 cards → no
   trailer starts, no stutter. Leave the screen → audio stops (nothing decoding in the background).
5. `adb shell am start -a android.intent.action.VIEW -d "aniflow://anime/21"` from a cold start → Detail
   for One Piece, and BACK goes to Home rather than closing the app.
6. `adb shell am start -a android.intent.action.VIEW -d "aniflow://watch/21/1"` → player.
7. `adb shell am start -a android.intent.action.VIEW -d "aniflow://anime/notanumber"` → nothing happens,
   no crash.
8. Rotate the phone while on a deep-linked Detail screen → stays on Detail (this is the
   `deepLink.value = null` consume working).
9. Search icon in Discover → Search screen, type, tap a result, BACK → returns to Search with the query
   still there, BACK again → Discover.

## Report to Harmeet

Short, blunt, and it must include the four things he cannot discover by reading the diff:

1. **CLAUDE.md's P2 instruction was wrong in two places, and here is the evidence.** "Delete the rest of
   `ui/redesign`" does not compile — `theme/GlassModifiers.kt` + `GlassTokens.kt` are used by 8
   non-redesign files at ~60 sites, so they were **moved** into `ui/design`, not deleted. And of the
   three "genuinely better redesign elements", the capsule bottom nav was **already** the standard
   tree's nav (`MainScreen.kt:295-440`); only the trailer hero and episode chunking were real ports.
   Two ports and one deletion, not three promotions.
2. **`com.example.aniflow.redesign` is now an orphan on any device that has it** — his own test builds,
   and `releases/AniFinal_1.8.6-redesign.apk` is in the repo. It has its own DataStore, will never
   receive another update, and **P3's importer will not migrate its watchlist**. Uninstall it before P3
   ships, not after.
3. **Every gradle task name changed** (`assembleStandardDebug` → `assembleDebug`) and the APK path lost
   its `standard/` segment. `README.md` and `CLAUDE.md` were updated in the same commit.
4. **Library shipped as a bookmark grid, not the five status segments audit §15 asks for** — because
   `WatchlistStore` stores a `List<Anime>` with no status field, so four of the five segments would be
   permanently empty. Deferred to P3, where Room gives them a real column. Say this explicitly rather
   than letting him find a renamed tab and assume the segments were forgotten.

Plus the measured numbers, not the predicted ones: LOC deleted, `MainScreen.kt` before/after, and the
new APK size.

Also worth one line each: deep links are a **custom scheme** (`aniflow://`) because App Links require a
verified domain that does not exist — a `https://` filter with `autoVerify="true"` would silently never
verify. And the `applicationId` rename that audit §14 wants is an **uninstall-required break**; bundling
it with P0.3's signing-key break into one release costs users one reinstall instead of two.

## Traps

1. **Do not resolve the 12 flavour checks to the `false` arm.** The locked decision keeps the *redesign
   visual treatment* and deletes the *redesign screen tree*. In the four player selectors the check
   usually picks only a shape or a colour — delete the `if`, keep the redesign arm.
2. **`FeedbackInputDialog` and `AmbiguousSelectionDialog` exist in both trees.** `DetailScreen.kt:606`
   and `:643` call the standard ones. A delete-by-name sweep breaks Detail. Grep each symbol, not each
   file — this is the mistake this playbook exists to prevent.
3. **`Main` going `data object` → `data class` breaks `rememberNavBackStack(Main)`.** It becomes
   `rememberNavBackStack(Main())`. `Tab` is an enum, which kotlinx.serialization handles without an
   annotation, but leaving `@Serializable` on it is harmless.
4. **Do not delete `lifecycle-viewmodel-navigation3`.** P0.6 already had to make an exception for it;
   removing the flavour block is not a licence to prune dependencies again in this phase.
5. **Do not touch `PhoneSettingsScreen.kt`'s 434 LOC.** "Profile" is a tab rename and an icon change in
   this phase. Restructuring settings is not in P2 and there is no section for it.
6. **Do not port the trailer hero without lifecycle handling.** If the source `BackgroundTrailerPlayer`
   has no `onDispose { player.release() }`, write one and say in the report that you added it. A leaked
   `ExoPlayer` on the home screen is the P0.5 class of defect coming back.
7. **The `SearchViewModel` extraction is a move, not a copy.** When you are done,
   `MainScreenViewModel` must have **zero** search symbols left (Definition of done #8). Two copies of a
   300 ms debounce is worse than the god-object you started with.
8. **`ui/design/modifier/` and `ui/design/component/` now exist** (P1 deliberately did not create them —
   see P1 Traps). §1 creates `modifier/`. Still do not start a component library in P2; P4 is where
   TV-focusable components get designed, and building them now means building them twice.
