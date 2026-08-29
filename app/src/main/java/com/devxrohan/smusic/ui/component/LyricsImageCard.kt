/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.ui.component

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import com.devxrohan.smusic.models.MediaMetadata

// ─────────────────────────────────────────────────────────────────────────────
// LyricsImageCard — thin wrapper (backward-compatible)
//
// Todos los callers existentes siguen funcionando sin cambios.
// Internamente delega a GlassCardLayout con un LyricsCardConfig derivado.
// Para usar el nuevo sistema con múltiples layouts, usa LyricsShareCarouselSheet
// o LyricsCardByLayout directamente.
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun LyricsImageCard(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    glassStyle: LyricsGlassStyle = LyricsGlassStyle.FrostedDark,
    darkBackground: Boolean = true,       // mantenido por compatibilidad; actualmente no usado por GlassCardLayout
    backgroundColor: Color? = null,       // mantenido por compatibilidad
    textColor: Color? = null,
    secondaryTextColor: Color? = null,
) {
    // Si se proveen overrides de color, se aplican sobre una copia del estilo
    val effectiveStyle = remember(glassStyle, textColor, secondaryTextColor) {
        glassStyle.copy(
            textColor          = textColor          ?: glassStyle.textColor,
            secondaryTextColor = secondaryTextColor ?: glassStyle.secondaryTextColor,
        )
    }

    GlassCardLayout(
        lyricText     = lyricText,
        mediaMetadata = mediaMetadata,
        config        = LyricsCardConfig(glassStyle = effectiveStyle),
    )
}
