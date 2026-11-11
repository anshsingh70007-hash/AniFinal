package com.example.aniflow.ui.phone

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import com.example.aniflow.ui.phone.motion.PhoneMotion
import com.example.aniflow.ui.phone.motion.rememberHaptics

/**
 * Phone detail opens like a card sheet: it rises from the bottom on the glide curve while the page
 * behind sinks to 94% and dims (board tile 2/18). Going back reverses it.
 */
object PhoneSheetTransitions {
    private const val OPEN_MS = 600
    private const val CLOSE_MS = 420

    val open: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform = {
        slideInVertically(PhoneMotion.glide(OPEN_MS)) { it }
            .togetherWith(scaleOut(PhoneMotion.glide(OPEN_MS), targetScale = 0.94f) + fadeOut(PhoneMotion.glide(OPEN_MS), targetAlpha = 0.4f))
    }
    val close: AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform = {
        (scaleIn(PhoneMotion.glide(CLOSE_MS), initialScale = 0.94f) + fadeIn(PhoneMotion.glide(CLOSE_MS), initialAlpha = 0.4f))
            .togetherWith(slideOutVertically(PhoneMotion.glide(CLOSE_MS)) { it })
    }
    val predictiveClose: AnimatedContentTransitionScope<Scene<*>>.(Int) -> ContentTransform = { _ ->
        (scaleIn(tween(CLOSE_MS), initialScale = 0.94f) + fadeIn(tween(CLOSE_MS), initialAlpha = 0.4f))
            .togetherWith(slideOutVertically(tween(CLOSE_MS)) { it })
    }
}

/**
 * Pull the sheet down from the top of its content to close it (board tiles 2 and 21). It follows
 * the finger 1:1; on release the speed is projected forward the way iOS does it, so a short fast
 * flick closes it too, and a slow drag past a third of the screen does. Otherwise it springs back.
 * Works with any scrolling content inside: it only takes the scroll the content didn't use.
 */
@Composable
fun PhoneSheetDismiss(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val haptics = rememberHaptics()
    var offset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        val connection = remember(heightPx) {
            object : NestedScrollConnection {
                var grabbed = false
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // Pushing back up while pulled: undo the pull before the content scrolls.
                    if (offset > 0f && available.y < 0f) {
                        val use = maxOf(available.y, -offset)
                        offset += use
                        return Offset(0f, use)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0f) {
                        if (!grabbed) { grabbed = true; haptics.gestureStart() }
                        offset += available.y
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offset <= 0f) { grabbed = false; return Velocity.Zero }
                    val d = offset
                    val v = available.y / 1000f            // px per ms
                    val projected = d + v * 499f           // deceleration rate 0.998
                    val close = projected > heightPx * 0.4f || (d > heightPx * 0.18f && v > -0.15f)
                    grabbed = false
                    haptics.gestureEnd()
                    if (close) {
                        val ms = if (v > 0.05f) ((heightPx - d) / v * 1.7f).toInt().coerceIn(240, 600) else 600
                        animate(d, heightPx, animationSpec = tween(ms, easing = PhoneMotion.GlideEasing)) { x, _ -> offset = x }
                        onDismiss()
                    } else {
                        animate(d, 0f, animationSpec = PhoneMotion.snappy()) { x, _ -> offset = x }
                    }
                    return available
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(connection)
                .graphicsLayer {
                    translationY = offset
                    val p = (offset / heightPx).coerceIn(0f, 1f)
                    shape = RoundedCornerShape((28 * minOf(1f, p * 8f)).dp)
                    clip = offset > 0f
                }
        ) { content() }
    }
}
