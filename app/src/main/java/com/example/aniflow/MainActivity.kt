package com.example.aniflow

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.aniflow.theme.AniFlowTheme
import com.example.aniflow.data.SettingsStore
import com.example.aniflow.ui.focus.FocusHealerRoot
import com.example.aniflow.ui.sound.Sfx

val LocalDeviceType = staticCompositionLocalOf<DeviceType> {
    error("DeviceType not provided")
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    com.example.aniflow.data.CrashGuard.install(this)
    super.onCreate(savedInstanceState)

    val deviceType = DeviceDetector.detect(this)
    if (deviceType == DeviceType.TV) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    enableEdgeToEdge()
    Sfx.init(applicationContext)
    setContent {
      LaunchedEffect(Unit) {
        SettingsStore(applicationContext).navSounds.collect { Sfx.enabled = it }
      }
      CompositionLocalProvider(LocalDeviceType provides deviceType) {
        AniFlowTheme {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FocusHealerRoot(enabled = deviceType == DeviceType.TV) {
              MainNavigation()
            }
          }
        }
      }
    }
  }

  // Overriding Activity.dispatchKeyEvent is public API; lint resolves `super` to androidx.core's
  // restricted override of it and flags a call that is in fact just the framework's.
  @android.annotation.SuppressLint("RestrictedApi")
  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val handled = super.dispatchKeyEvent(event)
    Sfx.onKey(event, handled)
    return handled
  }

  override fun onStop() {
    super.onStop()
    com.example.aniflow.ui.redesign.audio.BleachBgmManager.pause()
  }

  // Low-RAM TVs: hand back decoded posters before the system starts killing things (the player's
  // WebView renderer is usually first).
  @Suppress("DEPRECATION")
  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
      runCatching { coil3.SingletonImageLoader.get(this).memoryCache?.clear() }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    com.example.aniflow.ui.redesign.audio.BleachBgmManager.release()
  }
}
