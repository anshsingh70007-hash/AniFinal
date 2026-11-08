# P1 — design system, result types, AppContainer

**Prerequisite:** P0 is code complete and `assembleStandardDebug` is green. Read
`P0_STATUS_AND_FINISH.md` first so you don't undo it. Read `REFERENCE_CODEBASE_MAP.md` for the
verified facts about the tree — **do not re-derive them**.

**One session. End with a green build. Do not start P2.**

## What P1 is

Four things, in this order:

1. `ui/design/` — a real token system, `DeviceType`-aware, no global mutable state.
2. `core/result/` — `AniResult<T>` + `AniError`, and one `UiState<T>`. Kill `null`-as-error at the
   repository boundary.
3. `di/AppContainer.kt` — one place that constructs the graph, replacing the `remember { … }` pile in
   `Navigation.kt:23-36`.
4. Replace hardcoded `fontSize` literals in **live** screens with type tokens.

## What P1 is NOT

- **Not** a visual redesign. Same pixels, different plumbing. If a screen looks different at the end
  of P1, you have overreached.
- **Not** a rewrite of `theme/*` call sites. 23 files read those colours (309 matching lines). You
  will shim, not sweep. See §4.
- **Not** touching `ui/redesign/**`. P2 deletes all 3,553 LOC of it. Every minute spent tokenising
  redesign files is wasted.
- **Not** Room. **Not** Hilt. **Not** new screens.

## Confirmed problems this phase fixes (evidence, not opinion)

| # | Class | Evidence | Effect |
|---|---|---|---|
| 1 | ARCHITECTURAL | `theme/Color.kt:8,9,17,18,20,21` — six top-level `var … by mutableStateOf(Color)` | Process-global mutable state. Two themed subtrees are impossible. |
| 2 | ARCHITECTURAL | `theme/Theme.kt:25-40` — `AniFlowTheme` **assigns to those globals during composition** | A composable with a side effect on global state. Recomposition-order dependent; a preview or a second theme call repaints the whole app. |
| 3 | TECH DEBT | 229 `fontSize = N.sp` literals (`grep -ro 'fontSize = [0-9]*\.sp' app/src/main`), 13 distinct sizes incl. `9.sp`, `10.sp`, `11.sp` | No type scale. TV reads 9sp text from 3 m away. |
| 4 | ARCHITECTURAL | `AnimeRepository.kt:23` `fun getAnimeDetail(id: Int): Flow<Anime?>` | `null` means offline, rate-limited, deleted, and contract-changed all at once. `DetailViewModel.kt:48-51` turns every one of them into the string "Anime details not found." |
| 5 | ARCHITECTURAL | `Navigation.kt:23-36` — repository + 4 stores built with `remember {}` in a composable; `DetailScreen.kt:63` and `PlayerScreen.kt:87` each build **another** `ProviderMappingStore` / `SettingsStore` | No single graph. Duplicate DataStore instances on the same file is a documented corruption risk. |
| 6 | UX | `theme/Type.kt` has one `Typography` with no TV scale; `MainActivity.kt:29` calls `AniFlowTheme {}` with no `deviceType` | Phone type sizes on a 10-foot UI. |

## Target file tree (create exactly this — no extra files)

```
com/example/aniflow/
├── core/result/AniResult.kt        AniResult<T>, AniError, map/getOrNull helpers
├── core/result/UiState.kt          UiState<T> — the ONE UI-facing state type
├── di/AppContainer.kt              the graph + LocalAppContainer
├── AniFlowApp.kt                   Application subclass; owns AppContainer
└── ui/design/
    ├── AniFlowTheme.kt             single entry point, takes DeviceType
    ├── tokens/Palette.kt           raw colour ramps, no semantics
    ├── tokens/Colors.kt            @Immutable AniFlowColors + LocalAniFlowColors
    ├── tokens/Spacing.kt           @Immutable AniFlowSpacing + LocalAniFlowSpacing
    ├── tokens/Radius.kt            object AniFlowRadius (static — no device variance)
    ├── tokens/Motion.kt            object AniFlowMotion (static)
    └── tokens/Type.kt              AniFlowTypography(deviceType) -> Typography
```

`theme/Color.kt`, `theme/Theme.kt`, `theme/Type.kt` **stay on disk** in P1 and become shims (§4).
P2 deletes them. Do not delete them now — 23 files still import them and P2 rewrites most of those
files anyway.

## §1 — tokens/Palette.kt

