package com.example.aniflow.ui.brand

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.example.aniflow.DeviceDetector
import com.example.aniflow.DeviceType
import com.example.aniflow.ui.onboarding.FirstRun
import com.example.aniflow.ui.onboarding.FirstRunState
import com.example.aniflow.ui.sound.Sfx
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * AniFlow identity: the arch A.
 *
 *  - The mark is a rounded arch "A" whose counter is a play button cut out of it (negative space),
 *    with the right foot flicked outward like a brush stroke ending. Violet into pink, flat.
 *  - The wordmark is custom geometric monoline lowercase built from strokes and arcs (no font);
 *    the i's dot is a small orchid tile.
 *
 * res/drawable/ic_launcher_mark_*.xml and banner_aniflow.xml use the same path data, so the
 * launcher icon, the TV banner and the in-app animation match exactly.
 */
object AniFlowBrand {
    val Night = Color(0xFF07050C)
    val Stage = Color(0xFF160A26)     // centre of the launch backdrop
    val Paper = Color(0xFFF3ECFF)     // wordmark ink
    val Orchid = Color(0xFFC77DFF)
    val Violet = Color(0xFF6A3CF0)
    val Pink = Color(0xFFFF6FA5)
    val Magenta = Color(0xFFD94BB8)   // the inverted flash

    // ---- mark, in a 100 x 100 box --------------------------------------------------------------
    const val OUTER = "M12 92 C12 46 30 8 50 8 C70 8 88 46 88 80 Q88.6 90.5 97 92 L62 92 C62 75 57 62 50 62 C43 62 38 75 38 92 Z"
    const val TRI = "M44.6 29.6 L58.9 38.6 Q61 40 58.9 41.4 L44.6 50.4 Q42.4 51.8 42.4 49.2 L42.4 30.8 Q42.4 28.2 44.6 29.6 Z"
    /** Centre of the play counter: the iris opens from here. */
    val COUNTER = Offset(48.6f, 40f)
    /** Optical centre of the A (the flicked foot pulls it right of 50). */
    val MARK_CENTRE = Offset(54.5f, 50f)
    val GRADIENT = arrayOf(0f to Violet, 0.46f to Color(0xFF9147E4), 0.8f to Color(0xFFD650C6), 1f to Pink)
    val GRAD_START = Offset(18.8f, 9.7f)
    val GRAD_END = Offset(90.2f, 92f)

    // ---- wordmark "aniflow", stroke 9, x-height 14..60, ascender -10, total 268 x 70 ------------
    val WORD_GLYPHS = listOf(
        "M39.5,14 V60 M39.5,37 A18.5,18.5 0 1 1 2.5,37 A18.5,18.5 0 1 1 39.5,37",            // a
        "M55.5,14 V60 M55.5,37 A18.5,18.5 0 0 1 92.5,37 V60",                                // n
        "M108.5,14 V60",                                                                      // ı
        "M130.5,60 V8.5 A14,14 0 0 1 144.5,-5.5 M120,18.5 H146",                             // f
        "M156.5,-10 V60",                                                                     // l
        "M172.5,37 A18.5,18.5 0 1 1 209.5,37 A18.5,18.5 0 1 1 172.5,37",                     // o
        "M225.5,14 V46 A9.5,9.5 0 0 0 244.5,46 V24 M244.5,46 A9.5,9.5 0 0 0 263.5,46 V14",   // w
    )
    const val WORD_STROKE_W = 9f
    const val DOT_L = 103f
    const val DOT_T = -6f
    const val DOT_SIZE = 11f
    const val WORD_W = 268f
    const val WORD_TOP = -10f
    const val WORD_H = 70f

    fun markBrush(k: Float, origin: Offset = Offset.Zero) = Brush.linearGradient(
        *GRADIENT, start = origin + GRAD_START * k, end = origin + GRAD_END * k,
    )
}

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

private val markPath: Path by lazy {
    parse(AniFlowBrand.OUTER + " " + AniFlowBrand.TRI).apply { fillType = PathFillType.EvenOdd }
}

