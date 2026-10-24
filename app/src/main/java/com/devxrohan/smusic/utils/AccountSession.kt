package com.devxrohan.smusic.utils

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.devxrohan.smusic.constants.AccountChannelHandleKey
import com.devxrohan.smusic.constants.AccountEmailKey
import com.devxrohan.smusic.constants.AccountModeKey
import com.devxrohan.smusic.constants.AccountNameKey
import com.devxrohan.smusic.constants.AccountPhotoUrlKey
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.DataSyncIdKey
import com.devxrohan.smusic.constants.VisitorDataKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.Event
import com.devxrohan.smusic.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.time.LocalDateTime

/**
 * SMusic has ONE profile. A YouTube account is simply connected to it:
 *  - connecting merges the account's library/feed into the existing profile (nothing local is cleared)
 *  - signing out removes only the account session; every song, like, history entry, Pulse signal
 *    and playlist that is already on the device stays exactly where it was.
 */

/** Keys remembering which remote ids the last sync saw, so only things REMOVED on YouTube get removed locally. */
internal val RemoteSeenLikedSongsKey = stringPreferencesKey("yt_seen_liked_songs")
internal val RemoteSeenLibrarySongsKey = stringPreferencesKey("yt_seen_library_songs")
internal val RemoteSeenLikedAlbumsKey = stringPreferencesKey("yt_seen_liked_albums")
internal val RemoteSeenArtistsKey = stringPreferencesKey("yt_seen_artists")
internal val RemoteSeenPlaylistsKey = stringPreferencesKey("yt_seen_playlists")
private val RemoteSeenKeys = listOf(
    RemoteSeenLikedSongsKey, RemoteSeenLibrarySongsKey, RemoteSeenLikedAlbumsKey,
    RemoteSeenArtistsKey, RemoteSeenPlaylistsKey,
)

/**
 * Signs out of YouTube completely. Everything that belonged to the account session is cleared (cookie,
 * dataSyncId, account identity, the account-bound visitorData, WebView cookies and the in-memory client state),
 * so the next request is a normal anonymous guest request exactly like before the account was added.
 * The local library, history and Pulse data are untouched.
 */
