package com.example.aniflow.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

// "Nocturne": black with a violet undertone, and one purple family for every accent.
// Ramp, darkest -> lightest: Aubergine, Mulberry, Violet, Amethyst, Orchid, Lavender.
val Aubergine = Color(0xFF240046)
val Mulberry = Color(0xFF5A189A)
val Violet = Color(0xFF7B2CBF)
val Amethyst = Color(0xFF9D4EDD)
val Orchid = Color(0xFFC77DFF)
val Lavender = Color(0xFFE0AAFF)

var PrimaryDark by mutableStateOf(Color(0xFF09070F))          // App background
var PrimaryDarker by mutableStateOf(Color(0xFF050409))        // Deepest layer / TV canvas
val PrimaryAccent = Amethyst                                  // Primary actions, focus
val PrimaryAccentLight = Orchid                               // Focused/hover
val SecondaryAccent = Lavender                                // Highlights, borders on focus
val TertiaryAccent = Color(0xFFB5179E)                        // Magenta-plum, used sparingly
val SuccessGreen = Color(0xFF10B981)                          // Emerald
val WarningAmber = Color(0xFFF59E0B)                          // Amber (warnings only)

var SurfaceCard by mutableStateOf(Color(0xFF15101F))          // Card container
var SurfaceBorder by mutableStateOf(Color(0xFF2B2140))        // Subtle card outline

var TextPrimary by mutableStateOf(Color(0xFFF5F0FF))          // Lavender-white
var TextSecondary by mutableStateOf(Color(0xFFB4A9CC))        // Muted lilac grey
val TextTertiary = Color(0xFF7D7296)

/** Signature gradient for primary buttons, progress and badges. */
val AuroraGradient = Brush.linearGradient(listOf(Mulberry, Amethyst, Orchid))