Raw ramps only. No semantic names, no `Local*`. This file is the only place a hex literal is allowed.
Values are taken from the current `theme/Color.kt` so nothing changes visually.

```kotlin
package com.example.aniflow.ui.design.tokens

import androidx.compose.ui.graphics.Color

// Raw ramps. Nothing in the app reads these directly — AniFlowColors maps them to roles.
internal object Palette {
    val Neutral0    = Color(0xFF000000)   // AMOLED true black
    val Neutral50   = Color(0xFF06060F)   // was PrimaryDarker
    val Neutral100  = Color(0xFF0D0D1A)   // was PrimaryDark
    val Neutral150  = Color(0xFF0D0D0D)   // AMOLED card
    val Neutral200  = Color(0xFF1A1A2E)   // was SurfaceCard
    val Neutral250  = Color(0xFF1A1A1A)   // AMOLED border
    val Neutral300  = Color(0xFF2D2D4A)   // was SurfaceBorder
    val Neutral600  = Color(0xFF64748B)   // was TextTertiary
    val Neutral700  = Color(0xFF94A3B8)   // was TextSecondary
    val Neutral750  = Color(0xFFB0B0B0)   // AMOLED secondary text
    val Neutral900  = Color(0xFFF1F5F9)   // was TextPrimary
    val Neutral1000 = Color(0xFFFFFFFF)

    val Violet500 = Color(0xFF7C3AED)     // was PrimaryAccent
    val Violet300 = Color(0xFFA78BFA)     // was PrimaryAccentLight
    val Cyan500   = Color(0xFF06B6D4)     // was SecondaryAccent
    val Rose500   = Color(0xFFF43F5E)     // was TertiaryAccent
    val Green500  = Color(0xFF10B981)     // was SuccessGreen
    val Amber500  = Color(0xFFF59E0B)     // was WarningAmber
}
```

## §2 — tokens/Colors.kt

Semantic roles. `@Immutable` matters: without it Compose treats the class as unstable and every
consumer of `LocalAniFlowColors` recomposes on every theme read.

```kotlin
package com.example.aniflow.ui.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AniFlowColors(
    val background: Color,        // window / scaffold
    val surface: Color,           // card body
    val surfaceRaised: Color,     // sheet, dialog, elevated card
    val surfaceOverlay: Color,    // scrim over artwork
    val borderSubtle: Color,
    val borderFocus: Color,       // TV focus ring
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentMuted: Color,
    val statusAiring: Color,
    val statusSuccess: Color,
    val statusError: Color
)

val DarkColors = AniFlowColors(
    background = Palette.Neutral100,
    surface = Palette.Neutral200,
    surfaceRaised = Palette.Neutral50,
    surfaceOverlay = Palette.Neutral0.copy(alpha = 0.70f),
    borderSubtle = Palette.Neutral300,
    borderFocus = Palette.Violet300,
    textPrimary = Palette.Neutral900,
    textSecondary = Palette.Neutral700,
    textTertiary = Palette.Neutral600,
    accent = Palette.Violet500,
    accentMuted = Palette.Violet300,
    statusAiring = Palette.Amber500,
    statusSuccess = Palette.Green500,
    statusError = Palette.Rose500
)

val AmoledColors = DarkColors.copy(
    background = Palette.Neutral0,
    surface = Palette.Neutral150,
    surfaceRaised = Palette.Neutral0,
    borderSubtle = Palette.Neutral250,
    textPrimary = Palette.Neutral1000,
    textSecondary = Palette.Neutral750
)

// staticCompositionLocalOf, not compositionLocalOf: the value changes only when the user changes
// theme mode, and static skips per-read invalidation tracking.
val LocalAniFlowColors = staticCompositionLocalOf { DarkColors }
```

## §3 — Spacing, Radius, Motion, Type

`Spacing` is the only token set that varies by device, and only in two derived values. Do **not**
scale the base steps — a 4 dp gap is 4 dp on both. What changes is page padding (TV overscan) and
the gap between rails.

```kotlin
// tokens/Spacing.kt
package com.example.aniflow.ui.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aniflow.DeviceType

@Immutable
data class AniFlowSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    val pagePadding: Dp = 16.dp,   // TV: 48.dp — overscan safe area
    val sectionGap: Dp = 24.dp     // TV: 40.dp
)

fun spacingFor(deviceType: DeviceType): AniFlowSpacing = when (deviceType) {
    DeviceType.PHONE -> AniFlowSpacing()
    DeviceType.TV -> AniFlowSpacing(pagePadding = 48.dp, sectionGap = 40.dp)
}

val LocalAniFlowSpacing = staticCompositionLocalOf { AniFlowSpacing() }
```