suspend fun signOutYouTubeAccount(context: Context) {
    // Invalidate the in-memory preference cache BEFORE DataStore emits its update. Without this,
    // getSuspend(InnerTubeCookieKey) could return the old cached cookie, re-authenticate YouTube,
    // and keep showing the account feed until the process was restarted.
    invalidateDataStoreCache(
        InnerTubeCookieKey, VisitorDataKey, DataSyncIdKey,
        AccountNameKey, AccountEmailKey, AccountChannelHandleKey, AccountPhotoUrlKey, AccountModeKey
    )
    RemoteSeenKeys.forEach { invalidateDataStoreCache(it) }
    YouTube.cookie = null
    YouTube.dataSyncId = null
    YouTube.visitorData = null

    // Persist the session removal; the in-memory session is already anonymous above.
    context.dataStore.edit { prefs ->
        prefs.remove(InnerTubeCookieKey)
        // The visitorData saved at login is tied to the account session. Dropping it makes App.kt create a
        // fresh anonymous one, so guest requests start clean instead of reusing the account's identity.
        prefs.remove(VisitorDataKey)
        prefs.remove(DataSyncIdKey)
        prefs.remove(AccountNameKey)
        prefs.remove(AccountEmailKey)
        prefs.remove(AccountChannelHandleKey)
        prefs.remove(AccountPhotoUrlKey)
        prefs.remove(AccountModeKey)
        RemoteSeenKeys.forEach { prefs.remove(it) }
    }
    invalidateDataStoreCache(
        InnerTubeCookieKey, VisitorDataKey, DataSyncIdKey,
        AccountNameKey, AccountEmailKey, AccountChannelHandleKey, AccountPhotoUrlKey, AccountModeKey
    )
    RemoteSeenKeys.forEach { invalidateDataStoreCache(it) }
    YouTube.cookie = null
    YouTube.dataSyncId = null
    // Do not reuse the account's visitorData in memory; a new anonymous one is fetched by the App.kt observer.
    YouTube.visitorData = null
    // The login WebView used passive Google sign-in; await cookie removal so an immediate
    // return to Login cannot silently reconnect the previous Google account.
    runCatching {
        suspendCancellableCoroutine<Unit> { continuation ->
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
}

// ---- One-time migration from the old Guest/YouTube split -------------------------------------
private val GuestSnapshotReadyKey = booleanPreferencesKey("guest_snapshot_ready")
private val GuestLikedSongIdsKey = stringPreferencesKey("guest_liked_song_ids")
private val GuestSubscribedArtistIdsKey = stringPreferencesKey("guest_subscribed_artist_ids")
private val GuestLikedAlbumIdsKey = stringPreferencesKey("guest_liked_album_ids")
private val GuestBookmarkedPlaylistIdsKey = stringPreferencesKey("guest_bookmarked_playlist_ids")
private const val GUEST_HISTORY_SNAPSHOT = "guest_history_snapshot.tsv"

/**
 * Older builds parked Guest likes/history in a snapshot and wiped them while an account was active.
 * Merge that snapshot back (additively) so those users get everything back in the unified profile.
 */
suspend fun migrateLegacyGuestSnapshot(context: Context, database: MusicDatabase) {
    AccountSyncGate.mutex.withLock {
        val store = context.dataStore
        val prefs = store.data.first()
        val snapshotFile = File(context.filesDir, GUEST_HISTORY_SNAPSHOT)
        if (prefs[GuestSnapshotReadyKey] == true) {
            fun ids(key: androidx.datastore.preferences.core.Preferences.Key<String>) =
                prefs[key].orEmpty().lines().filter(String::isNotBlank).toSet()
            val songIds = ids(GuestLikedSongIdsKey)
            val artistIds = ids(GuestSubscribedArtistIdsKey)
            val albumIds = ids(GuestLikedAlbumIdsKey)
            val playlistIds = ids(GuestBookmarkedPlaylistIdsKey)
            withContext(Dispatchers.IO) {
                val now = LocalDateTime.now()
                database.allSongs().first().filter { it.id in songIds && !it.song.liked }.forEach {
                    database.update(it.song.copy(liked = true, likedDate = it.song.likedDate ?: now))
                }
                database.artistsByNameAsc().first().filter { it.id in artistIds && it.artist.bookmarkedAt == null }.forEach {
                    database.update(it.artist.copy(bookmarkedAt = now))
                }
                database.albumsByNameAsc().first().filter { it.id in albumIds && it.album.bookmarkedAt == null }.forEach {
                    database.update(it.album.copy(bookmarkedAt = now, likedDate = it.album.likedDate ?: now))
                }
                database.playlistsByCreateDateAsc().first().filter { it.id in playlistIds && it.playlist.bookmarkedAt == null }.forEach {
                    database.update(it.playlist.copy(bookmarkedAt = now))
                }
                if (snapshotFile.exists()) {
                    val available = database.allSongs().first().map { it.id }.toSet()
                    snapshotFile.readLines().filter(String::isNotBlank).forEach { line ->
                        val f = line.split('\t')
                        if (f.size == 3 && f[0] in available) runCatching {
                            database.insert(Event(songId = f[0], timestamp = LocalDateTime.parse(f[1]), playTime = f[2].toLong()))
                        }
                    }
                }
            }
            snapshotFile.delete()
            store.edit {
                it.remove(GuestSnapshotReadyKey); it.remove(GuestLikedSongIdsKey); it.remove(GuestSubscribedArtistIdsKey)
                it.remove(GuestLikedAlbumIdsKey); it.remove(GuestBookmarkedPlaylistIdsKey)
            }
        }
        if (prefs[AccountModeKey] != null) store.edit { it.remove(AccountModeKey) }
    }
}
