package com.devxrohan.smusic

import com.devxrohan.smusic.ui.screens.homeHeadingCase
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeadingCaseTest {
    @Test
    fun sectionHeadingsUseConsistentInitialCapitals() {
        assertEquals("The Usuals", homeHeadingCase("The Usuals", Locale.ENGLISH))
        assertEquals("Quick Picks", homeHeadingCase("quick picks", Locale.ENGLISH))
        assertEquals("Forgotten Favorites", homeHeadingCase("Forgotten favorites", Locale.ENGLISH))
    }

    @Test
    fun acronymsPunctuationAndOtherScriptsArePreserved() {
        assertEquals("Your EDM Mixes", homeHeadingCase("Your EDM mixes", Locale.ENGLISH))
        assertEquals("Rock & Roll", homeHeadingCase("rock & roll", Locale.ENGLISH))
        assertEquals("संगीत के लिए", homeHeadingCase("संगीत के लिए", Locale.ENGLISH))
    }
}