/** Draws the finished mark into a [sizePx] square. */
fun DrawScope.drawAMark(sizePx: Float) {
    val k = sizePx / 100f
    scale(k, k, pivot = Offset.Zero) { drawPath(markPath, AniFlowBrand.markBrush(1f)) }
}

@Composable
fun AniFlowMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawAMark(this.size.width) }
}

private val wordGlyphs: List<Path> by lazy { AniFlowBrand.WORD_GLYPHS.map(::parse) }

/** Draws the wordmark with its top-left at the origin, [heightPx] tall. */
private fun DrawScope.drawWordmark(heightPx: Float, color: Color, dotColor: Color, reveal: Float = 1f, dot: Float = 1f) {
    val k = heightPx / AniFlowBrand.WORD_H
    scale(k, k, pivot = Offset.Zero) {
        translate(top = -AniFlowBrand.WORD_TOP) {
            // left < 0: the a's bowl overshoots x=0 by half a stroke (optical overshoot, on purpose).
            clipRect(left = -20f, top = -40f, bottom = 120f, right = AniFlowBrand.WORD_W * reveal.coerceIn(0f, 1f) + 6f) {
                val stroke = Stroke(AniFlowBrand.WORD_STROKE_W, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
                wordGlyphs.forEach { drawPath(it, color, style = stroke) }
            }
            if (dot > 0f) {
                drawRoundRect(
                    dotColor,
                    topLeft = Offset(AniFlowBrand.DOT_L, AniFlowBrand.DOT_T + (1f - dot) * -16f),
                    size = Size(AniFlowBrand.DOT_SIZE, AniFlowBrand.DOT_SIZE),
                    cornerRadius = CornerRadius(2.4f, 2.4f),
                    alpha = dot.coerceIn(0f, 1f),
                )
            }
        }
    }
}

/** Wordmark at [height]; [reveal] 0..1 wipes it in left-to-right; [dot] 0..1 drops the i-dot in. */
@Composable
fun AniFlowWordmark(
    height: Dp,
    modifier: Modifier = Modifier,
    reveal: Float = 1f,
    dot: Float = 1f,
    color: Color = AniFlowBrand.Paper,
) {
    val width = height * (AniFlowBrand.WORD_W / AniFlowBrand.WORD_H)
    Canvas(modifier.width(width).height(height)) {
        if (reveal > 0f) drawWordmark(size.height, color, AniFlowBrand.Orchid, reveal, dot)
    }
}

// ================================================================================================
// Launch
// ================================================================================================

/** Shown once per process. */
private var launchShown = false

private const val PREFS = "aniflow_brand"
private const val KEY_INTRO_SEEN = "introSeen"
private const val KEY_FIRST_RUN_DONE = "firstRunDone"

private enum class LaunchStage { Intro, FirstRun, Done }

/**
 * App open. Everything underneath composes straight away so Home is loaded by the time this lifts.
 *
 *  - Every launch: the ED-style logo intro (~3.3 s first time, ~1.5 s after that; any key or tap
 *    skips). Phones and returning TV users iris out of the play counter into the app.
 *  - First launch after a fresh install (TV or phone): the intro hard-cuts to black, then [FirstRun]
 *    plays the welcome clip
 *    and offers sign-up / log-in / continue.
 *
 * The overlays never take focus (TV focus must already sit on Home when they lift), so keys are
 * intercepted here on the way down and routed by stage.
 */
@Composable
fun AniFlowLaunch(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    // Only for a fresh install, so people updating the app don't suddenly get the welcome clip.
    val firstRunWanted = remember {
        !launchShown && !prefs.getBoolean(KEY_FIRST_RUN_DONE, false) && runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        }.getOrDefault(false)
    }
    val fast = remember { prefs.getBoolean(KEY_INTRO_SEEN, false) }
    var stage by remember { mutableStateOf(if (launchShown) LaunchStage.Done else LaunchStage.Intro) }
    val firstRun = remember { if (firstRunWanted) FirstRunState(isTv = DeviceDetector.detect(context) == DeviceType.TV) else null }

    val endIntro = {
        if (stage == LaunchStage.Intro) {
            launchShown = true
            prefs.edit().putBoolean(KEY_INTRO_SEEN, true).apply()
            stage = if (firstRun != null) LaunchStage.FirstRun else LaunchStage.Done
        }
    }
    val endFirstRun = {
        prefs.edit().putBoolean(KEY_FIRST_RUN_DONE, true).apply()
        stage = LaunchStage.Done
    }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            when (stage) {
                LaunchStage.Intro -> { if (e.type == KeyEventType.KeyDown) endIntro(); true }
                LaunchStage.FirstRun -> { firstRun?.onKey(e); true }
                LaunchStage.Done -> false
            }
        }
    ) {
        content()
        if (firstRun != null && stage != LaunchStage.Done) {
            FirstRun(firstRun, started = stage == LaunchStage.FirstRun, onDone = endFirstRun)
        }
        if (stage == LaunchStage.Intro) {
            LaunchSequence(fast = fast, irisExit = firstRun == null, onDone = endIntro)
        }
    }
}

