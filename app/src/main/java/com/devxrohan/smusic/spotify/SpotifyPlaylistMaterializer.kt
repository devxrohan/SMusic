package com.devxrohan.smusic.spotify

import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.PlaylistEntity
import com.devxrohan.smusic.db.entities.PlaylistSongMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Resolves connected playlists into the existing library only while their page is open. */
class SpotifyPlaylistMaterializer(
    private val scope: CoroutineScope,
    private val database: MusicDatabase,
    private val repository: SpotifyLibraryRepository,
) {
    data class State(val loading: Boolean = false, val completed: Int = 0, val total: Int = 0, val error: String? = null, val coverUrl: String? = null)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun load(localId: String) {
        if (!localId.startsWith("SP:") || job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val importJob = requireNotNull(currentCoroutineContext()[Job])
            SpotifyPlaylistImports.register(localId, importJob)
            mutableState.value = State(loading = true)
            try {
                val remoteId = localId.removePrefix("SP:")
                val remote = repository.playlists.value.firstOrNull { it.id == remoteId }
                    ?: repository.playlist(remoteId)
                val cover = remote.images.firstOrNull()?.url
                mutableState.value = mutableState.value.copy(coverUrl = cover)
                val blocked = repository.blockedItems()
                val tracks = repository.playlistTracks(remoteId).filterNot { SpotifySyncPolicy.playlistTrackBlockKey(localId, it.id) in blocked }
                val previous = database.playlist(localId).first()
                val entity = previous?.playlist?.copy(name = remote.name, remoteSongCount = tracks.size)
                    ?: PlaylistEntity(id = localId, name = remote.name, remoteSongCount = tracks.size, bookmarkedAt = LocalDateTime.now())
                if (previous == null) database.insert(entity) else database.update(entity)
                val saved = database.playlistSongs(localId).first().associateBy { it.map.setVideoId }
                val retained = mutableSetOf<Int>()
                var missing = 0
                // Bound parallel searches to four; database writes retain Spotify order.
                val claimedMaps = mutableSetOf<Int>()
                tracks.withIndex().chunked(4).forEach { batch ->
                    val plan = batch.map { (index, track) ->
                        val key = "spotify:${track.id}:$index"
                        val existing = saved[key]?.takeIf { it.map.id !in claimedMaps }
                            ?: saved.values.firstOrNull { it.map.id !in claimedMaps && SpotifySyncPolicy.spotifyTrackId(it.map.setVideoId) == track.id }
                        existing?.let { claimedMaps += it.map.id }
                        Triple(index, track, existing)
                    }
                    val resolved = coroutineScope {
                        plan.map { (index, track, existing) -> async {
                            val key = "spotify:${track.id}:$index"
                            Triple(index, key, existing to if (existing == null) SpotifyPlaybackResolver.resolveToMetadata(track) else null)
                        } }.awaitAll()
                    }
                    resolved.forEach { (index, sourceKey, result) ->
                        val (existing, song) = result
                        if (existing != null) {
                            retained += existing.map.id
                            if (existing.map.position != index || existing.map.setVideoId != sourceKey) database.update(existing.map.copy(position = index, setVideoId = sourceKey))
                        } else if (song != null) {
                            database.insert(song)
                            database.insert(PlaylistSongMap(playlistId = localId, songId = song.id, position = index, setVideoId = sourceKey))
                        } else missing++
                    }
                    mutableState.value = State(loading = true, completed = batch.last().index + 1, total = tracks.size, coverUrl = cover)
                }
                saved.values.filter { it.map.id !in retained && SpotifySyncPolicy.spotifyTrackId(it.map.setVideoId) != null }.forEach { database.delete(it.map) }
                mutableState.value = State(completed = tracks.size, total = tracks.size, coverUrl = cover,
                    error = if (missing > 0) "$missing songs could not be matched on YouTube Music." else null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, error = spotifyErrorMessage(error))
            } finally {
                mutableState.value = mutableState.value.copy(loading = false)
                SpotifyPlaylistImports.finished(localId, importJob)
            }
        }
    }
}
