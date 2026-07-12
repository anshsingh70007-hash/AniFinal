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

## S-07 — AniLight quality normalization: solve the current defect before replacing the provider

- **Severity:** High player correctness
- **Decision:** Keep AniLight as the primary provider **if and only if** the implementation below passes the quality and stability acceptance tests. Do not replace AniLight merely because its server IDs are currently shown as quality choices.
- **Exact cause:** `AniLightProvider.processSourcesResponse` converts an unknown or missing provider quality to `"Auto"`, then constructs labels such as `"Auto - MISORA (SUB)"`, `"Auto - NEAR (SUB)"`, and `"Auto - MISA (SUB)"`. The code groups by the entire display string, so each server becomes a separate “quality.” `PlayerViewModel.pickInitialSource` then explicitly prefers any Auto source. These are server endpoints or adaptive master playlists, not distinct video qualities.
- **Files to inspect:** `AniLightProvider.kt:273-290, 334-382`, `PlayerViewModel.kt:139-256`, `PlayerScreen.kt` quality-track helper, `QualitySelector.kt`, `StreamingSource.kt`.

### Objective

The user-facing quality menu must contain only `Auto`, `1080p`, `720p`, `480p`, and `360p` when those choices are genuinely available. Provider/server names must never appear in that menu. Server selection and quality selection are independent dimensions.

### Required implementation

1. Preserve every AniLight response as a server endpoint with `providerId`, language, headers, and stream type. Do not encode these fields into `quality`.
2. For each endpoint, inspect the media:
   - For an HLS master playlist, parse `#EXT-X-STREAM-INF` variants and their `RESOLUTION`/`BANDWIDTH` attributes. Expose one adaptive `Auto` choice plus only the actual height variants found in the master.
   - For a single-variant/media playlist, probe Media3 tracks after preparation. It can support one known fixed height, but it cannot honestly manufacture 1080/720/480/360 choices.
   - For progressive media, use declared provider metadata or Media3 format height after preparation.
3. Normalize heights into supported UI buckets with an explicit rule. Recommended: exact heights first; optionally map 1072–1088 to 1080, 704–736 to 720, 464–496 to 480, and 344–376 to 360. Do not label an unknown stream as 1080p.
4. Model the menu as `QualityPolicy.Auto` or `QualityPolicy.FixedHeight(height)`. Model the active server separately as `SourceEndpoint`.
5. In Auto mode, select the adaptive master and clear video track overrides. In fixed mode, keep the same endpoint and apply a Media3 track selection constraint/override only if that height exists on that endpoint. Do not switch to another server merely because the user changes quality.
6. If the current endpoint lacks the requested height, first search another healthy same-provider/same-language endpoint that advertises that height. If none exists, retain the current playback and show “1080p unavailable” rather than silently reverting to Auto.
7. Persist `QualityPolicy`, not a provider label and not a signed URL. On episode change/failover, reapply the persisted policy after tracks become available. The visible selection must remain 1080p/720p/etc. if satisfied; if unavailable, display a distinct unavailable/fallback state rather than falsely highlighting Auto.
8. Keep a separate advanced server picker for diagnostics/manual recovery. Server labels may be `Misa`, `Near`, `Misora`, etc. only there.

### Potential mistakes

- Do not create four menu rows unless four real renditions exist.
- Do not parse quality solely from the URL string.
- Do not group by display labels.
- Do not use `setMinVideoSize` plus an exact override together.
- Do not seek to the current position solely to force a track change; Media3 applies track-selection parameter updates.
- Do not let failover overwrite the user’s quality policy with Auto.

### Verification checklist

- An AniLight adaptive master with four variants renders exactly Auto, 1080p, 720p, 480p, 360p.
- Three AniLight servers returning unknown adaptive playlists render one quality menu, not three `Auto - SERVER` rows.
- Selecting 720p changes the video track but not the endpoint when that endpoint carries 720p.
- Episode next/previous and same-language failover preserve the selected quality policy.
- When 1080p does not exist, the app never claims that it is playing 1080p.
- Track changes are verified through Media3 `Tracks`, not only through UI state.

### Success criteria

Across a fixture set of at least 20 AniLight episodes and all supported servers, the menu contains no server names, no duplicate Auto rows, no fabricated heights, and the selected fixed height matches the active Media3 video track within the normalization tolerance.

## S-08 — Anikoto evaluation and backup-provider architecture

- **Severity:** High availability architecture
- **Evidence verified on 2026-07-12:** `https://anikotoapi.site/series/{id}` returns episode `embed_url.sub`/`embed_url.dub` values, not documented raw HLS/DASH/progressive URLs or quality variants. The public documentation says the API is intended for server-side use and allows 60 requests per IP per 120 seconds. A sampled current embed URL from `/series/8851` returned HTTP 410 when inspected. Therefore Anikoto is **not currently a drop-in Media3 source provider and is not proven more stable than AniLight**.
- **Decision:** Do not remove AniLight now. Implement AniLight quality normalization first. Prepare Anikoto behind the provider boundary as an experimental backup, but do not promote it to automatic production failover until the acceptance gate below passes.

