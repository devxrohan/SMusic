package com.devxrohan.smusic

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.customization.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class HomeLayoutTest {
    @Test fun olderCustomLayoutsGainSpeedDialWithoutReorderingExistingSections() {
        val old = HomeLayout.defaultOrder.filterNot { it == "speed_dial" }.reversed()
        val migrated = HomeLayout.decode(old.joinToString(","))
        assertEquals(old, migrated.filterNot { it == "speed_dial" })
        assertEquals(1, migrated.count { it == "speed_dial" })
        HomeLayout.presets.forEach { assertEquals(JsonPrimitive(true), it.values["homeSpeedDialVisible"]) }
    }
    @Test fun legacyPresetsPreserveTheirOpeningOrder() {
        assertEquals(listOf("recent", "quick", "youtube"), HomeLayout.decode("RECENT_FIRST").take(3))
        assertEquals(listOf("youtube", "quick", "recent"), HomeLayout.decode("YOUTUBE_FIRST").take(3))
        assertEquals(HomeLayout.defaultOrder, HomeLayout.decode(null))
    }
    @Test fun customOrderRoundTripsAndInvalidOrdersFallBack() {
        val order = HomeLayout.defaultOrder.reversed()
        assertEquals(order, HomeLayout.decode(HomeLayout.encode(order)))
        assertFalse(HomeLayout.isValid("quick,quick,recent"))
        assertFalse(HomeLayout.isValid(HomeLayout.encode(order) + ",unknown"))
        assertEquals(HomeLayout.defaultOrder, HomeLayout.decode("bad"))
    }
    @Test fun everyReadyMadePresetValidatesAndPreservesUnrelatedSettings() {
        assertEquals(2, HomeLayout.presets.size)
        HomeLayout.presets.forEach { preset ->
            val profile = CustomizationProfile(name = preset.name, values = preset.values)
            CustomizationSchema.validate(profile)
            val prefs = mutablePreferencesOf(PlayerScreenStyleKey to "PAPER", InnerTubeCookieKey to "secret")
            CustomizationSchema.apply(prefs, profile, setOf(CustomizationCategory.LAYOUT))
            assertTrue(HomeLayout.isValid(prefs[HomeSectionPriorityKey]!!))
            assertEquals("PAPER", prefs[PlayerScreenStyleKey])
            assertEquals("secret", prefs[InnerTubeCookieKey])
        }
    }
    @Test fun importingDuplicateSectionOrderIsRejectedBeforeMutation() {
        val prefs = mutablePreferencesOf(HomeSectionPriorityKey to "PICKS_FIRST")
        assertThrows(IllegalArgumentException::class.java) {
            CustomizationSchema.apply(prefs, CustomizationProfile(name = "Bad", values = mapOf(HomeSectionPriorityKey.name to JsonPrimitive("quick,quick"))), setOf(CustomizationCategory.LAYOUT))
        }
        assertEquals("PICKS_FIRST", prefs[HomeSectionPriorityKey])
    }
    @Test fun visibleItemLimitChangesAcrossSelectionsAndDefaultsWithoutPadding() {
        val source = (1..30).toList()
        assertEquals(source.take(6), HomeLayout.limitItems(source, "6"))
        assertEquals(source.take(24), HomeLayout.limitItems(source, "24"))
        assertEquals(source, HomeLayout.limitItems(source, "DEFAULT"))
        assertEquals(listOf(1, 2), HomeLayout.limitItems(listOf(1, 2), "24"))
        assertEquals(source, HomeLayout.limitItems(source, "invalid"))
    }
}
