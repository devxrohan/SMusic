package com.devxrohan.smusic

import com.devxrohan.smusic.utils.ExportArtworkPolicy
import com.devxrohan.smusic.utils.PlaybackSource
import org.junit.Assert.*
import org.junit.Test

class ExportArtworkPolicyTest {
    @Test fun singlesCannotShareAnUnknownAlbumArtworkSlot() {
        assertNotEquals(ExportArtworkPolicy.album("First", "Artist", null), ExportArtworkPolicy.album("Second", "Artist", "<unknown>"))
        assertEquals("Real album", ExportArtworkPolicy.album("Song", "Artist", "Real album"))
    }
    @Test fun artworkCacheCannotReuseAnotherTrackOrAnOlderThumbnail() {
        assertNotEquals(ExportArtworkPolicy.cacheKey("first", "url"), ExportArtworkPolicy.cacheKey("second", "url"))
        assertNotEquals(ExportArtworkPolicy.cacheKey("first", "url"), ExportArtworkPolicy.cacheKey("first", "new-url"))
    }
    @Test fun localContentAndFileIdsAreNeverLabelledYouTube() {
        for (id in listOf("local:42", "content://media/external/audio/media/42", "file:///storage/Music/song.m4a"))
            assertTrue(PlaybackSource.isLocal(id))
        assertFalse(PlaybackSource.isLocal("abcdefghijk"))
        assertTrue(PlaybackSource.isLocal("abcdefghijk", "file:///download/song.m4a"))
    }
}
