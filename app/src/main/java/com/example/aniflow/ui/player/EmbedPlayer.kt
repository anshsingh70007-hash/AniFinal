package com.example.aniflow.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.aniflow.DeviceType
import com.example.aniflow.data.model.AudioType
import com.example.aniflow.ui.tv.home.TvTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EMBED_HOST = "megaplay.buzz"
private const val LOAD_TIMEOUT_MS = 25_000L
private const val STALL_TIMEOUT_MS = 20_000L
private const val MAX_HEALS = 3
private const val AUTO_HIDE_MS = 5_000L
private const val STILL_WATCHING_AFTER = 3
private const val STATUS_CONNECTING = "Connecting to server…"
private val EPISODE_IN_URL = Regex("/stream/(?:ani|mal)/\\d+/(\\d+)/")
/** Present when the URL's episode is offset (a Part 2 played as episodes 13+ of Part 1): the real number. */
private val EPISODE_IN_FRAGMENT = Regex("#ep(\\d+)$")

private const val PAUSE_ALL_MEDIA =
    "document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();}catch(e){}});"
private const val MUTE_AND_PAUSE_ALL_MEDIA =
    "document.querySelectorAll('video,audio').forEach(function(m){try{m.muted=true;m.pause();}catch(e){}});"

/** Hides the page's own skip buttons (we mirror them) and lifts captions while our bar is up. */
private const val PAGE_CSS =
    ".botright,#skip-intro-megaplay,#skip-outro-megaplay{opacity:0!important;pointer-events:none!important}" +
        ".jw-captions{transition:transform .25s ease}.ani-ui .jw-captions{transform:translateY(-16%)}"

/**
 * One look at the page's player: JW Player's public API when present (MegaPlay uses it), else the
 * bare <video>. Reads playback state only — never stream URLs. Also keeps JW's own controls off.
 */
private val SNAPSHOT_JS = """
(function(){try{
 if((document.title||'').indexOf('Error')===0)return {err:1};
 if(document.head&&!document.getElementById('ani-css')){var st=document.createElement('style');st.id='ani-css';st.textContent='$PAGE_CSS';document.head.appendChild(st);}
 function vis(id){var e=document.getElementById(id);if(!e)return 0;var cs=getComputedStyle(e);return (e.style.display!=='none'&&cs.display!=='none'&&cs.visibility!=='hidden')?1:0;}
 var r={si:vis('skip-intro-megaplay'),so:vis('skip-outro-megaplay')};
 var vv=document.querySelector('video');r.r=(vv&&vv.playbackRate)?vv.playbackRate:1;
 var j=null;try{if(typeof jwplayer==='function'){j=jwplayer();if(!j||typeof j.getState!=='function')j=null;}}catch(e){j=null;}
 if(j){
  try{if(j.getControls&&j.getControls())j.setControls(false);}catch(e){}
  r.k='jw';r.t=j.getPosition()||0;r.d=j.getDuration()||0;r.s=j.getState()||'idle';
  var bp=0;try{bp=j.getBuffer()||0;}catch(e){}r.b=r.d>0?bp/100*r.d:0;
  try{var q=j.getQualityLevels()||[];r.q=q.map(function(x){return String(x.label||'');});r.qi=j.getCurrentQuality();}catch(e){}
  try{var c=j.getCaptionsList()||[];r.c=c.map(function(x){return String(x.label||x.id||'');});r.ci=j.getCurrentCaptions();}catch(e){}
  return r;
 }
 if(vv){r.k='v';r.t=vv.currentTime||0;r.d=isFinite(vv.duration)?vv.duration:0;
  r.s=vv.ended?'complete':(vv.paused?'paused':(vv.readyState<3?'buffering':'playing'));
  try{r.b=vv.buffered.length?vv.buffered.end(vv.buffered.length-1):0;}catch(e){r.b=0;}
  return r;}
 r.k='none';return r;
}catch(e){return {k:'none'};}})()
"""

/** Wraps a command so it runs against JW (`j`) or the bare video (`v`) and can never throw. */
private fun jw(body: String) =
    "(function(){var j=null;try{if(typeof jwplayer==='function'){j=jwplayer();if(!j||typeof j.getState!=='function')j=null;}}catch(e){j=null;}" +
        "var v=document.querySelector('video');try{$body}catch(e){}})()"

// play()/pause() are guarded by state: on JW7 a bare call toggles.
/**
 * Anime colour grade (AniFlow_Video_Enhancement_Research.md): gentle on purpose, since flat fills band
 * when pushed. Chromium folds these into one GPU colour matrix. Re-applied each tick because the
 * page can swap its <video>.
 */
private const val GRADE = "contrast(1.06) saturate(1.12) brightness(1.01)"
private fun jsGrade(on: Boolean) =
    "(function(){var v=document.querySelector('video');if(v&&v.style.filter!=='${if (on) GRADE else ""}')v.style.filter='${if (on) GRADE else ""}';})()"

/**
 * TV: put the player in HTML5 fullscreen. Only then does Chromium hand the video to a native
 * surface (a hardware video plane), which the TV's own picture engine upscales and sharpens to 4K,
 * as it did for the old native player. Fullscreen needs a real gesture: this arms a one-shot
 * listener, then [WebHolder.tap] delivers a genuine touch, which the listener swallows so the
 * page never sees it as play/pause.
 */
private const val JS_ARM_FULLSCREEN = "(function(){if(window.__aniFs)return;window.__aniFs=1;var done=false,until=0;" +
    "var evs=['pointerdown','pointerup','mousedown','mouseup','touchstart','touchend','click'];" +
    "function off(){evs.forEach(function(n){window.removeEventListener(n,h,true);});window.__aniFs=0;}" +
    "function h(e){if(!done){done=true;until=Date.now()+700;try{var j=(typeof jwplayer==='function')?jwplayer():null;" +
    "var el=(j&&j.getContainer&&j.getContainer())||document.documentElement;" +
    "var p=el.requestFullscreen?el.requestFullscreen():(el.webkitRequestFullscreen?el.webkitRequestFullscreen():null);" +
    "if(p&&p.catch)p.catch(function(){});}catch(x){}}" +
    "if(Date.now()<=until){e.stopImmediatePropagation();e.preventDefault();}else off();}" +
    "evs.forEach(function(n){window.addEventListener(n,h,{capture:true,passive:false});});setTimeout(off,3000);})()"

