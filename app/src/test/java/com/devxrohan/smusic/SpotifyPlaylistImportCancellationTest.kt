package com.devxrohan.smusic

import com.devxrohan.smusic.spotify.SpotifyPlaylistImports
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SpotifyPlaylistImportCancellationTest {
    @Test fun removalWaitsForImportToStopBeforeChangingPlaylist() = runBlocking {
        val registered = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val importing = launch {
            val job = currentCoroutineContext()[Job]!!
            SpotifyPlaylistImports.register("SP:test", job)
            registered.complete(Unit)
            try { awaitCancellation() }
            finally { stopped.complete(Unit); SpotifyPlaylistImports.finished("SP:test", job) }
        }
        registered.await()
        SpotifyPlaylistImports.cancel("SP:test")
        assertTrue(importing.isCancelled)
        assertTrue(stopped.isCompleted)
    }
}
