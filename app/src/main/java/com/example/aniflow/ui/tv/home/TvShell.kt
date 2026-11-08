package com.example.aniflow.ui.tv.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.ui.brand.AniFlowMark
import com.example.aniflow.ui.brand.AniFlowWordmark
import com.example.aniflow.ui.focus.primaryFocus
import kotlinx.coroutines.delay

/** What the current screen wants from the top bar. Home sets [browsing] while focus is in the rows. */
@Stable
class TvChromeState {
    /** Focus is down in the content: the bar folds into a small "you are here" capsule. */
    var browsing by mutableStateOf(false)
    /** A full-screen layer (trailer viewer) is up: no chrome at all. */
    var fullscreen by mutableStateOf(false)
}

val LocalTvChrome = staticCompositionLocalOf { TvChromeState() }

private data class BarTab(val tab: Int, val label: String)

// Tab indices match MainScreenViewModel. Settings is the gear on the right.
private val barTabs = listOf(BarTab(0, "Home"), BarTab(1, "Search"), BarTab(2, "Library"), BarTab(4, "Schedule"))
private val TabWidth = 84.dp
private val CapsulePad = 4.dp

/**
 * TV chrome: a centred glass capsule at the top with a pill that springs between tabs, the logo on
 * the left and Settings on the right. It overlays the content (never pushes it). While you browse
 * the home rows it folds into a mini capsule; BACK or UP brings it back.
 *
 * [railFocusRequester] / [onRailFocusChanged] keep MainScreen's BACK contract (the names predate the
 * top bar): BACK from content lands on the bar; BACK on the bar goes Home, then confirm-to-exit.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvShell(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    railFocusRequester: FocusRequester,
    onRailFocusChanged: (Boolean) -> Unit,
    /** Bump to move focus into the content from outside the bar (e.g. the remote's Search key). */
    focusContentSignal: Int = 0,
    content: @Composable (contentFocus: FocusRequester) -> Unit,
) {
    val contentFocus = remember { FocusRequester() }
    val chrome = remember { TvChromeState() }
    var barFocused by remember { mutableStateOf(false) }
    // Selecting a tab moves focus into it — but only after the new tab has composed.
    var focusContentRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusContentRequest) {
        if (focusContentRequest > 0) { delay(180); runCatching { contentFocus.requestFocus() } }
    }
    LaunchedEffect(focusContentSignal) { if (focusContentSignal > 0) focusContentRequest++ }
    LaunchedEffect(selectedTab) { chrome.browsing = false; chrome.fullscreen = false }

    CompositionLocalProvider(LocalTvChrome provides chrome) {
        Box(Modifier.fillMaxSize().background(TvTokens.Canvas)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = if (selectedTab == 0) 0.dp else TvTokens.BarSpace)
                    .primaryFocus(contentFocus)
                    .focusRestorer()
                    .focusGroup()
            ) {
                content(contentFocus)
            }

            val showFull = !chrome.fullscreen && (barFocused || !chrome.browsing)
            val fullA by animateFloatAsState(if (showFull) 1f else 0f, tween(320, easing = TvEase), label = "barA")
            val miniA by animateFloatAsState(if (!chrome.fullscreen && !showFull) 1f else 0f, tween(260, delayMillis = if (showFull) 0 else 120), label = "miniA")

            // Soft top scrim so the bar reads over bright banner art.
            Box(
                Modifier.fillMaxWidth().height(96.dp).graphicsLayer { alpha = fullA }
                    .background(Brush.verticalGradient(listOf(TvTokens.Canvas.copy(alpha = 0.75f), Color.Transparent)))
            )

            TopBar(
                selectedTab = selectedTab,
                barFocused = barFocused,
                railFocusRequester = railFocusRequester,
                modifier = Modifier
                    .graphicsLayer { alpha = fullA; translationY = (1f - fullA) * -18.dp.toPx() }
                    .onFocusChanged { barFocused = it.hasFocus; onRailFocusChanged(it.hasFocus) },
                onSelect = { tab ->
                    onSelectTab(tab)
                    focusContentRequest++
                },
            )

            if (miniA > 0.01f) MiniCapsule(selectedTab, Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = miniA; scaleX = 0.8f + 0.2f * miniA; scaleY = 0.8f + 0.2f * miniA })
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopBar(
    selectedTab: Int,
    barFocused: Boolean,
    railFocusRequester: FocusRequester,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    // The capsule unfolds from its centre on launch.
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(150); open.animateTo(1f, tween(620, easing = TvEase)) }

    Box(modifier.fillMaxWidth().padding(top = TvTokens.BarTop).height(TvTokens.BarHeight)) {
        Row(Modifier.align(Alignment.CenterStart).padding(start = 40.dp).graphicsLayer { alpha = open.value }, verticalAlignment = Alignment.CenterVertically) {
            AniFlowMark(24.dp)
            AniFlowWordmark(15.dp, Modifier.padding(start = 10.dp))
        }

        Row(
            Modifier
                .align(Alignment.Center)
                .focusRestorer(railFocusRequester)
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var focusedTab by remember { mutableIntStateOf(-1) }
            val pillIndex = barTabs.indexOfFirst { it.tab == (if (barFocused && focusedTab >= 0) focusedTab else selectedTab) }
            val pillX by animateDpAsState((TabWidth * pillIndex.coerceAtLeast(0)), TvTokens.PillSpring, label = "pillX")
            val pillA by animateFloatAsState(if (pillIndex >= 0) 1f else 0f, tween(200), label = "pillA")
            val capScale by animateFloatAsState(if (barFocused) 1.04f else 1f, TvTokens.FocusSpring, label = "capScale")

            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = (0.35f + 0.65f * open.value) * capScale
                        scaleY = capScale
                        alpha = open.value
                        transformOrigin = TransformOrigin.Center
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (barFocused) 0.12f else 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                    .padding(CapsulePad)
            ) {
                // The pill: off-white while the bar has focus (it is the focus), dimmer otherwise.
                Box(
                    Modifier
                        .offset(x = pillX)
                        .width(TabWidth)
                        .height(TvTokens.BarHeight - CapsulePad * 2)
                        .graphicsLayer { alpha = pillA }
                        .clip(RoundedCornerShape(50))
                        .background(if (barFocused) TvTokens.Focus else Color.White.copy(alpha = 0.16f))
                )
                Row {
                    barTabs.forEach { t ->
                        val onPill = t.tab == (if (barFocused && focusedTab >= 0) focusedTab else selectedTab)
                        val color by animateColorAsState(
                            when {
                                onPill && barFocused -> TvTokens.OnFocus
                                onPill -> TvTokens.TextPrimary
                                else -> TvTokens.TextTertiary
                            }, tween(180), label = "tabC"
                        )
                        Box(
                            Modifier
                                .width(TabWidth)
                                .height(TvTokens.BarHeight - CapsulePad * 2)
                                .then(if (t.tab == selectedTab) Modifier.focusRequester(railFocusRequester) else Modifier)
                                .onFocusChanged { if (it.isFocused) focusedTab = t.tab else if (focusedTab == t.tab) focusedTab = -1 }
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(t.tab) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t.label, color = color, fontSize = 14.sp, fontWeight = if (onPill) FontWeight.Bold else FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.width(14.dp))
            GearButton(
                selected = selectedTab == 3,
                modifier = if (selectedTab == 3) Modifier.focusRequester(railFocusRequester) else Modifier,
                onClick = { onSelect(3) },
                alpha = { open.value },
            )
        }
    }
}

@Composable
private fun GearButton(selected: Boolean, modifier: Modifier, onClick: () -> Unit, alpha: () -> Float) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, TvTokens.FocusSpring, label = "gear")
    val turn by animateFloatAsState(if (focused) 60f else 0f, tween(420, easing = TvEase), label = "gearTurn")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha() }
            .size(TvTokens.BarHeight)
            .clip(CircleShape)
            .background(if (focused) TvTokens.Focus else Color.White.copy(alpha = if (selected) 0.16f else 0.08f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Settings, contentDescription = "Settings",
            tint = if (focused) TvTokens.OnFocus else if (selected) TvTokens.TextPrimary else TvTokens.TextTertiary,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn }
        )
    }
}

/** Folded bar: where you are, and a hint that UP/BACK brings the bar back. Breathes gently. */
@Composable
private fun MiniCapsule(selectedTab: Int, modifier: Modifier) {
    val label = barTabs.firstOrNull { it.tab == selectedTab }?.label ?: "Settings"
    val breathe by rememberInfiniteTransition(label = "mini").animateFloat(0.55f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "miniDot")
    Row(
        modifier
            .padding(top = TvTokens.BarTop + 4.dp)
            .clip(RoundedCornerShape(50))
            .background(TvTokens.Panel.copy(alpha = 0.78f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).graphicsLayer { alpha = breathe }.clip(CircleShape).background(TvTokens.AccentGlow))
        Text(label, color = TvTokens.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = TvTokens.TextTertiary, modifier = Modifier.size(16.dp))
    }
}
