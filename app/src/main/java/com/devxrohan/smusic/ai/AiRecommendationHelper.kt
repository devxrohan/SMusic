package com.devxrohan.smusic.ai

import android.content.Context
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.PlaylistEntity
import com.devxrohan.smusic.db.entities.PlaylistSongMap
import com.devxrohan.smusic.generator.GeneratorRepository
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.models.toMediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

object AiRecommendationHelper {
    const val PLAYLIST_NAME = "Recommended for you"
    private val generationMutex = Mutex()

    suspend fun generateRecommendations(
        context: Context,
        database: MusicDatabase? = null,
        customPrompt: String? = null,
        onLog: (suspend (String) -> Unit)? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            val db = database ?: InternalDatabase.newInstance(context)
            try {
                onLog?.invoke("Analyzing your listening taste…")
                val mostPlayed = db.mostPlayedSongs(fromTimeStamp = 0L, limit = 25).firstOrNull().orEmpty()
                val liked = db.likedSongsByPlayTimeAsc().firstOrNull().orEmpty().take(25)
                val recent = db.recentSongs(limit = 25).firstOrNull().orEmpty()
                val taste = (mostPlayed + liked + recent).distinctBy { it.id }.take(30)
                    .joinToString("\n") { it.title + " by " + it.artists.joinToString { artist -> artist.name } }
                val prompt = "Recommend 20 songs based on these listening signals:\n" + taste +
                    "\nUser request: " + customPrompt.orEmpty() +
                    "\nReturn only a JSON array of objects with title and artist fields. No markdown or prose."
                val response = AiTextClient.complete(context, prompt, 2048)
                val suggestions = response.getOrNull()?.let {
                    runCatching { AiTextProtocol.songs(it) }.getOrNull()
                }.orEmpty()
                val resolved = mutableListOf<MediaMetadata>()
                for ((index, suggestion) in suggestions.withIndex()) {
                    coroutineContext.ensureActive()
                    onLog?.invoke("Finding song " + (index + 1) + " of " + suggestions.size + "…")
                    val songs = YouTube.search((suggestion.title + " " + suggestion.artist).trim(), YouTube.SearchFilter.FILTER_SONG)
                        .getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                    songs.firstOrNull()?.toMediaMetadata()?.let { resolved.add(it) }
                }
                val generator = GeneratorRepository(context.applicationContext, db)
                val tracks = if (resolved.isNotEmpty()) generator.precheck(resolved) else {
                    onLog?.invoke("AI unavailable; building a listening-based mix with the library generator…")
                    if (customPrompt.isNullOrBlank()) generator.fetchMyMix(20) {}
                    else generator.fetchSmartAi(customPrompt, 20) {}
                }
                coroutineContext.ensureActive()
                check(tracks.isNotEmpty()) { "No recommendations found. Please try again when connected." }
                val existing = db.searchPlaylists(PLAYLIST_NAME).firstOrNull()?.find { it.playlist.name == PLAYLIST_NAME }?.playlist
                    ?: db.searchPlaylists("Recommended by AI").firstOrNull()?.find { it.playlist.name == "Recommended by AI" }?.playlist
                val now = LocalDateTime.now()
                val playlist = existing?.copy(name = PLAYLIST_NAME, lastUpdateTime = now)
                    ?: PlaylistEntity(name = PLAYLIST_NAME, createdAt = now, lastUpdateTime = now, bookmarkedAt = now, isEditable = true)
                onLog?.invoke("Saving " + tracks.size + " songs to " + PLAYLIST_NAME + "…")
                db.withTransaction {
                    if (existing == null) insert(playlist) else { update(playlist); clearPlaylist(playlist.id) }
                    tracks.forEachIndexed { index, track ->
                        insert(track)
                        insert(PlaylistSongMap(playlistId = playlist.id, songId = track.id, position = index))
                    }
                }
                Result.success(tracks.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            } finally {
                if (database == null) db.close()
            }
        }
    }
}
