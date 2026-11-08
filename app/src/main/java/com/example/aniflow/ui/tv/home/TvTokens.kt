package com.example.aniflow.ui.tv.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TV design tokens. Sized for the 960x540 dp canvas every Android TV reports at 720p/768p/1080p
 * (the OnePlus Y1S 32" is 1366x768 at tvdpi ≈ 1024x576 dp). Everything on the TV home reads from
 * here — no inline literals — so the P1 design system can absorb this file instead of rewriting it.
 */
object TvTokens {
    // Colour: one black, six violets, one off-white (redesign board 6). Violet is for art, progress
    // and indicators; focus is always off-white, never a coloured glow.
    val Canvas = Color(0xFF07050C)
    val Panel = Color(0xFF0B0814)
    val Surface = Color(0xFF100B1C)
    val SurfaceHigh = Color(0xFF22133F)   // plum
    val Dusk = Color(0xFF3A2266)
    val Violet = Color(0xFF5B3394)
    val Accent = Color(0xFF7B4BC9)        // brand: primary fills, progress
    val AccentGlow = Color(0xFFB98BFF)    // indicator: underlines, live dots, highlights
    val AccentDeep = Color(0xFF5B3394)    // violet: gradient ends, badges
    val AccentPale = Color(0xFFCDB4FF)    // eyebrow text on dark
    val Score = Color(0xFFCDB4FF)
    val Focus = Color(0xFFF7F2FF)
    val OnFocus = Color(0xFF0B0712)
    val TextPrimary = Color(0xFFF7F2FF)
    val TextSecondary = Color(0xBFEDE4FF)
    val TextTertiary = Color(0x85E6DCFF)
    val AccentGradient = Brush.linearGradient(listOf(AccentDeep, Accent, AccentGlow))

    // Layout
    val BarTop: Dp = 14.dp                // centred capsule top bar
    val BarHeight: Dp = 40.dp
    val BarSpace: Dp = 64.dp              // content inset below the bar on non-home tabs
    val ContentStart: Dp = 56.dp          // left edge of hero text and rows
    val HeroInfoTop: Dp = 84.dp
    val ListTopBrowse: Dp = 252.dp        // rows region top while browsing rows
    val HeroRowGap: Dp = 28.dp            // space between the hero block's measured bottom and the rows

    // Cards: one height for every state, so expanding only changes width (no vertical relayout).
    val PosterWidth: Dp = 112.dp
    val CardHeight: Dp = 168.dp
    val ExpandedWidth: Dp = 300.dp        // ≈ 16:9 at CardHeight
    val CardGap: Dp = 20.dp               // room for the focused card's scale + ring on both sides
    val RowHeader: Dp = 40.dp             // row title + its gap: the focused row stops this far down so its title stays visible
    val CardRadius: Dp = 8.dp
    const val FocusScale = 1.07f
    const val ExpandDwellMs = 850L         // stay this long on a card and it opens up
    const val HeroDebounceMs = 380L        // billboard follows focus after this settle time
    const val SpotlightMs = 9_000L         // hero rotates to the next spotlight title

    // Type (sp). TV is read from 2-3 m: nothing below 12sp.
    val HeroTitle = 40.sp
    val HeroMeta = 14.sp
    val HeroBody = 14.sp
    val RowTitle = 17.sp
    val CardTitle = 15.sp
    val CardMeta = 12.sp
    val RailLabel = 16.sp

    // Motion
    val FocusSpring = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    /** Tab pill: 480 ms-ish with a slight overshoot. */
    val PillSpring = spring<Dp>(dampingRatio = 0.66f, stiffness = 420f)
    val ExpandSpring = spring<Dp>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * "12 episodes" / "12 eps", or null when there is nothing honest to say: unknown or zero counts
 * (announced shows report 0), and single-episode movies (the MOVIE chip already says it).
 */
internal fun com.example.aniflow.data.model.Anime.episodeLabel(short: Boolean = false): String? {
    val n = episodes?.takeIf { it > 0 } ?: return null
    if (n == 1 && format.equals("MOVIE", ignoreCase = true)) return null
    return when {
        short -> "$n ep" + if (n == 1) "" else "s"
        else -> "$n episode" + if (n == 1) "" else "s"
    }
}

/** "Coming Spring 2027" / "Coming 2027" for announced titles, else null. */
internal fun com.example.aniflow.data.model.Anime.comingLabel(): String? {
    if (!status.equals("NOT_YET_RELEASED", ignoreCase = true)) return null
    val year = seasonYear ?: return "Coming soon"
    val season = season?.lowercase()?.replaceFirstChar { it.uppercase() }
    return if (season != null) "Coming $season $year" else "Coming $year"
}

