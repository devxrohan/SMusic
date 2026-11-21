package com.devxrohan.smusic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.devxrohan.smusic.ui.theme.balancedArtworkScheme
import com.google.material.color.hct.Hct
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkColorSchemeTest {
    @Test fun extremeArtworkSeedsKeepSurfacesMutedAndTextReadable() {
        val seeds = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue,
            Color.Yellow, Color.Cyan, Color.Magenta, Color(0xFF261004))
        for (dark in listOf(true, false)) for (seed in seeds) {
            val scheme = balancedArtworkScheme(seed, dark)
            val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceContainer,
                scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
            surfaces.forEach { surface ->
                val hct = Hct.fromInt(surface.toArgb())
                assertTrue("Oversaturated surface for $seed", hct.chroma < 9.0)
                assertTrue("Unexpected surface brightness for $seed", if (dark) hct.tone in 7.0..31.0 else hct.tone in 89.0..99.0)
            }
            listOf(scheme.onSurface to scheme.surfaceContainerHigh,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onBackground to scheme.background).forEach { (text, background) ->
                val light = maxOf(text.luminance(), background.luminance())
                val shade = minOf(text.luminance(), background.luminance())
                assertTrue("Insufficient text contrast for $seed", (light + .05f) / (shade + .05f) >= 4.5f)
            }
        }
    }
}
