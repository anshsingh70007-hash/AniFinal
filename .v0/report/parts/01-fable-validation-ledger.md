# Part 01 — Fable 5 Validation Ledger

This part validates the original audit rather than repeating it. Severity reflects user harm, exploitability, and release impact—not how unpleasant the code looks.

## Critical-series verdicts

| Original ID | Verdict | Revised severity | Independent finding |
|---|---|---:|---|
| C-1 broken tests | CONFIRMED | High | Both test source sets reference obsolete constructor/API shapes and cannot provide a green test gate. This blocks CI but does not break the production APK, so Critical was inflated. |
| C-2 debug signing | CONFIRMED | Critical for distributed APKs | `release.signingConfig` explicitly selects `debug`. The original claim that anyone can necessarily sign with the same certificate is too broad: debug keystores are generated per machine unless the actual keystore leaked. The real failures are non-reproducible signer identity, insecure key custody, and inability to guarantee updates. |
| C-3 updater MITM/RCE | PARTIAL | Critical as a chain with C-2; High independently | HTTPS without certificate pinning is not “unauthenticated”; platform TLS validation is authentication. Global cleartext permission also does not magically downgrade HTTPS. The real verified weaknesses are no digest/signer verification, arbitrary redirect `Location` accepted as a URL (including HTTP), no redirect limit/relative URL handling, unmanaged download lifecycle, and debug signing. Fix trust at the artifact layer; pinning is optional and has operational risk. |
| C-4 history races | CONFIRMED | High | `saveProgress`, `removeHistory`, and `clearHistory` read then write in separate DataStore operations on an unmanaged scope. Last-writer-wins is real. The original `SupervisorJob` discussion is secondary; atomic `edit` and structured ownership are the fix. |
| C-5 Big Buck Bunny fallback | CONFIRMED | High | A provider failure can return unrelated media as a successful episode. This is correctness corruption, not a crash. |
| C-6 mutable theme globals | CONFIRMED | High | Top-level Compose state is written from composition, and light mode still uses `darkColorScheme`. The migration should be incremental with a custom `CompositionLocal`, not a blind global find/replace. |
| C-7 composition-created dependencies | CONFIRMED | High | Lifetimes and testability are poor. However `remember` does survive ordinary recomposition and application contexts avoid Activity leaks. A lightweight `Application` container is sufficient; Hilt is not mandatory for this app size. |
| C-8 ProGuard | PARTIAL | Medium | Blanket keeps and `-overloadaggressively` need review. The previous statement that `Stormbreaker` is nonexistent was incomplete: no Kotlin class exists, but four `libstormbreaker.so` files do. The keep rule still targets a nonexistent Java class and does not protect JNI. Native libraries are unstripped and apparently unused—this is a newly elevated build/security issue. |
| C-9 APK/log files in git | CONFIRMED | High | Repository bloat is real. It is not an application runtime Critical. Move release assets to GitHub Releases and remove logs; history rewriting requires coordination because it rewrites commit hashes. |

## High-series verdicts

| Original ID | Verdict | Revised severity / correction |
|---|---|---|
| H-1 stale player closure | CONFIRMED | High. `DisposableEffect(exoPlayer)` captures `currentEp` from the composition that installed the listener. Use live VM state or `rememberUpdatedState`; do not add episode as an effect key because that would release the player on each episode. |
| H-2 lifecycle/background/PiP | PARTIAL | High. Background behavior needs device verification, but no MediaSession or PiP entry logic exists. `setAudioAttributes(..., true)` delegates focus handling to Media3; the audit should not imply audio focus is wholly absent. Decide pause-on-background versus background playback before implementing. |
| H-3 seek-fighting | CONFIRMED | High UX. Slider seeks on every drag event. The polling-loop criticism is also valid. |
| H-4 failover position | PARTIAL | High. The stronger verified bug is that failures after first READY never call `handlePlaybackError`. Position loss is plausible but requires runtime reproduction. Build a failover controller with an explicit checkpoint. |
| H-5 cold one-shot Flows/cache | CONFIRMED | Medium. These APIs should be suspend functions. Plain cache-field visibility is less concerning because access mostly occurs through IO coroutines but remains unsynchronized. |
| H-6 delayed orchestration/fake fallback | CONFIRMED | High. Magic ID `1535` and fallback-as-success are invalid state modeling. |
| H-7 production logs | CONFIRMED | Medium. Response bodies and stack traces are poor release hygiene. No auth tokens currently exist, so privacy severity should not be overstated. |
| H-8 synchronized provider fan-out | PARTIAL | High. Shared synchronized list and response-order source choice are real. Claim that the synchronized section performs network I/O was imprecise: network awaits happen before `processSourcesResponse`; nevertheless shared mutation and nondeterministic ordering are weak. |
| H-9 unused dependencies | CONFIRMED by source import scan | Medium. R8 may remove bytecode, so APK impact must be measured rather than assumed. Build complexity and attack surface remain. |
| H-10 intro assets in main | CONFIRMED | High APK impact. Move to flavor source set and measure codec/device compatibility before H.265 conversion. |
| H-11 package suffix branching | CONFIRMED | Medium. A flavor bool is the safe first step; full source-set separation can create duplicate-maintenance cost if rushed. |
| H-12 detail loading | CONFIRMED | Medium–High. Empty/error/loading are conflated in both detail implementations. The nested child launch is cancelled with the parent effect, so “leaks across animeId” was overstated; the state machine is still fragile. |
| H-13 wrong fuzzy match | CONFIRMED | Critical content correctness when triggered. `firstOrNull()` fallback has no score threshold. Manual mapping needs a persisted provider mapping keyed by AniList ID and provider version. |
| H-14 TV controls | CONFIRMED | High for TV. Slider is explicitly disabled. Focus behavior requires TV/device testing. |
| H-15 config/tablet | PARTIAL | Medium. `configChanges` is not automatically wrong for Compose and `rememberNavBackStack` may restore keys. Lack of expanded layouts/saveable transient state is real. Do not remove manifest config handling until rotation/player lifecycle tests exist. |

