package com.example.aniflow.ui.tv

import com.example.aniflow.ui.tv.home.episodeLabel
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.aniflow.data.model.Anime
import com.example.aniflow.ui.tv.home.TvTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val KEYS = "abcdefghijklmnopqrstuvwxyz1234567890".map { it.toString() }
private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Slice of Life",
    "Mystery", "Thriller", "Psychological", "Supernatural", "Horror", "Sports", "Mecha", "Music",
)
private val KEY_W = 44.dp
private val KEY_H = 38.dp
private val KEY_GAP = 6.dp

/**
 * TV search. An on-screen keyboard instead of a text field: a focused TextField on TV pops the
 * system IME over the whole screen and swallows the D-pad, which is what made Search feel stuck.
 * Hardware keyboards and remotes with number keys still type directly (see onPreviewKeyEvent).
 */
@Composable
fun TvBrowseScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedGenre: String?,
    onGenreSelect: (String?) -> Unit,
    results: List<Anime>,
    hasNextPage: Boolean,
    isSearchLoading: Boolean,
    onLoadMore: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    recent: List<String> = emptyList(),
    onClearRecent: () -> Unit = {},
) {
    // Local source of truth for edits. `query` only catches up on the next recomposition, so keys
    // arriving faster than a frame (held keys, `input text`, quick typists) used to be appended to a
    // stale value and dropped characters: "wild last boss" came out as "ild lt bos".
    var currentQuery by remember { mutableStateOf(query) }
    val type: (String) -> Unit = { val t = it.take(60); currentQuery = t; onQueryChange(t) }

    Row(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val n = e.nativeKeyEvent
                when {
                    n.keyCode == android.view.KeyEvent.KEYCODE_DEL -> {
                        type(currentQuery.dropLast(1)); true
                    }
                    n.keyCode == android.view.KeyEvent.KEYCODE_SPACE -> {
                        if (currentQuery.isNotEmpty() && !currentQuery.endsWith(' ')) type("$currentQuery ")
                        true
                    }
                    n.isPrintingKey && !n.isCtrlPressed && n.unicodeChar != 0 -> {
                        val c = n.unicodeChar.toChar()
                        if (c.isLetterOrDigit() || c in "-'!?.:") { type(currentQuery + c.lowercaseChar()); true } else false
                    }
                    else -> false
                }
            }
    ) {
        // The keyboard and the genre row don't both fit on a 540dp-tall TV, so this column scrolls,
        // and it snaps whole sections into view as focus moves between them: the keyboard with its
        // query box at the top, or the genre row at the bottom. (Delayed a beat so focus's own
        // bring-into-view scroll, which runs right after the focus change, doesn't cancel it.)
        val leftScroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        Column(Modifier.width(KEY_W * 6 + KEY_GAP * 5).fillMaxHeight().verticalScroll(leftScroll).padding(bottom = 20.dp)) {
            QueryBox(currentQuery)
            Spacer(Modifier.height(14.dp))
            // Voice: the TV's own recogniser (Google on Android TV). The key only appears when one
            // is installed, so remotes without a mic never see a dead button.
            val context = androidx.compose.ui.platform.LocalContext.current
            val voiceIntent = remember {
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say an anime title")
                    .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Google TTS also claims this action on Android TV, which pops a chooser. Go
                // straight to the real recogniser: Google TV's voice input, then the Google app.
                val handlers = runCatching {
                    context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
                }.getOrDefault(emptyList())
                val pick = listOf("com.google.android.katniss", "com.google.android.googlequicksearchbox").firstOrNull { it in handlers }
                    ?: handlers.firstOrNull { !it.contains("tts") }
                pick?.let { intent.setPackage(it) }
            }
            val voice = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { r ->
                r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                    ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { type(it) }
            }
            Box(Modifier.onFocusChanged { if (it.hasFocus) scope.launch { delay(60); leftScroll.animateScrollTo(0) } }) {
            Keyboard(
                onVoice = voiceIntent?.let { i -> { runCatching { voice.launch(i) } } },
                onKey = { type(currentQuery + it) },
                onSpace = { if (currentQuery.isNotEmpty() && !currentQuery.endsWith(' ')) type("$currentQuery ") },
                onDelete = { type(currentQuery.dropLast(1)) },
                onClear = { type("") },
            )
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.onFocusChanged { if (it.hasFocus) scope.launch { delay(60); leftScroll.animateScrollTo(leftScroll.maxValue) } }) {
            Text(
                "BROWSE BY GENRE", color = TvTokens.TextTertiary, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                modifier = Modifier.focusRestorer().focusGroup()
            ) {
                items(GENRES) { g ->
                    GenreChip(g, selected = g == selectedGenre) { currentQuery = ""; onGenreSelect(if (g == selectedGenre) null else g) }
                }
            }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp)) {
            val q = query.trim()
            // Typing on a remote is slow: one press brings back anything searched before.
            if (recent.isNotEmpty() && selectedGenre == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT", color = TvTokens.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.width(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                        modifier = Modifier.focusRestorer().focusGroup()
                    ) {
                        items(recent) { r -> GenreChip(r, selected = r.equals(q, ignoreCase = true)) { type(r) } }
                        item { GenreChip("Clear", selected = false, onClick = onClearRecent) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(30.dp)) {
                Text(
                    text = when {
                        q.isNotEmpty() -> "Results for “$q”"
                        selectedGenre != null -> selectedGenre
                        else -> "Trending right now"
                    },
                    color = TvTokens.TextPrimary, fontSize = TvTokens.RowTitle, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (isSearchLoading) { Spacer(Modifier.width(12.dp)); LoadingDots() }
            }
            Spacer(Modifier.height(10.dp))
            when {
                results.isEmpty() && isSearchLoading -> SkeletonGrid()
                results.isEmpty() && q.isNotEmpty() -> NoResults(q)
                else -> ResultsGrid(results, q, selectedGenre, hasNextPage, onLoadMore, onAnimeClick)
            }
        }
    }
}

@Composable
private fun QueryBox(query: String) {
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(530); caretOn = !caretOn } }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(TvTokens.Surface, shape)
            .border(1.dp, Brush.horizontalGradient(listOf(TvTokens.AccentDeep, TvTokens.Accent, TvTokens.AccentDeep)), shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (query.isEmpty()) {
            Box(Modifier.width(2.dp).height(22.dp).background(TvTokens.AccentGlow.copy(alpha = if (caretOn) 1f else 0f)))
            Spacer(Modifier.width(8.dp))
            Text("Search anime", color = TvTokens.TextTertiary, fontSize = 17.sp)
        } else {
            Text(
                query.takeLast(22), color = TvTokens.TextPrimary, fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            Box(Modifier.padding(start = 2.dp).width(2.dp).height(22.dp).background(TvTokens.AccentGlow.copy(alpha = if (caretOn) 1f else 0f)))
        }
    }
}

