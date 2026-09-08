package com.example.aniflow.ui.redesign.components

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.R
import com.example.aniflow.ui.redesign.theme.BleachTybwTokens
import com.example.aniflow.ui.redesign.audio.BleachBgmManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight immutable state for a single Reiatsu ember particle.
 * Calculated once and animated entirely in the Canvas draw loop with zero allocations.
 */
private class ReiatsuParticle(
    val initialX: Float,
    val speedY: Float,
    val wobbleFreq: Float,
    val wobbleAmp: Float,
    val baseRadius: Float,
    val baseAlpha: Float,
    val color: Color,
    val phase: Float
)

/**
 * Animated Reiatsu (Spiritual Pressure) particle engine.
 * Renders rising crimson and gold spirit motes that float upward with organic turbulence.
 * Operates purely on the GPU Canvas draw phase for 60fps with zero recomposition.
 */
@Composable
fun BleachReiatsuParticles(
    modifier: Modifier = Modifier,
    particleCount: Int = 30,
    active: Boolean = true
) {
    // Particle dots disabled per user aesthetic request for clean, non-distracting hero spotlight
}

/**
 * Animated razor blade reflection sweep across a surface.
 * Emulates the signature Zanpakuto blade sheen sweep across the spotlight card.
 * Operates purely on the GPU Canvas draw phase with zero recomposition.
 */
@Composable
fun BleachBladeSlashSheen(
    modifier: Modifier = Modifier,
    intervalMs: Int = 6000,
    active: Boolean = true
) {
    if (!active) return

    val transition = rememberInfiniteTransition(label = "bladeSlashSheen")
    val cycleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = intervalMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheenCycle"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Active sweep window: happens during the first 25% of the cycle
        if (cycleProgress > 0.25f) return@Canvas

        val p = cycleProgress / 0.25f // 0f..1f sweep
        val startX = -w * 0.4f + (w * 1.8f) * p
        val endX = startX + w * 0.35f

        val sheenBrush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.10f),
                BleachTybwTokens.BankaiGold.copy(alpha = 0.32f),
                Color.White.copy(alpha = 0.60f),
                BleachTybwTokens.BankaiGold.copy(alpha = 0.32f),
                BleachTybwTokens.ReiatsuCrimson.copy(alpha = 0.10f),
                Color.Transparent
            ),
            start = Offset(startX, 0f),
            end = Offset(endX, h)
        )

        drawRect(
            brush = sheenBrush,
            size = size
        )
    }
}

/**
 * Bleach TYBW Spiritual Energy Vortex.
 * Renders rising undulating Reiatsu energy tendrils, swirling spiritual arcs,
 * and integrated rising ember particles creating an authentic anime atmosphere.
 * Designed for 60fps on Android TV and mobile with zero recomposition.
 */
@Composable
fun BleachSpiritualVortex(
    modifier: Modifier = Modifier,
    tendrilCount: Int = 3,
    particleCount: Int = 35,
    active: Boolean = true
) {
    if (!active) return

    val transition = rememberInfiniteTransition(label = "spiritualGlowLoop")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(
                    BleachTybwTokens.ReiatsuCrimson.copy(alpha = pulse * 0.15f),
                    Color.Transparent
                ),
                radius = 700f
            )
        )
    )
}

/**
 * High-voltage pulsating Reiatsu & Bankai lighting border glow for Bleach TYBW cards.
 * Uses GPU graphicsLayer alpha modulation to guarantee ZERO recomposition.
 */
