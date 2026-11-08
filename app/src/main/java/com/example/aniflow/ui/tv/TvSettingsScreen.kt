package com.example.aniflow.ui.tv

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.ui.focus.primaryFocus
import com.example.aniflow.ui.settings.AniSwitch
import com.example.aniflow.ui.settings.DefaultSpeeds
import com.example.aniflow.ui.settings.SettingsController
import com.example.aniflow.ui.settings.UpdateState
import com.example.aniflow.ui.settings.bytesText
import com.example.aniflow.ui.settings.rememberSettingsController
import com.example.aniflow.ui.settings.speedText
import com.example.aniflow.ui.tv.home.TvTokens
import kotlinx.coroutines.delay

private enum class Cat(val label: String, val tagline: String, val icon: ImageVector) {
    Playback("Playback", "How episodes start, sound and flow into the next.", Icons.Rounded.PlayArrow),
    Experience("Experience", "Sound and motion around the app.", Icons.Rounded.Star),
    Library("Library & Storage", "Your history, your list and the space AniFlow uses.", Icons.Rounded.Favorite),
    Updates("Updates", "Delivered over HTTPS and signature-checked before install.", Icons.Rounded.Refresh),
    About("About", "What's under the hood, and the remote's shortcuts.", Icons.Rounded.Info),
}

private val Danger = Color(0xFFFF5C8A)
private val RowShape = RoundedCornerShape(14.dp)

/**
 * TV settings: categories on the left follow focus (no OK needed), the panel on the right
 * swaps with a short slide. LEFT from anywhere in the panel returns to the category it belongs
 * to, never a neighbour, so moving back can't silently switch panels.
 */
@Composable
fun TvSettingsScreen(
    watchlistStore: WatchlistStore,
    watchHistoryStore: WatchHistoryStore,
    settingsStore: SettingsStore,
    repository: AnimeRepository? = null,
) {
    val c = rememberSettingsController(settingsStore, watchlistStore, watchHistoryStore, repository)
    var selected by rememberSaveable { mutableStateOf(Cat.Playback) }
    val catFocus = remember { Cat.entries.associateWith { FocusRequester() } }
    val back = catFocus.getValue(selected)

    Box(
        Modifier
            .fillMaxSize()
            .background(TvTokens.Canvas)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(TvTokens.AccentDeep.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.05f), radius = size.width * 0.55f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(TvTokens.Accent.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 1.0f), radius = size.width * 0.5f
                    )
                )
            }
    ) {
        Row(Modifier.fillMaxSize().padding(start = TvTokens.ContentStart, end = 48.dp, top = 26.dp)) {
            Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("Settings", color = TvTokens.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                ProfileCard(c)
                Spacer(Modifier.height(12.dp))
                Cat.entries.forEach { cat ->
                    val req = catFocus.getValue(cat)
                    CategoryItem(
                        cat = cat,
                        summary = summaryOf(cat, c),
                        selected = cat == selected,
                        modifier = if (cat == Cat.Playback) Modifier.primaryFocus(req) else Modifier.focusRequester(req),
                        onFocused = { selected = cat },
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.width(36.dp))
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 60)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 14 })
                        .togetherWith(fadeOut(tween(110)))
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "panel",
            ) { cat ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(cat.label, color = TvTokens.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(cat.tagline, color = TvTokens.TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    when (cat) {
                        Cat.Playback -> PlaybackPanel(c, back)
                        Cat.Experience -> ExperiencePanel(c, back)
                        Cat.Library -> LibraryPanel(c, back)
                        Cat.Updates -> UpdatesPanel(c, back)
                        Cat.About -> AboutPanel(c, back)
                    }
                }
            }
        }
    }
}

private fun summaryOf(cat: Cat, c: SettingsController): String = when (cat) {
    Cat.Playback -> "${c.audio.uppercase()} · ${speedText(c.speed)} · Autoplay ${if (c.autoplay) "on" else "off"}"
    Cat.Experience -> "Sounds ${if (c.navSounds) "on" else "off"} · Trailers ${if (c.heroTrailers) "on" else "off"}"
    Cat.Library -> "${c.titlesStarted} in history · ${bytesText(c.cacheBytes)} cache"
    Cat.Updates -> when (val u = c.update) {
        is UpdateState.Available -> "v${u.info.versionName} available"
        is UpdateState.Downloading -> "Downloading ${(u.progress * 100).toInt()}%"
        UpdateState.Checking -> "Checking…"
        UpdateState.UpToDate -> "Up to date"
        UpdateState.Failed -> "Couldn't check"
        UpdateState.Idle -> "v${c.versionName}"
    }
    Cat.About -> "Version, device, shortcuts"
}

