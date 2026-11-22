package com.devxrohan.smusic

import com.devxrohan.smusic.pulse.language.PulseLanguageEvidence
import com.devxrohan.smusic.pulse.language.PulseLanguagePolicy
import org.junit.Assert.*
import org.junit.Test

class PulseLanguagePolicyTest {
    private fun language(vararg codes: String) = PulseLanguageEvidence(codes.toSet(), "lyrics-model", 1.0)

    @Test fun knownDifferentLanguagesCannotEnterTheSameSeedRadio() {
        assertFalse(PulseLanguagePolicy.compatible(language("en"), language("hi")))
        assertFalse(PulseLanguagePolicy.compatible(language("hi"), language("en")))
        assertTrue(PulseLanguagePolicy.compatible(language("hi"), language("hi")))
    }

    @Test fun bilingualAndInstrumentalTracksAreNotMistakenForMismatches() {
        assertTrue(PulseLanguagePolicy.compatible(language("en"), language("en", "hi")))
        assertTrue(PulseLanguagePolicy.compatible(language("en"), language("zxx")))
    }

    @Test fun unknownAndUncertainEvidenceDoesNotEmptyTheQueue() {
        assertTrue(PulseLanguagePolicy.compatible(language("en"), null))
        assertTrue(PulseLanguagePolicy.compatible(null, language("hi")))
        assertTrue(PulseLanguagePolicy.compatible(language("en"), PulseLanguageEvidence(setOf("hi"), "lyrics-model", .6)))
    }

    @Test fun languageLearningRequiresConsistentHighConfidenceLyricSamples() {
        assertEquals(setOf("hi"), PulseLanguagePolicy.fromConsistentSamples(listOf("hi" to .99, "hi" to .97))?.languages)
        assertNull(PulseLanguagePolicy.fromConsistentSamples(listOf("hi" to .99, "en" to .99)))
        assertNull(PulseLanguagePolicy.fromConsistentSamples(listOf("en" to .99, "en" to .7)))
        assertNull(PulseLanguagePolicy.fromConsistentSamples(listOf("und" to 1.0, "und" to 1.0)))
    }

}
