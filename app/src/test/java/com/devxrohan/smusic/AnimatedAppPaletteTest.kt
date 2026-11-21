package com.devxrohan.smusic

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.devxrohan.smusic.ui.theme.interpolateAppPalette
import org.junit.Assert.*
import org.junit.Test

class AnimatedAppPaletteTest {
    @Test fun transitionsPreserveExactEndpointsAndFixedRoles() {
        val start = darkColorScheme(primary = Color.Black, primaryFixed = Color.Black)
        val end = darkColorScheme(primary = Color.White, primaryFixed = Color.White)
        assertSame(start, interpolateAppPalette(start, end, 0f))
        assertSame(end, interpolateAppPalette(start, end, 1f))
        val middle = interpolateAppPalette(start, end, .5f)
        assertEquals(Color(0xFF808080), middle.primary)
        assertEquals(Color(0xFF808080), middle.primaryFixed)
        assertEquals(start.error, middle.error)
    }
}
