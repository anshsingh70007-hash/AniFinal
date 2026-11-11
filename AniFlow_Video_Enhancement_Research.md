# Video enhancement: anime colour grade, sharpening, "4K" (research, 2026-11-11)

Status 2026-11-11: §1 Anime colour is shipped in 1.9.3 (default on phone, off on TV; see Session4 "Fourth pass"). §2 is not built. §3 stands.

## What we actually play (measured, not assumed)

Probed MegaPlay's player page (the page AniFlow embeds) in a browser, on Gintama° E1:

| Fact | Value | Why it matters |
|---|---|---|
| Source resolution | **1920×1080**, one quality level only ("1080p") | There is no higher source to pick. |
| Where the `<video>` lives | top document of the page (already used by `SNAPSHOT_JS` in `ui/player/EmbedPlayer.kt:114`) | We can style it from our injected JS. |
| Video source | MSE `blob:` (hls.js) | Frames are **not** cross-origin tainted. |
| Frames readable by canvas/WebGL | **yes** (`drawImage` + `getImageData` succeeded), WebGL2 available | A per-frame GPU shader (sharpening, upscaling) is technically possible inside the page. |

## 1. Anime colour grade: feasible, cheap, recommended

Anime is not graded like live action. Flat colour fills, clean line art and 8-bit, limited-range web encodes mean:
- Big saturation or contrast pushes cause **banding** in flat fills and gradients (skies, fades). Banding hits "the flat color fills of animation" first ([forasoft](https://www.forasoft.com/learn/video-quality/articles-vqm/banding-gradient-steps), [unifab](https://unifab.ai/resource/what-is-color-banding.htm)).
- Most "washed out" anime is a range mismatch, and a black-level stretch is a stretch applied on top of correct data, not a repair ([Doom9](https://forum.doom9.org/archive/index.php/t-183382.html)). Chromium decodes BT.709 limited range correctly, so we must not crush blacks again.
- Skin tones and whites must stay neutral: anime palettes are deliberate.

So the grade is gentle: **contrast 1.06, saturation 1.12, brightness 1.01** (blacks a touch deeper, whites a touch cleaner, colours livelier, no hue shift).

**How:** CSS `filter: contrast() saturate() brightness()` on the `<video>` from our injected JS. Chromium collapses those into one colour matrix applied on the GPU compositor quad, essentially free ([Chromium filter design](https://www.chromium.org/developers/design-documents/image-filters/), [grida analysis](https://grida.co/docs/wg/research/chromium/effect-optimizations)). Works the same on phone and TV. It touches only the picture, never our controls.

**Risk:** close to none. It's one CSS property, it's reversible, and there is no per-frame work. A preview on a real Gintama° frame was rendered: the difference is visible but not cartoonish.

## 2. Sharpening ("sharpness"): feasible, but needs a real-device test

CSS has no sharpen. The SVG `feConvolveMatrix` route renders the element to a buffer and runs a filter graph each frame. That's the slow path, so it's not suitable for 24 fps 1080p video on TV boxes.

The right tool is a **WebGL pass of AMD FidelityFX CAS** (Contrast Adaptive Sharpening, MIT licence). It sharpens line art without ringing and is used in anime mpv setups ([mpv configs](https://github.com/Xurdejl/mpv-config)). Each frame is drawn from the `<video>` into a canvas over it via `requestVideoFrameCallback`. Cost: one 1080p texture upload and one shader pass per frame. That's trivial on phones. On low-end TV boxes (Mali-G31/G52 class) it's unknown until measured. If the device can't keep up it must switch itself off, never stutter.

Proposal: an optional "Sharpen lines" toggle. Ship it on phones first. Turn it on for TV only after testing on a real TV box.

## 3. "4K" quality option: not possible honestly

| Reason | Evidence |
|---|---|
| The source is 1080p, one level. Upscaling invents pixels; it can't recover 4K detail. | probe above |
| On Android TV, app UI (and a WebView is UI) is rendered at max 1080p and the TV scales it up. WebViews often get an even smaller 960×540 CSS viewport. Only a native `SurfaceView` reaches 4K. | [Ionic forum](https://forum.ionicframework.com/t/android-tv-webview-resolution-seems-to-be-1-2-hd-960x540-for-all-devices-hd-4k-8k/246839), [Sony community](https://community.sony.rs/t5/android-tv/sony-4k-bravia-tv-reports-1080p-resolution-to-apps/td-p/2251639/page/3), [Chromium webview-dev](https://groups.google.com/a/chromium.org/g/android-webview-dev/c/3IJk6sOnoEQ) |
| Our playback is the embed (native playback is down for every title since Nov 2026). Even a perfect 4K upscale inside the page would be squeezed back to 1080p before the screen. | `ui/player/PlayerViewModel.kt` embed comment |
| Phones: screens are ~1080–1440 px wide. A 4K frame is downscaled again. | n/a |
| Real anime upscalers (Anime4K) are cheap on desktop GPUs (~1–3 ms on a Vega 64), but no source gives mobile timings ([Anime4K](https://github.com/bloc97/Anime4K), [Anime4K-WebGPU](https://cdn.jsdelivr.net/npm/anime4k-webgpu@1.0.0/README.md), [WebSR](https://github.com/sb2702/websr)). TV boxes are far weaker. | n/a |

A "4K" button would be a label on a 1080p picture. That's the kind of lie the audit removed.

**What is honest:** "Enhanced" = colour grade + CAS line sharpening at the screen's real resolution. That's the visible part of what an upscaler gives you on these screens. Anime4K-style line restoration could come later, as an experiment behind the same toggle, if CAS proves cheap enough on TV.

**When 4K becomes real:** only if a native (ExoPlayer) source comes back. Then video goes through a `SurfaceView` at native resolution and Media3's GL effects pipeline can upscale and grade on the GPU. That's tied to the P5 player work, not this.

## Proposed build order

1. **Anime colour** (default ON, toggle in Settings and in the player's quality sheet; phone and TV identical). Small change in `EmbedPlayer.kt` plus one setting.
2. **Sharpen lines** (CAS WebGL; phone first, auto-off when frames drop; TV after a real-device check).
3. No "4K" option.