private val JS_PLAY = jw("if(j){var s=j.getState();if(s!=='playing'&&s!=='buffering')j.play();}else if(v){v.play();}")
private val JS_PAUSE = jw("if(j){var s=j.getState();if(s==='playing'||s==='buffering')j.pause();}else if(v){v.pause();}")

// Double.toString() is locale-independent; String.format("%f") would write "12,5" in some locales.
private fun jsSeek(ms: Long): String {
    val s = (ms / 1000.0).toString()
    return jw("if(j){j.seek($s);}else if(v){v.currentTime=$s;}")
}
private fun jsRate(r: Float): String {
    val s = r.toString()
    return jw("if(j&&j.setPlaybackRate){j.setPlaybackRate($s);}if(v){v.playbackRate=$s;}")
}
private fun jsQuality(i: Int) = jw("if(j){j.setCurrentQuality($i);}")
private fun jsCaptions(i: Int) = jw("if(j){j.setCurrentCaptions($i);}")
private fun jsSkip(kind: SkipKind): String {
    val id = if (kind == SkipKind.Intro) "skip-intro-megaplay" else "skip-outro-megaplay"
    return jw("var b=document.getElementById('$id');if(b){b.click();}")
}
private fun jsUiShown(shown: Boolean) =
    "document.documentElement&&document.documentElement.classList.toggle('ani-ui',$shown);"

private sealed interface Phase {
    data class Loading(val status: String) : Phase
    data object Ready : Phase
    data class Failed(val title: String, val detail: String) : Phase
}

/** Latest view of the page's player, written by the poll loop (plus optimistic UI writes). */
@Stable
private class PlaybackSnapshot {
    var kind by mutableStateOf("none")
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var state by mutableStateOf("idle")
    var rate by mutableFloatStateOf(1f)
    var qualities by mutableStateOf<List<String>>(emptyList())
    var quality by mutableIntStateOf(-1)
    var captions by mutableStateOf<List<String>>(emptyList())
    var caption by mutableIntStateOf(-1)
    var skip by mutableStateOf<SkipKind?>(null)

    val playing: Boolean get() = state == "playing" || state == "buffering"

    fun reset() {
        kind = "none"; positionMs = 0L; durationMs = 0L; bufferedMs = 0L; state = "idle"
        qualities = emptyList(); quality = -1; captions = emptyList(); caption = -1; skip = null
    }

    fun update(o: JSONObject) {
        kind = o.optString("k", "none")
        positionMs = secs(o.optDouble("t", 0.0))
        durationMs = secs(o.optDouble("d", 0.0))
        bufferedMs = secs(o.optDouble("b", 0.0))
        state = o.optString("s", "idle")
        rate = o.optDouble("r", 1.0).toFloat().takeIf { it > 0f && !it.isNaN() } ?: 1f
        qualities = o.optJSONArray("q").strings()
        quality = o.optInt("qi", -1)
        captions = o.optJSONArray("c").strings()
        caption = o.optInt("ci", -1)
        skip = when {
            o.optInt("si", 0) == 1 -> SkipKind.Intro
            o.optInt("so", 0) == 1 -> SkipKind.Outro
            else -> null
        }
    }
}

private fun secs(v: Double): Long = if (v.isNaN() || v.isInfinite() || v <= 0.0) 0L else (v * 1000).toLong()
private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }

/** Per-episode bookkeeping for the watchdogs; not UI state. */
private class EpisodeSession {
    var resumeLoaded = false
    var resumeMs: Long? = null
    var resumeTries = 0
    var heals = 0
    var lastT = 0L
    var lastAdvanceAt = 0L
    var endHandled = false
    var userPaused = false
    var kicked = false
    var fullscreenTries = 0
    var lastFullscreenTry = 0L
    fun markProgress(t: Long) { lastT = t; lastAdvanceAt = SystemClock.uptimeMillis() }
}

/** The page's WebView plus what its clients reported. A dead (renderer-crashed) view is never touched again. */
private class WebHolder(val view: WebView, val root: FrameLayout) {
    var dead = false
    /** The view Chromium hands over in HTML5 fullscreen (holds the native video surface). */
    var custom: View? = null
    var customCallback: WebChromeClient.CustomViewCallback? = null
    val inFullscreen: Boolean get() = custom != null

