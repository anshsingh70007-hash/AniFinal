package com.example.aniflow.ui.redesign.theme

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassSurface(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    isFocused: Boolean = false
): Modifier = this.composed {
    val borderBrush = remember(isFocused) {
        if (isFocused) {
            Brush.linearGradient(
                colors = listOf(GlassTokens.GlowCyan, GlassTokens.GlowPurple)
            )
        } else {
            Brush.linearGradient(
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
        .border(borderWidth, borderBrush, shape)
}

fun Modifier.focusGlow(
    isFocused: Boolean,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    focusedScale: Float = 1.08f
): Modifier = this.composed {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessLow
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
                
                // Draw a thick blurred accent border behind for premium glowing effect
                drawOutline(
                    outline = outline,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassTokens.GlowCyan.copy(alpha = glowAlpha),
                            GlassTokens.GlowPurple.copy(alpha = glowAlpha * 0.5f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
}

fun Modifier.filmGrainOverlay(
    grainOpacity: Float = 0.04f
): Modifier = this.composed {
    val infiniteTransition = rememberInfiniteTransition(label = "GrainTransition")
    val frameState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GrainFrame"
    )

    this.drawWithContent {
        drawContent()
        
        // Procedural film grain noise overlay without external bitmap
        // Using points drawn pseudo-randomly to give dynamic cinema noise texture
        val rand = java.util.Random((frameState.value * 1000).toLong())
        val width = size.width
        val height = size.height
        
        if (width > 0 && height > 0) {
            val pointCount = (width * height * 0.0005f).toInt().coerceIn(100, 3000)
            val points = List(pointCount) {
                androidx.compose.ui.geometry.Offset(rand.nextFloat() * width, rand.nextFloat() * height)
            }
            
            drawPoints(
                points = points,
                pointMode = PointMode.Points,
                color = Color.White.copy(alpha = grainOpacity),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
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