@Composable
private fun Keyboard(onKey: (String) -> Unit, onSpace: () -> Unit, onDelete: () -> Unit, onClear: () -> Unit, onVoice: (() -> Unit)? = null) {
    Column(Modifier.focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        KEYS.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEach { k -> Key(k, Modifier.size(KEY_W, KEY_H)) { onKey(k) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            if (onVoice != null) {
                // Mic takes one key; space, delete and clear share the rest.
                val rest = (KEY_W * 5 + KEY_GAP * 4 - KEY_GAP * 2) / 3
                Key("🎙", Modifier.size(KEY_W, KEY_H), onVoice)
                Key("space", Modifier.size(rest, KEY_H), onSpace)
                Key("⌫", Modifier.size(rest, KEY_H), onDelete)
                Key("clear", Modifier.size(rest, KEY_H), onClear)
            } else {
                val third = (KEY_W * 6 + KEY_GAP * 5 - KEY_GAP * 2) / 3
                Key("space", Modifier.size(third + (third - KEY_W) / 2, KEY_H), onSpace)
                Key("⌫", Modifier.size(third - (third - KEY_W) / 4, KEY_H), onDelete)
                Key("clear", Modifier.size(third - (third - KEY_W) / 4, KEY_H), onClear)
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, TvTokens.FocusSpring, label = "key")
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.background(TvTokens.AccentGradient, shape) else Modifier.background(TvTokens.Surface, shape))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (focused) Color.White else TvTokens.TextSecondary,
            fontSize = if (label.length == 1) 17.sp else 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun GenreChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .graphicsLayer { val s = if (focused) 1.07f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.background(TvTokens.AccentGradient)
                    selected -> Modifier.background(TvTokens.AccentDeep)
                    else -> Modifier.background(TvTokens.Surface)
                }
            )
            .border(1.dp, if (selected && !focused) TvTokens.AccentGlow else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label, color = if (focused || selected) Color.White else TvTokens.TextSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ResultsGrid(
    results: List<Anime>,
    query: String,
    genre: String?,
    hasNextPage: Boolean,
    onLoadMore: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
) {
    // Pages can overlap; a duplicate key would crash the lazy grid.
    val items = remember(results) { results.distinctBy { it.id } }
    val gridState = rememberLazyGridState()
    val loadMore by rememberUpdatedState(onLoadMore)
    val more by rememberUpdatedState(hasNextPage)
    LaunchedEffect(query, genre) { runCatching { gridState.scrollToItem(0) } }
    LaunchedEffect(gridState) {
        snapshotFlow { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to gridState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (more && total > 0 && last >= total - 6) loadMore() }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize().focusRestorer().focusGroup()
    ) {
        items(items, key = { it.id }) { anime -> TvPosterTile(anime) { onAnimeClick(anime) } }
    }
}

/** Poster + title tile shared by TV Search and My List. */
@Composable
internal fun TvPosterTile(anime: Anime, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, TvTokens.FocusSpring, label = "tile")
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "tileGlow")
    val shape = RoundedCornerShape(TvTokens.CardRadius)
    Column(modifier.zIndex(if (focused) 1f else 0f)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow((18 * glow).dp, shape, ambientColor = TvTokens.Accent, spotColor = TvTokens.AccentGlow)
                .border(2.dp, Color.White.copy(alpha = glow), shape)
                .clip(shape)
                .background(TvTokens.Surface)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(anime.coverImage).size(232, 348).crossfade(true).build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val score = anime.averageScore
            if (score != null && score > 0) {
                Text(
                    "$score%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            anime.title, color = if (focused) TvTokens.TextPrimary else TvTokens.TextSecondary,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        val meta = listOfNotNull(
            anime.seasonYear?.toString(),
            anime.format?.replace('_', ' ')?.let { if (it.length <= 3) it else it.lowercase().replaceFirstChar(Char::titlecase) },
            anime.episodeLabel(short = true),
        ).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, color = TvTokens.TextTertiary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun SkeletonGrid() {
    val a by rememberInfiniteTransition(label = "sk").animateFloat(
        0.35f, 0.85f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "skA"
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(6.dp),
    ) {
        items(8) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(TvTokens.CardRadius))
                    .background(TvTokens.SurfaceHigh.copy(alpha = a))
            )
        }
    }
}

@Composable
private fun NoResults(q: String) {
    Column(Modifier.fillMaxWidth().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No matches for “$q”", color = TvTokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Try fewer letters or the Japanese title. If nothing loads at all, check the connection.", color = TvTokens.TextTertiary, fontSize = 14.sp)
    }
}

@Composable
private fun LoadingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(420, delayMillis = i * 140), RepeatMode.Reverse),
                label = "dot$i"
            )
            Box(Modifier.size(6.dp).clip(CircleShape).background(TvTokens.AccentGlow.copy(alpha = a)))
        }
    }
}
