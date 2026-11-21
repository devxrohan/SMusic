package com.devxrohan.smusic

import com.devxrohan.smusic.update.releaseNotesMarkup
import org.junit.Assert.*
import org.junit.Test

class ReleaseNotesMarkupTest {
    @Test fun atomTableKeepsItsColumnsAndSurroundingArtwork() {
        val raw = "<h2>Changes</h2><img src=\"https://example.org/cover.png\"><table><tr><th>Feature</th><th>Details</th></tr><tr><td>Lyrics</td><td>Cards</td></tr></table>"
        val notes = releaseNotesMarkup(raw)
        assertTrue(notes.contains("| Feature | Details |"))
        assertTrue(notes.contains("| Lyrics | Cards |"))
        assertTrue(notes.contains("https://example.org/cover.png"))
        assertFalse(notes.contains("<table>"))
        assertEquals("## Title\n**Unchanged**", releaseNotesMarkup("## Title\n**Unchanged**"))
    }
}