// ---- panels -------------------------------------------------------------------------------------

@Composable
private fun PlaybackPanel(c: SettingsController, back: FocusRequester) {
    ChoiceRow(
        "Audio", "Episodes open in this language when it's available, and fall back to the other.",
        options = listOf("Subtitled", "Dubbed"), selected = if (c.audio == "dub") 1 else 0, back = back,
        onSelect = { c.selectAudio(if (it == 1) "dub" else "sub") }
    )
    ChoiceRow(
        "Default speed", "Where every episode starts. Change it per episode from the player.",
        options = DefaultSpeeds.map(::speedText), selected = DefaultSpeeds.indexOfFirst { kotlin.math.abs(it - c.speed) < 0.01f },
        back = back, onSelect = { c.selectSpeed(DefaultSpeeds[it]) }
    )
    ToggleRow("Autoplay next episode", "A 10-second Up Next countdown as the credits roll.", c.autoplay, back, c::toggleAutoplay)
    ToggleRow("Skip intros and credits", "Jumps past openings and endings by itself, with a heads-up first.", c.autoSkip, back, c::toggleAutoSkip)
}

@Composable
private fun ExperiencePanel(c: SettingsController, back: FocusRequester) {
    ToggleRow("Navigation sounds", "Soft koto plucks as you move, select and go back.", c.navSounds, back, c::toggleNavSounds)
    ToggleRow("Trailer previews", "Rest on a title on Home and its trailer fades in behind it.", c.heroTrailers, back, c::toggleHeroTrailers)
}

@Composable
private fun LibraryPanel(c: SettingsController, back: FocusRequester) {
    ActionRow(
        "Clear watch history", "Removes Continue Watching and every saved position (${c.titlesStarted} titles).",
        action = "Clear", back = back, danger = true, confirm = "Press OK again to erase ${c.titlesStarted} titles", done = "History cleared",
        onAction = c::clearHistory
    )
    ActionRow(
        "Clear watchlist", "Empties your list (${c.watchlistCount} titles).",
        action = "Clear", back = back, danger = true, confirm = "Press OK again to empty your list", done = "Watchlist cleared",
        onAction = c::clearWatchlist
    )
    ActionRow(
        "Free up space", "Posters and page caches: ${bytesText(c.cacheBytes)}. They download again as needed; your lists stay.",
        action = "Clean", back = back, done = "Cache cleared", onAction = c::clearCache
    )
}

@Composable
private fun UpdatesPanel(c: SettingsController, back: FocusRequester) {
    val u = c.update
    val (desc, action) = when (u) {
        UpdateState.Idle -> "You're on v${c.versionName}. Press OK to look for a newer one." to "Check"
        UpdateState.Checking -> "Checking…" to "…"
        UpdateState.UpToDate -> "You're on the latest version, v${c.versionName}." to "Check again"
        is UpdateState.Available -> "v${u.info.versionName} is ready. Press OK to download and install." to "Install"
        is UpdateState.Downloading -> "Downloading v${u.info.versionName}… ${(u.progress * 100).toInt()}%" to "…"
        UpdateState.Failed -> "Couldn't reach the update server. Press OK to try again." to "Retry"
    }
    ActionRow(
        "Check for updates", desc, action = action, back = back, onAction = c::updateAction,
        busy = u is UpdateState.Checking,
        progress = (u as? UpdateState.Downloading)?.progress,
        accent = u is UpdateState.Available,
    )
    (u as? UpdateState.Available)?.info?.updateNotes?.takeIf { it.isNotBlank() }?.let { notes ->
        InfoRow("What's new", notes, back)
    }
    ToggleRow("Check when AniFlow opens", "Quietly looks for an update on launch.", c.checkOnStart, back, c::toggleCheckOnStart)
}

@Composable
private fun AboutPanel(c: SettingsController, back: FocusRequester) {
    InfoRow("Version", "v${c.versionName} · build ${c.versionCode}", back)
    InfoRow("This device", "Android TV · ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", back)
    InfoRow("Catalog", "Titles, artwork and schedules from AniList. Home rows follow anilight.live and refresh themselves.", back)
    ShortcutsCard(back)
}

// ---- building blocks ----------------------------------------------------------------------------

