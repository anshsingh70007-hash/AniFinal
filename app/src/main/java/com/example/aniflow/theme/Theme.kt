package com.example.aniflow.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.aniflow.data.SettingsStore

@Composable
fun AniFlowTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val themeMode by settingsStore.themeMode.collectAsState(initial = "dark")

    val isAmoled = themeMode == "amoled"

    if (isAmoled) {
        PrimaryDark = Color(0xFF000000)
        PrimaryDarker = Color(0xFF000000)
        TextPrimary = Color(0xFFFFFFFF)
        TextSecondary = Color(0xFFB0B0B0)
        SurfaceCard = Color(0xFF0D0D0D)
        SurfaceBorder = Color(0xFF1A1A1A)
    } else {
        // Dark mode
        PrimaryDark = Color(0xFF0D0D1A)
        PrimaryDarker = Color(0xFF06060F)
        TextPrimary = Color(0xFFF1F5F9)
        TextSecondary = Color(0xFF94A3B8)
        SurfaceCard = Color(0xFF1A1A2E)
        SurfaceBorder = Color(0xFF2D2D4A)
    }

    val colorScheme = darkColorScheme(
        primary = PrimaryAccent,
        secondary = SecondaryAccent,
        tertiary = TertiaryAccent,
        background = PrimaryDark,
        surface = SurfaceCard,
        onPrimary = TextPrimary,
        onSecondary = TextPrimary,
        onTertiary = TextPrimary,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        surfaceVariant = PrimaryDarker,
        outline = SurfaceBorder
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

