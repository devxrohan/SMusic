package com.devxrohan.smusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** A complete look — background, surfaces, text, and accent together — not just an accent hue. Selecting
 * one restyles the whole app, not a single swatch. */
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

    /** "SMusic" is our own default — same red brand, retained as the fallback. */
    val SMUSIC_DEFAULT = dark("smusic_default", "SMusic", 0xFF0A0A0A, 0xFF121212, 0xFF1C1C1E, 0xFF262628, 0xFFE53935)

    val DARK = listOf(
        SMUSIC_DEFAULT,
        dark("system_dynamic", "Wallpaper", 0xFF171216, 0xFF211B20, 0xFF30282E, 0xFF3C333A, 0xFFE9B4D0),
        dark("rosewood", "Rosewood", 0xFF1B1110, 0xFF261A18, 0xFF392724, 0xFF4B322E, 0xFFFFB4AA),
        dark("soft_lilac", "Soft Lilac", 0xFF161219, 0xFF211C25, 0xFF302837, 0xFF40354A, 0xFFD3BBF5),
        dark("moss", "Moss", 0xFF12150F, 0xFF1C2118, 0xFF293123, 0xFF38422E, 0xFFBDCF9C),
        dark("slate_blue", "Slate Blue", 0xFF111419, 0xFF1A2028, 0xFF27313D, 0xFF354251, 0xFFAFCAE8),
        dark("midnight", "Midnight", 0xFF0A0A0A, 0xFF121212, 0xFF1C1C1E, 0xFF262628, 0xFFE62429),
        dark("charcoal", "Charcoal", 0xFF181818, 0xFF1E1E1E, 0xFF2A2A2C, 0xFF343436, 0xFF1DB954),
        dark("deep_ocean", "Deep Ocean", 0xFF07101A, 0xFF0D1B2A, 0xFF16283D, 0xFF1E3450, 0xFF3AA0FF),
        dark("forest_night", "Forest Night", 0xFF0B120D, 0xFF111C14, 0xFF1A2B1F, 0xFF23392A, 0xFF4CBB6B),
        dark("royal_plum", "Royal Plum", 0xFF150B1B, 0xFF1E1027, 0xFF2C1839, 0xFF3A214A, 0xFFB980FF),
        dark("amber_dusk", "Amber Dusk", 0xFF160F08, 0xFF211609, 0xFF33240F, 0xFF443016, 0xFFFF9E42)
    )

    val LIGHT = listOf(
        light("rose_paper", "Rose Paper", 0xFFFFF8F6, 0xFFFFF0EC, 0xFFF4DDD7, 0xFFE8CCC5, 0xFF9B4C42),
        light("lavender_paper", "Lavender Paper", 0xFFFCF8FF, 0xFFF5EEFC, 0xFFE9DEF3, 0xFFDCCDE9, 0xFF745391),
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
        // Keep light-theme outlines visibly defined. Several cards/boxes apply
        // their own subtle alpha, so the base outlineVariant needs enough
        // contrast to remain visible on white/light surfaces.
        outline = if (isDark) textTertiary else Color(0xFF66666C),
        outlineVariant = if (isDark) {
            textTertiary.copy(alpha = 0.4f)
        } else {
            Color(0xFF77777D)
        },
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

