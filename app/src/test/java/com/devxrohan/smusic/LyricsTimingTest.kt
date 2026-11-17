package com.devxrohan.smusic

import com.devxrohan.smusic.lyrics.LyricsEntry
import com.devxrohan.smusic.lyrics.LyricsUtils
import com.devxrohan.smusic.lyrics.SMusicLyricsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LyricsTimingTest {
    @Test
    fun lrcSortsLinesAndScalesFractionalSeconds() {
        val lines = SMusicLyricsUtils.parseLyrics("[00:02.345]Second\n[00:01.23]First")
        assertEquals(listOf(1230L, 2345L), lines.map { it.time })
        assertEquals(listOf("First", "Second"), lines.map { it.text })
    }

    @Test
    fun repeatedTimestampsPreserveRepeatedChorus() {
        val lines = SMusicLyricsUtils.parseLyrics("[00:01.00][00:09.00]Chorus")
        assertEquals(listOf(1000L, 9000L), lines.map { it.time })
        assertEquals(listOf("Chorus", "Chorus"), lines.map { it.text })
    }

    @Test
    fun enhancedLrcRemovesTimingTagsFromDisplayedWords() {
        val line = SMusicLyricsUtils.parseLyrics("[00:01.00]<00:01.000>Hello <00:01.500>world").single()
        assertFalse(line.text.contains("<00:"))
        assertEquals("Hello world", line.text.trim())
        assertEquals(listOf("Hello", "world"), line.words!!.map { it.text.trim() })
    }

    @Test
    fun malformedLinesAndMetadataDoNotBecomeLyrics() {
        assertEquals(emptyList<LyricsEntry>(), SMusicLyricsUtils.parseLyrics("[ar:Artist]\n[broken]Text\n[00:01.00]"))
    }

    @Test
    fun playheadLookupHandlesEmptyBeforeFirstAndAfterLast() {
        val lines = listOf(LyricsEntry(1000L, "First"), LyricsEntry(2000L, "Second"))
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(emptyList(), 500L))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 0L))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 1500L))
        assertEquals(1, LyricsUtils.findCurrentLineIndex(lines, 9999L))
    }

    @Test
    fun configuredLeadAppliesToSharedPlayheadLookup() {
        val lines = listOf(LyricsEntry(1000L, "First"), LyricsEntry(2000L, "Second"))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 1900L))
        assertEquals(1, SMusicLyricsUtils.findCurrentLineIndex(lines, 1900L, leadMs = 200L))
    }
}
