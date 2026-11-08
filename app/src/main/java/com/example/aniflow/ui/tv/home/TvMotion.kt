package com.example.aniflow.ui.tv.home

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * The TV focus treatment: an off-white ring drawn [gap] outside the element, so the canvas shows
 * through as a dark gap between them. Drawn outside the bounds — put it before any clip.
 */
fun Modifier.tvFocusRing(alpha: () -> Float, radius: Dp, gap: Dp = 3.dp, width: Dp = 2.dp): Modifier = drawBehind {
    val a = alpha()
    if (a <= 0.01f) return@drawBehind
    val g = gap.toPx()
    val w = width.toPx()
    val o = g + w / 2f
    drawRoundRect(
        color = TvTokens.Focus.copy(alpha = 0.92f * a),
        topLeft = Offset(-o, -o),
        size = Size(size.width + 2 * o, size.height + 2 * o),
        cornerRadius = CornerRadius(radius.toPx() + o),
        style = Stroke(w),
    )
}

/** Left-to-right reveal: draws only the first [progress] of the width. */
fun Modifier.wipeReveal(progress: () -> Float): Modifier = drawWithContent {
    val p = progress()
    if (p >= 1f) drawContent() else clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
}

/** 0 -> 1 once per [key], after [delayMs]. Used for the staggered entrances. */
@Composable
fun rememberEntrance(key: Any?, durationMs: Int = 520, delayMs: Int = 0): Animatable<Float, *> {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(durationMs, delayMillis = delayMs, easing = TvEase)) }
    return a
}

val TvEase = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Skeleton pulse for loading placeholders. */
@Composable
fun rememberShimmer(): State<Float> =
    rememberInfiniteTransition(label = "shimmer").animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shimmerA"
    )

/**
 * Film grain: one static 128 px noise tile, repeated by a shader. Kills gradient banding on TV
 * panels for the cost of a single textured rect — no per-frame work, no AGSL.
 */
private val grainBrush: ShaderBrush by lazy {
    val n = 128
    val rnd = Random(7)
    val px = IntArray(n * n) {
        val v = rnd.nextInt(256)
        val a = 4 + rnd.nextInt(5)
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

fun Modifier.tvGrain(): Modifier = drawWithContent {
    drawContent()
    drawRect(grainBrush)
}
