# Part 06 — Execution Roadmap, Coverage, and Guardrails

This file is the ordering contract for Gemini. Parts 01–05 define evidence and implementation detail; this part prevents unsafe batching.

## Phase 0 — Establish a trustworthy gate

1. Replace obsolete tests with a compiling harness.
2. Add CI for both flavors and lint.
3. Record baseline APK sizes, signer certificate, startup, stream-resolution, and first-frame behavior.
4. Preserve a reproducible playback fixture and provider JSON fixtures.

**Exit:** every future change has a build/test gate and baseline comparison.

## Phase 1 — Distribution and data integrity

1. Configure real release signing and document signer migration.
2. Add update SHA-256, signer verification, redirect/size controls, and HTTPS policy.
3. Make history mutations atomic.
4. Remove fake playback/catalog success states.
5. Fix stale episode progress capture and duplicate navigation pushes.
6. Remove unsafe nullable assertions in update/player paths.

**Exit:** wrong/unverified APKs cannot install; concurrent history writes survive; provider failure never produces unrelated media.

## Phase 2 — Streaming and player reliability

1. Add typed provider match results and confidence tests.
2. Preserve stable source/server/language/header identity.
3. Resolve servers with bounded immutable concurrency and deterministic ordering.
4. Introduce failover controller with classification/checkpoints.
5. Fix scrubbing, lifecycle policy, noisy handling, subtitle MIME, and quality semantics.

**Exit:** no low-confidence series auto-binds; transient failures do not immediately switch; position/language/headers survive failover.

## Phase 3 — Architecture and state

1. Add `AppContainer` composition root and factories.
2. Convert one-shot Flows to suspend APIs and centralize caches.
3. Introduce section, detail, search, and player typed states.
4. Decouple AppLoader and package-name flavor checks.
5. Decompose MainScreen without changing visual behavior.

**Exit:** ViewModels are unit-testable; process/config recreation follows explicit ownership; empty/error/loading are distinct.

## Phase 4 — Accessibility, UI, and performance

1. Semantics, target sizes, icons, Back/focus, and insets.
2. Immutable theme migration and typography roles.
3. Stop grain/ambient per-frame allocation; narrow recomposition scope.
4. Move/recompress flavor assets and remove verified-unused dependencies/native binaries.
5. Add expanded-width layouts only after phone/TV behavior is stable.

**Exit:** accessibility matrix passes; idle visual effects do not continuously allocate; standard APK no longer carries redesign intro videos.

## Finding-to-part coverage

| Original area | Authoritative implementation part |
|---|---|
| C-1 broken tests | Part 05 T-01/T-02 |
| C-2/C-3 signing/updater | Part 04 SEC-01/02 |
| C-4 history race | Part 04 A-02 |
| C-5/H-6 fake success | Parts 02 S-06, 05 U-01 |
| C-6 theme | Part 05 U-04 |
| C-7 dependency ownership | Part 04 A-01 |
| C-8 native/R8 | Part 04 B-01 |
| C-9 repository binaries/logs | Part 04 B-02 |
| H-1–H-4 player | Part 03 |
| H-5 repository Flow/cache | Part 04 A-03 |
| H-7 logging | Part 04 SEC-03 |
| H-8/H-13 streaming match/fan-out | Part 02 S-01–S-04 |
| H-9/H-10/H-11 build/flavors | Part 04 B-02/A-01 |
| H-12 detail state | Parts 04 A-03, 05 U-01 |
| H-14 TV controls | Parts 03 P-02, 05 U-02 |
| H-15 restoration/layout | Part 05 U-03 and device suites |
| M-1–M-3 image/modifier/grain | Part 05 PERF-01–03 |
| M-4/M-5 cache/update throttle | Part 04 A-03/SEC-02 |
| M-6–M-16 UI/architecture | Parts 04–05 |
| L-1–L-14 | Apply only in related scoped commits; no cleanup mega-commit |
| V-NEW-01 native binaries | Part 04 B-01 |
| V-NEW-02 quality semantics | Parts 02 S-02, 03 P-05 |
| V-NEW-03 speed persistence | Part 03 P-06 decision |

## Mandatory implementation protocol

For every task Gemini must:

1. Re-open current files and enumerate all call sites.
2. State baseline behavior and available automated/device coverage.
3. Implement one conceptual change per commit.
4. Add/adjust tests with the behavior change.
5. Run the smallest relevant gate, then both-flavor build/lint before completion.
6. Report exact commands/results and before/after behavior.
7. Stop on ambiguous product decisions (background playback, speed persistence, provider tie, signer migration) rather than guessing.

## Prohibited shortcuts

- no fake video/catalog data as success
- no first-search-result provider fallback
- no raw URL as persisted source identity
- no global arbitrary provider headers
- no certificate pinning presented as a substitute for artifact verification
- no Hilt/module split solely to appear architectural
- no repository history rewrite without coordination
- no blanket UI/theme/player refactor in one commit
- no severity claim without source/runtime evidence
- no optimization claim without measurement

## Final release gate

- tests compile and pass; both flavors build and lint
- expected release signer verified
- updater adversarial tests pass
- minified release playback smoke path passes
- no unrelated media/fake catalog success path remains
- accessibility/device matrix recorded
- APK and repository size deltas documented
- no signed URLs, response bodies, viewing history, APKs, or logcat dumps added to source control