```kotlin
// tokens/Radius.kt
package com.example.aniflow.ui.design.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object AniFlowRadius {
    val s = 8.dp
    val m = 12.dp
    val l = 20.dp
    val pill = 999.dp

    val ShapeS = RoundedCornerShape(s)
    val ShapeM = RoundedCornerShape(m)
    val ShapeL = RoundedCornerShape(l)
    val ShapePill = RoundedCornerShape(pill)
}
```

```kotlin
// tokens/Motion.kt
package com.example.aniflow.ui.design.tokens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp

object AniFlowMotion {
    const val durationFast = 120
    const val durationMedium = 250
    const val durationSlow = 400

    fun <T> springSnappy() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    fun <T> springSoft() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )
}
```

**Type is the part that carries real risk**, because 139 live call sites depend on getting the mapping
right. One family, seven steps, one TV multiplier. `1.25f` is chosen so today's `14.sp` body becomes
`17.5.sp` on TV — Google's 10-foot guidance is a 16sp floor for body text and this clears it while
keeping the existing layout arithmetic close enough not to reflow rails.

```kotlin
// tokens/Type.kt
package com.example.aniflow.ui.design.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.aniflow.DeviceType

private const val TV_SCALE = 1.25f

private fun TextUnit.scaled(scale: Float): TextUnit = (value * scale).sp

fun aniFlowTypography(deviceType: DeviceType): Typography {
    val s = if (deviceType == DeviceType.TV) TV_SCALE else 1f
    val family = FontFamily.SansSerif   // P1 ships the system family; a bundled face is a P2 asset task
    fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp.scaled(s),
        lineHeight = line.sp.scaled(s)
    )
    return Typography(
        displayLarge  = style(40, 48, FontWeight.Bold),      // hero title
        headlineLarge = style(28, 36, FontWeight.Bold),      // screen title
        headlineMedium= style(22, 28, FontWeight.SemiBold),  // section header
        titleLarge    = style(18, 24, FontWeight.SemiBold),  // card title
        titleMedium   = style(16, 22, FontWeight.SemiBold),
        bodyLarge     = style(16, 24, FontWeight.Normal),
        bodyMedium    = style(14, 20, FontWeight.Normal),    // default body
        bodySmall     = style(12, 16, FontWeight.Normal),    // metadata
        labelLarge    = style(14, 20, FontWeight.SemiBold),  // buttons
        labelMedium   = style(12, 16, FontWeight.Medium)     // chips, badges
    )
}
```

## §4 — AniFlowTheme.kt and the shim that saves you 300 edits

The new theme entry point. Note what it does **not** do: it never writes to a global.

```kotlin
package com.example.aniflow.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.aniflow.DeviceType
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.ui.design.tokens.*

@Composable
fun AniFlowTheme(
    deviceType: DeviceType,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // TODO(P1.9): read this from LocalAppContainer once AppContainer lands, not a fresh store.
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val themeMode by settingsStore.themeMode.collectAsState(initial = "dark")

    val colors = if (themeMode == "amoled") AmoledColors else DarkColors
    val spacing = remember(deviceType) { spacingFor(deviceType) }
    val typography = remember(deviceType) { aniFlowTypography(deviceType) }

    // Material3's scheme is derived from our roles, not the other way round. Every Material
    // component (Button, Card, TextField) then picks up the right colours for free.
    val scheme = remember(colors) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.textPrimary,
            secondary = colors.accentMuted,
            onSecondary = colors.textPrimary,
            tertiary = colors.statusError,
            onTertiary = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderSubtle,
            error = colors.statusError
        )
    }

    CompositionLocalProvider(
        LocalAniFlowColors provides colors,
        LocalAniFlowSpacing provides spacing
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
```

### The shim — read this before you touch `theme/Color.kt`

**Measured cost of doing it the naive way:** `grep -l 'import com.example.aniflow.theme.\*'` returns
**23 files**, and those files contain **309 lines** that read `PrimaryDark` / `PrimaryDarker` /
`SurfaceCard` / `SurfaceBorder` / `TextPrimary` / `TextSecondary`. Because every one of them uses a
wildcard import, converting the symbols to `LocalAniFlowColors.current.x` breaks all 309 at once, with
no import line to guide you. **Seven of those 23 files (68 of the 309 lines) are `ui/redesign/*`, which
P2 deletes.** Sweeping them is throwaway work.