@Composable
fun BleachLightingBorderGlow(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    strokeWidth: Dp = 2.dp
) {
    val transition = rememberInfiniteTransition(label = "borderGlowPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val borderBrush = remember {
        Brush.sweepGradient(
            colors = listOf(
                BleachTybwTokens.ReiatsuCrimson,
                BleachTybwTokens.BankaiGold,
                BleachTybwTokens.QuincyReishiBlue,
                BleachTybwTokens.AizenReiatsuPurple,
                BleachTybwTokens.ReiatsuCrimson
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = pulseAlpha }
            .border(
                width = strokeWidth,
                brush = borderBrush,
                shape = shape
            )
    )
}

/**
 * Authentic Bleach TYBW Reiatsu & Quincy electric lightning discharge engine.
 * Generates fractal jagged branching lightning bolts with multi-layered glow,
 * high-energy core, fork branches, and realistic high-voltage discharge cadence.
 * Operates purely on the GPU Canvas draw phase for 60fps with zero recomposition.
 */
@Composable
fun BleachElectricLightning(
    modifier: Modifier = Modifier,
    boltCount: Int = 2,
    active: Boolean = true
) {
    if (!active) return

    val transition = rememberInfiniteTransition(label = "tybwLightningCycle")
    // Fast cycle (1.8s loop) for sharp electrical strike flashes and pauses
    val cycleTime by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleTime"
    )

    val isBgmActive = BleachBgmManager.isPlaying && !BleachBgmManager.isMuted
    val audioMultiplier = if (isBgmActive) 1.25f else 1.0f

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val t = cycleTime
        // Strike windows: 3 distinct flash strikes per 1.8s cycle (at ~120-300ms, ~720-900ms, ~1280-1460ms)
        val strikeIndex = when {
            t in 120f..300f -> 0
            t in 720f..900f -> 1
            t in 1280f..1460f -> 2
            else -> -1
        }
        if (strikeIndex < 0) return@Canvas // In active pause between discharges

        val strikeProgress = when (strikeIndex) {
            0 -> (t - 120f) / 180f
            1 -> (t - 720f) / 180f
            else -> (t - 1280f) / 180f
        }
        // Micro-strobe flicker: rapid intensity flutter during strike
        val strobe = (sin(strikeProgress * Math.PI * 8).toFloat() * 0.35f + 0.65f).coerceIn(0f, 1f)
        val flashAlpha = ((1f - strikeProgress) * strobe * audioMultiplier).coerceIn(0f, 1f)
        if (flashAlpha <= 0.05f) return@Canvas

        val rngSeed = (strikeIndex * 7919 + (t / 40f).toInt() * 31).toLong()

        // Deterministic linear congruential pseudo-random generator
        var seed = rngSeed
        fun nextRng(): Float {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            return (seed.toFloat() / 0x7fffffffL)
        }

        for (b in 0 until boltCount) {
            val isQuincy = (b + strikeIndex) % 2 == 0
            val bloomColor = if (isQuincy) BleachTybwTokens.QuincyReishiBlue else BleachTybwTokens.ReiatsuCrimson
            val midColor = if (isQuincy) BleachTybwTokens.AizenEnergyArc else BleachTybwTokens.BankaiGold
            val coreColor = if (isQuincy) BleachTybwTokens.QuincyWhite else Color(0xFFFFF5F5)

            // Dynamic anchor endpoints across perimeter or interior
            val side = (strikeIndex * 2 + b) % 4
            val start = when (side) {
                0 -> Offset(width * (0.15f + nextRng() * 0.7f), 0f)
                1 -> Offset(width, height * (0.15f + nextRng() * 0.7f))
                2 -> Offset(width * (0.15f + nextRng() * 0.7f), height)
                else -> Offset(0f, height * (0.15f + nextRng() * 0.7f))
            }
            val end = when ((side + 2) % 4) {
                0 -> Offset(width * (0.1f + nextRng() * 0.8f), height * 0.25f)
                1 -> Offset(width * 0.75f, height * (0.1f + nextRng() * 0.8f))
                2 -> Offset(width * (0.1f + nextRng() * 0.8f), height * 0.85f)
                else -> Offset(width * 0.25f, height * (0.1f + nextRng() * 0.8f))
            }

            // Build main jagged fractal bolt (14 segments)
            val segments = 14
            val mainPoints = ArrayList<Offset>(segments + 1)
            mainPoints.add(start)

            val dx = (end.x - start.x) / segments
            val dy = (end.y - start.y) / segments
            val perpX = -dy
            val perpY = dx
            val perpLen = kotlin.math.sqrt(perpX * perpX + perpY * perpY).coerceAtLeast(0.001f)
            val normPerpX = perpX / perpLen
            val normPerpY = perpY / perpLen

            val maxJitter = (width + height) * 0.055f

            for (s in 1 until segments) {
                val targetBaseX = start.x + dx * s
                val targetBaseY = start.y + dy * s
                val jitterMag = (nextRng() - 0.5f) * 2f * maxJitter
                val nodeX = (targetBaseX + normPerpX * jitterMag).coerceIn(2f, width - 2f)
                val nodeY = (targetBaseY + normPerpY * jitterMag).coerceIn(2f, height - 2f)
                mainPoints.add(Offset(nodeX, nodeY))
            }
            mainPoints.add(end)

            // Draw primary bolt path
            val mainPath = Path()
            mainPath.moveTo(mainPoints[0].x, mainPoints[0].y)
            for (pt in mainPoints) {
                mainPath.lineTo(pt.x, pt.y)
            }

            // Layer 1: Wide Outer Reiatsu Bloom Glow
            drawPath(
                path = mainPath,
                color = bloomColor.copy(alpha = 0.45f * flashAlpha),
                style = Stroke(width = 5.5.dp.toPx())
            )
            // Layer 2: Concentrated Saturated Mid-Arc
            drawPath(
                path = mainPath,
                color = midColor.copy(alpha = 0.80f * flashAlpha),
                style = Stroke(width = 2.6.dp.toPx())
            )
            // Layer 3: Blinding Intense White Core
            drawPath(
                path = mainPath,
                color = coreColor.copy(alpha = 0.98f * flashAlpha),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Branching Forks (subsidiary branching arcs splitting at 35-45 degrees)
            val branchIndex1 = 4 + (nextRng() * 4).toInt()
            if (branchIndex1 < mainPoints.size - 2) {
                val branchStart = mainPoints[branchIndex1]
                val branchPath = Path()
                branchPath.moveTo(branchStart.x, branchStart.y)
                var bCurr = branchStart
                val bLen = width * 0.14f
                val branchSegments = 5
                val branchAngle = (nextRng() - 0.5f) * 1.2f

                for (bs in 0 until branchSegments) {
                    val angle = (bs * 0.35f + branchAngle)
                    val bNextX = (bCurr.x + kotlin.math.cos(angle) * (bLen / branchSegments) + (nextRng() - 0.5f) * 12f).coerceIn(2f, width - 2f)
                    val bNextY = (bCurr.y + kotlin.math.sin(angle) * (bLen / branchSegments) + (nextRng() - 0.5f) * 12f).coerceIn(2f, height - 2f)
                    val bNext = Offset(bNextX, bNextY)
                    branchPath.lineTo(bNext.x, bNext.y)
                    bCurr = bNext
                }

                // Branch Bloom & Core
                drawPath(
                    path = branchPath,
                    color = bloomColor.copy(alpha = 0.35f * flashAlpha),
                    style = Stroke(width = 3.5.dp.toPx())
                )
                drawPath(
                    path = branchPath,
                    color = coreColor.copy(alpha = 0.90f * flashAlpha),
                    style = Stroke(width = 1.0.dp.toPx())
                )
            }

            // Sharp spark discharge nodes at vertex kinks
            for (idx in listOf(3, 7, 11)) {
                if (idx < mainPoints.size) {
                    val p = mainPoints[idx]
                    drawCircle(
                        color = coreColor.copy(alpha = 0.85f * flashAlpha),
                        radius = 2.2.dp.toPx(),
                        center = p
                    )
                    drawCircle(
                        color = bloomColor.copy(alpha = 0.40f * flashAlpha),
                        radius = 5.0.dp.toPx(),
                        center = p
                    )
                }
            }
        }
    }
}