// ---- timeline, in ms at 1x ---------------------------------------------------------------------
private const val SLASH_IN = 60f
private const val SLASH_OUT = 310f
private const val DRAW_IN = 300f
private const val DRAW_OUT = 900f
private const val INVERT_OUT = 1000f
private const val FLOOD_OUT = 1560f
private const val CUT = 1600f
private const val BURST_IN = 1640f
private const val BURST_OUT = 2300f
private const val SLAM = 1840f
private const val EXIT = 2750f
private const val END_IRIS = 3300f
private const val END_CUT = 2900f
private const val ON_TWOS = 83.33f   // 12 drawings a second, like hand-drawn animation
private const val ON_ONES = 41.67f   // 24

private fun step(t: Float, every: Float) = floor(t / every) * every
private fun p01(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x).pow(3)
private fun easeIn(x: Float) = x * x * x

/** An outline resampled into evenly spaced points with their normals, so it can be made to boil. */
private class Contour(d: String, val n: Int) {
    val x = FloatArray(n); val y = FloatArray(n); val nx = FloatArray(n); val ny = FloatArray(n)
    private val cells = max(4, n / 11)
    init {
        val m = PathMeasure().apply { setPath(parse(d), false) }
        val len = m.length
        for (i in 0 until n) {
            val dist = len * i / n
            val p = m.getPosition(dist); val tg = m.getTangent(dist)
            x[i] = p.x; y[i] = p.y; nx[i] = -tg.y; ny[i] = tg.x
        }
    }

    private fun hash(i: Int, seed: Int): Float {
        var h = i * 374761393 + seed * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 32767.5f - 1f
    }

    /** Smooth periodic noise along the contour (no seam where it closes). */
    private fun noise(i: Int, seed: Int): Float {
        val f = i.toFloat() * cells / n
        val c0 = floor(f).toInt()
        val u = f - c0
        val s = u * u * (3f - 2f * u)
        val a = hash(c0 % cells, seed); val b = hash((c0 + 1) % cells, seed)
        return a + (b - a) * s + 0.25f * hash(i, seed + 7)
    }

    /** Writes the boiled outline into [out], in screen space, up to [upTo] of its length. */
    fun boil(out: Path, seed: Int, amp: Float, k: Float, o: Offset, upTo: Float = 1f, close: Boolean = true) {
        val last = (n * upTo.coerceIn(0f, 1f)).toInt()
        if (last < 2) return
        for (i in 0 until last) {
            val w = noise(i, seed) * amp
            val px = o.x + (x[i] + nx[i] * w) * k
            val py = o.y + (y[i] + ny[i] * w) * k
            if (i == 0) out.moveTo(px, py) else out.lineTo(px, py)
        }
        if (close && last >= n) out.close()
    }
}

