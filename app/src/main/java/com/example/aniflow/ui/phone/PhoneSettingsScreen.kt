package com.example.aniflow.ui.phone

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.data.WatchHistoryStore
import com.example.aniflow.data.WatchlistStore
import com.example.aniflow.data.repository.AnimeRepository
import com.example.aniflow.ui.settings.AniSwitch
import com.example.aniflow.ui.settings.DefaultSpeeds
import com.example.aniflow.ui.settings.SettingsController
import com.example.aniflow.ui.settings.UpdateState
import com.example.aniflow.ui.settings.bytesText
import com.example.aniflow.ui.settings.rememberSettingsController
import com.example.aniflow.ui.settings.speedText
import com.example.aniflow.ui.settings.watchTimeText
import com.example.aniflow.ui.tv.home.TvTokens
import kotlinx.coroutines.delay

private val Danger = Color(0xFFFF5C8A)
private val CardShape = RoundedCornerShape(20.dp)

/** Phone settings: a stats header, then grouped cards. Destructive actions ask first. */
@Composable
fun PhoneSettingsScreen(
    watchlistStore: WatchlistStore,
    watchHistoryStore: WatchHistoryStore,
    settingsStore: SettingsStore,
    repository: AnimeRepository? = null,
) {
    val c = rememberSettingsController(settingsStore, watchlistStore, watchHistoryStore, repository)
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2_200); toast = null } }

    Box(Modifier.fillMaxSize().background(TvTokens.Canvas)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Settings", color = TvTokens.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
            StatsHeader(c)

            Section("Playback")
            Card {
                ChoiceItem("Audio", "Opens episodes in this language when available", listOf("Subtitled", "Dubbed"), if (c.audio == "dub") 1 else 0) {
                    c.selectAudio(if (it == 1) "dub" else "sub")
                }
                Divider()
                ChoiceItem("Default speed", "Where every episode starts", DefaultSpeeds.map(::speedText),
                    DefaultSpeeds.indexOfFirst { kotlin.math.abs(it - c.speed) < 0.01f }) { c.selectSpeed(DefaultSpeeds[it]) }
                Divider()
                ToggleItem("Autoplay next episode", "Up Next countdown as the credits roll", c.autoplay, c::toggleAutoplay)
                Divider()
                ToggleItem("Skip intros and credits", "Jumps past openings and endings by itself", c.autoSkip, c::toggleAutoSkip)
            }

            Section("Experience")
            Card {
                ToggleItem("Navigation sounds", "Soft koto plucks as you move around", c.navSounds, c::toggleNavSounds)
            }

            Section("Library & storage")
            Card {
                ActionItem("Clear watch history", "${c.titlesStarted} titles · Continue Watching and saved positions", "Clear", danger = true) {
                    confirm = "Erase your watch history? Continue Watching and every saved position will be gone." to {
                        c.clearHistory(); toast = "History cleared"
                    }
                }
                Divider()
                ActionItem("Clear watchlist", "${c.watchlistCount} titles on your list", "Clear", danger = true) {
                    confirm = "Empty your watchlist? This removes all ${c.watchlistCount} titles." to {
                        c.clearWatchlist(); toast = "Watchlist cleared"
                    }
                }
                Divider()
                ActionItem("Free up space", "${bytesText(c.cacheBytes)} of posters and page caches. Your lists stay.", "Clean") {
                    c.clearCache(); toast = "Cache cleared"
                }
            }

            Section("Updates")
            Card { UpdateItem(c); Divider(); ToggleItem("Check when AniFlow opens", "Quietly looks for an update on launch", c.checkOnStart, c::toggleCheckOnStart) }

            Section("About")
            Card {
                InfoItem("Version", "v${c.versionName} · build ${c.versionCode}")
                Divider()
                InfoItem("This device", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
                Divider()
                InfoItem("Catalog", "Titles, artwork and schedules from AniList. Home rows follow anilight.live and refresh themselves.")
            }
            Spacer(Modifier.height(96.dp))
        }

        AnimatedVisibility(
            toast != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                "✓  ${toast.orEmpty()}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(TvTokens.AccentGradient).padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }

    confirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = TvTokens.SurfaceHigh,
            title = { Text("Are you sure?", color = TvTokens.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(message, color = TvTokens.TextSecondary) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Erase", color = Danger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = TvTokens.TextSecondary) } },
        )
    }
}

@Composable
private fun StatsHeader(c: SettingsController) {
    val minutes by animateIntAsState(c.minutesWatched.toInt(), tween(1400, easing = FastOutSlowInEasing), label = "min")
    val eps by animateIntAsState(c.episodesWatched, tween(1200, easing = FastOutSlowInEasing), label = "eps")
    val list by animateIntAsState(c.watchlistCount, tween(1000, easing = FastOutSlowInEasing), label = "list")
    Box(
        Modifier.fillMaxWidth().clip(CardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF3D0F73), Color(0xFF1C1033), Color(0xFF120C1E))))
            .border(1.dp, Brush.linearGradient(listOf(TvTokens.AccentGlow.copy(alpha = 0.6f), Color.Transparent)), CardShape)
            .padding(18.dp)
    ) {
        Column {
            Text("YOUR ANIFLOW", color = TvTokens.AccentPale, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            Row {
                Stat(watchTimeText(minutes.toLong()), "watched", Modifier.weight(1f))
                Stat("$eps", "episodes", Modifier.weight(1f))
                Stat("$list", "on your list", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = TvTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = TvTokens.TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(), color = TvTokens.AccentGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp)
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(TvTokens.Surface)
            .border(1.dp, Color.White.copy(alpha = 0.06f), CardShape),
        content = content
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
}

@Composable
private fun ItemText(title: String, desc: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, color = TvTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(desc, color = TvTokens.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ToggleItem(title: String, desc: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemText(title, desc, Modifier.weight(1f))
        Spacer(Modifier.width(14.dp))
        AniSwitch(checked, width = 48.dp, height = 28.dp)
    }
}

@Composable
private fun ChoiceItem(title: String, desc: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
        ItemText(title, desc)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) TvTokens.AccentGradient else SolidColor(Color.White.copy(alpha = 0.06f)))
                        .border(1.dp, Color.White.copy(alpha = if (on) 0f else 0.10f), RoundedCornerShape(50))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (on) Color.White else TvTokens.TextSecondary, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ActionItem(title: String, desc: String, action: String, danger: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemText(title, desc, Modifier.weight(1f))
        Spacer(Modifier.width(14.dp))
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = TvTokens.AccentGlow, strokeWidth = 2.5.dp)
        else Text(
            action, color = if (danger) Danger else TvTokens.AccentPale, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background((if (danger) Danger else TvTokens.Accent).copy(alpha = 0.14f))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun UpdateItem(c: SettingsController) {
    val u = c.update
    val (desc, action) = when (u) {
        UpdateState.Idle -> "You're on v${c.versionName}" to "Check"
        UpdateState.Checking -> "Checking…" to ""
        UpdateState.UpToDate -> "You're on the latest version, v${c.versionName}" to "Check again"
        is UpdateState.Available -> "v${u.info.versionName} is ready to install" to "Install"
        is UpdateState.Downloading -> "Downloading v${u.info.versionName}… ${(u.progress * 100).toInt()}%" to ""
        UpdateState.Failed -> "Couldn't reach the update server" to "Retry"
    }
    Column {
        ActionItem("Check for updates", desc, action, busy = u is UpdateState.Checking || u is UpdateState.Downloading, onClick = c::updateAction)
        (u as? UpdateState.Downloading)?.let {
            LinearProgressIndicator(
                progress = { it.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = TvTokens.AccentGlow, trackColor = Color.White.copy(alpha = 0.08f),
            )
        }
        (u as? UpdateState.Available)?.info?.updateNotes?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = TvTokens.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp))
        }
    }
}

@Composable
private fun InfoItem(title: String, value: String) {
    ItemText(title, value, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp))
}