/**
 * Stylized Bleach TYBW Tribute Header Badge with authentic Substitute Shinigami combat pass skull
 * and crisp dual-language typography rendering authentic Japanese kanji with high contrast.
 */
@Composable
fun BleachTributePill(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        BleachTybwTokens.ReiatsuDark.copy(alpha = 0.95f),
                        BleachTybwTokens.ReiatsuSurface.copy(alpha = 0.95f)
                    )
                ),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        BleachTybwTokens.ReiatsuCrimson,
                        BleachTybwTokens.BankaiGold,
                        BleachTybwTokens.QuincyReishiBlue
                    )
                ),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.bleach_shinigami_skull_crimson),
            contentDescription = "Substitute Shinigami Skull",
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = "BLEACH • ",
            color = BleachTybwTokens.QuincyWhite,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = BleachTybwTokens.BleachFontFamily,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "千年血戦篇 禍進譚",
            color = BleachTybwTokens.BankaiGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Text(
            text = " • THE CALAMITY",
            color = BleachTybwTokens.ReiatsuBlood,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = BleachTybwTokens.BleachFontFamily,
            letterSpacing = 1.2.sp
        )
    }
}

/**
 * Premium Bankai Action Button exclusively for Bleach: TYBW.
 * Displays "卍解 START BANKAI" with electric lighting aura, dynamic golden shimmering border,
 * tactile spring physics, and high-voltage pulsing spiritual pressure glow.
 */