/**
 * The intro, in the spirit of a TV-anime ending: hand-drawn and on twos, with hard cuts on the beat.
 *  1. a magenta brush slash tears across the frame;
 *  2. a boiling ink line draws the A;
 *  3. one inverted frame: magenta field, black silhouette;
 *  4. violet liquid floods up inside the A in steps;
 *  5. on the beat the play counter cuts out of it, with a white frame and a shake, and a halftone
 *     shockwave runs out from the counter;
 *  6. the wordmark slams in with a colour split; the mark boils slowly while it holds;
 *  7. out: the counter irises open onto the app (or, before the TV welcome clip, a cut to black).
 */
@Composable
private fun LaunchSequence(fast: Boolean, irisExit: Boolean, onDone: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    val speed = if (fast) 2.2f else 1f
    val end = if (irisExit) END_IRIS else END_CUT

    val outer = remember { Contour(AniFlowBrand.OUTER, 240) }
    val tri = remember { Contour(AniFlowBrand.TRI, 64) }
    val pA = remember { Path() }
    val pB = remember { Path() }
    val iris = remember { Path() }

    LaunchedEffect(Unit) {
        // The app composes underneath while this is up, and the first ~1-2 s of a cold start
        // (more on a 1 GB TV) stall the main thread. Hold on the plain dark frame until frames
        // arrive smoothly, so the animation never starts and then freezes half-way. Capped at 3 s.
        val start = withFrameNanos { it }
        var last = start
        var smooth = 0
        while (smooth < 4 && last - start < 3_000_000_000L) {
            val now = withFrameNanos { it }
            smooth = if (now - last < 34_000_000L) smooth + 1 else 0
            last = now
        }
        Sfx.launch()
        val t0 = withFrameNanos { it }
        while (t < end) {
            val now = withFrameNanos { it }
            t = (now - t0) / 1_000_000f * speed
        }
        onDone()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            // Not clickable(): that would make the overlay focusable on TV. Eat every pointer
            // event so nothing underneath scrolls; a tap skips.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                        if (e.type == PointerEventType.Release) onDone()
                    }
                }
            }
    ) {
        val now = t
        val markPx = min(size.minDimension * 0.30f, 168f * density)
        val k = markPx / 100f
        val wordH = markPx * 0.26f
        val gap = markPx * 0.2f
        val wordW = wordH * AniFlowBrand.WORD_W / AniFlowBrand.WORD_H
        val top = center.y - (markPx + gap + wordH) / 2f
        val shake = shake(now) * density
        // Box origin of the mark, so its optical centre sits on the vertical axis.
        val o = Offset(center.x - AniFlowBrand.MARK_CENTRE.x * k, top) + shake
        val counter = o + AniFlowBrand.COUNTER * k
        val u = density

        fun scene() {
            val inverted = now in DRAW_OUT..INVERT_OUT
            drawRect(if (inverted) AniFlowBrand.Magenta else AniFlowBrand.Night)
            if (now >= CUT) {
                drawRect(Brush.radialGradient(listOf(AniFlowBrand.Stage, AniFlowBrand.Night), center = counter, radius = size.maxDimension * 0.6f))
            }

            // 1. brush slash
            if (now in SLASH_IN..(SLASH_OUT + ON_TWOS)) drawSlash(p01(step(now, ON_TWOS), SLASH_IN, SLASH_OUT), now >= SLASH_OUT, pA)

            // 5b. halftone shockwave, behind the mark
            if (now in BURST_IN..BURST_OUT) drawBurst(counter, p01(step(now, ON_ONES), BURST_IN, BURST_OUT), u)

            val twos = (step(now, ON_TWOS) / ON_TWOS).toInt()
            when {
                // 2. the ink line draws the A
                now < DRAW_OUT -> if (now >= DRAW_IN) {
                    val p = easeOut(p01(step(now, ON_TWOS), DRAW_IN, DRAW_OUT - 120f))
                    pA.reset(); outer.boil(pA, twos, 1.1f, k, o, upTo = p / 0.78f, close = true)
                    pB.reset(); tri.boil(pB, twos + 31, 0.8f, k, o, upTo = (p - 0.72f) / 0.28f, close = true)
                    val ink = Stroke(2.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(pA, AniFlowBrand.Paper, style = ink)
                    drawPath(pB, AniFlowBrand.Paper, style = ink)
                }
                // 3. inverted frame: black silhouette on magenta, counter still filled
                now < INVERT_OUT -> {
                    pA.reset(); outer.boil(pA, twos, 1.2f, k, o)
                    drawPath(pA, Color.Black)
                }
                // 4. liquid floods up inside the A
                now < CUT -> {
                    pA.reset(); outer.boil(pA, twos, 1.0f, k, o)
                    val level = 1f - easeOut(p01(step(now, ON_TWOS), INVERT_OUT, FLOOD_OUT))
                    val surfaceY = o.y + (8f + 86f * level) * k
                    clipPath(pA) {
                        drawRect(Color(0xFF120A22))
                        if (level < 0.999f) {
                            pB.reset()
                            val phase = twos * 1.7f
                            pB.moveTo(o.x, surfaceY)
                            var x = 0f
                            while (x <= 100f) {
                                pB.lineTo(o.x + x * k, surfaceY + sin(x * 0.19f + phase) * 2.4f * k * (0.3f + level))
                                x += 4f
                            }
                            pB.lineTo(o.x + 100f * k, o.y + 100f * k); pB.lineTo(o.x, o.y + 100f * k); pB.close()
                            drawPath(pB, Brush.verticalGradient(
                                0f to Color(0xFF8E45E6), 1f to Color(0xFF3A1A8C),
                                startY = surfaceY, endY = o.y + 92f * k,
                            ))
                        }
                    }
                    drawPath(pA, AniFlowBrand.Paper, style = Stroke(2.2f * k, join = StrokeJoin.Round))
                }
                // 5-7. the finished mark, boiling slowly
                else -> {
                    val hold = now >= BURST_OUT
                    val seed = if (hold) (step(now, 125f) / 125f).toInt() else twos
                    val amp = if (hold) 0.45f else 0.7f
                    pA.reset(); pA.fillType = PathFillType.EvenOdd
                    outer.boil(pA, seed, amp, k, o); tri.boil(pA, seed + 31, amp * 0.6f, k, o)
                    val grow = if (irisExit && now > EXIT) 1f + 0.12f * easeIn(p01(now, EXIT, END_IRIS)) else 1f
                    scale(grow, grow, pivot = counter) { drawPath(pA, AniFlowBrand.markBrush(k, o)) }
                    pA.fillType = PathFillType.NonZero
                }
            }

            // 6. wordmark slam with a colour split
            if (now >= SLAM) {
                val dt = step(now - SLAM, ON_ONES)
                val pop = when { dt < ON_ONES -> 1.16f; dt < 2 * ON_ONES -> 1.05f; else -> 1f }
                val split = 7f * u * (1f - p01(dt, 0f, 280f))
                val wo = Offset(center.x - wordW / 2f, top + markPx + gap) + shake
                scale(pop, pop, pivot = wo + Offset(wordW / 2f, wordH / 2f)) {
                    if (split > 0.5f) {
                        translate(wo.x - split, wo.y) { drawWordmark(wordH, AniFlowBrand.Pink.copy(alpha = 0.85f), AniFlowBrand.Pink) }
                        translate(wo.x + split, wo.y) { drawWordmark(wordH, AniFlowBrand.Violet.copy(alpha = 0.85f), AniFlowBrand.Violet) }
                    }
                    translate(wo.x, wo.y) { drawWordmark(wordH, AniFlowBrand.Paper, AniFlowBrand.Orchid) }
                }
            }

            // 5a. one white frame on the cut
            if (now in CUT..(CUT + 50f)) drawRect(Color.White, alpha = if (now < CUT + 25f) 0.9f else 0.4f)
        }

        when {
            now >= EXIT && !irisExit -> drawRect(Color.Black)   // hard cut; the welcome clip follows
            now >= EXIT -> {
                val r = hypot(size.width, size.height) * easeIn(p01(now, EXIT, END_IRIS))
                iris.reset()
                iris.addOval(Rect(counter, r))
                clipPath(iris, ClipOp.Difference) { scene() }
            }
            else -> scene()
        }
    }
}

