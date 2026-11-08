# AniFlow TV & Mobile Interaction Design Guide

This guide describes the behavioral contracts, remote-control key event handling, and touch-screen ergonomics for AniFlow on both TV and Phone platforms.

---

## 1. Android TV Remote Control (10-Foot Experience)

### The Hardware Reality
A standard Android TV user sits 8–12 feet away holding a minimalist remote:
- Directional pad: **UP, DOWN, LEFT, RIGHT**
- Action button: **CENTER / SELECT (DPAD_CENTER)**
- System keys: **BACK, HOME, PLAY/PAUSE**
- *There is no mouse, no touch, and no cursor.*

### Critical TV Rules

#### 1. Always Keep a Focused Element Visible
- At all times, exactly **one** interactive item on screen must have active focus.
- When navigating with UP/DOWN/LEFT/RIGHT, the focus border / glow must clearly indicate where the user is.
- Use `Modifier.focusGlow(isFocused, shape, focusedScale)` from `ui.redesign.theme.GlassModifiers.kt`.

#### 2. Dialog & Modal Focus Trapping (Rule 2)
When any modal or overlay appears (e.g., Update Takeover, Quality Selector, Subtitle Selector, Server Switcher):
- The entire background screen must be disabled from focus:
  ```kotlin
  Modifier.focusProperties { canFocus = false }
  ```
- The dialog container must have a `FocusRequester` attached to its first/primary action button.
- In a `LaunchedEffect`, trigger:
  ```kotlin
  LaunchedEffect(visible) {
      if (visible && deviceType == DeviceType.TV) {
          try {
              delay(50)
              focusRequester.requestFocus()
          } catch (e: Exception) {
              // ignore
          }
      }
  }
  ```
- D-pad navigation MUST be trapped inside the modal until the user clicks an option or presses BACK to dismiss.

#### 3. Video Player Remote Controls
- **When controls are hidden**:
  - Pressing `DPAD_LEFT`: Seeks backward 10 seconds, shows temporary rewind indicator ("-10s"), and reveals controls.
  - Pressing `DPAD_RIGHT`: Seeks forward 10 seconds, shows temporary forward indicator ("+10s"), and reveals controls.
  - Pressing `DPAD_CENTER` / `ENTER` / `UP` / `DOWN`: Reveals controls and requests focus on the central Play/Pause button.
- **When controls are visible**:
  - `DPAD_LEFT` / `DPAD_RIGHT` moves focus across the control row: `[Prev Ep] [-10s] [Play/Pause] [+10s] [Next Ep] [Quality] [Subtitles] [Audio]`.
  - Pressing `DPAD_UP` shifts focus up to the seek progress bar.
  - Pressing `BACK` hides the controls without exiting the player.
  - Pressing `BACK` when controls are already hidden navigates back to the Detail screen.
- **Auto-Hide Reset**:
  - Controls auto-hide after 5 seconds of **inactivity**.
  - Any key event (D-pad move, center click) resets the 5-second countdown timer.
  - Controls must **never** auto-hide while a selector dialog (Quality, Subtitles, Servers) is open!

#### 4. Search on TV
- Entering long text with a D-pad remote on an on-screen keyboard is painful.
- Provide the **A–Z Alphabetical Filter Bar** in `RedesignTvBrowseScreen.kt`.
- Clicking a letter immediately queries anime starting with that letter.

#### 5. Screen Wakefulness
- In `PlayerScreen.kt`, the video surface (`PlayerView`) must have:
  ```kotlin
  PlayerView(ctx).apply {
      keepScreenOn = true
      ...
  }
  ```
  This prevents the TV ambient screensaver from waking up during an episode.

---

## 2. Android Phone (Touchscreen Experience)

### The Hardware Reality
A mobile user holds the device in one hand or two hands, 12–18 inches from their face:
- Primary inputs: **Taps, Double-Taps, Swipes, Long-Presses**
- Dynamic orientation: Portrait for browsing, Landscape for watching.

### Critical Mobile Rules

#### 1. Minimum Touch Targets (48dp Rule)
- Every interactive button, chip, and icon must have a clickable bounds of at least `48.dp x 48.dp`.
- Filter chips should have comfortable padding (`horizontal = 14.dp, vertical = 8.dp`).

#### 2. Video Player Touch Gestures
- **Single Tap**: Toggles playback controls visibility (`fadeIn` / `fadeOut`).
- **Double Tap Left Half**: Seeks backward 10 seconds with animated rewind pill (`-10s`).
- **Double Tap Right Half**: Seeks forward 10 seconds with animated forward pill (`+10s`).
- **Center Controls**: Quick Prev/Play/Next buttons in the center of the screen.
- **Orientation**: Automatically request `SCREEN_ORIENTATION_USER_LANDSCAPE` when entering Player, and restore `SCREEN_ORIENTATION_USER` when exiting.

#### 3. Back Navigation Flow
- Sub-tabs (Browse, Library, Settings) -> pressing BACK returns to Home tab (Tab 0).
- Home tab -> pressing BACK triggers "Press back again to exit" toast with a 2-second timeout window.
- Player -> pressing BACK when controls are visible hides controls; when hidden, exits player.

---

## 3. Form-Factor Comparison Matrix

| Component | Phone (Touch) | TV (Remote) |
|---|---|---|
| **Top Navigation** | Hidden / Minimal search | `TvTopNavBar` with Home, Browse, Library, Settings |
| **Bottom Navigation**| Capsule bottom bar with floating glass | Not present (TV uses Top Bar) |
| **Grid Spacing** | 2-3 columns, 10-12dp gap | 4-6 columns adaptive (140dp items), 20dp gap |
| **Hero Spotlight** | 260dp height, swipeable banner | 380dp height, background trailer player, overscan margin |
| **Player Scrubbing**| Draggable progress slider | Discrete steps ($\pm 10$s) via D-pad or Seek buttons |
| **Subtitles / Quality**| Direct tap opens modal | D-pad navigation with initial focus request |