@Composable
fun BleachBankaiButton(
    text: String = "卍解 START BANKAI",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
    enabled: Boolean = true,
    isFocusedOverride: Boolean? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val effectiveFocused = isFocusedOverride ?: isFocused
    val scale by animateFloatAsState(
        targetValue = if (effectiveFocused) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bankaiBtnScale"
    )

    val transition = rememberInfiniteTransition(label = "bankaiBtnShimmer")
    val shimmerShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bankaiShimmerShift"
    )

    val btnBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                BleachTybwTokens.ReiatsuCrimson,
                BleachTybwTokens.ReiatsuBlood,
                BleachTybwTokens.ReiatsuSurface,
                BleachTybwTokens.ReiatsuCrimson
            )
        )
    }

    val baseModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(RoundedCornerShape(10.dp))
        .background(btnBrush)
        .drawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            if (w > 0f && h > 0f) {
                val strokeW = (if (effectiveFocused) 2.5f else 1.5f).dp.toPx()
                val shift = shimmerShift
                val borderBrush = Brush.sweepGradient(
                    colors = listOf(
                        BleachTybwTokens.BankaiGold,
                        BleachTybwTokens.ReiatsuCrimson,
                        BleachTybwTokens.QuincyReishiBlue,
                        BleachTybwTokens.BankaiGold
                    ),
                    center = Offset(w * shift, h * 0.5f)
                )
                drawRoundRect(
                    brush = borderBrush,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    style = Stroke(width = strokeW)
                )
            }
        }

    val boxModifier = if (enabled) {
        baseModifier
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = if (isTv) 24.dp else 18.dp, vertical = if (isTv) 12.dp else 9.dp)
    } else {
        baseModifier
            .padding(horizontal = if (isTv) 24.dp else 18.dp, vertical = if (isTv) 12.dp else 9.dp)
    }

    Box(
        modifier = boxModifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.bleach_shinigami_skull_white),
                contentDescription = null,
                modifier = Modifier.size(if (isTv) 18.dp else 15.dp)
            )
            if (text.startsWith("卍解")) {
                Text(
                    text = "卍解",
                    color = BleachTybwTokens.BankaiGold,
                    fontSize = if (isTv) 15.sp else 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = text.removePrefix("卍解").trim(),
                    color = Color.White,
                    fontSize = if (isTv) 13.sp else 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = BleachTybwTokens.BleachFontFamily,
                    letterSpacing = 1.2.sp
                )
            } else {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = if (isTv) 14.sp else 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = BleachTybwTokens.BleachFontFamily,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/**
 * Sōsuke Aizen's God-Tier Spiritual Pressure (Reiatsu) Screen Shudder & Shockwave Host.
 * Periodically unleashes an overwhelming surge of Aizen's signature violet and obsidian
 * spiritual pressure that literally shakes the entire application layer.
 *
 * Implemented using purely hardware-accelerated GPU graphicsLayer transforms.
 * Reading surgeProgress ONLY inside the graphicsLayer and Canvas lambdas guarantees ZERO
 * root recomposition of the child content, preserving silky smooth 60fps playback and navigation.
 */
@Composable
fun AizenSpiritualPressureHost(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val context = LocalContext.current
    var isSurgeActive by remember { mutableStateOf(false) }
    val surgeProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(6_000L)
        while (isActive) {
            isSurgeActive = true
            surgeProgress.snapTo(0f)
            surgeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3200, easing = LinearEasing)
            )
            isSurgeActive = false

            // Repeat periodically every 35-50 seconds
            val nextDelay = Random.nextLong(35_000L, 50_000L)
            delay(nextDelay)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // App layout undergoing hardware-accelerated spatial tremor
        // Reads surgeProgress.value ONLY in graphicsLayer lambda: ZERO recompositions of content()!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isSurgeActive) {
                        val p = surgeProgress.value
                        val intensity = when {
                            p < 0.1f -> p / 0.1f
                            p < 0.75f -> 1f - (p - 0.1f) / 0.65f
                            else -> 0f
                        }
                        if (intensity > 0f) {
                            val t = p * 100f
                            translationX = (sin(t * 3.5f) * 8f * intensity).dp.toPx()
                            translationY = (cos(t * 4.2f) * 6f * intensity).dp.toPx()
                            rotationZ = sin(t * 2.8f) * 0.35f * intensity
                        }
                    }
                }
        ) {
            content()
        }

        // Full-screen Kurohitsugi (Black Coffin) & Aizen Reiatsu atmospheric overlay
        if (isSurgeActive) {
            AizenReiatsuVignetteOverlay(surgeProgress = surgeProgress)
        }
    }
}

