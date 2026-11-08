package com.example.aniflow.ui.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay

/**
 * TV focus safety net.
 *
 * A D-pad UI with nothing focused is dead: the first remote press is spent just bringing a
 * highlight back. That happens whenever the focused node leaves composition — a dialog closes, a
 * list swaps its items, a screen transition disposes the old screen before the new one claims
 * focus, a tab switch lands on an empty state.
 *
 * Screens mark the element where focus naturally belongs with [primaryFocus]. When the whole app
 * has had no focus for longer than a transition, the most recently attached primary target gets
 * it back; if none can take it, focus enters the first focusable thing on screen.
 */
class FocusHealer internal constructor() {
    internal var appHasFocus by mutableStateOf(false)
    private val targets = ArrayList<FocusRequester>()

    internal fun attach(r: FocusRequester) { targets.remove(r); targets.add(r) }
    internal fun detach(r: FocusRequester) { targets.remove(r) }

    internal suspend fun heal(fallback: () -> Unit) {
        for (r in targets.reversed()) {
            runCatching { r.requestFocus() }
            withFrameNanos { }
            if (appHasFocus) return
        }
        fallback()
    }
}

val LocalFocusHealer = staticCompositionLocalOf<FocusHealer?> { null }

/** Wraps the whole app. Only active on TV; phones have no focus to lose. */
@Composable
fun FocusHealerRoot(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val healer = remember { FocusHealer() }
    val window = LocalWindowInfo.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(healer) {
        snapshotFlow { !healer.appHasFocus && window.isWindowFocused }.collect { lost ->
            if (!lost) return@collect
            // Give transitions and screens' own initial-focus requests time to land first.
            delay(450)
            if (!healer.appHasFocus && window.isWindowFocused) {
                healer.heal { runCatching { focusManager.moveFocus(FocusDirection.Next) } }
            }
        }
    }
    CompositionLocalProvider(LocalFocusHealer provides healer) {
        Box(Modifier.fillMaxSize().onFocusChanged { healer.appHasFocus = it.hasFocus }.focusGroup()) {
            content()
        }
    }
}

/**
 * [focusRequester] that also registers [requester] as this screen's home for focus, so the
 * [FocusHealerRoot] can put focus back here when it is lost.
 */
@Composable
fun Modifier.primaryFocus(requester: FocusRequester): Modifier {
    val healer = LocalFocusHealer.current
    if (healer != null) {
        DisposableEffect(requester, healer) {
            healer.attach(requester)
            onDispose { healer.detach(requester) }
        }
    }
    return this.focusRequester(requester)
}
