# Part 03 — Player Lifecycle, Controls, and Failover

**Dependency:** implement Part 02 source identity before redesigning failover. Small isolated fixes P-01 and P-02 may land earlier.

## P-01 — Eliminate stale episode capture in progress saves

- **Severity:** High correctness
- **Evidence:** `PlayerScreen.kt` computes `currentEp` in composition, while `DisposableEffect(exoPlayer)` installs a listener once. Pause and disposal callbacks can therefore use the initial null/old episode.
- **Change:** read the current episode from ViewModel state inside each callback, or use `rememberUpdatedState`. Do not key the effect by episode because its disposer releases the player.
- **Test:** load episodes asynchronously, switch episodes, pause, leave; assert each save uses the active episode number.

## P-02 — Make scrubbing local until commit

- **Severity:** High UX
- **Evidence:** the phone progress slider seeks during every `onValueChange`; the ticker only updates while playing.
- **Change:** keep a local drag position, seek once from `onValueChangeFinished`, and update displayed position while READY even when paused. Restart control auto-hide on slider interaction.
- **TV:** provide focusable rewind/forward buttons and D-pad steps; do not enable a standard touch Slider and assume it is TV-accessible.
- **Success:** one seek per completed drag; paused seeks are immediately reflected; targets are at least 48dp.

## P-03 — Choose and implement a lifecycle contract

The current app declares PiP support but has no PiP entry path, MediaSession, or lifecycle observer. Select exactly one mode:

1. **Pause-on-background (recommended first):** on `ON_STOP`, checkpoint and pause. Remove unused `media3-session`; keep PiP disabled until implemented.
2. **PiP:** enter PiP while actively playing, supply aspect ratio/actions, and test Home, lock, rotation, and process recreation.
3. **Background playback:** move ownership to `MediaSessionService`, publish a notification, and make the composable a controller—not player owner.

Do not mix a composition-owned player with a background service.

## P-04 — Centralize failover in a controller

- Move retry/error classification and source progression out of `PlayerScreen`.
- Capture a monotonic playback checkpoint before stop/clear.
- Retry the same source for transient IO errors with bounded backoff; re-resolve one expired 403/404; then switch to the next deterministic same-language source.
- Decoder/DRM errors must not automatically rotate arbitrary servers unless an alternate codec is known.
- Maintain per-episode generation and endpoint cooldown to prevent A→B→A oscillation.
- Resume within one second of the checkpoint and never silently change SUB/DUB.

## P-05 — Correct Media3 configuration and enforce truthful quality state

- Remove the forced 15 Mbps initial estimate; use Media3 estimation.
- Reduce 50s/120s buffering defaults and expose a data-saver policy after measurement.
- Disable cross-protocol redirects unless a documented source requires them.
- Add `setHandleAudioBecomingNoisy(true)`.
- Detect `.srt` as SubRip rather than VTT; retain ASS handling.
- Apply Part 02 S-07: server endpoint selection and quality policy are separate. The quality selector may show only Auto and real rendition heights; Misa/Near/Misora belong in a separate server selector.
- Represent preference as `QualityPolicy.Auto` or `QualityPolicy.FixedHeight(Int)`. Persist and reapply it after episode changes, source preparation, and failover.
- Populate available fixed heights from parsed HLS variants and/or Media3 `Tracks`. Do not infer an unknown provider source as Auto and then expose one Auto row per server.
- In Auto, clear video overrides. In fixed mode, select a matching supported video track on the active endpoint. Remove redundant min-size plus exact override and remove the self-seek used to apply an override.
- Observe Media3 track changes and derive `activeVideoHeight`; UI success requires the actual active format to satisfy the selected fixed-height policy. Keep requested policy separate from active track so unavailable quality cannot silently appear as Auto.
- Cross-provider failover must preserve the requested policy, language, episode identity, and playback checkpoint. If the backup lacks the requested fixed height, display an explicit fallback/unavailable state and ask before changing the persisted policy.

### Quality regression tests

1. Three AniLight adaptive endpoints produce one Auto menu plus the union of actual heights, never `Auto - MISA/NEAR/MISORA`.
2. Selecting each fixed height is asserted against Media3 `Tracks`/active `Format.height`.
3. Next episode, process-state recreation, and AniLight endpoint failover reapply the same `QualityPolicy`.
4. Anikoto `EmbedOnly` is never inserted into the native Media3 quality selector.
5. If Anikoto later supplies qualified native sources, its renditions pass through the same normalized model; no provider-specific quality UI is allowed.

## P-06 — State ownership and error UX

Create one immutable `PlayerUiState` containing episode generation, source selection, position checkpoint, buffering/error state, speed, subtitle policy, and controls-relevant capabilities. Keep ExoPlayer callbacks translated to ViewModel intents. Replace nullable plus `!!` paths with typed states and render actions appropriate to `NoSources`, `Network`, `Unsupported`, and `AllSourcesFailed`.

## Verification matrix

| Scenario | Required assertion |
|---|---|
| Pause after async episode load | correct episode saved |
| Switch episode then exit | old episode not overwritten |
| Drag 30 seconds | exactly one final seek |
| First transient timeout | same endpoint retried |
| Mid-play 403 | re-resolution attempted, position retained |
| Decoder error | no blind server rotation |
| Headphones unplug | playback pauses |
| Home/lock | behavior matches selected lifecycle contract |
| TV D-pad | seek and all controls reachable |
| Source switch | headers and language remain source-correct |

## Commit boundaries

1. stale-state fix + regression test
2. scrub behavior + accessibility targets
3. lifecycle contract
4. failover controller
5. Media3 tuning and subtitle MIME fixes

Do not combine these with theme or repository architecture migrations.
