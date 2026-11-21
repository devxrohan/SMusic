package com.devxrohan.smusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.utils.rememberPreference

data class LyricsInk(val enabled: Boolean, val brush: Brush)

val LocalLyricsInk = androidx.compose.runtime.staticCompositionLocalOf { LyricsInk(false, SolidColor(Color.White)) }

object LyricsInkPresets {
    data class Preset(val name: String, val gradient: Boolean, val first: String, val second: String)
    val all = listOf(
        Preset("Pearl", false, "FFFFFF", "FFFFFF"),
        Preset("Mint", false, "A7F3D0", "A7F3D0"),
        Preset("Golden", false, "FCD34D", "FCD34D"),
        Preset("Aurora", true, "67E8F9", "C084FC"),
        Preset("Sunset", true, "FB7185", "FBBF24"),
        Preset("Ocean", true, "60A5FA", "34D399"),
        Preset("Candy", true, "F9A8D4", "A5B4FC"),
    )
    fun color(hex: String): Color = Color((0xFF000000L or hex.removePrefix("#").toLong(16)).toInt())
    fun create(style: String, first: String, second: String): LyricsInk {
        val a = runCatching { color(first) }.getOrDefault(Color.White)
        val b = runCatching { color(second) }.getOrDefault(a)
        return LyricsInk(style != "THEME", if (style == "GRADIENT") Brush.linearGradient(listOf(a, b)) else SolidColor(a))
    }
}

@Composable
fun rememberLyricsInk(): LyricsInk {
    val style by rememberPreference(LyricsColorStyleKey, "THEME")
    val first by rememberPreference(LyricsColorPrimaryKey, "FFFFFF")
    val second by rememberPreference(LyricsColorSecondaryKey, "C084FC")
    return remember(style, first, second) { LyricsInkPresets.create(style, first, second) }
}

/** Tint the rendered glyph mask, retaining karaoke fill, inactive opacity and glyph shapes. */
fun Modifier.lyricsInk(ink: LyricsInk): Modifier = if (!ink.enabled) this else
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        onDrawWithContent { drawContent(); drawRect(ink.brush, blendMode = BlendMode.SrcIn) }
    }