private val SHAKE_CUT = listOf(Offset(6f, -4f), Offset(-5f, 3f), Offset(3f, 2f))
private val SHAKE_SLAM = listOf(Offset(0f, 5f), Offset(0f, -3f))

private fun shake(t: Float): Offset {
    if (t >= CUT && t < CUT + 3 * ON_ONES) return SHAKE_CUT[((t - CUT) / ON_ONES).toInt().coerceIn(0, 2)]
    if (t >= SLAM && t < SLAM + 2 * ON_ONES) return SHAKE_SLAM[((t - SLAM) / ON_ONES).toInt().coerceIn(0, 1)]
    return Offset.Zero
}

/** A tapered brush stroke from bottom-left to top-right; [p] is how far it has torn across. */
private fun DrawScope.drawSlash(p: Float, fading: Boolean, path: Path) {
    if (p <= 0f) return
    val a = Offset(size.width * -0.05f, size.height * 0.78f)
    val b = Offset(size.width * 1.05f, size.height * 0.18f)
    val head = a + (b - a) * easeOut(p)
    val tail = a + (b - a) * (if (fading) 0.75f else max(0f, p - 0.55f))
    val dx = head.x - tail.x; val dy = head.y - tail.y
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val nx = -dy / len; val ny = dx / len
    val w = size.minDimension * if (fading) 0.018f else 0.05f
    path.reset()
    path.moveTo(tail.x, tail.y)
    path.quadraticTo((tail.x + head.x) / 2f + nx * w, (tail.y + head.y) / 2f + ny * w, head.x, head.y)
    path.quadraticTo((tail.x + head.x) / 2f - nx * w * 0.4f, (tail.y + head.y) / 2f - ny * w * 0.4f, tail.x, tail.y)
    path.close()
    drawPath(path, AniFlowBrand.Magenta)
}

