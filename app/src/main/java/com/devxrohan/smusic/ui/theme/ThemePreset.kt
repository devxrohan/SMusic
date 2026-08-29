package com.devxrohan.smusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * A complete look — background, surfaces, text, and accent together — not
 * just an accent hue. Selecting one restyles the whole app, not a single
 * swatch. Ported from the Elite Music (com.elite.music) project's
 * ThemePreset system — same 11 presets, same colors.
 */
data class ThemePreset(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceElevated2: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color
)

object ThemePresets {
    private fun dark(id: String, name: String, bg: Long, surf: Long, elev: Long, elev2: Long, accent: Long) = ThemePreset(
        id = id, displayName = name, isDark = true,
        background = Color(bg), surface = Color(surf), surfaceElevated = Color(elev), surfaceElevated2 = Color(elev2),
        textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFA3A3A3), textTertiary = Color(0xFF6E6E70),
        accent = Color(accent)
    )

    private fun light(id: String, name: String, bg: Long, surf: Long, elev: Long, elev2: Long, accent: Long) = ThemePreset(
        id = id, displayName = name, isDark = false,
        background = Color(bg), surface = Color(surf), surfaceElevated = Color(elev), surfaceElevated2 = Color(elev2),
        textPrimary = Color(0xFF141414), textSecondary = Color(0xFF5C5C5E), textTertiary = Color(0xFF8B8B8D),
        accent = Color(accent)
    )

    /** "SMusic" is our own default — same red brand, not part of the Elite Music port, kept first so it stays the fallback. */
    val SMUSIC_DEFAULT = dark("smusic_default", "SMusic", 0xFF0A0A0A, 0xFF121212, 0xFF1C1C1E, 0xFF262628, 0xFFE53935)

    val DARK = listOf(
        SMUSIC_DEFAULT,
        dark("midnight", "Midnight", 0xFF0A0A0A, 0xFF121212, 0xFF1C1C1E, 0xFF262628, 0xFFE62429),
        dark("charcoal", "Charcoal", 0xFF181818, 0xFF1E1E1E, 0xFF2A2A2C, 0xFF343436, 0xFF1DB954),
        dark("deep_ocean", "Deep Ocean", 0xFF07101A, 0xFF0D1B2A, 0xFF16283D, 0xFF1E3450, 0xFF3AA0FF),
        dark("forest_night", "Forest Night", 0xFF0B120D, 0xFF111C14, 0xFF1A2B1F, 0xFF23392A, 0xFF4CBB6B),
        dark("royal_plum", "Royal Plum", 0xFF150B1B, 0xFF1E1027, 0xFF2C1839, 0xFF3A214A, 0xFFB980FF),
        dark("amber_dusk", "Amber Dusk", 0xFF160F08, 0xFF211609, 0xFF33240F, 0xFF443016, 0xFFFF9E42)
    )

    val LIGHT = listOf(
        light("pure", "Pure", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF1F1F2, 0xFFE6E6E8, 0xFFE62429),
        light("cream", "Cream", 0xFFFBF6EE, 0xFFFFFFFF, 0xFFF1E9D8, 0xFFE9DDC4, 0xFFB5652C),
        light("cloud", "Cloud", 0xFFF3F5F8, 0xFFFFFFFF, 0xFFE6EAF0, 0xFFD8DEE8, 0xFF3A6FE0),
        light("sage", "Sage", 0xFFF2F6F1, 0xFFFFFFFF, 0xFFE3EBE0, 0xFFD3E0CE, 0xFF2F8F5B),
        light("blush", "Blush", 0xFFFBF1F3, 0xFFFFFFFF, 0xFFF3DDE2, 0xFFE9C6CE, 0xFFD64876)
    )

    val ALL = DARK + LIGHT

    fun fromId(id: String?): ThemePreset = ALL.firstOrNull { it.id == id } ?: SMUSIC_DEFAULT
}

/** Maps this preset's 10 explicit colors onto the ~30 slots Material3's ColorScheme needs, deriving the rest sensibly (containers as elevated surfaces, "on" colors from luminance). */
fun ThemePreset.toColorScheme(): androidx.compose.material3.ColorScheme {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    return androidx.compose.material3.ColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.24f).compositeOver(surface),
        onPrimaryContainer = textPrimary,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = textPrimary,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = surfaceElevated2,
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        inverseSurface = textPrimary,
        inverseOnSurface = surface,
        error = Color(0xFFCF6679),
        onError = Color.White,
        errorContainer = Color(0xFF4A0E16),
        onErrorContainer = Color(0xFFFFDAD9),
        outline = textTertiary,
        outlineVariant = textTertiary.copy(alpha = 0.4f),
        scrim = Color.Black,
        surfaceBright = surfaceElevated2,
        surfaceDim = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceElevated,
        surfaceContainerHighest = surfaceElevated2,
        surfaceContainerLow = background,
        surfaceContainerLowest = background,
    )
}

