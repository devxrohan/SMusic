package com.devxrohan.smusic

import com.devxrohan.smusic.utils.PcmInputCursor
import com.devxrohan.smusic.utils.lyricsClipWindow
import org.junit.Assert.*
import org.junit.Test

class LyricsExportTimingTest {
    @Test
    fun middleAndFinalPassagesHaveTheSameDurationAsAnOpeningPassage() {
        for (start in listOf(0L, 185_000L, 598_000L)) {
            val clip = lyricsClipWindow(start, start + 6_500L)
            assertEquals(start, clip.startMs)
            assertEquals(6_500L, clip.durationMs)
            assertEquals(start + 6_500L, clip.endMs)
        }
    }

    @Test
    fun passageLimitDoesNotIncludeTheSongsLeadingMinutes() {
        assertEquals(45_000L, lyricsClipWindow(600_000L, 700_000L).durationMs)
        assertEquals(1_200L, lyricsClipWindow(600_000L, 600_100L).durationMs)
    }

    @Test
    fun oversizedDecodedAudioIsFullySplitIntoEncoderBuffers() {
        val cursor = PcmInputCursor(23_040, 2, 48_000, 2_000_000L)
        val slices = generateSequence { cursor.next(4_097) }.toList()
        assertTrue(cursor.complete)
        assertEquals(23_040, slices.sumOf { it.size })
        assertEquals(listOf(0, 4096, 8192, 12288, 16384, 20480), slices.map { it.offset })
        assertTrue(slices.all { it.size in 4..4096 && it.size % 4 == 0 })
        assertEquals(2_000_000L, slices.first().timeUs)
        assertEquals(2_106_666L, slices.last().timeUs)
        assertNull(cursor.next(4096))
    }

    @Test(expected = IllegalArgumentException::class)
    fun impossibleInputCapacityFailsInsteadOfQueueingEmptyBuffersForever() {
        PcmInputCursor(4, 2, 48_000, 0).next(3)
    }
}
