package com.example.aniflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.aniflow.theme.AniFlowTheme
import com.example.aniflow.ui.redesign.components.IntroOverlay

val LocalDeviceType = staticCompositionLocalOf<DeviceType> {
    error("DeviceType not provided")
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val deviceType = DeviceDetector.detect(this)
    if (deviceType == DeviceType.TV) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    enableEdgeToEdge()
    setContent {
      CompositionLocalProvider(LocalDeviceType provides deviceType) {
        AniFlowTheme {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val isRedesign = remember { packageName.endsWith(".redesign") }
            if (isRedesign) {
              var showIntro by remember { mutableStateOf(true) }
              var startAppReveal by remember { mutableStateOf(false) }
              
              Box(modifier = Modifier.fillMaxSize()) {
                val appScale by animateFloatAsState(
                    targetValue = if (startAppReveal) 1.0f else 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "appScale"
                )
                val appAlpha by animateFloatAsState(
                    targetValue = if (startAppReveal) 1.0f else 0.0f,
                    animationSpec = tween(durationMillis = 800),
                    label = "appAlpha"
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            alpha = appAlpha
                        )
                ) {
                    MainNavigation()
                }
                
                if (showIntro) {
                  IntroOverlay(
                    onStartFadeOut = {
                      startAppReveal = true
                    },
                    onFinished = {
                      showIntro = false
                    }
                  )
                }
              }
            } else {
              MainNavigation()
            }
          }
        }
      }
    }
  }
}