So: convert the six mutable globals into **`@Composable` property getters** that read the
CompositionLocal. Every existing read inside a `@Composable` function keeps compiling, unchanged,
and now gets its value from the theme instead of a global.

```kotlin
// theme/Color.kt — P1 replacement. Shim only; P2 deletes this file.
package com.example.aniflow.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.example.aniflow.ui.design.tokens.LocalAniFlowColors
import com.example.aniflow.ui.design.tokens.Palette

// These were `var … by mutableStateOf` and AniFlowTheme assigned to them during composition.
// They are now read-only views onto LocalAniFlowColors. Call sites are unchanged; the global
// mutable state is gone. Migrate call sites to LocalAniFlowColors.current in P2, then delete this.
val PrimaryDark: Color   @Composable @ReadOnlyComposable get() = LocalAniFlowColors.current.background
val PrimaryDarker: Color @Composable @ReadOnlyComposable get() = LocalAniFlowColors.current.surfaceRaised
val SurfaceCard: Color   @Composable @ReadOnlyComposable get() = LocalAniFlowColors.current.surface
val SurfaceBorder: Color @Composable @ReadOnlyComposable get() = LocalAniFlowColors.current.borderSubtle
val TextPrimary: Color   @Composable @ReadOnlyComposable get() = LocalAniFlowColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAniFlowColors.current.textSecondary

// These were already immutable `val`s. Keep them constant — no composable getter needed.
val PrimaryAccent = Palette.Violet500
val PrimaryAccentLight = Palette.Violet300
val SecondaryAccent = Palette.Cyan500
val TertiaryAccent = Palette.Rose500
val SuccessGreen = Palette.Green500
val WarningAmber = Palette.Amber500
val TextTertiary = Palette.Neutral600
```

**This will not compile everywhere, and that is the point.** A `@Composable` getter can only be read
from a composable context. The compiler will now point at exactly the places that were reading
theme state from a *non*-composable context — which were latent bugs. Expect errors in these shapes,
and fix them this way:

| Error shape | Fix |
|---|---|
| Read inside `remember { … }` | Hoist: `val c = SurfaceCard` outside, then `remember(c) { … }` |
| Read inside a `Canvas`/`drawBehind`/`drawWithContent` lambda | Hoist to a local `val` before the modifier |
| Read inside a non-`@Composable` helper function | Add a `Color` parameter to the helper; pass it from the caller |
| Read in a default parameter value of a non-composable fun | Same — make it a required parameter |
| Read in a top-level `val` or `object` initialiser | Move it into the composable that uses it |

Do **not** "fix" these by re-adding a global or by hardcoding the hex. If a fix needs more than a
hoist, note the file and move on — P2 rewrites it.

`theme/Theme.kt` becomes a two-line delegate so `MainActivity` and any preview keep working:

```kotlin
// theme/Theme.kt — P1 shim. Delete in P2 along with this package.
@Composable
fun AniFlowTheme(content: @Composable () -> Unit) =
    com.example.aniflow.ui.design.AniFlowTheme(LocalDeviceType.current, content)
```

`theme/Type.kt`: delete the `Typography` value and let `ui/design/tokens/Type.kt` own it. If anything
still imports `com.example.aniflow.theme.Typography`, grep it — as of this writing only `theme/Theme.kt`
used it.

`MainActivity.kt:29` changes to the real entry point, so TV gets scaled type:

```kotlin
CompositionLocalProvider(LocalDeviceType provides deviceType) {
    AniFlowTheme(deviceType = deviceType) {   // com.example.aniflow.ui.design.AniFlowTheme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainNavigation()
        }
    }
}
```

## §5 — the fontSize sweep, scoped honestly

**Do not chase "229".** That is the whole-tree count. Broken down by `grep -c`:

| Bucket | Count | Action |
|---|---|---|
| `theme/Type.kt` | 10 | Legitimate — they *are* the scale. Moves to `tokens/Type.kt`. |
| `ui/redesign/*` (7 files) | 80 | **Skip.** P2 deletes these files. |
| Live screens (15 files) | **139** | This is P1's actual target. |

