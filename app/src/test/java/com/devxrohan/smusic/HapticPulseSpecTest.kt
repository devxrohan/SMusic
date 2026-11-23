package com.devxrohan.smusic

import com.devxrohan.smusic.ui.component.HapticPulseSpec
import org.junit.Assert.*
import org.junit.Test

class HapticPulseSpecTest {
    @Test fun zeroStrengthIsSilent() {
        assertEquals(0L, HapticPulseSpec.fromStrength(0, false).durationMs)
        assertEquals(0L, HapticPulseSpec.fromStrength(-10, true).durationMs)
    }
    @Test fun strengthIncreasesDurationAndAmplitudeForBothMotorTypes() {
        for (tick in listOf(false, true)) {
            val low = HapticPulseSpec.fromStrength(10, tick)
            val high = HapticPulseSpec.fromStrength(100, tick)
            assertTrue(high.durationMs > low.durationMs)
            assertTrue(high.amplitude > low.amplitude)
            assertTrue(high.amplitude <= 255)
        }
    }
    @Test fun sliderTicksRemainShortEvenAtMaximumStrength() {
        assertEquals(18L, HapticPulseSpec.fromStrength(100, true).durationMs)
        assertEquals(45L, HapticPulseSpec.fromStrength(100, false).durationMs)
        assertEquals(HapticPulseSpec.fromStrength(100, true), HapticPulseSpec.fromStrength(200, true))
    }
}