### Architecture

```kotlin
interface EpisodeProvider {
    val id: ProviderId
    suspend fun findSeries(identity: AnimeIdentity): ProviderMatchResult
    suspend fun episodes(series: ProviderSeriesId): List<ProviderEpisode>
    suspend fun resolve(request: EpisodeRequest): ProviderPlaybackResult
}
```

`ProviderPlaybackResult` must distinguish:

- `NativeSources(List<SourceEndpoint>)` — verified raw media usable by Media3;
- `EmbedOnly(EmbedUrl)` — a web player URL, not a native source;
- typed errors including not found, rate limited, stale embed, and provider changed.

### Steps for Gemini

1. Refactor AniLight behind `EpisodeProvider` without changing behavior first; pin existing contract fixtures.
2. Add `AnikotoProvider` for series matching and episode discovery using `ani_id` when present, then `mal_id`, then scored title/year matching. Never assume AniList ID equals Anikoto numeric `id`.
3. Put Anikoto calls behind the app’s own backend/proxy and cache series/episode metadata. The API explicitly discourages direct production-client use; many app installations behind carrier NAT could also share a public IP and trigger 429/403.
4. Do not send raw embed URLs to ExoPlayer. Only return `NativeSources` if a documented/licensed endpoint supplies raw media and required headers. Do not scrape/deobfuscate third-party embed internals as a “stable API” strategy.
5. If only `EmbedOnly` is available, keep it disabled by default for the native player. A WebView fallback is a separate product/security decision and must include domain allowlisting, JavaScript/interface restrictions, external-navigation blocking, lifecycle cleanup, ad/privacy review, and explicit reduced-feature UX. It will not guarantee the native 1080/720/480/360 selector.
6. Add provider health telemetry: metadata success rate, episode match rate, raw-source resolution rate, first-frame success, HTTP 410/429/403 rate, and median resolution latency. Do not log full URLs or titles in release telemetry.
7. Automatic cross-provider failover is permitted only when both providers resolve the same AniList/MAL identity and episode number/language. Require a confidence threshold and prevent sequel/episode drift.
8. Failover order after qualification: retry/re-resolve current AniLight endpoint → alternate AniLight endpoint → Anikoto native source. Never jump to an Anikoto embed on a single transient AniLight timeout.

### Anikoto qualification gate

Before enabling as production backup, run a seven-day probe over at least 100 representative series/episodes and require:

- at least 95% correct series and episode identity;
- documented raw playback sources or an explicitly accepted embed-only UX;
- at least 98% successful metadata responses excluding client misuse;
- acceptable 410/stale-embed rate and first-frame success;
- stable headers/redirects and HTTPS behavior;
- no 429/403 under projected backend cache load;
- terms/licensing/privacy review appropriate to distribution.

If Anikoto cannot provide native raw sources, it does not meet the native-player backup requirement. Keep the adapter dormant or reject it; do not remove a working AniLight integration for an unproven embed-only replacement.

### Provider-removal decision

Remove AniLight only if S-07 fails because AniLight consistently supplies neither parseable adaptive playlists nor reliable fixed-resolution metadata, **and** Anikoto (or another provider) passes the native-source qualification gate. If AniLight passes S-07, retain it as primary and keep qualified backup adapters available. If no provider passes, surface a typed no-source state; never fabricate quality options or unrelated media.

## S-09 — Miruro evaluation: stronger technical candidate, but not a public stable API

- **Severity:** High availability architecture
- **Evidence verified on 2026-07-12:** `https://www.miruro.to/` is a consumer streaming website protected by a Cloudflare human-verification challenge. The accessible site exposes a playback product, but no official public developer API, versioned contract, service-level agreement, or supported Android integration was found. Third-party GitHub projects claiming “Miruro API” extract/decrypt Miruro/provider M3U8 links; these are reverse-engineered community services, not evidence of an official stable Miruro contract.
- **Assessment:** Miruro is technically more promising than Anikoto for a native Media3 backup because community implementations claim to return HLS/M3U8 sources and AniList-based episode mappings. It is operationally riskier than a documented API because Cloudflare, encryption, provider routing, request signatures, or upstream page changes can break it without notice.
- **Decision:** Add Miruro as a second experimental candidate behind `EpisodeProvider`. Do not call `miruro.to` directly from the Android app, do not ship a copied decryption/extraction key in the APK, and do not treat an arbitrary public “Miruro API” deployment as production infrastructure.

### Objective

Determine whether a self-controlled Miruro adapter can provide correctly matched native HLS sources and real rendition metadata reliably enough to become the preferred backup after AniLight. The desired order is:

1. AniLight primary after S-07 quality normalization;
2. self-hosted, qualified Miruro adapter as first backup if it passes S-09;
3. qualified Anikoto native source as another backup;
4. typed unavailable state rather than an unverified embed or unrelated stream.

If AniLight fails its acceptance gate, a qualified Miruro adapter may become primary. Provider priority must remain remotely configurable so an app update is not required to disable a broken integration.

