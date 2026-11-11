package com.example.aniflow.ui.phone

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aniflow.data.model.WatchHistoryEntry
import com.example.aniflow.theme.*
import com.example.aniflow.ui.phone.motion.PhoneMotion
import com.example.aniflow.ui.phone.motion.pressScale
import com.example.aniflow.ui.phone.motion.rememberHaptics

/** MainScreenViewModel tab indices. */
private const val TAB_HOME = 0
private const val TAB_DISCOVER = 1
private const val TAB_LIBRARY = 2
private const val TAB_SETTINGS = 3
private const val TAB_SCHEDULE = 4

private class TabItem(val tab: Int, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(TAB_HOME, "Home", Icons.Rounded.Home),
    TabItem(TAB_DISCOVER, "Discover", Icons.Rounded.Search),
    TabItem(TAB_LIBRARY, "Library", Icons.Filled.Favorite),
    TabItem(TAB_SCHEDULE, "Schedule", Icons.Filled.DateRange),
)

/** Left-to-right place of a tab, for the direction tabs slide in. Settings (the avatar) is leftmost. */
private fun slotOf(tab: Int) = when (tab) { TAB_SETTINGS -> -1; else -> TABS.indexOfFirst { it.tab == tab } }

private fun titleOf(tab: Int) = when (tab) {
    TAB_DISCOVER -> "Discover"; TAB_LIBRARY -> "Library"; TAB_SETTINGS -> "Settings"; TAB_SCHEDULE -> "Schedule"; else -> "Home"
}

/** How much of the bottom of the screen the floating tab bar and resume bar cover; lists pad by it. */
val LocalPhoneBottomInset = compositionLocalOf { 120.dp }

private val BAR_H = 64.dp
private val COMPACT = 56.dp
private val RESUME_H = 56.dp
private val GAP = 10.dp
private val BOTTOM_MARGIN = 12.dp

/**
 * The phone app frame from the approved design: C's top bar (avatar, For You / Series / Films,
 * search) over A's floating capsule tab bar. Scrolling down shrinks the tab bar to one button and
 * the resume bar slides in beside it; scrolling up brings it back.
 */
@Composable
fun PhoneShell(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    segment: Int,
    onSegment: (Int) -> Unit,
    onSearch: () -> Unit,
    resume: WatchHistoryEntry?,
    onResume: (WatchHistoryEntry) -> Unit,
    content: @Composable (tab: Int, bottomInset: Dp) -> Unit,
) {
    var compact by remember { mutableStateOf(false) }
    LaunchedEffect(currentTab) { compact = false }
    var dismissedResume by rememberSaveable { mutableLongStateOf(0L) }
    val shownResume = resume?.takeIf { it.lastWatchedTime != dismissedResume }

    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -8f) compact = true else if (available.y > 8f) compact = false
                }
                return Offset.Zero
            }
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBottom + BOTTOM_MARGIN + BAR_H + if (shownResume != null) RESUME_H + GAP else 0.dp

    Box(Modifier.fillMaxSize().background(PrimaryDark)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(currentTab, onSelectTab, segment, onSegment, onSearch)
            val holder = rememberSaveableStateHolder()
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val dir = if (slotOf(targetState) >= slotOf(initialState)) 1 else -1
                    (fadeIn(PhoneMotion.fade()) + slideInHorizontally(PhoneMotion.smooth()) { dir * it / 12 })
                        .togetherWith(fadeOut(PhoneMotion.fade(160)))
                        .using(SizeTransform(clip = false))
                },
                label = "tabs",
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(scrollWatcher),
            ) { tab ->
                holder.SaveableStateProvider(tab) { content(tab, bottomInset) }
            }
        }
        // Content fades out under the floating chrome instead of showing in the gap below it.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navBottom + BOTTOM_MARGIN + BAR_H * 0.75f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, PrimaryDark.copy(alpha = 0.92f), PrimaryDark)))
        )
        BottomChrome(
            currentTab = currentTab,
            onSelectTab = { if (compact && it == currentTab) compact = false else onSelectTab(it) },
            compact = compact,
            onExpand = { compact = false },
            resume = shownResume,
            onResume = onResume,
            onDismissResume = { dismissedResume = it.lastWatchedTime },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopBar(currentTab: Int, onSelectTab: (Int) -> Unit, segment: Int, onSegment: (Int) -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val onSettings = currentTab == TAB_SETTINGS
        val avatarI = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(48.dp)
                .pressScale(avatarI)
                .clip(CircleShape)
                .clickable(interactionSource = avatarI, indication = null) { onSelectTab(TAB_SETTINGS) }
                .semantics { contentDescription = "Profile and settings"; selected = onSettings; role = Role.Tab },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (onSettings) PrimaryAccent else SurfaceBorder)
                    .border(1.dp, if (onSettings) SecondaryAccent else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Person, null, tint = TextPrimary, modifier = Modifier.size(22.dp)) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // Tile 24: the segments shrink and fade on the spring; the tab name rises into place.
            AnimatedContent(
                targetState = currentTab == TAB_HOME,
                transitionSpec = {
                    (fadeIn(PhoneMotion.fade()) + scaleIn(PhoneMotion.spring(), initialScale = 0.92f) + slideInVertically(PhoneMotion.smooth()) { it / 6 })
                        .togetherWith(fadeOut(PhoneMotion.fade(160)) + scaleOut(PhoneMotion.fade(160), targetScale = 0.92f))
                },
                label = "topbar",
            ) { home ->
                if (home) SegmentedControl(segment, onSegment)
                else Text(titleOf(currentTab), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Search, "Search", tint = TextPrimary)
        }
    }
}

