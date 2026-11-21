package com.devxrohan.smusic

import com.devxrohan.smusic.ui.component.browseArtworkUrl
import org.junit.Assert.*
import org.junit.Test

class BrowseArtworkTest {
    @Test fun browsingBoundsTrustedCdnRequestsAndKeepsOtherUrlsIntact() {
        assertEquals("https://lh3.googleusercontent.com/cover=w512-h512-p-l90-rj", browseArtworkUrl("https://lh3.googleusercontent.com/cover=w2160-h2160-p-l90-rj"))
        assertEquals("https://yt3.ggpht.com/avatar=s512", browseArtworkUrl("https://yt3.ggpht.com/avatar=s2048"))
        val other = "https://googleusercontent.com.example.org/a=s2048"
        assertEquals(other, browseArtworkUrl(other))
        assertEquals("content://media/cover/3", browseArtworkUrl("content://media/cover/3"))
        assertNull(browseArtworkUrl(""))
    }
}
