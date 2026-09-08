package com.example.aniflow.ui.redesign.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.aniflow.R
import com.example.aniflow.data.model.Anime

/**
 * Bleach: Thousand-Year Blood War (TYBW) Climax Tribute Design Tokens
 * Exclusively active when displaying Bleach: TYBW to honor the anime's finale.
 */
object BleachTybwTokens {
    // Reiatsu (Spiritual Pressure) & Blood War Palette
    val ReiatsuCrimson = Color(0xFFE50914)       // Getsuga Tensho Scarlet
    val ReiatsuBlood = Color(0xFFFF003C)         // Pure Blood Flare
    val ReiatsuObsidian = Color(0xFF070003)      // Deepest Shadow Obsidian
    val ReiatsuDark = Color(0xFF100106)          // Dark Blood Glass Surface
    val ReiatsuSurface = Color(0xFF1A0209)       // Frosted Crimson Slate
    val ReiatsuBorder = Color(0xFF8B0018)        // Crimson Blade Steel
    
    // Quincy / Wandenreich Spirit Energy Palette
    val QuincyReishiBlue = Color(0xFF00E5FF)     // Holy Spirit Blue
    val QuincyIceCyan = Color(0xFF80D8FF)        // Silbern Ice Glow
    val QuincyWhite = Color(0xFFF5F9FF)          // Wandenreich Uniform White
    
    // Bankai Accents
    val BankaiGold = Color(0xFFFFD700)           // Soul King / Royal Guard Gold
    val ZanpakutoSteel = Color(0xFFC0C0C8)       // Blade Edge Polish
    
    // Sōsuke Aizen Spiritual Pressure (Reiatsu) & Kurohitsugi Palette
    val AizenReiatsuPurple = Color(0xFF5B008C)    // Tyrannical Godly Reiatsu
    val AizenKurohitsugi = Color(0xFF1E0038)       // Black Coffin Obsidian Violet
    val AizenAbyssBlack = Color(0xFF07000F)        // Muken Absolute Darkness
    val AizenKyokaSuigetsu = Color(0xFFB066FF)     // Complete Hypnosis Aura
    val AizenEnergyArc = Color(0xFFE040FB)         // High-Voltage Violet Spark
    
    // Typography
    val BleachFontFamily = FontFamily(
        Font(R.font.cinzel_bold, FontWeight.Bold)
    )
}

/**
 * Air-tight identification function specifically targeting Bleach: Thousand-Year Blood War.
 * Explicitly matches the 4 cours of TYBW:
 * - Part 1: Sennen Kessen-hen (ID 116674)
 * - Part 2: Ketsubetsu-tan / The Separation (ID 159322)
 * - Part 3: Soukoku-tan / The Conflict (ID 169755)
 * - Part 4: Kashin-tan / The Calamity (ID 185874)
 *
 * Excludes original Bleach 2004 (ID 269) and all other series.
 */
fun Anime.isBleachTybw(): Boolean {
    if (id in listOf(116674, 159322, 169755, 185874)) return true
    
    val romaji = title.lowercase()
    val english = englishTitle?.lowercase() ?: ""
    
    // Must be Bleach, but MUST also have Thousand-Year Blood War / Sennen Kessen identifiers
    val isBleach = romaji.contains("bleach") || english.contains("bleach")
    if (!isBleach) return false
    
    val isTybw = romaji.contains("thousand-year") || romaji.contains("thousand year") ||
                 romaji.contains("sennen kessen") || romaji.contains("tybw") ||
                 english.contains("thousand-year") || english.contains("thousand year") ||
                 english.contains("sennen kessen") || english.contains("tybw")
                 
    return isTybw && id != 269
}