The 139, largest first:
`AdvancedServerProviderSelector.kt` 22 · `PhoneSettingsScreen.kt` 21 · `DetailScreen.kt` 20 ·
`TvHomeScreen.kt` 18 · `PhoneHomeScreen.kt` 16 · `MainScreen.kt` 13 · `PlayerScreen.kt` 10 ·
`TvSettingsScreen.kt` 5 · `SubtitleSelector.kt` 3 · `SpeedSelector.kt` 3 · `TvBrowseScreen.kt` 2 ·
`QualitySelector.kt` 2 · `PhoneBrowseScreen.kt` 2 · `TvLibraryScreen.kt` 1 · `PhoneLibraryScreen.kt` 1

Mechanical mapping — apply it literally, do not redesign type while you are in there:

| Literal | Occurrences (tree-wide) | Replace with |
|---|---|---|
| `40.sp` | 1 | `style = MaterialTheme.typography.displayLarge` |
| `28.sp` | 5 | `headlineLarge` |
| `24.sp`, `22.sp` | 6, 4 | `headlineMedium` |
| `20.sp`, `18.sp` | 10, 15 | `titleLarge` |
| `16.sp` | 20 | `titleMedium` if bold/semibold, else `bodyLarge` |
| `14.sp`, `13.sp` | 54, 18 | `bodyMedium` (`labelLarge` if it is a button/chip label) |
| `12.sp`, `11.sp` | 51, 23 | `bodySmall` (`labelMedium` for badges) |
| `10.sp`, `9.sp` | 16, 6 | `labelMedium` — **and flag each one.** 9sp is unreadable on a phone and invisible on TV. There is no token below 12sp on purpose. |

Rewrite shape — replace the `fontSize`/`fontWeight` pair with a `style`, keep everything else:

```kotlin
// before
Text(text = anime.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
     color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)

// after
Text(text = anime.title, style = MaterialTheme.typography.labelLarge,
     color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
```

Two traps:

1. **`style =` overrides, it does not merge.** If the call site had `fontWeight`, `letterSpacing` or
   `lineHeight` that the token does not carry, use
   `style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)` — do not silently
   drop it.
2. When a `Text` sits inside a **TV** composable that also sets `maxLines = 1` with a fixed-width
   parent, the 1.25× scale can now clip. Fix by removing the fixed width, not by shrinking the token.

Order of work: do `MainScreen.kt`, `DetailScreen.kt`, `PhoneHomeScreen.kt`, `TvHomeScreen.kt` first
(highest visibility), compile, then the rest. If you run short on context, **stop after a compiling
subset and write down which files still have literals** — a half-tokenised tree that builds is a fine
P1 outcome; a broken tree is not.

## §6 — core/result: AniResult, AniError, UiState

```kotlin
// core/result/AniResult.kt
package com.example.aniflow.core.result

sealed interface AniError {
    /** No usable network. Retry is meaningful. */
    data object Offline : AniError
    /** AniList returned 429, or our own rate limiter tripped. */
    data class RateLimited(val retryAfterSeconds: Long?) : AniError
    /** Upstream reachable but failed: 5xx, timeout, TLS. */
    data class Upstream(val code: Int?, val detail: String?) : AniError
    /** We parsed the response and it did not match what we expect. A bug, not a network event. */
    data class ContractChanged(val where: String) : AniError
    /** The thing genuinely does not exist. */
    data object NotFound : AniError
    data class Unknown(val detail: String?) : AniError
}

sealed interface AniResult<out T> {
    data class Success<T>(val data: T) : AniResult<T>
    data class Failure(val error: AniError) : AniResult<Nothing>
}

inline fun <T, R> AniResult<T>.map(transform: (T) -> R): AniResult<R> = when (this) {
    is AniResult.Success -> AniResult.Success(transform(data))
    is AniResult.Failure -> this
}

fun <T> AniResult<T>.getOrNull(): T? = (this as? AniResult.Success)?.data

/** Single place that turns a thrown exception into an AniError. Use it in every repository catch. */
fun Throwable.toAniError(): AniError = when (this) {
    is java.net.UnknownHostException, is java.net.ConnectException -> AniError.Offline
    is java.net.SocketTimeoutException -> AniError.Upstream(null, "timeout")
    is kotlinx.serialization.SerializationException -> AniError.ContractChanged(this.message ?: "parse")
    else -> AniError.Unknown(this.message)
}
```

Note there is **no `Loading` case** in `AniResult`. Loading is a UI concern, not a repository one — a
suspend function that is still running does not need to return "I am running". That belongs in
`UiState`:

```kotlin
// core/result/UiState.kt
package com.example.aniflow.core.result

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    /** Content is on screen from cache while a refresh failed — show a banner, not an error page. */
    data class Stale<T>(val data: T, val error: AniError) : UiState<T>
    data class Empty(val reason: String? = null) : UiState<Nothing>
    data class Error(val error: AniError) : UiState<Nothing>
}

fun <T> AniResult<T>.toUiState(isEmpty: (T) -> Boolean = { false }): UiState<T> = when (this) {
    is AniResult.Success -> if (isEmpty(data)) UiState.Empty() else UiState.Content(data)
    is AniResult.Failure -> UiState.Error(error)
}
```

`UiState.Stale` is the one case worth arguing for: P3 makes Room the read path, and "cached content +
failed refresh" is the single most common real state in an offline-first app. Adding it now means P3
does not have to touch every screen again.

## §7 — apply AniResult to exactly one method, and report what you find

### A confirmed bug the audit does not name, found while scoping this phase

**BUG — the app shows the wrong anime when detail lookup fails.**
`data/repository/DefaultAnimeRepository.kt:264`:

```kotlin
override fun getAnimeDetail(id: Int): Flow<Anime?> = flow {
    val detail = aniListApi.getAnimeDetail(id)
    emit(detail ?: getFallbackAnimeList().find { it.id == id } ?: getFallbackAnimeList().first())
}.flowOn(Dispatchers.IO)
```

`getFallbackAnimeList()` (`:367`) is **three hardcoded anime** — Death Note (id 1535), One Piece (21),
Attack on Titan (16498), with `picsum.photos` random images as banners. So if AniList is unreachable
and you tap anime 12345, the Detail screen renders **Death Note** as though it were the anime you
tapped, with a working Play button. `DetailViewModel` never sees `null`, so it cannot show an error.

This is the same class of failure as P0.2's 24 fabricated episodes, and it is arguably worse because it
is silent. The same fallback is emitted at **18 sites** in that file (`:85 :89 :107 :111 :129 :133
:156 :160 :178 :182 :188 :205 :209 :227 :231 :237 :255 :264`) — every home rail falls back to the same
three shows, which is why `MainScreenViewModel.kt:322` contains the tell:

```kotlin
if (_trending.value.size <= 3 && _trending.value.firstOrNull()?.id == 1535) {
```

i.e. the ViewModel sniffs for "did the repository lie to me" by checking for Death Note's ID.

**Fix in P1: only `:264`.** Rails falling back to three shows is bad UX; detail falling back to a
different anime is a lie about identity, and it is the one that breaks Play. Leave the 17 list-shaped
fallbacks alone — P3 replaces them with a Room cache, which is the correct fix, and doing it now means
doing it twice. Report this to Harmeet as a P1 finding with the line numbers above.

### The change

```kotlin
// data/repository/AnimeRepository.kt
suspend fun getAnimeDetail(id: Int): AniResult<Anime>   // was: fun … : Flow<Anime?>
```

It becomes `suspend` because there was never more than one emission — `flow { emit(x) }` with a single
`emit` is a suspend function wearing a costume, and both call sites already collapse it with
`.first()` / a single `collect`.

```kotlin
// data/repository/DefaultAnimeRepository.kt
override suspend fun getAnimeDetail(id: Int): AniResult<Anime> = withContext(Dispatchers.IO) {
    try {
        val detail = aniListApi.getAnimeDetail(id)
        // No fallback. Showing a different anime under this one's ID is worse than an error.
        if (detail == null) AniResult.Failure(AniError.NotFound)
        else AniResult.Success(detail)
    } catch (e: Exception) {
        AniResult.Failure(e.toAniError())
    }
}
```

Both consumers change. `DetailViewModel.kt:47-51`:

```kotlin
loadJob = viewModelScope.launch {
    uiState.value = DetailUiState.Loading
    when (val result = repository.getAnimeDetail(animeId)) {
        is AniResult.Success -> loadEpisodes(result.data)
        is AniResult.Failure -> uiState.value = DetailUiState.Error(result.error.userMessage())
    }
}
```

`PlayerViewModel.kt:216-218`:

```kotlin
val detail = repository.getAnimeDetail(animeId).getOrNull()
anime.value = detail
if (detail != null) { … }        // the existing null branch below stays exactly as it is
```

