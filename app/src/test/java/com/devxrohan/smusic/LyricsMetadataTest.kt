package com.devxrohan.smusic

import com.devxrohan.smusic.lyrics.lyricsSearchArtists
import com.devxrohan.smusic.lyrics.artistForLyricsSearch
import org.junit.Assert.*
import org.junit.Test

class LyricsMetadataTest {
    @Test fun searchExcludesPlayCountsWithoutLosingRealArtistCredits() {
        assertEquals("", "1.4B plays".artistForLyricsSearch())
        assertEquals("Artist", "Artist • 1.4B plays".artistForLyricsSearch())
        assertEquals("Maroon 5, Rihanna", lyricsSearchArtists(listOf("1.4B plays", "Maroon 5", "42M views", "Rihanna")))
        assertEquals("Artist", "1.4B plays, Artist".artistForLyricsSearch())
    }
}
