package com.example.aniflow.ui.redesign.theme

import androidx.compose.ui.graphics.Color

object GlassTokens {
    // Translucent glass backgrounds
    val GlassSurface = Color(0x1AFFFFFF)        // White copy alpha 0.10
    val GlassSurfaceDark = Color(0x260A0A14)    // Dark deep slate with alpha 0.15
    
    // Glowing neon accents matching the brand logo
    val GlowCyan = Color(0xFF00E5FF)            // Electric cyan-blue from logo
    val GlowPurple = Color(0xFFCE30FF)          // Electric purple-magenta from logo
    val GlowRose = Color(0xFF8E24AA)            // Violet from logo
    
    // Thin frosted highlights
    val BorderHighlightStart = Color(0x4DFFFFFF) // White copy alpha 0.30
    val BorderHighlightEnd = Color(0x0DFFFFFF)   // White copy alpha 0.05
    val BorderNeonStart = Color(0x8000E5FF)      // Electric cyan copy alpha 0.50
    val BorderNeonEnd = Color(0x1ACE30FF)        // Purple copy alpha 0.10
    
    // Muted text
    val TextMuted = Color(0xFFCAC3D8)
}
