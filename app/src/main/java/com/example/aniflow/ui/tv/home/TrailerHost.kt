package com.example.aniflow.ui.tv.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.example.aniflow.data.remote.MediaExtras
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * One YouTube trailer surface: the full-screen trailer viewer on TV (home hero and detail).
 *
 * AniList trailers are YouTube ids, so they need a WebView. A WebView costs ~100-150 MB and 1-2 s
 * to start, which a 1 GB TV can afford exactly once. So there is a single instance, created on
 * first use, whose page is loaded once; every later trailer is a `loadVideoById` JS call into the
 * same player — no page reloads, no second Chromium renderer.
 *
 * Self-healing:
 *  - a video YouTube refuses (embed-disabled, removed, age-gated) is remembered for the process and
 *    [playFirst] moves on to the next candidate (Kitsu's upload of the same PV);
 *  - a renderer crash (the first thing a low-RAM TV kills) drops this WebView instead of the app;
 *    the next [play] builds a fresh one;
 *  - no usable WebView provider (missing, disabled, mid-update) turns trailers off; stills stay.
 */
class TrailerHost(private val context: Context) {
    /** True only once YouTube reports PLAYING, so the UI can fade the video in without a flash. */
    var isPlaying by mutableStateOf(false)
        private set

    /** Paused by the viewer (OK on the remote), as opposed to buffering or not started. */
    var isPaused by mutableStateOf(false)
        private set

    /** Last YouTube state reported; 0 = ended. */
    var ended by mutableStateOf(false)
        private set

    /** Last id YouTube refused; [playFirst] watches it to fail over without waiting out a timeout. */
    private var refusedId by mutableStateOf<String?>(null)

    private var lastState = -1
    private val stallCheck = Runnable { if (lastState == 3 && !destroyed) isPlaying = false }

    private var pageReady = false
    private var pendingId: String? = null
    private var currentId: String? = null
    private var destroyed = false
    private var unavailable = false

    /**
     * Created on the first [play], not at construction: initialising Chromium costs ~0.5-1.5 s on
     * the main thread, which used to land on the home screen's very first frame.
     */
    var webView: WebView? by mutableStateOf(null)
        private set

    private fun viewOrNull(): WebView? {
        webView?.let { return it }
        if (destroyed || unavailable) return null
        return try {
            createView().also { webView = it }
        } catch (t: Throwable) {
            android.util.Log.w("TrailerHost", "WebView unavailable, trailers off: ${t.message}")
            unavailable = true
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(): WebView = WebView(context.applicationContext).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setBackgroundColor(android.graphics.Color.BLACK)
        setOnTouchListener { _, _ -> true }
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.domStorageEnabled = true
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            // Returning true keeps the app alive when the renderer dies; this WebView is unusable
            // afterwards, so it is dropped and the next play() builds a new one.
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                if (view != null && view === webView) dropView()
                return true
            }
        }
        addJavascriptInterface(Bridge(), "AniTrailer")
    }

    // Bridge calls arrive on a JavaBridge thread. They hop to the main thread through the existing
    // view only — never through viewOrNull(), which could build a new WebView after destroy().
    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            webView?.post {
                if (destroyed) return@post
                pageReady = true
                val id = pendingId ?: return@post
                pendingId = null
                // Straight to JS: play(id) would no-op here because currentId already equals id,
                // which is why the first trailer after the page loaded never used to start.
                if (id == currentId) webView?.evaluateJavascript("aniPlay('$id')", null)
            }
        }

        // YT states: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued. Error => -2.
        @JavascriptInterface
        fun onState(state: Int) {
            webView?.post {
                if (destroyed) return@post
                lastState = state
                webView?.removeCallbacks(stallCheck)
                when (state) {
                    1 -> { isPlaying = true; isPaused = false; ended = false }
                    0 -> { isPlaying = false; currentId = null; ended = true }
                    // Paused (autoplay policy, focus loss) or buffering for more than a moment:
                    // hand the billboard back to the still instead of a frozen or black frame.
                    // PLAYING again brings the video back.
                    2 -> { isPlaying = false; isPaused = true }
                    3 -> webView?.postDelayed(stallCheck, STALL_MS)
                    -2 -> {
                        isPlaying = false
                        currentId?.let { refused.add(it); refusedId = it }
                        currentId = null
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Starts [videoId]; false when it cannot (invalid id, no WebView, host destroyed). */
    fun play(videoId: String): Boolean {
        if (destroyed || unavailable || !MediaExtras.YOUTUBE_ID.matches(videoId)) return false
        if (videoId == currentId) return true
        val v = viewOrNull() ?: return false
        isPlaying = false
        isPaused = false
        ended = false
        currentId = videoId
        if (!pageReady) {
            pendingId = videoId
            if (v.url == null) v.loadDataWithBaseURL(BASE_URL, PAGE, "text/html", "UTF-8", null)
            return true
        }
        // Interpolation is safe: the id matched [A-Za-z0-9_-] above.
        v.evaluateJavascript("aniPlay('$videoId')", null)
        return true
    }

    /**
     * Plays the first candidate YouTube actually serves. A refused id fails over at once; one that
     * never reaches PLAYING (stalled, bot-checked) is given [startTimeoutMs] and then skipped, but
     * not blacklisted — the network may simply have been slow. Returns the id that played.
     */
    suspend fun playFirst(candidates: List<String>): String? {
        for (id in candidates.distinct()) {
            if (id in refused) continue
            // The very first trailer also pays for Chromium + the iframe API: give it longer.
            val timeout = if (pageReady) 9_000L else 18_000L
            if (!play(id)) return null
            val started = withTimeoutOrNull(timeout) {
                snapshotFlow { isPlaying || refusedId == id }.first { it }
                isPlaying
            } ?: false
            if (started) return id
            // The page itself never came up (iframe API blocked, network down): rebuild it on the
            // next attempt instead of queueing into a dead page for the rest of the session.
            if (!pageReady) { dropView(); return null }
            stop()
        }
        return null
    }

    /** OK on the remote: pause a playing trailer, resume a paused one. */
    fun togglePause() {
        if (destroyed || !pageReady) return
        webView?.evaluateJavascript("aniToggle()", null)
    }

    /** Replays the current or last trailer from the start (after it ended). */
    fun replay(videoId: String) {
        currentId = null
        play(videoId)
    }

    fun stop() {
        if (destroyed) return
        pendingId = null
        currentId = null
        isPlaying = false
        val v = webView ?: return
        if (pageReady) v.evaluateJavascript("aniStop()", null)
    }

    fun onHostPause() { if (!destroyed) { stop(); webView?.onPause() } }
    fun onHostResume() { if (!destroyed) webView?.onResume() }

    private fun dropView() {
        val v = webView ?: return
        webView = null
        pageReady = false
        pendingId = null
        currentId = null
        isPlaying = false
        (v.parent as? ViewGroup)?.removeView(v)
        runCatching { v.destroy() }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        isPlaying = false
        val v = webView ?: return
        webView = null
        (v.parent as? ViewGroup)?.removeView(v)
        runCatching {
            v.stopLoading()
            v.loadUrl("about:blank")
            v.destroy()
        }
    }

    companion object {
        private const val STALL_MS = 3_500L
        private const val BASE_URL = "https://www.youtube-nocookie.com"

        /** Ids YouTube refused this process; never retried. */
        private val refused: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** `https://www.youtube-nocookie.com/embed/<id>?...` (AniListApi) -> `<id>`. Null for non-YouTube or malformed. */
        fun youtubeId(trailerUrl: String?): String? {
            if (trailerUrl == null || !trailerUrl.contains("youtube")) return null
            return trailerUrl.substringAfter("/embed/", "").substringBefore("?")
                .takeIf { MediaExtras.YOUTUBE_ID.matches(it) }
        }

        private val PAGE = """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000}
            #p{position:absolute;inset:0;width:100%;height:100%;pointer-events:none}</style></head>
            <body><div id="p"></div>
            <script>
              var player=null, ready=false, want=null;
              var s=document.createElement('script'); s.src='https://www.youtube.com/iframe_api';
              document.head.appendChild(s);
              function onYouTubeIframeAPIReady(){
                player=new YT.Player('p',{width:'100%',height:'100%',
                  playerVars:{autoplay:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,modestbranding:1,
                              playsinline:1,rel:0,cc_load_policy:0,origin:'https://www.youtube-nocookie.com'},
                  events:{
                    onReady:function(){ ready=true; AniTrailer.onReady(); if(want){aniPlay(want);} },
                    onStateChange:function(e){ AniTrailer.onState(e.data); },
                    onError:function(){ AniTrailer.onState(-2); }
                  }});
              }
              function aniPlay(id){
                if(!ready){ want=id; return; }
                want=null;
                try{ player.loadVideoById({videoId:id,startSeconds:0,suggestedQuality:'hd1080'});
                     player.unMute(); player.setVolume(100); }catch(e){ AniTrailer.onState(-2); }
              }
              function aniToggle(){ try{ if(player.getPlayerState()==1){player.pauseVideo();}else{player.playVideo();} }catch(e){} }
              function aniStop(){ want=null; try{ player.stopVideo(); }catch(e){} }
            </script></body></html>
        """.trimIndent()
    }
}
