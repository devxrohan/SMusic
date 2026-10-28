package com.devxrohan.smusic.utils

import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.AlbumItem
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.innertube.models.PlaylistItem
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.utils.completed
import com.devxrohan.smusic.innertube.utils.completedLibraryPage
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.ArtistEntity
import com.devxrohan.smusic.db.entities.PlaylistEntity
import com.devxrohan.smusic.db.entities.PlaylistSongMap
import com.devxrohan.smusic.db.entities.SongEntity
import com.devxrohan.smusic.models.toMediaMetadata
import android.content.Context
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.innertube.utils.parseCookieString
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SyncUtils @Inject constructor(
    val database: MusicDatabase,
    @ApplicationContext private val context: Context,
) {
    private suspend fun withAccountSync(block: suspend () -> Unit) {
        AccountSyncGate.mutex.withLock {
            val cookie = context.dataStore.data.first()[InnerTubeCookieKey].orEmpty()
            if ("SAPISID" !in parseCookieString(cookie)) return@withLock
            block()
        }
    }

    /** Pull everything from the connected account into the one local profile. Never deletes local-only data. */
    suspend fun syncAll() {
        syncLikedSongs(); syncLibrarySongs(); syncLikedAlbums(); syncArtistsSubscriptions(); syncSavedPlaylists()
    }

    private suspend fun seen(key: Preferences.Key<String>): Set<String> =
        context.dataStore.data.first()[key].orEmpty().lines().filter(String::isNotBlank).toSet()

    private suspend fun remember(key: Preferences.Key<String>, ids: Collection<String>) {
        context.dataStore.edit { it[key] = ids.joinToString("\n") }
    }

    suspend fun syncLikedSongs() = withAccountSync {
        YouTube.playlist("LM").completed().onSuccess { page ->
            val songs = page.songs.reversed()

            val remoteIds = songs.map(SongItem::id).toSet()
            // Only undo likes that were removed on YouTube since the last sync; local-only likes stay.
            val removedRemotely = seen(RemoteSeenLikedSongsKey) - remoteIds
            database.likedSongsByNameAsc().first()
                .filter { it.id in removedRemotely }
                .forEach { database.update(it.song.localToggleLike()) }
            remember(RemoteSeenLikedSongsKey, remoteIds)

            songs.forEach { song ->
                val dbSong = database.song(song.id).firstOrNull()
                database.transaction {
                    when (dbSong) {
                        null -> insert(song.toMediaMetadata(), SongEntity::localToggleLike)
                        else -> if (!dbSong.song.liked) update(dbSong.song.localToggleLike())
                    }
                }
            }
        }
    }

    suspend fun syncLibrarySongs() = withAccountSync {
        YouTube.library("FEmusic_liked_videos").completedLibraryPage().onSuccess { page ->
            val songs = page.items.filterIsInstance<SongItem>().reversed()

            val remoteIds = songs.map(SongItem::id).toSet()
            val removedRemotely = seen(RemoteSeenLibrarySongsKey) - remoteIds
            database.songsByNameAsc().first()
                .filter { it.id in removedRemotely && it.song.inLibrary != null }
                .forEach { database.update(it.song.toggleLibrary()) }
            remember(RemoteSeenLibrarySongsKey, remoteIds)

            songs.forEach { song ->
                val dbSong = database.song(song.id).firstOrNull()
                database.transaction {
                    when (dbSong) {
                        null -> insert(song.toMediaMetadata(), SongEntity::toggleLibrary)
                        else -> if (dbSong.song.inLibrary == null) update(dbSong.song.toggleLibrary())
                    }
                }
            }
        }
    }

    suspend fun syncLikedAlbums() = withAccountSync {
        YouTube.library("FEmusic_liked_albums").completedLibraryPage().onSuccess { page ->
            val albums = page.items.filterIsInstance<AlbumItem>().reversed()

            val remoteIds = albums.map(AlbumItem::id).toSet()
            val removedRemotely = seen(RemoteSeenLikedAlbumsKey) - remoteIds
            database.albumsLikedByNameAsc().first()
                .filter { it.id in removedRemotely }
                .forEach { database.update(it.album.localToggleLike()) }
            remember(RemoteSeenLikedAlbumsKey, remoteIds)

            albums.forEach { album ->
                val dbAlbum = database.album(album.id).firstOrNull()
                YouTube.album(album.browseId).onSuccess { albumPage ->
                    when (dbAlbum) {
                        null -> {
                            database.insert(albumPage)
                            database.album(album.id).firstOrNull()?.let {
                                database.update(it.album.localToggleLike())
                            }
                        }
                        else -> if (dbAlbum.album.bookmarkedAt == null)
                            database.update(dbAlbum.album.localToggleLike())
                    }
                }
            }
        }
    }

    suspend fun syncArtistsSubscriptions() = withAccountSync {
        YouTube.library("FEmusic_library_corpus_artists").completedLibraryPage().onSuccess { page ->
            val artists = page.items.filterIsInstance<ArtistItem>()

            val remoteIds = artists.map(ArtistItem::id).toSet()
            val removedRemotely = seen(RemoteSeenArtistsKey) - remoteIds
            database.artistsBookmarkedByNameAsc().first()
                .filter { it.id in removedRemotely }
                .forEach { database.update(it.artist.localToggleLike()) }
            remember(RemoteSeenArtistsKey, remoteIds)

            artists.forEach { artist ->
                val dbArtist = database.artist(artist.id).firstOrNull()
                database.transaction {
                    when (dbArtist) {
                        null -> {
                            insert(
                                ArtistEntity(
                                    id = artist.id,
                                    name = artist.title,
                                    thumbnailUrl = artist.thumbnail,
                                    channelId = artist.channelId,
                                    bookmarkedAt = LocalDateTime.now()
                                )
                            )
                        }
                        else -> if (dbArtist.artist.bookmarkedAt == null)
                            update(dbArtist.artist.localToggleLike())
                    }
                }
            }
        }
    }

    suspend fun syncSavedPlaylists() = withAccountSync {
        YouTube.library("FEmusic_liked_playlists").completedLibraryPage()
            .onSuccess { page ->
                val playlistList = page.items.filterIsInstance<PlaylistItem>()
                    .filterNot { it.id == "LM" ||  it.id == "SE" }
                    .reversed()
                val dbPlaylists = database.playlistsByNameAsc().first()

                // Playlist ids come back without a "VL" prefix from the
                // library fetch, but some import paths store browseId WITH
                // that prefix — normalize both sides so a playlist created
                // either way still matches its existing DB entry instead of
                // silently creating a duplicate on every sync.
                fun normalizeBrowseId(id: String?) = id?.removePrefix("VL")

                val remoteIds = playlistList.map(PlaylistItem::id).toSet()
                val removedRemotely = seen(RemoteSeenPlaylistsKey) - remoteIds
                dbPlaylists.filter { normalizeBrowseId(it.playlist.browseId) in removedRemotely && it.playlist.bookmarkedAt != null }
                    .forEach { database.update(it.playlist.localToggleLike()) }
                remember(RemoteSeenPlaylistsKey, remoteIds)

                playlistList.onEach { playlist ->
                    var playlistEntity = dbPlaylists.find { playlist.id == normalizeBrowseId(it.playlist.browseId) }?.playlist
                    if (playlistEntity == null) {
                        playlistEntity = PlaylistEntity(
                            name = playlist.title,
                            browseId = playlist.id,
                            isEditable = playlist.isEditable,
                            bookmarkedAt = LocalDateTime.now(),
                            remoteSongCount = playlist.songCountText?.let {
                                Regex("""\d+""").find(it)?.value?.toIntOrNull()
                            }
                        )

                        database.insert(playlistEntity)
                    } else database.update(playlistEntity, playlist)

                    syncPlaylist(playlist.id, playlistEntity.id)
                }
            }
            .onFailure { exception ->
                reportException(exception)
            }
    }

    suspend fun syncPlaylist(browseId: String, playlistId: String) {
        val playlistPage = YouTube.playlist(browseId).completed().getOrNull() ?: return
        database.transaction {
            clearPlaylist(playlistId)
            playlistPage.songs
                .map(SongItem::toMediaMetadata)
                .onEach(::insert)
                .mapIndexed { position, song ->
                    PlaylistSongMap(
                        songId = song.id,
                        playlistId = playlistId,
                        position = position,
                        setVideoId = song.setVideoId
                    )
                }.forEach(::insert)
        }
    }
}
