# Part 04 — Architecture, Data, Security, and Build

## A-01 — Establish an application composition root

- Add an `Application`-owned `AppContainer` with singleton repository, API clients, stores, and clock/version providers.
- Construct ViewModels through explicit factories. Remove raw `Context` from ViewModels; pass narrow dependencies.
- Keep the single Gradle module for now. Module splitting is not the cure for missing ownership.
- Replace package-name suffix checks with one flavor resource/config value first; only move implementations to flavor source sets after tests exist.

**Success:** one repository/store instance per process, recreation does not rebuild caches, ViewModels can be created in unit tests.

## A-02 — Make DataStore updates atomic

`WatchHistoryStore` performs read-modify-write across separate calls on an unmanaged IO scope. Convert mutators to suspend functions and decode/update/encode inside one `DataStore.edit`. Route calls through `viewModelScope`; keep history bounded to 50 entries. Apply the same review to watchlist mutations.

**Concurrency test:** launch simultaneous heartbeat, pause, and removal operations; no unrelated entry is lost and removed entries do not reappear.

## A-03 — Replace one-shot Flow APIs and ad hoc loading

- Repository methods that emit once should be `suspend` functions.
- Consolidate repeated TTL caches behind a mutex-protected cache helper.
- Replace fake catalog fallback data and magic AniList ID `1535` detection with typed per-section states.
- Add `DetailViewModel`; both detail flavors consume shared detail/episode state.
- Keep provider errors typed through repository boundaries rather than converting every failure to an empty list.

## SEC-01 — Restore release signing integrity

The release build explicitly uses the debug signing config. Add an environment-backed release signing config and fail release builds clearly when credentials are absent. Preserve the existing certificate only long enough to plan migration for already-distributed APKs; changing signer without Android-supported rotation breaks updates.

Never commit keystores or passwords.

## SEC-02 — Verify self-update artifacts

HTTPS platform validation is already authentication; certificate pinning is optional, not the primary fix. Artifact trust is missing.

1. Extend update metadata and `publish_update.py` with SHA-256 and immutable release URL.
2. Download to a private temporary file with size limit, cancellation, redirect limit, and HTTPS-only redirect policy.
3. Hash while streaming and compare in constant-time style.
4. Inspect archive signing certificates and require the expected/running signer according to the migration policy.
5. Delete failed/abandoned files; only then launch installer intent.
6. Persist update-check throttling and validate metadata schema/version.

Turn global cleartext off. If a required stream is HTTP-only, allow only a reviewed domain rather than global cleartext.

## SEC-03 — Remove sensitive release logging

Enable BuildConfig and centralize debug-gated logging. Remove full response bodies, URLs with query strings, update payloads, and `printStackTrace()` from release paths. Diagnostics should log stable source IDs/reason codes, not signed URLs or viewing history.

## B-01 — Repair shrinker and native artifact policy

- Remove the nonexistent Java `Stormbreaker` keep rule only together with a decision on the four `libstormbreaker.so` binaries.
- If native binaries are unused, remove all ABIs. If required, restore source, JNI bridge, pinned build, stripping, license/SBOM record, and tests.
- Remove blanket `data` and Ktor keeps incrementally; rely on library consumer rules and targeted serialization rules.
- Remove `-overloadaggressively`; run release playback smoke tests after every shrinker change.

## B-02 — Reduce build and repository weight

- Move intro videos to `src/redesign/res/raw`; verify standard compilation before deleting shared resources.
- Remove dependencies only after source/import and runtime verification. Retain `media3-session` only if Part 03 chooses background playback.
- Move APKs to GitHub Release assets; ignore `*.apk`, release output, and `logcat*.txt`.
- Coordinate any history rewrite—never rewrite shared history silently.
- Add CI jobs for both flavors: compile, lint, unit tests, release assembly without exposing signing secrets.

## B-03 — Dependency decisions

Measure before replacing the Ktor Android engine; OkHttp is not automatically a performance fix. Delete `redirectClient` only after all references are checked. Keep tolerant JSON parsing in release for provider resilience, and add contract fixtures to detect schema drift rather than using behaviorally different debug parsing.

## Verification gates

- clean clone builds both debug flavors
- release build fails safely without signing secrets and signs with expected cert when configured
- updater rejects wrong hash, wrong signer, oversized file, redirect loop, and HTTPS→HTTP redirect
- concurrent history operations are deterministic
- R8 release can search, open detail, resolve streams, play, save history, and install no fake media
- APK diff documents dependency/resource/native changes
- repository contains no APK/log artifacts after coordinated cleanup
