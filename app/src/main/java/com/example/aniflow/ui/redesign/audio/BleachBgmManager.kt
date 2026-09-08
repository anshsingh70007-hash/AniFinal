package com.example.aniflow.ui.redesign.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.aniflow.R
import kotlinx.coroutines.*

/**
 * Background Music Manager for Bleach: Thousand-Year Blood War tribute.
 * Loops Bleach's iconic "Treachery" OST (Aizen's theme) on repeat.
 *
 * Supports volume fading, mute toggling, and automatic suppression
 * during full-screen episode video playback.
 */
object BleachBgmManager {
    private const val TAG = "BleachBgmManager"
    private const val DEFAULT_VOLUME = 0.55f

    private var mediaPlayer: MediaPlayer? = null
    private var isInitialized = false

    var isMuted by mutableStateOf(false)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    private var isSuppressedByVideo = false
    private var fadeJob: Job? = null
    private val bgmScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Initializes and starts background playback of Treachery OST.
     */
    fun start(context: Context) {
        if (isInitialized && mediaPlayer != null) {
            if (!isSuppressedByVideo && !isMuted && mediaPlayer?.isPlaying == false) {
                resumeAndFadeIn()
            }
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context.applicationContext, R.raw.bleach_treachery_ost)?.apply {
                isLooping = true
                val vol = if (isMuted) 0f else DEFAULT_VOLUME
                setVolume(vol, vol)
                start()
            }
            isInitialized = true
            isPlaying = true
            Log.d(TAG, "Bleach Treachery OST started playing on repeat")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bleach Treachery OST", e)
        }
    }

    /**
     * Toggles mute state of the background music.
     */
    fun toggleMute() {
        isMuted = !isMuted
        val targetVolume = if (isMuted || isSuppressedByVideo) 0f else DEFAULT_VOLUME
        try {
            mediaPlayer?.setVolume(targetVolume, targetVolume)
        } catch (e: Exception) {
            Log.w(TAG, "Error changing volume: ${e.message}")
        }
    }

    /**
     * Smoothly fades out the volume before pausing MediaPlayer, eliminating audio pops or glitches.
     */
    fun fadeOutAndPause(durationMs: Long = 450L) {
        fadeJob?.cancel()
        fadeJob = bgmScope.launch {
            val mp = mediaPlayer ?: return@launch
            if (mp.isPlaying) {
                val steps = 15
                val stepDelay = (durationMs / steps).coerceAtLeast(10L)
                val currentVol = if (isMuted) 0f else DEFAULT_VOLUME
                for (i in steps downTo 0) {
                    val vol = currentVol * (i.toFloat() / steps.toFloat())
                    try {
                        mp.setVolume(vol, vol)
                    } catch (_: Exception) {}
                    delay(stepDelay)
                }
                try {
                    mp.pause()
                    isPlaying = false
                } catch (e: Exception) {
                    Log.w(TAG, "Error pausing BGM after fade: ${e.message}")
                }
            }
        }
    }

    /**
     * Starts and smoothly fades the volume up to default level.
     */
    fun resumeAndFadeIn(durationMs: Long = 500L) {
        if (isSuppressedByVideo || isMuted) return
        fadeJob?.cancel()
        fadeJob = bgmScope.launch {
            val mp = mediaPlayer ?: return@launch
            try {
                mp.setVolume(0f, 0f)
                if (!mp.isPlaying) {
                    mp.start()
                    isPlaying = true
                }
                val steps = 15
                val stepDelay = (durationMs / steps).coerceAtLeast(10L)
                for (i in 1..steps) {
                    val vol = DEFAULT_VOLUME * (i.toFloat() / steps.toFloat())
                    try {
                        mp.setVolume(vol, vol)
                    } catch (_: Exception) {}
                    delay(stepDelay)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error resuming BGM with fade: ${e.message}")
            }
        }
    }

    /**
     * Pauses the music when app enters background or when video player takes over.
     */
    fun pause() {
        fadeOutAndPause(durationMs = 250L)
    }

    /**
     * Resumes music if not suppressed or muted.
     */
    fun resume() {
        resumeAndFadeIn(durationMs = 400L)
    }

    /**
     * Called when opening full-screen video player or trailer with sound.
     * Gradually fades out music so watching anime begins with zero audio pop or overlap.
     */
    fun setVideoSuppressed(suppressed: Boolean) {
        if (isSuppressedByVideo == suppressed) return
        isSuppressedByVideo = suppressed
        if (suppressed) {
            fadeOutAndPause(durationMs = 450L)
        } else {
            resumeAndFadeIn(durationMs = 500L)
        }
    }

    /**
     * Clean release when destroying activity.
     */
    fun release() {
        fadeJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isInitialized = false
            isPlaying = false
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing BGM: ${e.message}")
        }
    }
}
