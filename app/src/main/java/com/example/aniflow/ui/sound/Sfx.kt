package com.example.aniflow.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.example.aniflow.R

/**
 * Koto navigation sounds.
 *
 * Every sample is one synthesised koto string (res/raw/sfx_*.wav, D5 root). Moving sideways walks
 * up the yo pentatonic scale (D E G A B D'), so browsing a rail plays a short melody instead of
 * ticking; moving between rows drops to softer low notes and restarts the phrase. Hitting the edge
 * of the UI gives a muted, damped string. Pitch comes from SoundPool playback rate, which is why
 * every rate stays inside SoundPool's 0.5–2.0 range.
 *
 * Fire-and-forget and fail-silent: a device without audio, a pool that failed to load, or a sound
 * that has not finished loading simply plays nothing.
 */
object Sfx {
    private const val PLUCK = 0
    private const val SELECT = 1
    private const val BACK = 2
    private const val EDGE = 3
    private const val TOGGLE = 4
    private const val LAUNCH = 5
    private val RES = intArrayOf(
        R.raw.sfx_pluck, R.raw.sfx_select, R.raw.sfx_back,
        R.raw.sfx_edge, R.raw.sfx_toggle, R.raw.sfx_launch,
    )
    private val VOLUME = floatArrayOf(0.35f, 0.45f, 0.35f, 0.30f, 0.45f, 0.60f)

    /** Yo pentatonic over one octave, as playback rates relative to D. */
    private val SCALE = floatArrayOf(1f, 1.1225f, 1.3348f, 1.4983f, 1.6818f, 2f)
    private const val ROW_DOWN = 0.7492f // A4
    private const val ROW_UP = 0.8409f   // B4

    @Volatile private var pool: SoundPool? = null
    private val ids = IntArray(RES.size)
    private val loaded = BooleanArray(RES.size)
    @Volatile private var launchRequestedAt = 0L

    /** User setting (Settings → Navigation sounds). */
    @Volatile var enabled = true

    /** Muted while video plays; a counter so overlapping screens don't unmute each other. */
    @Volatile private var muteDepth = 0

    private var walk = 0
    private var lastAt = 0L

    fun init(context: Context) {
        if (pool != null) return
        runCatching {
            val p = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // GAME rides the media stream, so the remote's volume keys control it.
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            p.setOnLoadCompleteListener { sp, sampleId, status ->
                val i = ids.indexOf(sampleId)
                if (i < 0 || status != 0) return@setOnLoadCompleteListener
                loaded[i] = true
                // The launch chime is requested during the splash, possibly before it decoded.
                if (i == LAUNCH && SystemClock.uptimeMillis() - launchRequestedAt < 700) {
                    launchRequestedAt = 0
                    if (audible()) sp.play(sampleId, VOLUME[LAUNCH], VOLUME[LAUNCH], 1, 0, 1f)
                }
            }
            // Launch first: it is the only one needed within the first second.
            for (i in intArrayOf(LAUNCH, PLUCK, SELECT, BACK, EDGE, TOGGLE)) {
                ids[i] = p.load(context.applicationContext, RES[i], 1)
            }
            pool = p
        }
    }

    private fun audible() = enabled && muteDepth == 0

    private fun play(i: Int, rate: Float = 1f, gain: Float = 1f) {
        if (!audible() || !loaded[i]) return
        val v = VOLUME[i] * gain
        runCatching { pool?.play(ids[i], v, v, 1, 0, rate.coerceIn(0.5f, 2f)) }
    }

    fun launch() {
        if (loaded[LAUNCH]) play(LAUNCH) else launchRequestedAt = SystemClock.uptimeMillis()
    }

    fun select() = play(SELECT)
    fun back() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt < 150) return // key path and navigation path can both report one press
        lastBackAt = now
        play(BACK)
    }
    private var lastBackAt = 0L

    fun edge() = play(EDGE)
    fun toggle(on: Boolean) = play(TOGGLE, rate = if (on) 1f else 0.8909f)

    /** A tap on phones: one step of the same walk, so tab switching sounds like the TV. */
    fun tap() = step(1, repeat = false)

    private fun step(direction: Int, repeat: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < if (repeat) 90 else 55) return
        lastAt = now
        walk = Math.floorMod(walk + direction, SCALE.size)
        play(PLUCK, SCALE[walk], if (repeat) 0.6f else 1f)
    }

    private fun row(down: Boolean, repeat: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < if (repeat) 90 else 55) return
        lastAt = now
        walk = 0
        play(PLUCK, if (down) ROW_DOWN else ROW_UP, if (repeat) 0.5f else 0.8f)
    }

    /** Called from Activity.dispatchKeyEvent with whether the UI consumed the event. */
    fun onKey(event: KeyEvent, handled: Boolean) {
        if (!audible()) return
        val repeat = event.repeatCount > 0
        when (event.action) {
            KeyEvent.ACTION_DOWN -> when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> when {
                    !handled -> if (!repeat) edge()
                    event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> step(1, repeat)
                    event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> step(-1, repeat)
                    else -> row(event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN, repeat)
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A ->
                    if (handled && !repeat) select()
            }
            KeyEvent.ACTION_UP ->
                if (event.keyCode == KeyEvent.KEYCODE_BACK && !event.isCanceled) back()
        }
    }

    /** Silences navigation sounds while the calling screen is in composition (the player). */
    @Composable
    fun MuteWhileShown() {
        DisposableEffect(Unit) {
            muteDepth++
            onDispose { muteDepth = (muteDepth - 1).coerceAtLeast(0) }
        }
    }
}
