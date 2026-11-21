package com.devxrohan.smusic

import com.devxrohan.smusic.playback.visualizerCaptureIsStale
import org.junit.Assert.*
import org.junit.Test

class VisualizerCaptureHealthTest {
    @Test fun absentOrStalledNativeFramesEnableDecoration() {
        assertTrue(visualizerCaptureIsStale(null, 1_500))
        assertTrue(visualizerCaptureIsStale(100, 1_600))
    }

    @Test fun freshFramesIncludingSilenceDoNotEnableDecoration() {
        assertFalse(visualizerCaptureIsStale(100, 1_599))
        assertFalse(visualizerCaptureIsStale(2_000, 2_000))
    }

    @Test fun returningNativeFramesRecoverFromDecorativeMode() {
        assertTrue(visualizerCaptureIsStale(100, 2_000))
        assertFalse(visualizerCaptureIsStale(2_000, 2_001))
    }
}
