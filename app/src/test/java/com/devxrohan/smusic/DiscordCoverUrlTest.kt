package com.devxrohan.smusic

import com.devxrohan.smusic.utils.DiscordCoverUrl
import org.junit.Assert.*
import org.junit.Test

class DiscordCoverUrlTest {
    @Test fun youtubeCoverUsesWidelyAvailableSize() {
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",
            DiscordCoverUrl.forSong("abcdefghijk", "https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg"))
    }
    @Test fun missingYoutubeCoverCanUseItsVideoId() {
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", DiscordCoverUrl.forSong("abcdefghijk", null))
    }
    @Test fun localArtworkIsNotExposed() {
        assertNull(DiscordCoverUrl.forSong("LOCAL_123", "content://media/artwork/1"))
    }
    @Test fun publicAlbumArtworkIsPreserved() {
        assertEquals("https://example.com/album.jpg", DiscordCoverUrl.forSong("abcdefghijk", "https://example.com/album.jpg"))
    }
    @Test fun googleCoverIsRequestedAtPresenceSize() {
        assertEquals("https://lh3.googleusercontent.com/art=w480-h480-p-l90-rj",
            DiscordCoverUrl.forSong("abcdefghijk", "https://lh3.googleusercontent.com/art=w60-h60-l90-rj"))
    }
}
