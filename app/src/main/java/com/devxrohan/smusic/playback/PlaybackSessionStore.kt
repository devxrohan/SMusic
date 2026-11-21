package com.devxrohan.smusic.playback

import android.util.AtomicFile
import com.devxrohan.smusic.models.PersistPlayerState
import com.devxrohan.smusic.models.PersistQueue
import java.io.File

/** One durable generation of queue, suggestions and player preferences; legacy files stay readable. */
internal class PlaybackSessionStore(filesDir: File) {
    data class Session(val queue: PersistQueue, val automix: PersistQueue, val state: PersistPlayerState)
    private val atomic = AtomicFile(File(filesDir, FILE_NAME))
    @Synchronized fun read(): Session? {
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
        return atomic.openRead().use { stream ->
            require(stream.channel.size() <= PlaybackSessionCodec.MAX_BYTES) { "Saved playback session is too large" }
            PlaybackSessionCodec.read(stream)
        }
    }
    @Synchronized fun write(session: Session) {
        PlaybackSessionCodec.validate(session)
        val stream = atomic.startWrite()
        try {
            PlaybackSessionCodec.write(stream, session)
            atomic.finishWrite(stream)
        } catch (failure: Throwable) { atomic.failWrite(stream); throw failure }
    }
    @Synchronized fun clear() = atomic.delete()
    companion object { const val FILE_NAME = "persistent_session.data" }
}
