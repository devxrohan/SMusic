package com.devxrohan.smusic

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.customization.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CustomizationProfileTest {
    private fun profile(vararg values: Pair<String, JsonElement>) = CustomizationProfile(name = "Night", values = mapOf(*values))

    @Test fun invalidLegacyPreferenceCanBeOpenedWithoutChangingStoredValue() {
        val prefs = mutablePreferencesOf(PlayerScreenStyleKey to "REMOVED_STYLE", LyricsTextSizeKey to -100f)
        val snapshot = CustomizationSchema.snapshot(prefs)
        assertEquals(JsonNull, snapshot.values[PlayerScreenStyleKey.name])
        assertEquals(JsonNull, snapshot.values[LyricsTextSizeKey.name])
        CustomizationSchema.validate(snapshot)
        assertEquals("REMOVED_STYLE", prefs[PlayerScreenStyleKey])
    }

    @Test fun exportContainsOnlyRegisteredPreferencesAndRoundTrips() {
        val prefs = mutablePreferencesOf(InnerTubeCookieKey to "secret", OpenRouterApiKey to "secret",
            PlayerScreenStyleKey to "CLASSIC", LyricsTextSizeKey to 28f)
        val snapshot = CustomizationSchema.snapshot(prefs, "Daily")
        val json = CustomizationSchema.encode(snapshot)
        assertFalse(json.contains("secret"))
        assertFalse(json.contains(InnerTubeCookieKey.name))
        assertEquals(snapshot, CustomizationSchema.decode(json))
    }

    @Test fun importRejectsUnknownKeysAndFutureVersions() {
        assertThrows(IllegalArgumentException::class.java) {
            CustomizationSchema.validate(profile(PlayerScreenStyleKey.name to JsonPrimitive("CLASSIC")).copy(schemaVersion = 2))
        }
        assertThrows(IllegalStateException::class.java) { CustomizationSchema.validate(profile(InnerTubeCookieKey.name to JsonPrimitive("secret"))) }
    }

    @Test fun numericRangesAndTypesAreValidated() {
        listOf(JsonPrimitive(999f), JsonPrimitive("26"), JsonPrimitive(true), JsonArray(emptyList())).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { CustomizationSchema.validate(profile(LyricsTextSizeKey.name to value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { CustomizationSchema.validate(profile(PlayerScreenStyleKey.name to JsonPrimitive("UNKNOWN_STYLE"))) }
        assertThrows(IllegalArgumentException::class.java) { CustomizationSchema.validate(profile(SwipeThumbnailKey.name to JsonPrimitive("true"))) }
    }

    @Test fun applyingOneCategoryLeavesOthersAndCredentialsAlone() {
        val prefs = mutablePreferencesOf(PlayerScreenStyleKey to "CLASSIC", DarkModeKey to "OFF", InnerTubeCookieKey to "secret")
        CustomizationSchema.apply(prefs, profile(PlayerScreenStyleKey.name to JsonPrimitive("PAPER"), DarkModeKey.name to JsonPrimitive("ON")), setOf(CustomizationCategory.PLAYER))
        assertEquals("PAPER", prefs[PlayerScreenStyleKey])
        assertEquals("OFF", prefs[DarkModeKey])
        assertEquals("secret", prefs[InnerTubeCookieKey])
    }

    @Test fun missingValuesPreservePreferencesAndNullRestoresAppDefault() {
        val prefs = mutablePreferencesOf(PlayerScreenStyleKey to "PAPER", SliderStyleKey to "SQUIGGLY")
        CustomizationSchema.apply(prefs, profile(PlayerScreenStyleKey.name to JsonNull), setOf(CustomizationCategory.PLAYER))
        assertNull(prefs[PlayerScreenStyleKey])
        assertEquals("SQUIGGLY", prefs[SliderStyleKey])
    }

    @Test fun invalidImportCannotPartiallyMutatePreferences() {
        val prefs = mutablePreferencesOf(PlayerScreenStyleKey to "CLASSIC", LyricsTextSizeKey to 26f)
        assertThrows(IllegalArgumentException::class.java) {
            CustomizationSchema.apply(prefs, profile(PlayerScreenStyleKey.name to JsonPrimitive("PAPER"), LyricsTextSizeKey.name to JsonPrimitive(-1)), CustomizationCategory.entries.toSet())
        }
        assertEquals("CLASSIC", prefs[PlayerScreenStyleKey])
        assertEquals(26f, prefs[LyricsTextSizeKey])
    }

    @Test fun malformedOversizedAndEmptyProfilesAreRejected() {
        listOf("{", " ".repeat(CustomizationSchema.MAX_BYTES + 1), """{"schemaVersion":1,"name":"Night","values":{}}""").forEach {
            assertThrows(Exception::class.java) { CustomizationSchema.decode(it) }
        }
    }

    @Test fun registryHasUniqueKeysAndValidDefaults() {
        assertEquals(CustomizationSchema.settings.size, CustomizationSchema.byKey.size)
        CustomizationSchema.settings.forEach { it.validate(it.default) }
    }
}
