package com.example.aniflow.ui.phone.motion

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * The phone motion system from the approved motion board (canvas "Motion & haptics board").
 * Three real springs plus three plain curves; nothing else on the phone animates on its own timing.
 * Stiffness = (2π / response)² with mass 1.
 */
object PhoneMotion {
    /** Pops: a lifted section, toggles, release after a press. ζ 0.62, response 0.5 s, ~8% overshoot. */
    fun <T> spring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = 158f)
    /** Tab pill, tab bar shrinking, scrubber knob. ζ 0.86, response 0.38 s. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 273f)
    /** Sections making way, content shifting. ζ 1.0, response 0.5 s, never bounces. */
    fun <T> smooth(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 158f)

    val GlideEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val ArriveEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Screens and sheets. */
    fun <T> glide(ms: Int = 600): FiniteAnimationSpec<T> = tween(ms, easing = GlideEasing)
    /** Toasts and menus entering. */
    fun <T> arrive(ms: Int = 400): FiniteAnimationSpec<T> = tween(ms, easing = ArriveEasing)
    /** The moment a finger lands. */
    fun <T> press(): FiniteAnimationSpec<T> = tween(110, easing = LinearOutSlowInEasing)
    /** Colour and fades. */
    fun <T> fade(ms: Int = 240): FiniteAnimationSpec<T> = tween(ms)

    /** Apple's rubber band: the further you pull, the less it moves. */
    fun rubberBand(x: Float, dim: Float, c: Float = 0.55f): Float {
        if (dim <= 0f) return 0f
        val sign = if (x < 0) -1f else 1f
        val ax = kotlin.math.abs(x)
        return sign * (1f - 1f / (ax * c / dim + 1f)) * dim
    }
}

/** True when the person has turned animations off (Settings › Accessibility › Remove animations). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * One crisp haptic per event, with the board's fallbacks for older Android versions.
 * Uses View.performHapticFeedback, so it follows the system "Touch feedback" setting.
 */
class Haptics(private val view: View) {
    private fun fire(constant: Int) {
        runCatching { view.performHapticFeedback(constant) }
    }

    /** Skip ±10 s; lightest tick. */
    fun clockTick() = fire(HapticFeedbackConstants.CLOCK_TICK)

    /** Each card while swiping the banner. */
    fun frequentTick() = fire(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    )

    /** Segmented control; a moving section passing another; Move up / Move down. */
    fun segmentTick() = fire(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    )

    /** A section lifting to move. */
    fun longPress() = fire(HapticFeedbackConstants.LONG_PRESS)

    /** Grabbing a sheet. */
    fun gestureStart() {
        if (Build.VERSION.SDK_INT >= 30) fire(HapticFeedbackConstants.GESTURE_START)
    }

    /** Letting go of a sheet; dropping a moved section. */
    fun gestureEnd() {
        if (Build.VERSION.SDK_INT >= 30) fire(HapticFeedbackConstants.GESTURE_END)
    }

    fun confirm() = fire(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/** Press-down scale: 96% for buttons, 97.5% for big cards; springs back on release. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed) pressed else 1f,
        if (isPressed) PhoneMotion.press() else PhoneMotion.spring(),
        label = "press"
    )
    return graphicsLayer { scaleX = s; scaleY = s }
}
