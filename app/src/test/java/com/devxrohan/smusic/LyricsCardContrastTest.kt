package com.devxrohan.smusic

import com.devxrohan.smusic.utils.ShareCardColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LyricsCardContrastTest {
    @Test
    fun darkAndLightCardCreditsKeepReadableContrastWithoutArtwork() {
        assertTrue(contrast(ShareCardColors.IVORY, ShareCardColors.INK) >= 7.0)
        assertTrue(contrast(ShareCardColors.MUTED, ShareCardColors.INK) >= 4.5)
        assertTrue(contrast(ShareCardColors.INK, ShareCardColors.IVORY) >= 7.0)
    }

    private fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = (color shr shift and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
