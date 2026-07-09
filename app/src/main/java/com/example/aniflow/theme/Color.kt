package com.example.aniflow.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

var PrimaryDark by mutableStateOf(Color(0xFF0D0D1A))          // Deep space background
var PrimaryDarker by mutableStateOf(Color(0xFF06060F))        // TV background/card body
val PrimaryAccent = Color(0xFF7C3AED)        // Electric violet
val PrimaryAccentLight = Color(0xFFA78BFA)   // Focused/hover violet
val SecondaryAccent = Color(0xFF06B6D4)      // Neon cyan
val TertiaryAccent = Color(0xFFF43F5E)       // Neon rose/pink
val SuccessGreen = Color(0xFF10B981)         // Emerald
val WarningAmber = Color(0xFFF59E0B)         // Amber (airing badge)

var SurfaceCard by mutableStateOf(Color(0xFF1A1A2E))          // Card container
var SurfaceBorder by mutableStateOf(Color(0xFF2D2D4A))        // Subtle card outline

var TextPrimary by mutableStateOf(Color(0xFFF1F5F9))          // Soft white
var TextSecondary by mutableStateOf(Color(0xFF94A3B8))        // Slate gray
val TextTertiary = Color(0xFF64748B)         // Slate dark gray

