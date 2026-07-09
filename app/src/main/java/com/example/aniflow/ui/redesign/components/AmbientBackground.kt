package com.example.aniflow.ui.redesign.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.aniflow.theme.PrimaryDark
import com.example.aniflow.ui.redesign.theme.GlassTokens
import com.example.aniflow.ui.redesign.theme.filmGrainOverlay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AmbientGlowTransition")
    
    // Slow drifting animation values (different periods for organic movement)
    val time1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Blob1Time"
    )

    val time2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Blob2Time"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryDark)
            .drawBehind {
                val w = size.width
                val h = size.height
                
                if (w > 0 && h > 0) {
                    // Blob 1: Cyan, drifting top-left
                    val cx1 = w * 0.25f + sin(time1) * w * 0.1f
                    val cy1 = h * 0.25f + cos(time1) * h * 0.1f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlassTokens.GlowCyan.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(cx1, cy1),
                            radius = w * 0.6f
                        )
                    )

                    // Blob 2: Purple, drifting center-right
                    val cx2 = w * 0.75f + cos(time2) * w * 0.12f
                    val cy2 = h * 0.5f + sin(time2) * h * 0.08f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlassTokens.GlowPurple.copy(alpha = 0.09f),
                                Color.Transparent
                            ),
                            center = Offset(cx2, cy2),
                            radius = w * 0.65f
                        )
                    )

                    // Blob 3: Rose/Pink, drifting bottom-left
                    val cx3 = w * 0.3f + sin(time2 + 1f) * w * 0.08f
                    val cy3 = h * 0.8f + cos(time1 + 1f) * h * 0.1f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlassTokens.GlowRose.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(cx3, cy3),
                            radius = w * 0.55f
                        )
                    )
                }
            }
            .filmGrainOverlay(grainOpacity = 0.03f)
    ) {
        content()
    }
}
