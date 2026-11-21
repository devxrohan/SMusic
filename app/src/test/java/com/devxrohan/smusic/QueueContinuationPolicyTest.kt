package com.devxrohan.smusic
import com.devxrohan.smusic.playback.queues.QueueContinuationPolicy
import org.junit.Assert.*
import org.junit.Test

class QueueContinuationPolicyTest {
    @Test fun alternateVideoIdsDoNotRepeatTheSameRecordingButRemixesRemainDistinct() {
        val original = QueueContinuationPolicy.recordingKey("My Song (Official Video)", listOf("12M plays", "JENNIE - Topic"))!!
        val duplicate = QueueContinuationPolicy.recordingKey("My Song", listOf("JENNIE"))!!
        val remix = QueueContinuationPolicy.recordingKey("My Song (Remix)", listOf("JENNIE"))!!
        assertEquals(original, duplicate)
        assertNotEquals(original, remix)
        assertEquals(listOf("remix"), QueueContinuationPolicy.unseen(listOf("other-video", "remix"), emptySet(),
            setOf(original), { if (it == "remix") remix else duplicate }) { it })
    }
    @Test fun nonLatinVowelMarksAreNotDiscardedFromRecordingIdentity() {
        assertNotEquals(QueueContinuationPolicy.recordingKey("कल", listOf("गायक")),
            QueueContinuationPolicy.recordingKey("काला", listOf("गायक")))
        assertNull(QueueContinuationPolicy.recordingKey("Song", emptyList()))
    }
    @Test fun providerPagesCannotReintroduceQueuedOrHeardSongs() {
        assertEquals(listOf("new"), QueueContinuationPolicy.unseen(listOf("heard", "queued", "new", "new", ""), setOf("heard", "queued")) { it })
    }
    @Test fun disablingCrossPageProtectionStillRemovesDuplicateRowsWithinAPage() {
        assertEquals(listOf("heard", "new"), QueueContinuationPolicy.unseen(listOf("heard", "new", "heard"), emptySet()) { it })
    }
    @Test fun entirelyRepeatedPageIsEmptySoTheLoaderCanTryTheNextContinuation() {
        assertTrue(QueueContinuationPolicy.unseen(listOf("old"), setOf("old")) { it }.isEmpty())
    }
}