@Composable
private fun SegmentedControl(segment: Int, onSegment: (Int) -> Unit) {
    val labels = listOf("For You", "Series", "Films")
    val haptics = rememberHaptics()
    val segW = 78.dp
    val x by animateDpAsState(segW * segment, PhoneMotion.snappy(), label = "seg")
    Box(
        Modifier
            .width(segW * labels.size + 8.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
            .padding(4.dp)
    ) {
        Box(Modifier.offset(x = x).width(segW).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(PrimaryAccent.copy(alpha = 0.55f)))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val sel = i == segment
                Box(
                    Modifier
                        .width(segW)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!sel) { haptics.segmentTick(); onSegment(i) }
                        }
                        .semantics { selected = sel; role = Role.Tab },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) TextPrimary else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BottomChrome(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    compact: Boolean,
    onExpand: () -> Unit,
    resume: WatchHistoryEntry?,
    onResume: (WatchHistoryEntry) -> Unit,
    onDismissResume: (WatchHistoryEntry) -> Unit,
    modifier: Modifier,
) {
    val cAnim by animateFloatAsState(if (compact) 1f else 0f, PhoneMotion.snappy(), label = "compact")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = BOTTOM_MARGIN)
    ) {
        val full = minOf(maxWidth, 440.dp)
        val c = cAnim.coerceIn(0f, 1.02f)
        val barW = lerp(full, COMPACT, c)
        val barH = lerp(BAR_H, COMPACT, c.coerceIn(0f, 1f))
        val totalH = BAR_H + if (resume != null) RESUME_H + GAP else 0.dp
        Box(Modifier.width(full).height(totalH).align(Alignment.BottomCenter)) {
            if (resume != null) {
                val x = lerp(0.dp, COMPACT + GAP, c)
                val y = lerp(0.dp, RESUME_H + GAP + (BAR_H - RESUME_H) / 2, c)
                ResumeBar(
                    entry = resume,
                    onResume = onResume,
                    onDismiss = onDismissResume,
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .width(full - x)
                        .height(RESUME_H),
                )
            }
            CapsuleTabBar(
                currentTab = currentTab,
                onSelectTab = onSelectTab,
                compactFraction = c.coerceIn(0f, 1f),
                onExpand = onExpand,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = (BAR_H - barH) / 2)
                    .width(barW)
                    .height(barH),
            )
        }
    }
}

