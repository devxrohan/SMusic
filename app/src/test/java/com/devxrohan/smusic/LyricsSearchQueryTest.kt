package com.devxrohan.smusic

import com.devxrohan.smusic.lyrics.artistForLyricsSearch
import com.devxrohan.smusic.lyrics.forLyricsSearch
import com.devxrohan.smusic.lyrics.lyricsArtistKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSearchQueryTest {
    @Test
    fun `removes YouTube feature and popularity labels but preserves recording version`() {
        assertEquals(
            "Dracula (Remix)",
            "Dracula (feat. JENNIE) (Remix) Official Music Video 109M views".forLyricsSearch()
        )
    }

    @Test
    fun `removes metadata field labels around title and artist`() {
        assertEquals("Guzarish", "Song Title: Guzarish Song Artist Satinder Satraj 109M plays".forLyricsSearch())
        assertEquals("Satinder Satraj", "Song Artist: Satinder Satraj - Topic".artistForLyricsSearch())
    }

    @Test
    fun `collaborator order and separators produce individual artist keys`() {
        val artists = "Artist Two, Artist One & Guest".artistForLyricsSearch().lyricsArtistKeys()
        assertTrue("artist one" in artists)
        assertTrue("artist two" in artists)
        assertTrue("guest" in artists)
    }
    @Test
    fun `artist words and recording qualifiers are preserved`() {
        assertEquals("Artist Two", "Artist Two".artistForLyricsSearch())
        assertEquals("Artist", "Artist".artistForLyricsSearch())
        assertEquals("Guest", "Artist: Guest".artistForLyricsSearch())
        assertEquals("Song (Live)", "Song (Live) Official Video".forLyricsSearch())
        assertEquals("Song (Acoustic)", "Song (Acoustic)".forLyricsSearch())
    }
}