## Medium/low corrections

- **M-1 Coil configuration:** valid optimization, but default Coil caches are not “no caching.” Add a singleton loader only after measuring; placeholders and stable image sizing are higher-value.
- **M-2 `Modifier.composed`:** valid modernization; not automatically a measurable hot-path bug. Benchmark before claiming a win.
- **M-3 grain allocations:** confirmed and severe enough to be **High performance**. It continuously creates random points/list objects while visible.
- **M-4 AniList cache key:** valid cleanup, Low–Medium. A cryptographic hash is unnecessary; use a compact operation/variables key.
- **M-5 updater throttle:** confirmed; persisted timestamp exists. Medium network/startup issue.
- **M-6 AppLoader coupling:** confirmed architectural smell. Medium.
- **M-7 emojis/accessibility:** confirmed in multiple headings; unlabeled cards require per-component verification. Medium.
- **M-8 typography:** confirmed consistency issue, Medium.
- **M-9 touch targets:** confirmed, High accessibility for phone player controls.
- **M-10 update back handling:** valid, Medium; the alleged `updateInfo!!` interleaving crash is unlikely under single-threaded snapshot composition but removing `!!` is still cleaner.
- **M-11 search state:** confirmed, Medium.
- **M-12 fabricated `Anime` navigation model:** confirmed, Medium correctness.
- **M-13 lenient JSON:** this is a design tradeoff, not a defect. Keep tolerant release parsing; add contract tests/telemetry rather than debug-only behavior differences that hide release failures.
- **M-14 Ktor OkHttp:** needs benchmark. Android engine is supported; switching engines is not inherently “faster.” `redirectClient` usage must be checked before removal.
- **M-15 MainScreen size:** confirmed maintainability issue, Medium.
- **M-16 insets:** confirmed, Medium.
- **L-6 exact track override:** confirmed and more important than Low. Exact `TrackSelectionOverride` plus min/max constraints and a self-seek is brittle; see Part 03.

## Previously missed repository evidence

### V-NEW-01 — Unused, unstripped native binaries with no reproducible source

- **Severity:** High
- **Evidence:** `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libstormbreaker.so`; no `System.loadLibrary`, `external fun`, Java/Kotlin bridge, Cargo/NDK source, or native build configuration is tracked. `file` reports all four binaries as “not stripped.” Total compressed impact must be measured, raw size is about 2 MB.
- **Why it matters:** Unreviewable binaries violate supply-chain reproducibility, add four ABI artifacts and may contain stale vulnerable code even if never loaded. Their presence contradicts the original report’s assumption that Stormbreaker simply does not exist.
- **Implementation task:** Prove ownership and purpose. If unused, remove all four binaries and the stale ProGuard rule. If required, restore source, JNI bridge, reproducible pinned toolchain, symbol stripping, SBOM/license record, and tests.
- **Do not:** Reverse-engineer and then claim recovered source is authoritative; keep binaries “just in case.”
- **Verification:** Compare APK contents/sizes, launch all flavors/ABIs, run streaming paths, verify no `UnsatisfiedLinkError`.

### V-NEW-02 — Quality selector semantics contradict implementation

- **Severity:** High
- **Evidence:** `QualitySelector.kt` says resolution “switches the actual stream URL”; callback only sets `selectedVideoQuality`. `PlayerScreen.applyVideoQualityOverride` changes Media3 track parameters; `PlayerViewModel.findSourceForResolution` is dead private code.
- **Impact:** Engineers and users cannot tell whether “1080p” means server stream choice or variant-track constraint. Fixed MP4/single-rendition sources cannot honor the selection; the UI can show 1080p while playing a lower-only source.
- **Resolution:** Model server/audio-language choice separately from rendition policy and expose “Unavailable on this server” instead of silently pretending.

### V-NEW-03 — Playback speed setting is not persisted from the player

- **Severity:** Medium
- **Evidence:** player selector writes `exoPlayer.setPlaybackSpeed` and `viewModel.playbackSpeed.value`; it never calls `SettingsStore.setDefaultPlaybackSpeed`. Settings can set a default, but in-player choice is session-only.
- **Decision required:** If selector means temporary speed, label it as session-only. If expected persistent, add an explicit “remember speed” preference; do not silently change global defaults on every tap.

## Gemini execution contract for this ledger

Before implementing any referenced task:
1. Re-open the current file and search for all call sites.
2. State whether behavior is covered by unit, instrumentation, or device tests.
3. Make one conceptual change per commit.
4. Do not combine architecture migration, UI redesign, and player behavior changes.
5. Attach build/test output and a before/after behavior note.