    fun showCustom(v: View, callback: WebChromeClient.CustomViewCallback?) {
        removeCustom()
        // Compose owns every key: the fullscreen view must never take focus from it.
        v.isFocusable = false
        (v as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        custom = v
        customCallback = callback
        // Android 13+: WebView registers its own back callback while fullscreen, above Compose's
        // BackHandler, so BACK would only leave fullscreen. Ours, registered after it at the same
        // priority, wins and hands BACK to the player as usual.
        if (Build.VERSION.SDK_INT >= 33) root.post { if (custom === v) claimBack() }
    }

    private var backCallback: Any? = null
    private var backDispatcher: Any? = null

    @androidx.annotation.RequiresApi(33)
    private fun claimBack() {
        releaseBack()
        val dispatcher = root.findOnBackInvokedDispatcher() ?: return
        val cb = android.window.OnBackInvokedCallback {
            generateSequence(root.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<ComponentActivity>().firstOrNull()
                ?.onBackPressedDispatcher?.onBackPressed()
        }
        runCatching { dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb) }
            .onSuccess { backCallback = cb; backDispatcher = dispatcher }
    }

    private fun releaseBack() {
        if (Build.VERSION.SDK_INT < 33) return
        val cb = backCallback as? android.window.OnBackInvokedCallback ?: return
        val dispatcher = backDispatcher as? android.window.OnBackInvokedDispatcher
        backCallback = null
        backDispatcher = null
        runCatching { dispatcher?.unregisterOnBackInvokedCallback(cb) }
    }

    fun removeCustom() {
        releaseBack()
        custom?.let { runCatching { root.removeView(it) } }
        custom = null
        customCallback = null
    }

    /** A real touch in the middle of the page: the user gesture fullscreen requires. */
    fun tap() {
        if (dead || view.width == 0) return
        val x = view.width / 2f
        val y = view.height / 2f
        val t = SystemClock.uptimeMillis()
        for ((action, at) in listOf(MotionEvent.ACTION_DOWN to t, MotionEvent.ACTION_UP to t + 40)) {
            val e = MotionEvent.obtain(t, at, action, x, y, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            runCatching { view.dispatchTouchEvent(e) }
            e.recycle()
        }
    }
    var mainFrameError = false
    var httpStatus = 0

    suspend fun eval(script: String): String? {
        if (dead) return null
        return withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine<String?> { cont ->
                try {
                    view.evaluateJavascript(script) { if (cont.isActive) cont.resume(it) }
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    fun run(script: String) {
        if (!dead) runCatching { view.evaluateJavascript(script, null) }
    }

    // WebView.onPause() alone is "best effort" and leaves HTML5 media playing on some (older TV)
    // builds, so the media is paused explicitly first.
    fun silence() {
        run(PAUSE_ALL_MEDIA)
        if (!dead) runCatching { view.onPause() }
        view.keepScreenOn = false
    }

    fun destroy() {
        view.keepScreenOn = false
        runCatching { customCallback?.onCustomViewHidden() }
        removeCustom()
        run(MUTE_AND_PAUSE_ALL_MEDIA)
        runCatching {
            if (!dead) view.onPause()
            (root.parent as? ViewGroup)?.removeView(root)
            if (!dead) { view.stopLoading(); view.loadUrl("about:blank") }
            view.destroy()
        }
        dead = true
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildHolder(context: Context, onRendererGone: () -> Unit): WebHolder {
    val view = WebView(context)
    val root = FrameLayout(context)
    val holder = WebHolder(view, root)
    root.setBackgroundColor(android.graphics.Color.BLACK)
    root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    view.setBackgroundColor(android.graphics.Color.BLACK)
    // The page is only a video surface: Compose owns every key and touch.
    view.isFocusable = false
    view.isFocusableInTouchMode = false
    view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    with(view.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        setSupportMultipleWindows(false)          // ad pop-ups have nowhere to open
        javaScriptCanOpenWindowsAutomatically = false
        allowFileAccess = false
        allowContentAccess = false
    }
    view.webChromeClient = object : WebChromeClient() {
        // No grey "play" poster flashing before the first frame.
        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean { result?.cancel(); return true }
        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean { result?.cancel(); return true }
        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean { result?.cancel(); return true }
        override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
        override fun onShowCustomView(v: View?, callback: CustomViewCallback?) {
            if (v == null || holder.dead) { callback?.onCustomViewHidden(); return }
            holder.showCustom(v, callback)
        }
        override fun onHideCustomView() { holder.removeCustom() }
    }
    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            // Stay on the player: anything that tries to navigate elsewhere (ads) is dropped.
            val host = request?.url?.host.orEmpty()
            return !(host == EMBED_HOST || host.endsWith(".$EMBED_HOST"))
        }
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) holder.mainFrameError = true
        }
        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            if (request?.isForMainFrame == true) holder.httpStatus = errorResponse?.statusCode ?: 0
        }
        // The renderer is the first thing a low-RAM TV kills. Returning true keeps the app alive;
        // this view is unusable afterwards and is replaced.
        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            holder.dead = true
            onRendererGone()
            return true
        }
    }
    return holder
}

private fun Context.isOnline(): Boolean {
    return try {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        true
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun FocusRequester.grab() {
    repeat(5) {
        if (runCatching { requestFocus() }.isSuccess) return
        delay(40)
    }
}

/**
 * Plays an episode in MegaPlay's embedded player, behind AniFlow's own controls.
 *
 * The page is just the video surface: JW Player's controls are switched off and every action goes
 * through JW's public API (play/pause, seek, speed, captions, quality, the page's skip buttons).
 * On top sit purple, Netflix-style controls, Skip Intro/Outro, Up Next with autoplay, and
 * self-healing: a load or stall watchdog reloads and resumes, a missing dub falls back to sub,
 * a renderer crash rebuilds the WebView, and an offline spell pauses the watchdogs until the
 * network is back.
 */
@Composable
fun EmbedPlayer(
    url: String,
    viewModel: PlayerViewModel,
    animeId: Int,
    deviceType: DeviceType,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val anime by viewModel.anime.collectAsState()
    val episodes by viewModel.episodeList.collectAsState()
    val index by viewModel.currentEpisodeIndex.collectAsState()
    val audio by viewModel.selectedAudioType.collectAsState()
    val autoNext by viewModel.autoPlayNextEpisode.collectAsState()
    val isTv = deviceType == DeviceType.TV
    val animeColorDefault = remember { com.example.aniflow.ui.settings.animeColorDefault(context) }
    val animeColor = remember { com.example.aniflow.data.SettingsStore(context.applicationContext).animeColor(animeColorDefault) }
        .collectAsState(initial = animeColorDefault)

    // The URL names the episode; trust it over the index so progress is never saved against an
    // episode other than the one playing.
    val urlEpisode = remember(url) {
        (EPISODE_IN_FRAGMENT.find(url) ?: EPISODE_IN_URL.find(url))?.groupValues?.get(1)?.toIntOrNull()
    }
    val epIndex = episodes.indexOfFirst { it.number == urlEpisode }.takeIf { it >= 0 } ?: index
    val episode = episodes.getOrNull(epIndex)
    val nextEpisode = episodes.getOrNull(epIndex + 1)
    val episodeRef = rememberUpdatedState(episode)
    val title = anime?.let { it.englishTitle ?: it.title }.orEmpty()
    val art = anime?.let { it.bannerImage ?: it.coverImage }

    val snap = remember { PlaybackSnapshot() }
    val session = remember(url) { EpisodeSession() }
    val sessionRef = rememberUpdatedState(session)
    var phase by remember(url) { mutableStateOf<Phase>(Phase.Loading(STATUS_CONNECTING)) }
    var generation by remember { mutableIntStateOf(0) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var rendererGone by remember { mutableIntStateOf(0) }

    val holder: WebHolder? = remember(generation) {
        try {
            buildHolder(context) { rendererGone++ }
        } catch (t: Throwable) {
            android.util.Log.w("EmbedPlayer", "WebView unavailable: ${t.message}")
            null
        }
    }

    // UI state
    var chromeVisible by remember { mutableStateOf(false) }
    var poke by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var restoreFocusTo by remember { mutableStateOf<PlayerPanel?>(null) }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekPoke by remember { mutableIntStateOf(0) }
    var seekHud by remember { mutableStateOf(false) }
    var seekBarFocused by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var upNextDismissed by remember(url) { mutableStateOf(false) }
    var ended by remember(url) { mutableStateOf(false) }
    var countdown by remember(url) { mutableStateOf<Int?>(null) }
    var stillWatching by remember { mutableStateOf(false) }
    var autoAdvances by remember { mutableIntStateOf(0) }
    var wantedRate by remember { mutableFloatStateOf(1f) }
    var rateTouched by remember { mutableStateOf(false) }
    // Settings > Default speed. (The embed player used to start every episode at 1x whatever the
    // setting said.) A speed picked in the player wins for the rest of the session.
    LaunchedEffect(Unit) { viewModel.playbackSpeed.collect { if (!rateTouched) wantedRate = it } }
    val autoSkip by viewModel.autoSkipIntro.collectAsState()
    var wantedCaption by remember { mutableStateOf<String?>(null) }
    var wantedQuality by remember { mutableStateOf<String?>(null) }
    var carryOverMs by remember { mutableStateOf<Long?>(null) }
    var level by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var levelHideNonce by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(-1f) }
    var volume by remember { mutableFloatStateOf(-1f) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val cardFocus = remember { FocusRequester() }
    val panelFocus = remember { PlayerPanel.entries.associateWith { FocusRequester() } }

    // ---- actions ---------------------------------------------------------------------------

    fun showChrome() { chromeVisible = true; poke++ }
    fun saveNow() {
        if (snap.durationMs > 0 && snap.positionMs > 0) viewModel.saveProgress(animeId, snap.positionMs, snap.durationMs, episodeRef.value)
    }
    fun play() { session.userPaused = false; holder?.run(JS_PLAY); snap.state = "playing" }
    fun pause() { session.userPaused = true; holder?.run(JS_PAUSE); snap.state = "paused" }
    fun replay() {
        ended = false; session.endHandled = false
        holder?.run(jsSeek(0)); holder?.run(JS_PLAY)
        session.markProgress(0)
    }
    fun togglePlay() { if (snap.playing) pause() else if (snap.state == "complete" || ended) replay() else play() }

    fun commitSeek() {
        val t = seekTarget ?: return
        seekTarget = null
        seekHud = false
        holder?.run(jsSeek(t))
        snap.positionMs = t
        session.markProgress(t)
        if (t < snap.durationMs - 25_000) { ended = false; session.endHandled = false }
    }
    fun seekBy(delta: Long, hud: Boolean) {
        val d = snap.durationMs
        if (d <= 0 || phase !is Phase.Ready) return
        val base = seekTarget ?: snap.positionMs
        seekTarget = (base + delta).coerceIn(0L, (d - 2_000L).coerceAtLeast(0L))
        seekHud = hud
        seekPoke++
    }
    fun stepFor(repeat: Int): Long = when {
        repeat < 6 -> 10_000L
        repeat < 20 -> 30_000L
        else -> 60_000L
    }

    fun playEpisode(i: Int) {
        if (i !in episodes.indices || i == epIndex) return
        autoAdvances = 0
        stillWatching = false
        saveNow()
        viewModel.embedEpisode(i)
    }
    fun advance(auto: Boolean) {
        val n = epIndex + 1
        if (n !in episodes.indices) return
        if (auto) {
            if (autoAdvances >= STILL_WATCHING_AFTER) { stillWatching = true; pause(); return }
            autoAdvances++
        } else {
            autoAdvances = 0
        }
        if (snap.durationMs > 0) {
            val done = ended || snap.durationMs - snap.positionMs < 180_000
            viewModel.saveProgress(animeId, if (done) snap.durationMs else snap.positionMs, snap.durationMs, episode)
        }
        stillWatching = false
        viewModel.embedEpisode(n)
    }
    fun switchAudio(type: AudioType) {
        if (type == audio) return
        carryOverMs = snap.positionMs.takeIf { it > 5_000 }
        saveNow()
        viewModel.embedAudio(type)
    }
    fun setRate(r: Float) { rateTouched = true; wantedRate = r; holder?.run(jsRate(r)); snap.rate = r; toastText = "Speed: ${speedLabel(r)}" }
    fun setCaption(i: Int) {
        val label = snap.captions.getOrNull(i) ?: return
        wantedCaption = label; holder?.run(jsCaptions(i)); snap.caption = i
        toastText = "Subtitles: $label"
    }
    fun setQuality(i: Int) {
        val label = snap.qualities.getOrNull(i) ?: return
        wantedQuality = label; holder?.run(jsQuality(i)); snap.quality = i
        toastText = "Quality: $label"
    }
    fun openPanel(p: PlayerPanel) { panel = p; chromeVisible = false }
    fun closePanel(restore: Boolean = true) {
        val p = panel
        panel = null
        if (restore && p != null) { restoreFocusTo = p; showChrome() }
    }
    fun retry() {
        session.heals = 0
        phase = Phase.Loading(STATUS_CONNECTING)
        if (holder == null || holder.dead) generation++ else reloadNonce++
    }
    fun heal(s: EpisodeSession, status: String) {
        s.heals++
        if (s.heals > MAX_HEALS) {
            holder?.silence()
            phase = Phase.Failed(
                "Playback keeps dropping",
                "The server isn't responding well right now. Retry, switch audio, or try again in a few minutes."
            )
            return
        }
        if (s.lastT > 5_000) { s.resumeMs = s.lastT; s.resumeTries = 0 }
        s.kicked = false
        phase = Phase.Loading(status)
        reloadNonce++
    }
    fun notFound() {
        if (audio == AudioType.DUB) {
            // Self-heal: most "missing" episodes are a dub that isn't out yet.
            toastText = "No dub for this episode yet. Switched to subtitles."
            carryOverMs = session.lastT.takeIf { it > 5_000 }
            viewModel.embedAudio(AudioType.SUB)
        } else {
            phase = Phase.Failed(
                "This episode isn't available yet",
                "The server doesn't have ${episodeLabel(episode).ifEmpty { "this episode" }} right now. Try again later or try the HD player."
            )
        }
    }
    fun unavailable() {
        // Not where we looked: MegaPlay may file it under the MAL id or the prequel. Same episode, same audio.
        if (viewModel.embedFallback(onNothing = ::notFound)) return
        notFound()
    }
    fun adjustLevel(side: Int, delta: Float) {
        val activity = context.findActivity() ?: return
        if (side < 0) {
            if (brightness < 0f) {
                brightness = activity.window.attributes.screenBrightness.takeIf { it >= 0f }
                    ?: runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
            }
            brightness = (brightness + delta).coerceIn(0.02f, 1f)
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness }
            level = "Brightness" to brightness
        } else {
            val am = context.getSystemService(AudioManager::class.java) ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            if (volume < 0f) volume = am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
            volume = (volume + delta).coerceIn(0f, 1f)
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0) }
            level = "Volume" to volume
        }
    }

    // ---- derived screen state ----------------------------------------------------------------

    val ready = phase is Phase.Ready
    val failed = phase as? Phase.Failed
    val remaining = snap.durationMs - snap.positionMs
    val creditsRolling = snap.skip == SkipKind.Outro || (snap.durationMs > 120_000 && remaining in 0..20_000)
    val showUpNext = ready && nextEpisode != null && !upNextDismissed && !stillWatching && panel == null && (creditsRolling || ended)
    val showEnd = ready && ended && !showUpNext && !stillWatching && panel == null
    val skipKind = if (ready && panel == null && !showUpNext && !showEnd && !stillWatching && seekTarget == null) snap.skip else null
    val cardShowing = showUpNext || showEnd || stillWatching

    // Settings > Auto-skip intro & credits: show what is about to happen, give a beat to cancel
    // by seeking, then press the page's own skip button.
    LaunchedEffect(skipKind, autoSkip) {
        val k = skipKind ?: return@LaunchedEffect
        if (!autoSkip) return@LaunchedEffect
        toastText = if (k == SkipKind.Intro) "Skipping intro…" else "Skipping credits…"
        delay(1_400)
        if (snap.skip == k) {
            holder?.run(jsSkip(k))
            session.markProgress(snap.positionMs)
        }
    }

    // ---- the page: load, poll, heal ------------------------------------------------------------

    LaunchedEffect(holder, url, reloadNonce) {
        val h = holder
        if (h == null) {
            phase = Phase.Failed(
                "Can't open the player",
                "Android System WebView is missing or updating on this device. Update it from the Play Store, then retry."
            )
            return@LaunchedEffect
        }
        if (phase is Phase.Failed || h.dead) return@LaunchedEffect
        val s = session
        if (!s.resumeLoaded) {
            s.resumeLoaded = true
            val carried = carryOverMs
            carryOverMs = null
            s.resumeMs = carried ?: runCatching { viewModel.getSavedProgressEntry(animeId) }.getOrNull()
                ?.takeIf { it.episodeNumber == episodeRef.value?.number && it.progressMs > 30_000 && it.progressMs < it.durationMs - 60_000 }
                ?.progressMs
        }
        snap.reset()
        h.mainFrameError = false
        h.httpStatus = 0
        h.view.loadUrl(url, mapOf("Referer" to "https://$EMBED_HOST/"))
        var loadStart = SystemClock.uptimeMillis()
        var readyAt = 0L
        var lastSaved = -1L
        s.lastAdvanceAt = loadStart
        while (true) {
            delay(if (chromeVisible || seekTarget != null || phase !is Phase.Ready) 500L else 1_000L)
            val now = SystemClock.uptimeMillis()

            // Offline: hold every watchdog; pick up where we were once the network is back.
            if (!context.isOnline()) {
                offline = true
                if (phase is Phase.Loading) phase = Phase.Loading("You're offline. Waiting for a connection…")
                loadStart = now
                s.lastAdvanceAt = now
                continue
            }
            if (offline) {
                offline = false
                if (phase !is Phase.Ready || snap.state == "error" || h.mainFrameError) {
                    heal(s, "Back online. Resuming…")
                    return@LaunchedEffect
                }
            }

            val status = h.httpStatus
            if (status in 400..499 && status != 408 && status != 429) { unavailable(); return@LaunchedEffect }
            val o = h.eval(SNAPSHOT_JS)?.takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (o?.optInt("err", 0) == 1) { unavailable(); return@LaunchedEffect }
            if (o != null) snap.update(o)
            val mediaReady = o != null && snap.kind != "none" && snap.durationMs > 0
            if (!mediaReady) {
                if (h.mainFrameError || status >= 500 || now - loadStart > LOAD_TIMEOUT_MS) {
                    heal(s, if (s.heals == 0) "The server is slow. Trying again…" else "Still trying…")
                    return@LaunchedEffect
                }
                continue
            }

            if (phase !is Phase.Ready) {
                phase = Phase.Ready
                readyAt = now
                s.lastAdvanceAt = now
                showChrome()   // shown once on start, so it's clear the controls exist
            }
            val t = snap.positionMs
            val d = snap.durationMs
            val st = snap.state
            if (st == "error") { heal(s, "Playback hiccup. Recovering…"); return@LaunchedEffect }

            // The user's choices survive reloads and new episodes.
            if (abs(snap.rate - wantedRate) > 0.01f) h.run(jsRate(wantedRate))
            if (now - readyAt < 15_000) {
                wantedCaption?.let { want -> val i = snap.captions.indexOf(want); if (i >= 0 && i != snap.caption) h.run(jsCaptions(i)) }
                wantedQuality?.let { want -> val i = snap.qualities.indexOf(want); if (i >= 0 && i != snap.quality) h.run(jsQuality(i)) }
            }

            // Resume (saved progress, a heal, or an audio switch). Some HLS seeks are ignored
            // until the first segments land, so it is retried a few times.
            s.resumeMs?.let { target ->
                if (st == "playing" || st == "paused") {
                    if (abs(t - target) < 5_000 || s.resumeTries >= 4) {
                        s.resumeMs = null
                    } else {
                        h.run(jsSeek(target)); s.resumeTries++; s.markProgress(target)
                    }
                }
            }

            // Autoplay kick: some WebViews ignore autoplay until poked.
            if (st == "playing") s.kicked = true

            // Picture: the colour grade, or (TV, grade off) the TV's own engine via a native surface.
            val grade = animeColor.value
            h.run(jsGrade(grade))
            if (isTv && !grade && st == "playing" && !h.inFullscreen && s.fullscreenTries < 2 &&
                now - s.lastFullscreenTry > 4_000 && now - readyAt > 1_500
            ) {
                s.fullscreenTries++
                s.lastFullscreenTry = now
                h.run(JS_ARM_FULLSCREEN)
                delay(80)
                h.tap()
            }
            else if (!s.kicked && !s.userPaused && (st == "paused" || st == "idle") && now - readyAt < 10_000) h.run(JS_PLAY)

            // Stall watchdog: only time spent trying to play counts.
            if (st == "playing" || st == "buffering") {
                if (abs(t - s.lastT) >= 250) {
                    s.lastT = t; s.lastAdvanceAt = now
                } else if (now - s.lastAdvanceAt > STALL_TIMEOUT_MS) {
                    heal(s, "The stream stalled. Reconnecting…")
                    return@LaunchedEffect
                }
            } else {
                s.lastAdvanceAt = now
                if (t > 0) s.lastT = t
            }

            h.view.keepScreenOn = st == "playing" || st == "buffering"
            if (t > 0 && abs(t - lastSaved) >= 15_000) {
                viewModel.saveProgress(animeId, t, d, episodeRef.value); lastSaved = t
            }
            if (!s.endHandled && (st == "complete" || (d > 60_000 && t >= d - 1_000))) {
                s.endHandled = true
                ended = true
                viewModel.saveProgress(animeId, d, d, episodeRef.value)
            }
        }
    }

    // Renderer crash: replace the WebView and resume where it died.
    LaunchedEffect(rendererGone) {
        if (rendererGone == 0) return@LaunchedEffect
        val s = session
        s.heals++
        if (s.lastT > 5_000) { s.resumeMs = s.lastT; s.resumeTries = 0 }
        phase = if (s.heals > MAX_HEALS) {
            Phase.Failed("The player keeps crashing", "This device ran low on memory. Close other apps and retry.")
        } else {
            Phase.Loading("Recovering the player…")
        }
        generation++
    }

    // Leaving must silence the page on every WebView version, on background (HOME, screensaver,
    // input switch) and on exit.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(holder, lifecycle) {
        val h = holder
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    h?.silence()
                    sessionRef.value.userPaused = true
                    saveNow()
                }
                // Comes back paused with the controls up; OK resumes.
                Lifecycle.Event.ON_RESUME -> {
                    if (h != null && !h.dead) runCatching { h.view.onResume() }
                    chromeVisible = true; poke++
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            saveNow()
            h?.destroy()
        }
    }

    // Phone: landscape, edge to edge. PlayerScreen's own fullscreen effect never runs for the
    // embed (it returns into this composable first), which left phones in portrait.
    if (!isTv) {
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            val window = activity?.window
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                window?.let { w ->
                    WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                    w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                }
            }
        }
    }

    // ---- timers ------------------------------------------------------------------------------

    LaunchedEffect(seekPoke) {
        if (seekTarget == null) return@LaunchedEffect
        delay(if (seekHud) 650L else 900L)
        commitSeek()
    }
    LaunchedEffect(chromeVisible, poke, snap.playing, panel, seekTarget != null) {
        if (chromeVisible && snap.playing && panel == null && seekTarget == null) {
            delay(AUTO_HIDE_MS)
            chromeVisible = false
        }
    }
    LaunchedEffect(chromeVisible || (seekHud && seekTarget != null), holder) {
        holder?.run(jsUiShown(chromeVisible || (seekHud && seekTarget != null)))
    }
    LaunchedEffect(toastText) { if (toastText != null) { delay(2_600); toastText = null } }
    LaunchedEffect(levelHideNonce) { if (levelHideNonce > 0) { delay(700); level = null } }

    // Up Next countdown: only in the last seconds (or once it ended), only with autoplay on.
    val countdownArmed = showUpNext && autoNext && (ended || (snap.durationMs > 120_000 && remaining <= 10_000))
    LaunchedEffect(countdownArmed, url) {
        if (!countdownArmed) { countdown = null; return@LaunchedEffect }
        for (n in 10 downTo 1) { countdown = n; delay(1_000) }
        countdown = null
        advance(auto = true)
    }

    // Focus follows what is on screen, so the remote never has to "find" the UI first.
    LaunchedEffect(chromeVisible, panel, cardShowing, failed != null, ready) {
        if (!isTv || panel != null) return@LaunchedEffect
        delay(48)
        val target = when {
            failed != null || cardShowing -> cardFocus
            chromeVisible && ready -> restoreFocusTo?.let { panelFocus[it] } ?: playFocus
            skipKind != null -> skipFocus
            else -> rootFocus
        }
        restoreFocusTo = null
        target.grab()
    }
    LaunchedEffect(skipKind != null) {
        if (!isTv || chromeVisible || panel != null || cardShowing || failed != null) return@LaunchedEffect
        delay(48)
        (if (skipKind != null) skipFocus else rootFocus).grab()
    }

    BackHandler {
        when {
            panel != null -> closePanel()
            seekTarget != null -> { seekTarget = null; seekHud = false }
            stillWatching -> onBack()
            isTv && chromeVisible && ready -> chromeVisible = false
            showUpNext -> upNextDismissed = true
            else -> onBack()
        }
    }

    // ---- keys --------------------------------------------------------------------------------

    val onKey: (KeyEvent) -> Boolean = handler@{ ev ->
        if (ev.type != KeyEventType.KeyDown) return@handler false
        val rep = ev.nativeKeyEvent.repeatCount
        autoAdvances = 0
        // Media keys work in every state.
        when (ev.key) {
            Key.MediaPlayPause -> { if (snap.playing) { pause(); showChrome() } else togglePlay(); return@handler true }
            Key.MediaPlay -> { play(); return@handler true }
            Key.MediaPause, Key.MediaStop -> { pause(); showChrome(); return@handler true }
            Key.MediaFastForward -> { seekBy(stepFor(rep), hud = !chromeVisible); return@handler true }
            Key.MediaRewind -> { seekBy(-stepFor(rep), hud = !chromeVisible); return@handler true }
            Key.MediaNext -> { playEpisode(epIndex + 1); return@handler true }
            Key.MediaPrevious -> { playEpisode(epIndex - 1); return@handler true }
        }
        if (!ready || panel != null || stillWatching) return@handler false
        if (chromeVisible) { poke++; return@handler false }
        when (ev.key) {
            Key.DirectionLeft -> if (cardShowing) false else { seekBy(-stepFor(rep), hud = true); true }
            Key.DirectionRight -> if (cardShowing) false else { seekBy(stepFor(rep), hud = true); true }
            Key.DirectionUp, Key.DirectionDown, Key.Menu -> { showChrome(); true }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar ->
                // A focused Skip / Up Next button takes OK itself.
                if (cardShowing || skipKind != null) false
                else { if (snap.playing) { pause(); showChrome() } else togglePlay(); true }
            else -> false
        }
    }

    // TV: the focused seek bar scrubs with LEFT/RIGHT (accelerating while held), OK commits.
    val seekBarModifier = if (!isTv) Modifier else Modifier
        .onFocusChanged {
            seekBarFocused = it.isFocused
            if (!it.isFocused && seekTarget != null && !seekHud) commitSeek()
        }
        .onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (ev.key) {
                Key.DirectionLeft -> { seekBy(-stepFor(ev.nativeKeyEvent.repeatCount), hud = false); true }
                Key.DirectionRight -> { seekBy(stepFor(ev.nativeKeyEvent.repeatCount), hud = false); true }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (seekTarget != null) commitSeek() else togglePlay(); true }
                else -> false
            }
        }
        .focusable()

    val actions = ChromeActions(
        playPause = { togglePlay(); poke++ },
        back10 = { seekBy(-10_000L, hud = false); poke++ },
        fwd10 = { seekBy(10_000L, hud = false); poke++ },
        next = { advance(auto = false) },
        openPanel = { openPanel(it) },
        dragPreview = { v -> seekTarget = v; seekHud = false },
        dragCommit = { v -> seekTarget = v; commitSeek(); poke++ },
    )

    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val endsAt = if (ready && snap.durationMs > 0) {
        val left = ((snap.durationMs - snap.positionMs) / snap.rate.coerceAtLeast(0.25f)).toLong()
        timeFormat.format(java.util.Date(System.currentTimeMillis() + left))
    } else null

    // ---- layout ------------------------------------------------------------------------------

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent(onKey)
            .focusable()
    ) {
        if (holder != null) {
            // key(holder): a rebuilt WebView needs a new AndroidView; the factory runs once per node.
            key(holder) {
                AndroidView(
                    factory = { holder.root.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!isTv) {
            GestureLayer(
                onTap = {
                    autoAdvances = 0
                    when {
                        panel != null -> closePanel()
                        ready -> if (chromeVisible) chromeVisible = false else showChrome()
                    }
                },
                onDoubleTap = { side ->
                    autoAdvances = 0
                    when (side) {
                        -1 -> seekBy(-10_000L, hud = !chromeVisible)
                        1 -> seekBy(10_000L, hud = !chromeVisible)
                        else -> togglePlay()
                    }
                },
                onLevel = { side, delta -> adjustLevel(side, delta) },
                onLevelEnd = { levelHideNonce++ },
            )
        }

        if (ready && snap.state == "buffering" && seekTarget == null) {
            BrandSpinner(64.dp, Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(phase is Phase.Loading, enter = fadeIn(), exit = fadeOut(tween(500))) {
            LoadingStage(art, title, episodeLabel(episode), (phase as? Phase.Loading)?.status ?: "Starting playback…")
        }

        AnimatedVisibility(ready && chromeVisible && panel == null, enter = fadeIn(tween(200)), exit = fadeOut(tween(350))) {
            Box(Modifier.fillMaxSize()) {
                TopInfo(
                    title = title,
                    subtitle = episodeLabel(episode),
                    endsAt = endsAt,
                    badge = if (audio == AudioType.DUB) "DUB" else "SUB",
                    isTv = isTv,
                    onBack = if (isTv) null else onBack,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                if (!isTv) CenterControls(snap.playing, actions, Modifier.align(Alignment.Center))
                BottomControls(
                    positionMs = snap.positionMs,
                    durationMs = snap.durationMs,
                    bufferedMs = snap.bufferedMs,
                    previewMs = if (seekHud) null else seekTarget,
                    playing = snap.playing,
                    isTv = isTv,
                    hasNext = nextEpisode != null,
                    hasQuality = snap.qualities.size > 1,
                    rate = snap.rate,
                    seekModifier = seekBarModifier,
                    seekFocused = seekBarFocused,
                    playFocus = playFocus,
                    panelFocus = panelFocus,
                    actions = actions,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        val liftBottom by animateDpAsState(
            if (chromeVisible) (if (isTv) 150.dp else 120.dp) else (if (isTv) 56.dp else 28.dp), label = "lift"
        )
        AnimatedVisibility(
            skipKind != null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = if (isTv) 48.dp else 24.dp, bottom = liftBottom),
            enter = slideInHorizontally { it / 2 } + fadeIn(),
            exit = fadeOut()
        ) {
            SkipButton(skipKind, skipFocus, onClick = {
                val k = skipKind ?: return@SkipButton
                holder?.run(jsSkip(k))
                session.markProgress(snap.positionMs)
            })
        }

        AnimatedVisibility(
            showUpNext,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = if (isTv) 40.dp else 20.dp, bottom = liftBottom),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            UpNextCard(nextEpisode, art, countdown, cardFocus, onPlay = { advance(auto = false) }, onDismiss = { upNextDismissed = true })
        }

        if (failed != null) Box(Modifier.fillMaxSize().background(TvTokens.Canvas.copy(alpha = 0.94f)))
        val lastFailed = remember { arrayOfNulls<Phase.Failed>(1) }
        if (failed != null) lastFailed[0] = failed
        AnimatedVisibility(failed != null, modifier = Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
            val f = lastFailed[0]
            CenterCard(
                title = f?.title.orEmpty(),
                body = f?.detail,
                primaryLabel = "Retry",
                onPrimary = { retry() },
                focusRequester = cardFocus,
                secondary = buildList {
                    if (audio == AudioType.SUB) add("Try Dub" to { switchAudio(AudioType.DUB) })
                    add("Try HD player" to { viewModel.retryNative() })
                    add("Back" to onBack)
                }
            )
        }

        AnimatedVisibility(showEnd, modifier = Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
            if (nextEpisode != null) {
                CenterCard(
                    "Episode finished", episodeLabel(episode), "Next Episode", { advance(auto = false) }, cardFocus,
                    secondary = listOf("Replay" to { replay() }, "Back" to onBack)
                )
            } else {
                CenterCard(
                    "You're all caught up", "That was the latest episode of ${title.ifEmpty { "this show" }}.", "Replay", { replay() }, cardFocus,
                    secondary = listOf("Back to details" to onBack)
                )
            }
        }

        AnimatedVisibility(stillWatching, modifier = Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
            CenterCard(
                "Still watching ${title.ifEmpty { "?" }}?", "Paused after a few episodes in a row.", "Continue watching",
                { stillWatching = false; autoAdvances = 0; advance(auto = false) }, cardFocus,
                secondary = listOf("Back" to onBack)
            )
        }

        AnimatedVisibility(seekHud && seekTarget != null && !chromeVisible, enter = fadeIn(tween(120)), exit = fadeOut(tween(250))) {
            val last = remember { longArrayOf(0L) }
            seekTarget?.let { last[0] = it }
            SeekHud(last[0], snap.positionMs, snap.durationMs, snap.bufferedMs, isTv)
        }

        level?.let { (label, value) -> LevelHud(label, value, Modifier.align(Alignment.Center)) }

        if (offline && ready) {
            PlayerToast("No internet connection. Playback resumes when you're back online.", Modifier.align(Alignment.TopCenter).padding(top = 28.dp))
        }
        AnimatedVisibility(
            toastText != null && !offline,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = fadeOut()
        ) {
            val last = remember { arrayOf("") }
            toastText?.let { last[0] = it }
            PlayerToast(last[0])
        }

        if (panel != null) Scrim(onDismiss = { closePanel() })
        AnimatedVisibility(
            panel != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            val last = remember { arrayOf(PlayerPanel.Episodes) }
            panel?.let { last[0] = it }
            when (last[0]) {
                PlayerPanel.Episodes -> EpisodesPanel(episodes, epIndex, isTv, art, onPick = { i -> closePanel(restore = false); playEpisode(i) })
                PlayerPanel.AudioSubs -> ChoicePanel(
                    title = "Audio & Subtitles",
                    isTv = isTv,
                    sections = listOf(
                        Triple("Audio", listOf("Japanese (Subtitled)", "English (Dub)"), if (audio == AudioType.DUB) 1 else 0),
                        Triple("Subtitles", snap.captions, snap.caption),
                    ),
                    note = if (snap.captions.isEmpty()) "Subtitles for this episode are part of the video." else null,
                    onPick = { section, i ->
                        if (section == 0) switchAudio(if (i == 1) AudioType.DUB else AudioType.SUB) else setCaption(i)
                        closePanel()
                    }
                )
                PlayerPanel.Speed -> ChoicePanel(
                    title = "Playback speed",
                    isTv = isTv,
                    sections = listOf(Triple("", PlayerSpeeds.map { speedLabel(it) }, PlayerSpeeds.indexOfFirst { abs(it - snap.rate) < 0.01f })),
                    onPick = { _, i -> setRate(PlayerSpeeds[i]); closePanel() }
                )
                PlayerPanel.Quality -> ChoicePanel(
                    title = "Quality",
                    isTv = isTv,
                    sections = listOf(Triple("", snap.qualities, snap.quality)),
                    onPick = { _, i -> setQuality(i); closePanel() }
                )
            }
        }
    }
}
