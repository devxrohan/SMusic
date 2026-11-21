package com.devxrohan.smusic

import com.devxrohan.smusic.update.UpdateEligibility
import com.devxrohan.smusic.update.VersionCompare
import org.junit.Assert.*
import org.junit.Test

class UpdateEligibilityTest {
    @Test fun oldOrEqualNamesWithoutBuildCodesAreNotUpdates() {
        assertNull(UpdateEligibility.newerCode("1.9.9", null, "2.0.0", 4))
        assertNull(UpdateEligibility.newerCode("v2.0.0", null, "2.0.0", 4))
        assertNull(UpdateEligibility.newerCode("latest", null, "2.0.0", 4))
        assertEquals(5, UpdateEligibility.newerCode("2.0.1", null, "2.0.0", 4))
    }

    @Test fun publishedBuildCodeAndNameMustBothPermitUpgrade() {
        assertNull(UpdateEligibility.newerCode("2.1.0", 3, "2.0.0", 4))
        assertNull(UpdateEligibility.newerCode("1.9.0", 20, "2.0.0", 4))
        assertEquals(5, UpdateEligibility.newerCode("2.0.0", 5, "2.0.0", 4))
        assertEquals(5, UpdateEligibility.newerCode("", 5, "2.0.0", 4))
    }

    @Test fun stableAndPrereleaseOrderingIsPreserved() {
        assertNull(UpdateEligibility.newerCode("2.0.0-beta.9", null, "2.0.0", 4))
        assertEquals(5, UpdateEligibility.newerCode("2.0.0", null, "2.0.0-beta.9", 4))
        assertTrue(VersionCompare.isNewer("2.0.0-beta.10", "2.0.0-beta.9"))
        assertEquals(0, VersionCompare.compare("v2.0.0+5", "2.0.0+4"))
    }
}