@Composable
private fun ProfileCard(c: SettingsController) {
    val minutes by animateIntAsState(c.minutesWatched.toInt(), tween(1400, easing = FastOutSlowInEasing), label = "min")
    val eps by animateIntAsState(c.episodesWatched, tween(1200, easing = FastOutSlowInEasing), label = "eps")
    val titles by animateIntAsState(c.titlesStarted, tween(1000, easing = FastOutSlowInEasing), label = "titles")
    val list by animateIntAsState(c.watchlistCount, tween(1000, easing = FastOutSlowInEasing), label = "list")
    // A slow light sweep across the card every few seconds; drawn, never recomposed.
    val sweep = rememberInfiniteTransition(label = "sheen")
    val x by sweep.animateFloat(
        initialValue = -0.6f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 7000
            -0.6f at 0 using LinearEasing
            1.6f at 1600 using FastOutSlowInEasing
            1.6f at 7000
        }, RepeatMode.Restart), label = "x"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3D0F73), Color(0xFF1C1033), Color(0xFF120C1E))))
            .border(1.dp, Brush.linearGradient(listOf(TvTokens.AccentGlow.copy(alpha = 0.6f), Color.Transparent)), RoundedCornerShape(18.dp))
            .drawWithContent {
                drawContent()
                val w = size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                        start = Offset(w * x - w * 0.3f, 0f), end = Offset(w * x + w * 0.3f, size.height)
                    )
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Text("YOUR ANIFLOW", color = TvTokens.AccentPale, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Row {
                Stat(if (minutes < 60) "${minutes}m" else "${minutes / 60}h", "watched", Modifier.weight(1f))
                Stat("$eps", "episodes", Modifier.weight(1f))
                Stat("$titles", "titles", Modifier.weight(1f))
                Stat("$list", "on list", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = TvTokens.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = TvTokens.TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun CategoryItem(cat: Cat, summary: String, selected: Boolean, modifier: Modifier, onFocused: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, TvTokens.FocusSpring, label = "s")
    val bg by animateColorAsState(
        when {
            focused -> TvTokens.SurfaceHigh
            selected -> TvTokens.Surface
            else -> Color.Transparent
        }, label = "bg"
    )
    val bar by animateDpAsState(if (selected) 22.dp else 0.dp, label = "bar")
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RowShape)
            .background(bg)
            .then(if (focused) Modifier.border(1.5.dp, TvTokens.AccentGradient, RowShape) else Modifier)
            .clickable(interaction, indication = null) { onFocused() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(bar).clip(RoundedCornerShape(2.dp)).background(TvTokens.AccentGradient))
        Spacer(Modifier.width(if (selected) 9.dp else 0.dp))
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                .background(if (selected || focused) TvTokens.AccentGradient else SolidColor(TvTokens.SurfaceHigh)),
            contentAlignment = Alignment.Center
        ) {
            Icon(cat.icon, contentDescription = null, tint = if (selected || focused) Color.White else TvTokens.TextSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                cat.label, color = if (selected || focused) TvTokens.TextPrimary else TvTokens.TextSecondary,
                fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
            Text(summary, color = TvTokens.TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The shared look of a panel row: lifts, brightens and gains a gradient edge on focus. */
@Composable
private fun RowSurface(focused: Boolean, modifier: Modifier, danger: Boolean = false, content: @Composable () -> Unit) {
    val scale by animateFloatAsState(if (focused) 1.015f else 1f, TvTokens.FocusSpring, label = "s")
    val bg by animateColorAsState(if (focused) TvTokens.SurfaceHigh else TvTokens.Surface.copy(alpha = 0.85f), label = "bg")
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RowShape)
            .background(bg)
            .border(
                if (focused) 1.5.dp else 1.dp,
                when {
                    focused && danger -> Brush.linearGradient(listOf(Danger, TvTokens.AccentDeep))
                    focused -> TvTokens.AccentGradient
                    else -> SolidColor(Color.White.copy(alpha = 0.06f))
                },
                RowShape
            )
    ) { content() }
}

@Composable
private fun RowText(title: String, desc: String, modifier: Modifier = Modifier, descColor: Color = TvTokens.TextSecondary) {
    Column(modifier) {
        Text(title, color = TvTokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(desc, color = descColor, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, back: FocusRequester, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    RowSurface(focused, Modifier.focusProperties { left = back }.clickable(interaction, indication = null) { onToggle() }) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RowText(title, desc, Modifier.weight(1f))
            Spacer(Modifier.width(20.dp))
            AniSwitch(checked)
        }
    }
}

@Composable
private fun ChoiceRow(title: String, desc: String, options: List<String>, selected: Int, back: FocusRequester, onSelect: (Int) -> Unit) {
    var anyFocused by remember { mutableStateOf(false) }
    val focusStates = remember(options.size) { BooleanArray(options.size) }
    RowSurface(anyFocused, Modifier) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
            RowText(title, desc)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEachIndexed { i, label ->
                    Chip(
                        label = label, selected = i == selected,
                        modifier = if (i == 0) Modifier.focusProperties { left = back } else Modifier,
                        onFocusChange = { f -> focusStates[i] = f; anyFocused = focusStates.any { it } },
                        onClick = { onSelect(i) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier, onFocusChange: (Boolean) -> Unit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocusChange(focused) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, TvTokens.FocusSpring, label = "s")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) TvTokens.AccentGradient else SolidColor(Color.White.copy(alpha = if (focused) 0.14f else 0.06f)))
            .border(if (focused) 2.dp else 1.dp, if (focused) TvTokens.AccentPale else Color.White.copy(alpha = 0.10f), shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            label, color = if (selected || focused) Color.White else TvTokens.TextSecondary,
            fontSize = 14.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
        )
    }
}

/**
 * A row with one action. [confirm] makes it two-step: the first OK arms it (the row says what is
 * about to happen) and only a second OK within 4 s does it. [done] flashes after it ran.
 */
@Composable
private fun ActionRow(
    title: String,
    desc: String,
    action: String,
    back: FocusRequester,
    onAction: () -> Unit,
    danger: Boolean = false,
    confirm: String? = null,
    done: String? = null,
    busy: Boolean = false,
    progress: Float? = null,
    accent: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var armed by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4_000); armed = false } }
    LaunchedEffect(flash) { if (flash) { delay(2_200); flash = false } }
    LaunchedEffect(focused) { if (!focused) armed = false }
    RowSurface(
        focused,
        Modifier.focusProperties { left = back }.clickable(interaction, indication = null) {
            when {
                confirm != null && !armed -> armed = true
                else -> { armed = false; onAction(); if (done != null) flash = true }
            }
        },
        danger = danger
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowText(
                    title,
                    when {
                        flash && done != null -> "✓  $done"
                        armed && confirm != null -> confirm
                        else -> desc
                    },
                    Modifier.weight(1f),
                    descColor = when {
                        flash -> TvTokens.AccentPale
                        armed -> Danger
                        else -> TvTokens.TextSecondary
                    }
                )
                Spacer(Modifier.width(20.dp))
                if (busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = TvTokens.AccentGlow, strokeWidth = 2.5.dp)
                } else {
                    val pillBg = when {
                        armed || (danger && focused) -> SolidColor(Danger)
                        accent || focused -> TvTokens.AccentGradient
                        else -> SolidColor(Color.White.copy(alpha = 0.08f))
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(pillBg).padding(horizontal = 16.dp, vertical = 7.dp)) {
                        Text(if (armed) "Confirm" else action, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            AnimatedVisibility(progress != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = TvTokens.AccentGlow, trackColor = Color.White.copy(alpha = 0.08f),
                )
            }
        }
    }
}

/** Read-only, but focusable so the panel scrolls to it on a remote. */
@Composable
private fun InfoRow(title: String, value: String, back: FocusRequester) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    RowSurface(focused, Modifier.focusProperties { left = back }.focusable(interactionSource = interaction)) {
        RowText(title, value, Modifier.padding(horizontal = 22.dp, vertical = 16.dp))
    }
}

@Composable
private fun ShortcutsCard(back: FocusRequester) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val keys = listOf(
        "▼  or  OK" to "Show the player controls",
        "◀  ▶" to "Seek 10 s with controls hidden; keep pressing to go faster",
        "Play / Pause" to "Works anywhere in the player, controls or not",
        "BACK" to "Closes panels, then the controls, then the player",
        "Rest on a title" to "Home opens its card and fades in the trailer",
        "BACK on Home" to "Rows → billboard → menu",
    )
    RowSurface(focused, Modifier.focusProperties { left = back }.focusable(interactionSource = interaction)) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
            Text("Remote shortcuts", color = TvTokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            keys.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(150.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.07f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) { Text(k, color = TvTokens.AccentPale, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                    Spacer(Modifier.width(14.dp))
                    Text(v, color = TvTokens.TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
