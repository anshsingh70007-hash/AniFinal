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

    // === NEW: Redesign style tokens ===
    // Deep AMOLED black for hero backgrounds
    val AmoledBlack = Color(0xFF050505)
    
    // Refined glass surfaces (lower opacity = more premium)
    val GlassThin = Color(0x0AFFFFFF)          // 4% white — for hero panels
    val GlassMedium = Color(0x14FFFFFF)         // 8% white — for content cards
    
    // Top-edge highlight line for glass cards
    val CardEdgeHighlight = Color(0x1FFFFFFF)   // 12% white — thin top line
    
    // Neutral charcoal for elevated surfaces
    val SurfaceElevated = Color(0xFF1A1A1A)
    
    // On-surface variant for secondary text (warmer than current TextMuted)
    val TextSubtle = Color(0xFFA3A3A3)
    
    // Section spacing constants (use these, don't guess)
    const val SECTION_GAP_MOBILE = 40          // dp between content sections
    const val SECTION_GAP_TV = 28              // dp between content sections on TV
    const val ROW_TITLE_BOTTOM = 16            // dp below section title
    const val CARD_GAP_MOBILE = 16             // dp between cards in a row
    const val CARD_GAP_TV = 32                 // dp between cards on TV
    const val PAGE_HORIZONTAL_MOBILE = 24      // dp left/right page padding
    const val PAGE_HORIZONTAL_TV = 48          // dp left/right page padding on TV
    const val OVERSCAN_MARGIN_TV = 48          // dp overscan-safe margin for TV edges
}
