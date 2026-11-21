package com.devxrohan.smusic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.material.color.hct.Hct
import com.google.material.color.scheme.SchemeTonalSpot

/** Artwork supplies hue; fixed tonal roles keep app surfaces and controls readable. */
internal fun balancedArtworkScheme(seed: Color, dark: Boolean): ColorScheme {
    val source = Hct.fromInt(seed.toArgb())
    val accentChroma = source.chroma.coerceAtMost(40.0)
    val neutralChroma = (source.chroma * .12).coerceAtMost(7.0)
    fun tone(chroma: Double, darkTone: Double, lightTone: Double): Color =
        Color(Hct.from(source.hue, chroma, if (dark) darkTone else lightTone).toInt())
    return SchemeTonalSpot(Hct.from(source.hue, accentChroma, 55.0), dark, 0.0)
        .toColorScheme().copy(
            primary = tone(accentChroma, 80.0, 40.0),
            primaryContainer = tone(accentChroma.coerceAtMost(26.0), 34.0, 90.0),
            onPrimaryContainer = tone(neutralChroma, 96.0, 12.0),
            secondaryContainer = tone(neutralChroma, 28.0, 90.0),
            onSecondaryContainer = tone(neutralChroma, 96.0, 12.0),
            background = tone(neutralChroma, 10.0, 98.0),
            onBackground = tone(neutralChroma, 94.0, 10.0),
            surface = tone(neutralChroma, 14.0, 98.0),
            onSurface = tone(neutralChroma, 94.0, 10.0),
            onSurfaceVariant = tone(neutralChroma, 82.0, 30.0),
            surfaceDim = tone(neutralChroma, 10.0, 88.0),
            surfaceBright = tone(neutralChroma, 30.0, 98.0),
            surfaceContainerLowest = tone(neutralChroma, 8.0, 100.0),
            surfaceContainerLow = tone(neutralChroma, 18.0, 96.0),
            surfaceContainer = tone(neutralChroma, 22.0, 94.0),
            surfaceContainerHigh = tone(neutralChroma, 26.0, 92.0),
            surfaceContainerHighest = tone(neutralChroma, 30.0, 90.0),
        )
}
