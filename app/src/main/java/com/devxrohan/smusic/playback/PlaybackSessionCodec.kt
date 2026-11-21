package com.devxrohan.smusic.playback

import com.devxrohan.smusic.models.PersistPlayerState
import com.devxrohan.smusic.models.PersistQueue
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream

/** Keeps the existing session format independent of Android's atomic-file lifecycle. */
internal object PlaybackSessionCodec {
    const val MAX_BYTES = 16L * 1024 * 1024
    private const val MAGIC = 0x534D5553
    private const val VERSION = 1

    fun validate(session: PlaybackSessionStore.Session) {
        require(session.queue.items.size <= 10_000 && session.automix.items.size <= 10_000) { "Saved playback queue is too large" }
        require(session.state.volume.isFinite() && session.state.volume in 0f..1f) { "Invalid saved playback volume" }
        require(session.queue.position >= 0 && session.state.currentPosition >= 0) { "Invalid saved playback position" }
    }

    fun read(stream: InputStream, maxBytes: Long = MAX_BYTES): PlaybackSessionStore.Session {
        require(maxBytes > 0)
        val limited = object : FilterInputStream(stream) {
            var count = 0L
            override fun read(): Int = `in`.read().also { if (it >= 0) { count++; require(count <= maxBytes) { "Saved playback session is too large" } } }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = `in`.read(bytes, offset, length).also {
                if (it > 0) { count += it; require(count <= maxBytes) { "Saved playback session is too large" } }
            }
        }
        return ObjectInputStream(limited).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) { "Unsupported playback session" }
            PlaybackSessionStore.Session(input.readObject() as PersistQueue, input.readObject() as PersistQueue, input.readObject() as PersistPlayerState).also(::validate)
        }
    }

    fun write(stream: OutputStream, session: PlaybackSessionStore.Session, maxBytes: Long = MAX_BYTES) {
        validate(session)
        require(maxBytes > 0)
        val limited = object : FilterOutputStream(stream) {
            var count = 0L
            override fun write(value: Int) {
                require(count + 1 <= maxBytes) { "Saved playback session is too large" }
                count++; out.write(value)
            }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                require(count + length <= maxBytes) { "Saved playback session is too large" }
                count += length; out.write(bytes, offset, length)
            }
        }
        // Do not close the caller-owned stream: AtomicFile.finishWrite still needs it open.
        ObjectOutputStream(limited).apply {
            writeInt(MAGIC); writeInt(VERSION)
            writeObject(session.queue); writeObject(session.automix); writeObject(session.state)
            flush()
        }
    }
}