### Required architecture

1. Implement `MiruroProvider : EpisodeProvider` in the backend/provider layer, not in Compose or `PlayerViewModel`.
2. Use AniList ID as the primary identity only when the selected Miruro implementation documents that mapping. Still verify canonical title, season/year, episode number, language, and episode count before playback.
3. Self-host and pin the reviewed adapter source/commit. Expose a small internal contract to Android; do not depend directly on a volunteer’s public Vercel/Render deployment.
4. Return `NativeSources` only after validating the URL is an HLS/DASH/progressive media endpoint and preserving required request headers. Reject HTML, Cloudflare challenge pages, and expired redirects as typed errors.
5. Parse the returned HLS master through the same rendition-normalization pipeline in S-07. Miruro must not create provider-specific quality labels. A Miruro master with four real variants yields Auto/1080p/720p/480p/360p; a single rendition yields only that truthful choice.
6. Cache metadata and episode mappings, but treat signed media URLs as short-lived. Re-resolve on 401/403/410 or expiry; never persist them as user preference.
7. Isolate extractor/encryption logic behind a backend module with contract fixtures and a kill switch. Do not place it in the APK, where it is easily reverse engineered and impossible to hotfix independently.
8. Add bounded request concurrency, timeout budgets, circuit breakers, and per-provider health state. A Cloudflare challenge must open the circuit; it must not trigger a retry storm from every device.
9. Preserve `QualityPolicy`, subtitle/language preference, episode identity, and playback checkpoint when moving between AniLight and Miruro.
10. Perform terms, licensing, privacy, and distribution review before production use. Technical extractability does not establish permission or long-term availability.

### Miruro qualification gate

Run a self-hosted seven-day probe over at least 100 series and 500 representative episodes, including old titles, current simulcasts, movies, specials, sub, and dub. Require:

- at least 99% correct AniList/season/episode identity for any automatically selected source;
- at least 95% episode-resolution success and at least 98% first-frame success among resolved sources;
- truthful rendition metadata confirmed against HLS manifests and active Media3 tracks;
- no unresolved Cloudflare challenge in the backend path under normal operation;
- measured 401/403/410/429 rates within an agreed error budget;
- median source resolution below 2 seconds and p95 below 5 seconds from the deployment region;
- no dependency on a third-party public proxy with unknown logging or uptime;
- provider behavior tested from the actual production hosting region;
- a working remote kill switch and circuit breaker;
- legal/security approval for the chosen integration method.

### Potential mistakes

- Do not label an unofficial GitHub extractor as the “official Miruro API.”
- Do not automate Cloudflare browser challenges in the Android client.
- Do not hardcode the consumer website’s private endpoints and assume they are stable.
- Do not use WebView interception to steal media requests as the native-player architecture.
- Do not merge Miruro server/provider names into the quality selector.
- Do not fail over merely because one segment timed out; classify and retry according to S-03/S-04.
- Do not use a source unless identity confidence and language match are proven.

### Verification checklist

- Unit fixtures cover every supported Miruro response, master playlist, malformed response, challenge HTML, expired URL, and missing-quality case.
- Integration tests verify source headers, redirects, subtitles, audio tracks, and HLS variants.
- A forced AniLight outage moves to Miruro only after AniLight retry/circuit policy is exhausted and resumes within the checkpoint tolerance.
- The requested fixed quality remains selected after cross-provider failover when Miruro carries that rendition.
- If Miruro lacks the requested rendition, UI reports the fallback explicitly and does not silently select Auto.
- Disabling Miruro remotely removes it from resolution without an app update.

### Success criteria

Miruro may become the first backup only after it passes the full gate on self-controlled infrastructure. Until then it remains experimental. The existence of a working consumer website or community extractor alone is insufficient evidence of production stability.

## Final provider decision matrix

| Provider | Current evidence | Native Media3 potential | Production role now | Promotion requirement |
|---|---|---:|---|---|
| AniLight | Existing app integration already resolves playable sources; quality model is wrong | High | Primary | Pass S-07 quality/stability tests |
| Miruro | Consumer site; Cloudflare protected; unofficial extractors claim M3U8 | Potentially high, unverified | Experimental candidate | Self-hosted adapter passes S-09 gate |
| Anikoto | Public metadata API returns embed URLs; sampled embed was stale | Low until raw sources are documented | Experimental metadata/embed candidate | Pass S-08 native-source gate |

Do not remove AniLight based on API appearance alone. Choose providers using measured identity correctness, raw-source availability, first-frame success, rendition truthfulness, operational control, and failure rate.

## Streaming observability specification

Add debug-only structured events, with URLs redacted:
- episode resolution duration
- provider and servers attempted/succeeded
- selected endpoint ID and policy reason
- available rendition heights and active Media3 track height
- first-frame latency
- rebuffer count/duration
- failover reason and destination provider/server
- Anikoto 410/429/403 and embed-only counts during qualification

Do not log query strings, cookies, signed tokens, complete response bodies, or viewing history in release logs.
