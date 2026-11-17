package com.devxrohan.smusic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUpdateVersionTest {
    @Test
    fun stableReleaseIsNewerThanItsBeta() {
        assertTrue(isNewerVersion("2.1.0", "2.1.0-beta.9"))
        assertFalse(isNewerVersion("2.1.0-beta.9", "2.1.0"))
    }

    @Test
    fun buildMetadataDoesNotHideANewerPatch() {
        assertTrue(isNewerVersion("v2.0.1+6", "2.0.0+5"))
        assertFalse(isNewerVersion("v2.0.0+6", "2.0.0+5"))
    }

    @Test
    fun betaNumbersUseNumericOrdering() {
        assertTrue(isNewerVersion("2.1.0-beta.10", "2.1.0-beta.9"))
        assertFalse(isNewerVersion("2.1.0-beta.9", "2.1.0-beta.10"))
    }
}
