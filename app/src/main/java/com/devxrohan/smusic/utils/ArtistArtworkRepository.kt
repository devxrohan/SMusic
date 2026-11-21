package com.devxrohan.smusic.utils

import android.os.SystemClock
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.ArtistEntity
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.pages.ArtistPage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Shares artist-page artwork with library and Pulse without changing saved artists. */
@Singleton
class ArtistArtworkRepository @Inject constructor(private val database: MusicDatabase) {
    private val attempts = ConcurrentHashMap<String, Long>()

    suspend fun cache(page: ArtistPage) = withContext(Dispatchers.IO) {
        if (page.artist.thumbnail.isBlank()) return@withContext
        database.withTransaction {
            val existing = artist(page.artist.id).first()?.artist
            if (existing == null) insert(ArtistEntity(id = page.artist.id, name = page.artist.title,
                thumbnailUrl = page.artist.thumbnail))
            else update(existing, page)
        }
    }

    suspend fun resolveMissing(id: String): String? = withContext(Dispatchers.IO) {
        database.artist(id).first()?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        if (!id.startsWith("UC")) return@withContext null
        val now = SystemClock.elapsedRealtime()
        val previous = attempts.putIfAbsent(id, now)
        if (previous != null && (now - previous < 300_000 || !attempts.replace(id, previous, now))) return@withContext null
        try {
            val page = withTimeout(12_000L) { MusicPageRequests.artist(id).getOrNull() } ?: return@withContext null
            cache(page)
            page.artist.thumbnail.takeIf { it.isNotBlank() }
        } catch (_: TimeoutCancellationException) { null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
}
