package com.devxrohan.smusic

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.customization.*
import com.devxrohan.smusic.ui.component.LyricsInkPresets
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class LyricsAppearanceTest {
    @Test fun allColourPresetsUseValidShareablePreferences() {
        LyricsInkPresets.all.forEach { preset ->
            val profile = CustomizationProfile(name = preset.name, values = mapOf(
                LyricsColorStyleKey.name to JsonPrimitive(if (preset.gradient) "GRADIENT" else "SOLID"),
                LyricsColorPrimaryKey.name to JsonPrimitive(preset.first),
                LyricsColorSecondaryKey.name to JsonPrimitive(preset.second)))
            CustomizationSchema.validate(profile)
            val prefs = mutablePreferencesOf(PlayerScreenStyleKey to "CLASSIC")
            CustomizationSchema.apply(prefs, profile, setOf(CustomizationCategory.LYRICS))
            assertEquals(preset.first, prefs[LyricsColorPrimaryKey])
            assertEquals("CLASSIC", prefs[PlayerScreenStyleKey])
        }
    }
    @Test fun malformedColoursAreRejected() {
        listOf("#FFF", "GGGGGG", "12345678", "", "red").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                CustomizationSchema.validate(CustomizationProfile(name = "Bad", values = mapOf(LyricsColorPrimaryKey.name to JsonPrimitive(it))))
            }
        }
    }
    @Test fun themeUsesExistingRendererColours() {
        assertFalse(LyricsInkPresets.create("THEME", "FFFFFF", "FFFFFF").enabled)
        assertTrue(LyricsInkPresets.create("GRADIENT", "67E8F9", "C084FC").enabled)
    }
}
