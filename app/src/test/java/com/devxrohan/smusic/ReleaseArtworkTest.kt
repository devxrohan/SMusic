package com.devxrohan.smusic

import com.devxrohan.smusic.ui.component.releaseArtworkUrls
import org.junit.Assert.*
import org.junit.Test

class ReleaseArtworkTest {
    @Test fun warmOnlyDistinctHttpsImagesWithinThePrefetchBudget() {
        val body = "![cover](https://example.org/one.png) ![same](https://example.org/one.png)" +
            "<img src=\"https://example.org/two.png?a=1&amp;b=2\">" +
            " ![extra](https://example.org/three.png) ![local](file:///private/file)"
        assertEquals(listOf("https://example.org/one.png", "https://example.org/two.png?a=1&b=2"), releaseArtworkUrls(body))
        assertEquals("https://example.org/one.png", releaseArtworkUrls(body).first())
        assertTrue(releaseArtworkUrls("[ordinary link](https://example.org/a.png)").isEmpty())
    }
}