`PlayerViewModel` keeps its `Anime?` field in P1 — converting the player to `UiState` is P5's job and
touching 1,010 lines of it here is out of scope. Using `getOrNull()` is the deliberate, minimal edit.

Add one mapping function so error text lives in one place instead of being invented per screen:

```kotlin
// core/result/AniErrorMessages.kt
fun AniError.userMessage(): String = when (this) {
    AniError.Offline -> "You're offline. Check your connection and try again."
    is AniError.RateLimited -> "AniList is rate-limiting us. Try again in a moment."
    is AniError.Upstream -> "AniList didn't respond. Try again."
    is AniError.ContractChanged -> "Couldn't read AniList's response. This is a bug — please report it."
    AniError.NotFound -> "This anime isn't on AniList anymore."
    is AniError.Unknown -> "Something went wrong."
}
```

Do **not** convert the other 11 repository methods in P1. They are `Flow<List<…>>`, they have 14
consumers in `MainScreenViewModel`, and P3 rewrites all of them to read from Room. Converting them now
is ~500 lines of churn that P3 throws away. If Harmeet asks why the boundary is half-migrated, the
answer is: the half that lies about identity is fixed, the half that degrades gracefully waits for the
cache that makes the fallback unnecessary.

## §8 — di/AppContainer.kt

Replaces the `remember { … }` construction in `Navigation.kt:23-36` and the two ad-hoc stores at
`DetailScreen.kt:63` and `PlayerScreen.kt:87`. That duplication is not theoretical: two
`ProviderMappingStore` instances mean two DataStore instances over the same file, which the DataStore
docs call out as a corruption path.

```kotlin
// di/AppContainer.kt
package com.example.aniflow.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.aniflow.data.*
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.data.repository.DefaultAnimeRepository

/**
 * The whole object graph. ~40 lines by design — see CLAUDE.md: no Hilt for a single-module app.
 * Everything is lazy so nothing touches disk until first use.
 */
class AppContainer(private val appContext: Context) {
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val watchlistStore: WatchlistStore by lazy { WatchlistStore(appContext) }
    val watchHistoryStore: WatchHistoryStore by lazy { WatchHistoryStore(appContext) }
    val userFeedbackStore: UserFeedbackStore by lazy { UserFeedbackStore(appContext) }
    val providerMappingStore: ProviderMappingStore by lazy { ProviderMappingStore(appContext) }
    val repository: AnimeRepository by lazy { DefaultAnimeRepository(appContext) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap the content in CompositionLocalProvider in MainActivity")
}
```

**There is a third `ProviderMappingStore`.** `DefaultAnimeRepository.kt:267` has
`private val providerMappingStore = ProviderMappingStore(context)`. So today the app builds three of
them (repository, `DetailScreen`, `RedesignDetailScreen`). Give the repository a constructor parameter
with a default so nothing else breaks:

```kotlin
class DefaultAnimeRepository(
    private val context: Context,
    private val providerMappingStore: ProviderMappingStore = ProviderMappingStore(context)
) : AnimeRepository {
    // delete the private val at :267
```

and have `AppContainer` pass its instance: `DefaultAnimeRepository(appContext, providerMappingStore)`.

```kotlin
// AniFlowApp.kt
package com.example.aniflow

import android.app.Application
import com.example.aniflow.di.AppContainer

class AniFlowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

`AndroidManifest.xml` — add the `android:name` to `<application>`:

```xml
<application
    android:name=".AniFlowApp"
    …
