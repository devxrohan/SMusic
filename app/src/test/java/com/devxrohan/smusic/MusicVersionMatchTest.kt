package com.devxrohan.smusic

import com.devxrohan.smusic.playback.MusicVersionMatch
import org.junit.Assert.*
import org.junit.Test

class MusicVersionMatchTest {
    @Test fun officialVideoDecorationMatchesTheSameRecording() {
        assertTrue(MusicVersionMatch.matches("Beanie (Official Music Video)", listOf("Chezile"), 132,
            "Beanie", listOf("Chezile - Topic"), 130))
    }
    @Test fun slowedLiveAndRemixVersionsAreNotSilentlyReplaced() {
        for (variant in listOf("Slowed", "Live", "Remix", "Sped Up")) {
            assertFalse(MusicVersionMatch.matches("Beanie ($variant)", listOf("Chezile"), 132,
                "Beanie", listOf("Chezile"), 132))
        }
    }
    @Test fun sameTitleFromDifferentArtistAndLongEditsAreRejected() {
        assertFalse(MusicVersionMatch.matches("Song", listOf("First"), 120, "Song", listOf("Second"), 120))
        assertFalse(MusicVersionMatch.matches("Song", listOf("First"), 120, "Song", listOf("First"), 180))
    }
    @Test fun nonLatinNamesAreComparedWithoutErasingThem() {
        assertTrue(MusicVersionMatch.matches("दिल", listOf("कलाकार"), 200, "दिल", listOf("कलाकार"), 201))
        assertFalse(MusicVersionMatch.matches("दिल", listOf("कलाकार"), 200, "रात", listOf("कलाकार"), 201))
    }
}
