package com.example.aniflow.ui.redesign.theme

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassSurface(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    isFocused: Boolean = false,
    showBorderUnfocused: Boolean = false,
    focusedBorderBrush: Brush? = null,
    unfocusedBorderBrush: Brush? = null
): Modifier = this.composed {
    val borderBrush = remember(isFocused, focusedBorderBrush, unfocusedBorderBrush) {
        if (isFocused) {
            focusedBorderBrush ?: Brush.linearGradient(
                colors = listOf(GlassTokens.GlowCyan, GlassTokens.GlowPurple)
            )
        } else {
            unfocusedBorderBrush ?: Brush.linearGradient(
                colors = listOf(GlassTokens.BorderHighlightStart, GlassTokens.BorderHighlightEnd)
            )
        }
    }
    
    val backgroundColor = remember(isFocused) {
        if (isFocused) {
            GlassTokens.GlassSurface.copy(alpha = 0.15f)
        } else {
            GlassTokens.GlassSurface
        }
    }

    this
        .clip(shape)
        .background(backgroundColor)
        .then(
            if (isFocused || showBorderUnfocused || unfocusedBorderBrush != null) {
                Modifier.border(borderWidth, borderBrush, shape)
            } else {
                Modifier
            }
        )
}

fun Modifier.focusGlow(
    isFocused: Boolean,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    focusedScale: Float = 1.08f,
    glowColors: List<Color>? = null
): Modifier = this.composed {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "FocusScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.6f else 0.0f,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "GlowAlpha"
    )

    this
        .scale(scale)
        .drawBehind {
            if (glowAlpha > 0f) {
                // Procedural Neon Glow Aura around the element
                val strokeWidth = 8.dp.toPx()
                val outline = shape.createOutline(size, layoutDirection, this)
                
                val colors = if (glowColors != null) {
                    glowColors.map { it.copy(alpha = it.alpha * glowAlpha) }
                } else {
                    listOf(
                        GlassTokens.GlowCyan.copy(alpha = glowAlpha),
                        GlassTokens.GlowPurple.copy(alpha = glowAlpha * 0.5f),
                        Color.Transparent
                    )
                }

                // Draw a thick blurred accent border behind for premium glowing effect
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(colors = colors),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
}

fun Modifier.darkGlassSurface(
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    borderWidth: Dp = 1.dp
): Modifier = this.composed {
    val borderBrush = remember {
        Brush.linearGradient(
            colors = listOf(GlassTokens.BorderHighlightStart, GlassTokens.BorderHighlightEnd)
        )
    }
    
    this
        .clip(shape)
        .background(Color(0xD90F0E17)) // Premium 85% opaque dark slate background
        .border(borderWidth, borderBrush, shape)
}

/**
 * Premium glass panel inspired by the visual reference designs.
 * Use for hero info overlays, floating nav bars, and featured cards.
 */
fun Modifier.premiumGlassPanel(
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    isFocused: Boolean = false,
    showTopHighlight: Boolean = true
): Modifier = this.composed {
    val borderBrush = remember(isFocused) {
        if (isFocused) {
            Brush.linearGradient(
                colors = listOf(GlassTokens.GlowCyan, GlassTokens.GlowPurple)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.03f)
                )
            )
        }
    }

    this
        .clip(shape)
        .background(GlassTokens.GlassThin)
        .border(borderWidth, borderBrush, shape)
        .then(
            if (showTopHighlight) {
                Modifier.drawWithContent {
                    drawContent()
                    // Top-edge highlight line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                GlassTokens.CardEdgeHighlight,
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            } else Modifier
        )
}

/**
 * Press spring scale animation for tactile touch response on mobile.
 */
fun Modifier.pressSpring(): Modifier = this.composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        tryAwaitRelease()
                    } finally {
                        isPressed = false
                    }
                }
            )
        }
}