```

`MainActivity.onCreate` provides it:

```kotlin
val container = (application as AniFlowApp).container
setContent {
    CompositionLocalProvider(
        LocalDeviceType provides deviceType,
        LocalAppContainer provides container
    ) {
        AniFlowTheme(deviceType = deviceType) { … }
    }
}
```

`Navigation.kt:23-36` collapses to:

```kotlin
val container = LocalAppContainer.current
val repository = container.repository
DisposableEffect(repository) {
    val engine = SelfHealingEngine(repository)
    engine.start()
    onDispose { engine.stop() }
}
// pass container.watchlistStore / .watchHistoryStore / .settingsStore / .userFeedbackStore
```

Then delete the ad-hoc constructions at `DetailScreen.kt:63` and `PlayerScreen.kt:87`, reading
`LocalAppContainer.current.providerMappingStore` / `.settingsStore` instead. **Keep the screens'
existing parameters** — do not start passing the container into every composable. Screens taking their
dependencies as parameters is correct; the container just supplies them at the nav layer.

`AniFlowTheme` should also stop building its own `SettingsStore` once the container exists:

```kotlin
val settingsStore = LocalAppContainer.current.settingsStore
```

Only do this if you have already wired `LocalAppContainer` above `AniFlowTheme` in `MainActivity`
(the ordering in the snippet above does that). If you invert them you get the
"AppContainer not provided" error at startup.

## Execution order (do not improvise this)

1. Create all of `ui/design/**`. Nothing imports it yet → compile. **Green.**
2. Create `core/result/**`. Nothing imports it yet → compile. **Green.**
3. Rewrite `theme/Color.kt` to the composable-getter shim + `theme/Theme.kt` delegate, point
   `MainActivity` at the new theme. **Compile and fix the non-composable-read errors.** This is the
   step that produces errors; budget for it. **Green.**
4. `fontSize` sweep, four highest-visibility files first, compile, then the remaining eleven. **Green.**
5. `AppContainer` + `AniFlowApp` + manifest + `Navigation.kt` + the two ad-hoc stores. **Green.**
6. `getAnimeDetail` → `AniResult` and its two consumers. **Green.**

Compile after **every** numbered step, not at the end. If step 3 explodes, you want to know it was
step 3.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export TMP="C:\\gradle_tmp"; export TEMP="C:\\gradle_tmp"
./gradlew.bat assembleStandardDebug --console=plain
./gradlew.bat testStandardDebugUnitTest --console=plain
```

## Definition of done

- `assembleStandardDebug` and `testStandardDebugUnitTest` both green.
- `grep -rn 'by mutableStateOf' app/src/main/java/com/example/aniflow/theme/` → **no hits.**
- `grep -rn 'PrimaryDark =' app/src/main` → **no hits** (nothing assigns to a theme colour).
- `grep -rc 'fontSize = [0-9]*\.sp' app/src/main` on the 15 live files → 0, or a written list of what
  is left and why.
- `grep -rn 'ProviderMappingStore(' app/src/main` → exactly **one** construction site (`AppContainer`).
- `getAnimeDetail` returns `AniResult<Anime>`; `getFallbackAnimeList()` is no longer reachable from it.

## Report to Harmeet (put this in chat, short)

1. The wrong-anime-on-failure bug at `DefaultAnimeRepository.kt:264`, fixed, plus the 17 sibling
   fallback sites deliberately left for P3.
2. The real fontSize number: 229 tree-wide, **139 in live screens**, 80 in `ui/redesign` that P2
   deletes, 10 legitimate in the type scale. The audit's "245" and the flat "229" are both misleading
   as a work estimate.
3. Every `9.sp`/`10.sp` site you promoted to 12sp — those are visible changes and he should look at
   them on a device.
4. What still needs a device: theme switch (dark ↔ AMOLED) actually repaints, and TV type at 1.25×
   does not clip any rail.

## Traps

- **`@Composable` getters cannot be used in `remember { }` keys or non-composable lambdas.** That is
  the whole diagnostic value of the shim. Hoist; don't work around.
- **`staticCompositionLocalOf` does not recompose readers on change.** That is fine for spacing and
  typography, and fine for colours *because* `AniFlowTheme` recomposes its whole subtree when
  `themeMode` changes — the `CompositionLocalProvider` is above `content`. If you move the provider
  below a `remember`, theme switching silently stops working. Verify on device (see report item 4).
- **Do not add a `premium` token set in P1.** Audit §14 mentions two token sets; the second one has no
  consumer until P2 and building it now is speculative work.
- **Do not create `ui/design/component/` or `ui/design/modifier/` in P1.** Audit §14 lists them, but
  `PosterCard`/`EpisodeCard` etc. only pay for themselves once P2 has collapsed the two UI trees and
  you can see which shapes are genuinely shared. Building 10 components against the *current* two-tree
  layout means building them twice. Say this to Harmeet rather than silently skipping it.
- `theme/*` uses wildcard imports in all 23 consumers, so a rename fails at ~40 sites with no import
  line to guide you. Grep the symbol, never the file.
- `ui/redesign/theme/GlassModifiers.kt` and `GlassTokens.kt` are imported by *non*-redesign files
  (`ui/main/MainScreen.kt` uses `glassSurface`, `darkGlassSurface`, `focusGlow`, `GlassTokens`). Leave
  them alone in P1. Untangling them is explicitly P2's problem.

