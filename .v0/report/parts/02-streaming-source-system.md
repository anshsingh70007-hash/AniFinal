# Part 02 — Streaming Source System

**Priority:** highest. Execute tasks S-01 through S-06 before player polish.
**Primary files:** `data/AniLightProvider.kt`, `data/repository/DefaultAnimeRepository.kt`, `data/model/StreamingSource.kt`, `data/NetworkModule.kt`, `data/AdBlocker.kt`.

## Current data path

AniList detail title → provider search → exact AniList ID or first search result → provider watch document → concurrent sub/dub server requests → mutable shared source list → group by human-readable `quality` → first arrival becomes primary and later arrivals become `backupUrls` → player receives flattened `StreamingSource` values.

This design loses provider/server identity at the point where reliable failover needs it most.

## S-01 — Prevent wrong-series binding

- **Severity:** Critical correctness
- **Objective:** Never select a provider title merely because it is the first search result.
- **Files to inspect:** `DefaultAnimeRepository.kt:253-324`, provider search models in `AniLightProvider.kt`, `Episode.kt`.
- **Files to modify later:** repository, a provider-mapping persistence component, detail UI for ambiguity.
- **Reason:** fallback `searchResults.firstOrNull()` can bind a franchise sibling or remake.

### Steps for Gemini
1. Introduce a normalized candidate score using AniList English, romaji, native title, release year if available, and provider AniList ID.
2. Treat exact AniList ID as authoritative.
3. If no ID match, score normalized token sets. Do not strip season/year information before scoring; retain a second loose form only for search queries.
4. Reject below a documented threshold. Return a typed `ProviderMatchResult.NotFound/Ambiguous/Matched`, not an empty episode list.
5. For ambiguous results, show a user selection sheet with title/poster/year; persist `provider + anilistId -> providerSlug`.
6. Invalidate mappings when provider returns 404 or mapping schema/provider version changes.

### Potential mistakes
- Using raw Levenshtein alone penalizes translated titles.
- Persisting only the slug without provider identity prevents future multi-provider support.
- Automatically choosing the highest score when top two are nearly tied recreates the bug.

### Verification/testing
- Unit table: franchise sequels, punctuation, Roman numerals, English/romaji mismatch, remakes, “Season 2,” exact IDs.
- Contract test provider payload with no match/one match/tie.
- Regression: an existing exact-ID title still opens without prompting.
- **Success:** no unconfirmed low-confidence candidate reaches `getEpisodeList`.

## S-02 — Replace flattened source strings with stable source identity

- **Severity:** High
- **Objective:** Preserve language, server, rendition type, URL, headers, and priority independently.
- **Current problem:** `quality` contains both resolution and `(SUB)/(DUB)` text; backup URLs have no associated headers/server identity.

### Target model

```kotlin
data class PlaybackSource(
  val id: SourceId,              // provider/server/language/url hash
  val provider: String,
  val server: String,
  val language: AudioLanguage,
  val streamType: StreamType,    // HLS, DASH, progressive, unknown
  val declaredResolution: Int?,  // null for adaptive master
  val url: String,
  val headers: Map<String, String>,
  val priority: Int
)
```

Do not store raw URLs as long-lived preference IDs because signed URLs expire. Preference should store policy (`SUB`, preferred server, rendition policy), not a transient URL.

### Steps
1. Extend provider response processing to retain server name and language.
2. Return one source object per actual endpoint; remove `backupUrls` from the domain model.
3. Sort deterministically using explicit provider/server priority, language preference, adaptive/fixed policy, and resolution.
4. Keep headers per source. Never apply headers from failed source A to backup source B.
5. Add diagnostic reason codes internally (`TIMEOUT`, `HTTP_STATUS`, `PARSER`, `EMPTY_PLAYLIST`, `DECODER`) without exposing raw URL/query secrets.

### Verification
- Shuffle provider response completion order 100 times; selected source order must remain identical.
- Verify sub/dub never changes during failover unless same-language sources are exhausted and user consents.
- Verify source-specific Referer/Origin survive failover.

## S-03 — Structured server resolution with bounded concurrency

- **Severity:** High
- **Objective:** Resolve sources quickly without shared mutable collections or provider overload.
- **Evidence:** provider launches many server requests and merges into synchronized lists; arrival order influences primary choice.

