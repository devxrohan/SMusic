package com.devxrohan.smusic

import com.devxrohan.smusic.utils.cachedArtworkUrl
import com.devxrohan.smusic.ui.component.browseArtworkUrl
import org.junit.Assert.*
import org.junit.Test

class ArtworkUrlTest {
    @Test fun classicAndNotificationsReuseTheBrowsingCoverVariant() {
        val large = "https://lh3.googleusercontent.com/cover=w2160-h2160-p-l90-rj"
        val small = "https://lh3.googleusercontent.com/cover=w226-h226-p-l90-rj"
        assertEquals(browseArtworkUrl(small), cachedArtworkUrl(large))
        assertEquals(cachedArtworkUrl(large), cachedArtworkUrl(cachedArtworkUrl(large)))
        assertEquals("https://yt3.ggpht.com/avatar=s512", cachedArtworkUrl("https://yt3.ggpht.com/avatar=s2160"))
    }

    @Test fun videoCoversDoNotRequireOptionalMaxResolutionArtwork() {
        assertEquals("https://i.ytimg.com/vi/song/hqdefault.jpg", cachedArtworkUrl("https://i.ytimg.com/vi/song/maxresdefault.jpg"))
        val alreadyAvailable = "https://i.ytimg.com/vi/song/hqdefault.jpg"
        assertEquals(alreadyAvailable, cachedArtworkUrl(alreadyAvailable))
    }

    @Test fun localAndUntrustedArtworkUrlsArePreserved() {
        for (url in listOf("content://media/albumart/12", "file:///music/cover.jpg", "https://googleusercontent.com.example.org/a=s2160", "https://ytimg.com.example.org/vi/song/maxresdefault.jpg")) {
            assertEquals(url, cachedArtworkUrl(url))
        }
        assertNull(cachedArtworkUrl(null))
        assertNull(cachedArtworkUrl("  "))
    }
}