/**
 * Isolated visual overlay for Aizen's spiritual pressure surge.
 * Evaluated exclusively in the draw phase, unfocusable so it never interferes with remote navigation.
 */
@Composable
private fun AizenReiatsuVignetteOverlay(
    surgeProgress: Animatable<Float, *>
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = surgeProgress.value
                    alpha = when {
                        p < 0.15f -> (p / 0.15f) * 0.8f
                        p < 0.65f -> 0.8f - ((p - 0.15f) / 0.5f) * 0.35f
                        else -> (1f - p) * 0.85f
                    }.coerceIn(0f, 1f)
                }
        ) {
            val p = surgeProgress.value

            // Oppressive dark violet & abyss black vignette
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        BleachTybwTokens.AizenKurohitsugi.copy(alpha = 0.45f),
                        BleachTybwTokens.AizenReiatsuPurple.copy(alpha = 0.7f),
                        BleachTybwTokens.AizenAbyssBlack.copy(alpha = 0.92f)
                    ),
                    radius = size.maxDimension * 0.85f,
                    center = Offset(size.width * 0.5f, size.height * 0.45f)
                )
            )

            // Expanding gravitational shockwave ring
            val ringRadius = size.maxDimension * (p * 0.9f)
            val strokeW = 4.dp.toPx() + (sin(p * Math.PI.toFloat()) * 8.dp.toPx())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        BleachTybwTokens.AizenEnergyArc.copy(alpha = 0.75f),
                        BleachTybwTokens.AizenKyokaSuigetsu.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    radius = ringRadius + strokeW,
                    center = Offset(size.width * 0.5f, size.height * 0.45f)
                ),
                radius = ringRadius,
                center = Offset(size.width * 0.5f, size.height * 0.45f),
                style = Stroke(width = strokeW)
            )
        }

        // High-voltage Aizen spiritual lightning arcs
        BleachElectricLightning(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = surgeProgress.value
                    alpha = ((sin(p * Math.PI.toFloat()) * 1.5f).coerceIn(0f, 1f))
                },
            boltCount = 4
        )

        // Ancient Reiatsu Kanwa banner fading in briefly
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .graphicsLayer {
                    val p = surgeProgress.value
                    alpha = ((sin(p * Math.PI.toFloat()) * 1.4f).coerceIn(0f, 1f))
                }
                .background(
                    color = BleachTybwTokens.AizenAbyssBlack.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = BleachTybwTokens.AizenKyokaSuigetsu.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "「霊圧」",
                    color = BleachTybwTokens.AizenEnergyArc,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = BleachTybwTokens.BleachFontFamily
                )
                Text(
                    text = "SŌSUKE AIZEN • SPIRITUAL PRESSURE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    fontFamily = BleachTybwTokens.BleachFontFamily
                )
            }
        }
    }
}