@Composable
private fun CapsuleTabBar(currentTab: Int, onSelectTab: (Int) -> Unit, compactFraction: Float, onExpand: () -> Unit, modifier: Modifier) {
    val slot = TABS.indexOfFirst { it.tab == currentTab }
    Box(
        modifier
            .graphicsLayer { shadowElevation = 18.dp.toPx(); shape = CircleShape; clip = true }
            .background(PrimaryDarker)
            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
    ) {
        if (compactFraction < 0.98f) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp).graphicsLayer { alpha = 1f - compactFraction }) {
                val tabW = maxWidth / TABS.size
                // Tile 20: the edge moving forward leads, the other follows on a softer spring,
                // so the pill stretches toward where it's going and settles back.
                var last by remember { mutableIntStateOf(slot) }
                val right = slot >= last
                SideEffect { last = slot }
                val lead = spring<Dp>(dampingRatio = 0.86f, stiffness = 420f)
                val trail = spring<Dp>(dampingRatio = 0.86f, stiffness = 170f)
                val l by animateDpAsState(tabW * slot.coerceAtLeast(0), if (right) trail else lead, label = "l")
                val r by animateDpAsState(tabW * (slot.coerceAtLeast(0) + 1), if (right) lead else trail, label = "r")
                if (slot >= 0) {
                    Box(
                        Modifier.offset(x = l).width((r - l).coerceAtLeast(0.dp)).fillMaxHeight()
                            .clip(CircleShape).background(PrimaryAccent.copy(alpha = 0.42f))
                    )
                }
                Row(Modifier.fillMaxSize()) {
                    TABS.forEach { t ->
                        val sel = t.tab == currentTab
                        val i = remember { MutableInteractionSource() }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pressScale(i, 0.92f)
                                .clip(CircleShape)
                                .clickable(interactionSource = i, indication = null) { onSelectTab(t.tab) }
                                .semantics { selected = sel; role = Role.Tab },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(t.icon, null, tint = if (sel) TextPrimary else TextSecondary, modifier = Modifier.size(22.dp))
                            Text(t.label, color = if (sel) TextPrimary else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (compactFraction > 0.02f) {
            val current = TABS.firstOrNull { it.tab == currentTab } ?: TABS[0]
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = compactFraction }
                    .clickable(onClick = onExpand)
                    .semantics { contentDescription = "Show tabs. Current: ${titleOf(currentTab)}" },
                contentAlignment = Alignment.Center,
            ) { Icon(current.icon, null, tint = TextPrimary, modifier = Modifier.size(24.dp)) }
        }
    }
}

/**
 * Where you left off, one tap from playing again. It stays above the tab bar and slides beside it
 * when the bar shrinks. The × hides it until you watch something else.
 */
@Composable
private fun ResumeBar(entry: WatchHistoryEntry, onResume: (WatchHistoryEntry) -> Unit, onDismiss: (WatchHistoryEntry) -> Unit, modifier: Modifier) {
    val i = remember { MutableInteractionSource() }
    val left = if (entry.durationMs > 0) ((entry.durationMs - entry.progressMs) / 60_000L).coerceAtLeast(1) else null
    val fraction = if (entry.durationMs > 0) (entry.progressMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .pressScale(i, 0.975f)
            .graphicsLayer { shadowElevation = 14.dp.toPx(); shape = RoundedCornerShape(18.dp); clip = true }
            .background(SurfaceCard)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = i, indication = null) { onResume(entry) }
            .semantics { contentDescription = "Resume ${entry.title}, episode ${entry.episodeNumber}" }
    ) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.coverImage, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceBorder)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Ep ${entry.episodeNumber}" + (left?.let { " · $it min left" } ?: ""),
                    color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { onResume(entry) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.PlayArrow, "Play", tint = TextPrimary)
            }
            IconButton(onClick = { onDismiss(entry) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Close, "Hide", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(2.dp).background(PrimaryAccent))
    }
}