### Steps
1. Use `supervisorScope` and `async` per candidate server.
2. Gate requests with `Semaphore(4)`; make limit configurable.
3. Return local immutable `ServerResolutionResult` from each child.
4. `awaitAll`, partition success/failure, then deterministic sort once.
5. Preserve cancellation: episode change must cancel outstanding resolution. Add a load generation/episode ID guard in ViewModel so stale results cannot overwrite the new episode.
6. Cache watch metadata by slug with a short TTL; do not cache signed playback URLs beyond their safe expiry.

### Mistakes
- `runCatching` catches `CancellationException` if used carelessly; rethrow cancellation.
- `supervisorScope` without timeout can hang forever. Keep per-request and whole-operation deadlines.
- Do not launch all providers eagerly on mobile data if one reliable source is sufficient; support staged resolution.

### Tests
- One server hangs, one throws, one succeeds.
- Episode changes while requests are active.
- Provider returns duplicates with different headers.
- Entire operation times out with a typed error and retry option.

## S-04 — Deterministic failover policy; eliminate unnecessary switching

- **Severity:** High
- **Objective:** Switch only when the active endpoint has a retryable source-level failure.

### Required policy
1. Classify error:
   - Retry current source: transient connection reset, timeout, 5xx, playlist refresh failure.
   - Switch endpoint: repeated retryable failure after bounded retries, 403/404 signed URL expiration after one re-resolution, malformed playlist.
   - Do not switch server: decoder/DRM/unsupported format until alternate codec/rendition rationale exists.
2. Retry same source with exponential backoff + jitter (for example 0.5s, 1.5s) while preserving playback position.
3. Re-resolve an expired URL before moving to a lower-priority server.
4. Move to next deterministic same-language source. Do not prioritize an `Auto` label blindly.
5. Reset failure history per episode/load generation, but retain cooldown per endpoint to avoid oscillation A→B→A.
6. Surface a compact diagnostic after all candidates fail; allow manual server selection.

### Exact existing cause of unnecessary switching
`PlayerViewModel.handlePlaybackError` immediately marks the current URL failed and consumes a backup on the first pre-start error. It does not classify Media3 error codes or retry the same endpoint. It then searches any “Auto” same-language source, independent of server reliability or whether that source is the same underlying CDN. This can switch on a single transient timeout.

### Success criteria
- No endpoint switch on first transient timeout.
- No return to a failed endpoint during the same episode generation.
- Language remains stable.
- Position discontinuity under 1 second after failover.

## S-05 — Fix ad blocking and header policy

- **Severity:** Medium–High
- **Evidence:** `AdBlocker.shouldBlock` uses substring matching over the entire URL. A legitimate path/query containing a blocked token can be rejected; a deceptive hostname can bypass/trigger incorrectly. `filterHeaders` is unused.

### Steps
1. Parse `Uri.host`; compare exact host or dot-boundary suffix (`host == blocked || host.endsWith(".$blocked")`).
2. Keep path rules separate and narrowly scoped.
3. Do not throw the same generic `IOException` as a network outage; use a typed `AdBlockedException` for diagnostics.
4. Define allowed forwarded headers. Never merge arbitrary provider headers into a global mutable factory without resetting them for the next source.
5. In player architecture, create a request/DataSource configuration per source or atomically set a complete property set; `setDefaultRequestProperties` must not leak stale keys.

### Tests
- `notdoubleclick.net` must not match `doubleclick.net`.
- `doubleclick.net.evil.test` must not match.
- subdomain matching works.
- source A headers do not appear in source B requests.

## S-06 — Remove fake-media success and model provider failures

- **Severity:** High
- **Objective:** A failed episode must never play unrelated fallback media.
- **Files:** `DefaultAnimeRepository.kt:361-381`, `PlayerViewModel.loadStreamingSourcesForIndex`.

### Steps
1. Delete unrelated MP4/VTT fallback.
2. Return `Result`/sealed error with `NoProviderMatch`, `NoEpisodes`, `NoSources`, `Network`, `ProviderChanged`.
3. Keep last known catalog cache separate from stream availability; never cache playback URLs as catalog fallback.
4. Render retry, choose-server, report-source, and back-to-episodes actions based on error type.

### Success
Automated test asserts every successful playback source originates from the resolved provider response; provider failure produces no `MediaItem`.

## Streaming observability specification

Add debug-only structured events, with URLs redacted:
- episode resolution duration
- servers attempted/succeeded
- selected source ID and policy reason
- first-frame latency
- rebuffer count/duration
- failover reason and destination

Do not log query strings, cookies, signed tokens, complete response bodies, or viewing history in release logs.
