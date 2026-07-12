# Part 05 — UI, Accessibility, Performance, and Tests

## U-01 — Fix state semantics before visual polish

Introduce typed UI states for home sections, browse/search, detail episodes, update takeover, and player failures. Loading, empty, offline, provider-unavailable, and no-results must not all render as an empty list. Remove fabricated `Anime` navigation objects; navigation callbacks should carry stable IDs.

## U-02 — Accessibility baseline

- Give every clickable anime card a semantic label containing title and action; decorative poster images may keep null descriptions only when the parent supplies semantics.
- Replace emoji and ASCII transport controls with consistent vector icons plus content descriptions.
- Enforce 48dp minimum targets, especially phone player actions and detail back/bookmark controls.
- Use Material typography roles and explicit line heights; test 200% font scale without clipping.
- Add selected-state semantics to tabs, quality, subtitle, speed, theme, and genre controls.
- Use `onClickLabel` where the visual label does not describe the action.
- Keep traversal/focus order deterministic on TV and block D-pad input during the intro overlay.

## U-03 — Insets, navigation, and takeover behavior

Use status/navigation/display-cutout insets instead of fixed top padding, including landscape player controls. Coordinate physical/predictive Back: player Back first dismisses transient overlays/controls according to device policy, update takeover intercepts Back when forced, and non-forced updates have a reachable dismiss action on TV. Guard duplicate backstack pushes.

## U-04 — Theme and component structure

Replace composition-time mutation of global colors with immutable dark/light/AMOLED schemes plus a `CompositionLocal` for custom glass tokens. Migrate screen-by-screen; do not perform blind token replacement. Split the 800+ line `MainScreen` into navigation shell, bars, update takeover, and state-holder inputs while retaining one shared behavior model for standard/redesign and phone/TV variants.

## PERF-01 — Stop perpetual allocation and invalidation

The animated grain/ambient implementation is the highest-priority visual performance target. Precompute grain or use a static asset/shader, cache geometry with `drawWithCache`, and pause animations when not visible. Avoid rebuilding random point lists per frame. Replace `Modifier.composed` only where modern node/draw APIs improve measured behavior.

## PERF-02 — Reduce recomposition scope

- Collect state near consumers rather than roughly twenty flows at the top of `MainScreen`.
- Expose immutable grouped state holders and mark stable models appropriately.
- Render home sections progressively rather than gating the page on one global spinner.
- Remove delay-based animation direction bookkeeping; derive direction from state.
- Skip/reduce intro media after first launch and move assets to the redesign flavor.

## PERF-03 — Measure network and media behavior

Track cold start, first content, stream resolution, first frame, rebuffering, and APK size in debug benchmarks. Do not claim gains from Coil/OkHttp/dependency removal until measured. Add stable image dimensions/placeholders first; configure a singleton image loader only with an explicit cache policy.

## T-01 — Replace obsolete tests immediately

Both existing tests target nonexistent APIs and cannot compile. Remove them in the same commit that adds a minimal valid test harness—do not leave the project with a green build achieved only by deleting tests.

### Required unit suites

1. provider title matcher: exact ID, sequel, remake/year, translated title, tie, below threshold
2. source ordering/failover: deterministic shuffle, language preservation, header isolation, retries
3. watch history: concurrent atomic updates, eviction, removal
4. Player state machine: stale generation ignored, checkpoint retention, error classification
5. Main/detail/search state: loading/success/empty/error and cancellation
6. updater metadata/hash/signer policy using pure helpers

Use coroutine test dispatchers and injected clocks; avoid real delays/network.

### Instrumentation/device suites

- navigation duplicate-tap guard and state restoration
- TalkBack semantics and 48dp targets
- 200% font scale, light/dark/AMOLED, phone/tablet/TV sizes
- TV D-pad focus and player seeking
- rotation, Home, lock, PiP/background contract
- Media3 smoke tests with local fixture HLS/MP4/subtitle assets
- update takeover Back/focus behavior

## T-02 — CI order

1. format/static analysis
2. unit tests
3. compile standard/redesign debug
4. lint
5. instrumentation smoke suite on an emulator
6. unsigned/minified release assembly and artifact-size report
7. signed release only in protected release workflow

## Acceptance checklist

- no unlabeled clickable poster cards
- all primary controls reachable by touch, keyboard, and D-pad where applicable
- no clipping at 200% font scale
- no fake content on offline/provider failure
- no continuous grain allocations while idle/offscreen
- search distinguishes idle/loading/empty/results/error
- player and update Back behavior deterministic
- obsolete tests replaced and CI green for both flavors
