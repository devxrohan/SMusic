package com.devxrohan.smusic.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.ArtistEntity
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.AlbumItem
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps Spotify membership separate from YouTube likes and playback metadata. */
@Singleton
class SpotifyLibraryBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val repository: SpotifyLibraryRepository,
    private val pulse: com.devxrohan.smusic.pulse.PulseEngine,
) {
    private val mutex = Mutex()
    @Volatile private var activeSync: Job? = null
    private var lastSync = 0L
    private var lastLikesSync = 0L
    val links = context.dataStore.data.map { preferences ->
        decodeLinks(preferences[SpotifyImportedLinksKey])
    }.distinctUntilChanged()

    private suspend fun save(values: Map<String, String>) {
        context.dataStore.edit { it[SpotifyImportedLinksKey] = Json.encodeToString(values) }
    }

    suspend fun syncLibrary(force: Boolean = false) = mutex.withLock {
        activeSync = currentCoroutineContext()[Job]
        try {
            if (context.dataStore.data.first()[ShowSpotifyPlaylistsKey] != true) return@withLock
            if (!force && System.currentTimeMillis() - lastSync < 300_000) return@withLock
            syncLikesUnlocked(force)
            val blocked = context.dataStore.data.first()[SpotifyBlockedItemsKey].orEmpty()
            val updated = links.first().toMutableMap()
            for (artist in repository.savedArtists()) {
                val key = "artist:${artist.id}"
                if (key in blocked) continue
                if (updated[key]?.let { database.artist(it).first()?.artist?.bookmarkedAt != null } == true) continue
                val result = YouTube.search(artist.name, YouTube.SearchFilter.FILTER_ARTIST).getOrThrow()
                    .items.filterIsInstance<ArtistItem>().firstOrNull { sameName(it.title, artist.name) } ?: continue
                val previous = database.artist(result.id).first()?.artist
                if (previous == null) database.insert(ArtistEntity(result.id, result.title, result.thumbnail,
                    channelId = result.channelId, bookmarkedAt = LocalDateTime.now()))
                else database.update(previous.copy(thumbnailUrl = previous.thumbnailUrl ?: result.thumbnail, channelId = previous.channelId ?: result.channelId, bookmarkedAt = previous.bookmarkedAt ?: LocalDateTime.now()))
                updated[key] = result.id
                save(updated)
            }
            for (album in repository.savedAlbums()) {
                val key = "album:${album.id}"
                if (key in blocked) continue
                if (updated[key]?.let { database.album(it).first()?.album?.bookmarkedAt != null } == true) continue
                val query = "${album.name} ${album.artists.firstOrNull()?.name.orEmpty()}".trim()
                val match = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrThrow()
                    .items.filterIsInstance<AlbumItem>().firstOrNull { candidate ->
                        sameName(candidate.title, album.name) && (album.artists.isEmpty() || candidate.artists.orEmpty().any { yt -> album.artists.any { sameName(it.name, yt.name) } })
                    } ?: continue
                val page = com.devxrohan.smusic.utils.MusicPageRequests.album(match.browseId).getOrThrow()
                database.insert(page)
                database.album(page.album.id).first()?.album?.let { if (it.bookmarkedAt == null) database.update(it.localToggleLike()) }
                updated[key] = page.album.id
                save(updated)
            }
            pulse.refreshLibraryTaste()
            lastSync = System.currentTimeMillis()
        } finally { activeSync = null }
    }

    suspend fun syncLikes(force: Boolean = false) = mutex.withLock {
        activeSync = currentCoroutineContext()[Job]
        try {
            if (context.dataStore.data.first()[ShowSpotifyPlaylistsKey] == true) syncLikesUnlocked(force)
        } finally { activeSync = null }
    }

    private suspend fun syncLikesUnlocked(force: Boolean) {
        if (!force && System.currentTimeMillis() - lastLikesSync < 300_000) return
        val snapshot = repository.likedSnapshot()
        val tracks = snapshot.tracks
        context.dataStore.edit { prefs ->
            prefs[SpotifyLikedCatalogIdsKey] = tracks.map { it.id }.toSet()
            prefs[SpotifyLikedCatalogFetchedAtKey] = System.currentTimeMillis()
            prefs[SpotifyLikedCatalogCompleteAtKey] = if (snapshot.complete) System.currentTimeMillis() else 0L
        }
        val blocked = context.dataStore.data.first()[SpotifyBlockedItemsKey].orEmpty()
        val updated = links.first().toMutableMap()
        val present = tracks.map { "track:${it.id}" }.toSet()
        updated.keys.removeAll { it.startsWith("track:") && it !in present }
        tracks.filterNot { "track:${it.id}" in blocked }.chunked(4).forEach { batch ->
            val resolved = coroutineScope {
                batch.map { track -> async(Dispatchers.IO) {
                    val key = "track:${track.id}"
                    val savedId = updated[key]
                    if (savedId != null && database.song(savedId).first() != null) key to savedId
                    else SpotifyPlaybackResolver.resolveToMetadata(track)?.let { metadata ->
                        database.insert(metadata)
                        key to metadata.id
                    }
                } }.awaitAll().filterNotNull()
            }
            updated.putAll(resolved)
            save(updated)
        }
        save(updated)
        pulse.refreshLibraryTaste()
        lastLikesSync = System.currentTimeMillis()
    }

    suspend fun remove(kind: String, localId: String, blockFutureSync: Boolean) {
        activeSync?.takeIf { it != currentCoroutineContext()[Job] }?.cancelAndJoin()
        if (kind == "playlist") SpotifyPlaylistImports.cancel("SP:${localId.removePrefix("SP:")}")
        val playlistTrack = if (kind == "playlist_track") SpotifySyncPolicy.parsePlaylistTrackToken(localId) else null
        playlistTrack?.let { SpotifyPlaylistImports.cancel(it.playlistId) }
        mutex.withLock {
            lastSync = 0L
            lastLikesSync = 0L
            if (kind == "playlist_track") {
                val ref = requireNotNull(playlistTrack)
                if (blockFutureSync) context.dataStore.edit { prefs ->
                    prefs[SpotifyBlockedItemsKey] = prefs[SpotifyBlockedItemsKey].orEmpty() + SpotifySyncPolicy.playlistTrackBlockKey(ref.playlistId, ref.spotifyId)
                }
                val songs = database.playlistSongs(ref.playlistId).first()
                songs.firstOrNull { it.map.id == ref.mapId && SpotifySyncPolicy.spotifyTrackId(it.map.setVideoId) == ref.spotifyId }?.let { database.delete(it.map) }
                database.playlist(ref.playlistId).first()?.playlist?.let { current ->
                    database.update(current.copy(remoteSongCount = ((current.remoteSongCount ?: songs.size) - 1).coerceAtLeast(0)))
                }
            } else if (kind == "playlist") {
                repository.removePlaylist(localId.removePrefix("SP:"), blockFutureSync)
                database.playlist("SP:${localId.removePrefix("SP:")}").first()?.let { database.delete(it.playlist) }
            } else {
                context.dataStore.edit { prefs -> prefs[SpotifyExportExclusionsKey] = prefs[SpotifyExportExclusionsKey].orEmpty() + "$kind:$localId" }
                val updated = links.first().toMutableMap()
                val keys = SpotifySyncPolicy.removalKeys(updated, kind, localId)
                if (blockFutureSync) context.dataStore.edit { prefs ->
                    prefs[SpotifyBlockedItemsKey] = prefs[SpotifyBlockedItemsKey].orEmpty() + keys
                }
                keys.forEach(updated::remove)
                save(updated)
                when (kind) {
                    "artist" -> database.artist(localId).first()?.artist?.let { if (it.bookmarkedAt != null) database.update(it.localToggleLike()) }
                    "album" -> database.album(localId).first()?.album?.let { if (it.bookmarkedAt != null) database.update(it.localToggleLike()) }
                    else -> Unit
                }
            }
            pulse.refreshLibraryTaste()
        }
    }

    companion object {
        internal fun decodeLinks(value: String?): Map<String, String> =
            runCatching { Json.decodeFromString<Map<String, String>>(value.orEmpty()) }.getOrDefault(emptyMap())
        internal fun sameName(first: String, second: String): Boolean {
            fun normalize(value: String) = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]"), "")
            return normalize(first).isNotEmpty() && normalize(first) == normalize(second)
        }
    }
}
