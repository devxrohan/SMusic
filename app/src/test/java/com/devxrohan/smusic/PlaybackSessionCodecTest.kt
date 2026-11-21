package com.devxrohan.smusic

import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.models.PersistPlayerState
import com.devxrohan.smusic.models.PersistQueue
import com.devxrohan.smusic.playback.PlaybackSessionCodec
import com.devxrohan.smusic.playback.PlaybackSessionStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import org.junit.Assert.*
import org.junit.Test

class PlaybackSessionCodecTest {
    private val track = MediaMetadata("song-a", "A song", listOf(MediaMetadata.Artist("artist-a", "An artist")), 180)
    private fun session() = PlaybackSessionStore.Session(
        PersistQueue("Queue", listOf(track, track.copy(id = "song-b")), 1, 1234L),
        PersistQueue("AutoMix", listOf(track.copy(id = "suggestion")), 0, 0),
        PersistPlayerState(true, 2, true, .7f, 1234L, 1, 3),
    )
    private fun encode(value: PlaybackSessionStore.Session) = ByteArrayOutputStream().also { PlaybackSessionCodec.write(it, value) }.toByteArray()
    private fun assertSameSessionValues(expected: PlaybackSessionStore.Session, actual: PlaybackSessionStore.Session) {
        // Java serialization recreates Kotlin object instances. Queue restoration uses
        // their type, so validate kind and contents rather than singleton identity.
        assertEquals(expected.queue.queueType.javaClass, actual.queue.queueType.javaClass)
        assertEquals(expected.automix.queueType.javaClass, actual.automix.queueType.javaClass)
        assertEquals(expected.queue.copy(queueType = actual.queue.queueType), actual.queue)
        assertEquals(expected.automix.copy(queueType = actual.automix.queueType), actual.automix)
        assertEquals(expected.state, actual.state)
    }

    @Test fun roundTripKeepsQueueSuggestionsAndPlaybackStateTogether() {
        val original = session()
        assertSameSessionValues(original, PlaybackSessionCodec.read(ByteArrayInputStream(encode(original))))
    }

    @Test fun readsTheExistingVersionOneObjectStreamFormat() {
        val original = session()
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { output ->
            output.writeInt(0x534D5553); output.writeInt(1)
            output.writeObject(original.queue); output.writeObject(original.automix); output.writeObject(original.state)
        }
        assertSameSessionValues(original, PlaybackSessionCodec.read(ByteArrayInputStream(bytes.toByteArray())))
    }

    @Test fun futureVersionOrWrongMagicIsRejected() {
        listOf(0x534D5553 to 2, 0 to 1).forEach { (magic, version) ->
            val bytes = ByteArrayOutputStream()
            ObjectOutputStream(bytes).use { it.writeInt(magic); it.writeInt(version) }
            assertThrows(IllegalArgumentException::class.java) { PlaybackSessionCodec.read(ByteArrayInputStream(bytes.toByteArray())) }
        }
    }

    @Test fun truncatedSessionDoesNotReturnAPartialQueue() {
        val bytes = encode(session())
        assertThrows(IOException::class.java) { PlaybackSessionCodec.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 20))) }
    }

    @Test fun invalidVolumeIsRejectedBeforeWritingAnyBytes() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f).forEach { volume ->
            val original = session()
            val bytes = ByteArrayOutputStream()
            assertThrows(IllegalArgumentException::class.java) { PlaybackSessionCodec.write(bytes, original.copy(state = original.state.copy(volume = volume))) }
            assertEquals(0, bytes.size())
        }
    }

    @Test fun oversizedQueuesAndNegativePlayheadsAreRejected() {
        val original = session()
        val invalid = listOf(
            original.copy(queue = original.queue.copy(items = List(10_001) { track })),
            original.copy(automix = original.automix.copy(items = List(10_001) { track })),
            original.copy(queue = original.queue.copy(position = -1)),
            original.copy(state = original.state.copy(currentPosition = -1)),
        )
        invalid.forEach { value -> assertThrows(IllegalArgumentException::class.java) { encode(value) } }
    }

    @Test fun byteBudgetsApplyToBothStreamDirections() {
        val bytes = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) { PlaybackSessionCodec.write(bytes, session(), maxBytes = 32) }
        assertTrue(bytes.size() <= 32)
        assertThrows(IllegalArgumentException::class.java) { PlaybackSessionCodec.read(ByteArrayInputStream(encode(session())), maxBytes = 32) }
    }

    @Test fun writerLeavesCallerOwnedAtomicFileStreamOpen() {
        var closed = false
        val bytes = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        PlaybackSessionCodec.write(bytes, session())
        assertFalse(closed)
        assertSameSessionValues(session(), PlaybackSessionCodec.read(ByteArrayInputStream(bytes.toByteArray())))
    }
}