private const val DOT_BUCKETS = 4

/** Halftone dots on a fixed grid, swelling where the expanding ring passes and fading behind it. */
private fun DrawScope.drawBurst(c: Offset, p: Float, u: Float) {
    val spacing = 15f * u
    val maxR = size.maxDimension * 0.6f
    val r = maxR * easeOut(p)
    val band = 90f * u
    val fade = 1f - 0.9f * p01(p, 0.55f, 1f)
    val buckets = Array(DOT_BUCKETS * 2) { ArrayList<Offset>() }
    val x0 = c.x - floor(c.x / spacing) * spacing
    val y0 = c.y - floor(c.y / spacing) * spacing
    var iy = 0
    var y = y0
    while (y < size.height) {
        var ix = 0
        var x = x0
        while (x < size.width) {
            val d = hypot(x - c.x, y - c.y)
            if (d <= r + band) {
                val s = (1f - abs(d - r) / band).coerceIn(0f, 1f) * (if (d < r) 0.55f + 0.45f * (d / max(r, 1f)) else 1f)
                if (s > 0.08f) {
                    val bucket = min(DOT_BUCKETS - 1, (s * DOT_BUCKETS).toInt())
                    buckets[bucket + if ((ix + iy) % 2 == 0) 0 else DOT_BUCKETS].add(Offset(x, y))
                }
            }
            x += spacing; ix++
        }
        y += spacing; iy++
    }
    for (i in buckets.indices) {
        val list = buckets[i]
        if (list.isEmpty()) continue
        val level = (i % DOT_BUCKETS + 1f) / DOT_BUCKETS
        val col = if (i < DOT_BUCKETS) Color(0xFF9147E4) else AniFlowBrand.Pink
        drawPoints(list, PointMode.Points, lerp(col, Color.White, 0.08f * level), strokeWidth = spacing * 0.78f * level, cap = StrokeCap.Round, alpha = 0.85f * fade)
    }
}
