package com.devxrohan.smusic.spotify

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.constants.SpotifyWriteBackKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Persists app edits; likes can drain immediately while playlist copies use explicit sync. */
@Singleton
class SpotifyOutboundSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val repository: SpotifyLibraryRepository,
    private val tracks: SpotifyOutboundTrackResolver,
    private val playlistEdits: SpotifyPlaylistEdits,
) {
    @Serializable
    data class PlaylistCopy(val remoteId: String, val fingerprint: String = "")
    data class Report(val unmatchedSongs: Int = 0)
    private val mutex = Mutex()

    private suspend fun allowed() = context.dataStore.data.first()[SpotifyWriteBackKey] == true && com.devxrohan.smusic.utils.AccountWriteConsent.isApproved(context, true)
    private suspend fun <T> remote(block: suspend () -> T): T = repository.accountRequest {
        if (!allowed()) throw CancellationException("Spotify uploads were switched off")
        block()
    }
    private inline fun <reified T> read(value: String?, fallback: T): T =
        if (value.isNullOrBlank()) fallback else Json.decodeFromString<T>(value)
    private suspend inline fun <reified T> save(key: Preferences.Key<String>, value: T) {
        context.dataStore.edit { it[key] = Json.encodeToString(value) }
    }

    suspend fun sync(): Report = mutex.withLock {
        if (!allowed()) return@withLock Report()
        ensureAccountIdentity()
        val missing = mutableSetOf<String>()
        uploadLikes(missing)
        uploadSavedItems()
        uploadPlaylists(missing)
        Report(missing.size)
    }

    private var validatedSession: String? = null
    private var lastLikeWriteAt = 0L

    private suspend fun ensureAccountIdentity() {
        val session = SpotifyCredentialStore.get(context).data.first()[SpotifySpDcKey].orEmpty()
        val binding = com.devxrohan.smusic.utils.AccountWriteConsent.fingerprint(true, session)
        if (validatedSession == binding) return
        // Consent is bound to this credential; a separate profile lookup must not gate heart delivery.
        val accountId = "session:$binding"
        context.dataStore.edit { prefs ->
            val old = prefs[SpotifyExportAccountKey].orEmpty()
            if (old.isNotBlank() && old != accountId) {
                listOf(SpotifyExportedPlaylistsKey, SpotifyExportedSavedKey, SpotifySentSavedKey, SpotifySentLikesKey).forEach(prefs::remove)
                prefs.remove(SpotifyOwnedLikesKey)
                prefs.remove(SpotifyLikedCatalogIdsKey)
                prefs.remove(SpotifyLikedCatalogCompleteAtKey)
                prefs.remove(SpotifyLikedCatalogFetchedAtKey)
            }
            prefs[SpotifyExportAccountKey] = accountId
        }
        validatedSession = binding
    }

    suspend fun pendingCount(): Int {
        val preferences = context.dataStore.data.first()
        val likes = read(preferences[SpotifyPendingLikesKey], emptyMap<String, Boolean>())
        val saved = read(preferences[SpotifySavedIntentKey], emptyMap<String, Boolean>())
        val sent = read(preferences[SpotifySentSavedKey], emptyMap<String, Boolean>())
        return likes.size + saved.count { (key, wanted) -> sent[key] != wanted } + playlistEdits.pendingCount()
    }

    suspend fun discardPending() {
        context.dataStore.edit { it.remove(SpotifyPendingLikesKey); it.remove(SpotifySavedIntentKey); it.remove(SpotifyPendingPlaylistAddsKey) }
    }

    suspend fun flushPendingChanges(): Report = mutex.withLock {
        if (context.dataStore.data.first()[SpotifyWriteBackKey] != true) { discardPending(); return@withLock Report() }
        if (pendingCount() == 0 || !allowed()) return@withLock Report()
        ensureAccountIdentity()
        val missing = mutableSetOf<String>()
        uploadLikes(missing)
        uploadSavedItems(onlyIntent = true)
        Report(missing.size + playlistEdits.flush())
    }

    private suspend fun uploadLikes(missing: MutableSet<String>) {
        val preferences = context.dataStore.data.first()
        val pending = read(preferences[SpotifyPendingLikesKey], emptyMap<String, Boolean>())
        if (pending.isEmpty()) return
        val sent = read(preferences[SpotifySentLikesKey], emptyMap<String, Boolean>())
        val catalog = preferences[SpotifyLikedCatalogIdsKey].orEmpty()
        val catalogFresh = System.currentTimeMillis() - (preferences[SpotifyLikedCatalogCompleteAtKey] ?: 0L) <= 3_600_000
        val positivesFresh = System.currentTimeMillis() - (preferences[SpotifyLikedCatalogFetchedAtKey] ?: 0L) <= 7 * 24 * 3_600_000L
        val owned = preferences[SpotifyOwnedLikesKey].orEmpty().toMutableSet()
        val imported = SpotifyLibraryBridge.decodeLinks(preferences[SpotifyImportedLinksKey])
        val cached = SpotifyLibraryBridge.decodeLinks(preferences[SpotifyOutboundMatchesKey])
        for ((id, liked) in pending) {
            if (!allowed()) throw CancellationException("Spotify uploads were switched off")
            val song = database.song(id).first()
            val spotifyId = if (song != null) tracks.resolve(song) else cached[id]
                ?: imported.entries.firstOrNull { it.key.startsWith("track:") && it.value == id }?.key?.removePrefix("track:")
            if (spotifyId == null) { missing += id; continue }
            val uri = "spotify:track:$spotifyId"
            val canRemove = uri in owned
            val action = SpotifyMirrorPolicy.action(liked, sent[id] == liked, if (positivesFresh && spotifyId in catalog) true else if (catalogFresh) false else null, canRemove)
            if (action != SpotifyMirrorPolicy.Action.ACKNOWLEDGE) {
                val wait = (1_500 - (System.currentTimeMillis() - lastLikeWriteAt)).coerceAtLeast(0)
                if (wait > 0) kotlinx.coroutines.delay(wait)
                remote { if (liked) Spotify.addToLibrary(listOf(uri)).getOrThrow() else Spotify.removeFromLibrary(listOf(uri)).getOrThrow() }
                lastLikeWriteAt = System.currentTimeMillis()
                if (action == SpotifyMirrorPolicy.Action.ADD_OWNED) owned += uri
                if (action == SpotifyMirrorPolicy.Action.REMOVE_OWNED) owned -= uri
            }
            context.dataStore.edit { prefs ->
                val latestSent = read(prefs[SpotifySentLikesKey], emptyMap<String, Boolean>())
                val latestPending = read(prefs[SpotifyPendingLikesKey], emptyMap<String, Boolean>())
                prefs[SpotifySentLikesKey] = Json.encodeToString(latestSent + (id to liked))
                prefs[SpotifyPendingLikesKey] = Json.encodeToString(SpotifyExportPolicy.acknowledge(latestPending, mapOf(id to liked)))
                prefs[SpotifyOwnedLikesKey] = owned.toSet()
                val links = SpotifyLibraryBridge.decodeLinks(prefs[SpotifyImportedLinksKey]).toMutableMap()
                if (liked) links["track:$spotifyId"] = id
                else if (canRemove) links.remove("track:$spotifyId")
                prefs[SpotifyImportedLinksKey] = Json.encodeToString(links)
            }
        }
    }

    private suspend fun uploadSavedItems(onlyIntent: Boolean = false) {
        val preferences = context.dataStore.data.first()
        val desired = mutableMapOf<String, Boolean>()
        if (!onlyIntent) {
            database.artistsBookmarkedByNameAsc().first().forEach { desired["artist:${it.id}"] = true }
            database.albumsLikedByNameAsc().first().forEach { desired["album:${it.id}"] = true }
        }
        desired.putAll(read(preferences[SpotifySavedIntentKey], emptyMap<String, Boolean>()))
        val sent = read(preferences[SpotifySentSavedKey], emptyMap<String, Boolean>()).toMutableMap()
        val matches = SpotifyLibraryBridge.decodeLinks(preferences[SpotifyExportedSavedKey]).toMutableMap()
        val imported = SpotifyLibraryBridge.decodeLinks(preferences[SpotifyImportedLinksKey])
        val exclusions = preferences[SpotifyExportExclusionsKey].orEmpty()
        desired.filter { (key, wanted) -> key !in exclusions && sent[key] != wanted }.forEach { (key, wanted) ->
            val kind = key.substringBefore(':'); val localId = key.substringAfter(':')
            val importedId = imported.entries.firstOrNull { it.key.startsWith("$kind:") && it.value == localId }?.key?.removePrefix("$kind:")
            val uri = matches[key] ?: importedId?.let { "spotify:$kind:$it" } ?: when (kind) {
                "artist" -> database.artist(localId).first()?.let { artist ->
                    remote { Spotify.search(artist.title, types = listOf("artist"), limit = 10).getOrThrow() }
                        .artists?.items?.firstOrNull { SpotifyLibraryBridge.sameName(it.name, artist.title) }?.let { "spotify:artist:${it.id}" }
                }
                "album" -> database.album(localId).first()?.let { album ->
                    remote { Spotify.search("${album.title} ${album.artists.firstOrNull()?.name.orEmpty()}", types = listOf("album"), limit = 10).getOrThrow() }
                        .albums?.items?.firstOrNull { match -> SpotifyLibraryBridge.sameName(match.name, album.title) &&
                            match.artists.any { sp -> album.artists.any { SpotifyLibraryBridge.sameName(it.name, sp.name) } } }?.let { "spotify:album:${it.id}" }
                }
                else -> null
            } ?: return@forEach
            remote { if (wanted) Spotify.addToLibrary(listOf(uri)).getOrThrow() else Spotify.removeFromLibrary(listOf(uri)).getOrThrow() }
            matches[key] = uri; sent[key] = wanted
            save(SpotifyExportedSavedKey, matches.toMap()); save(SpotifySentSavedKey, sent.toMap())
        }
    }

    private suspend fun uploadPlaylists(missing: MutableSet<String>) {
        val preferences = context.dataStore.data.first()
        val copies = read(preferences[SpotifyExportedPlaylistsKey], emptyMap<String, PlaylistCopy>()).toMutableMap()
        val local = database.playlistsByCreateDateAsc().first().filter { it.playlist.isEditable && !it.id.startsWith("SP:") }
        val localIds = local.map { it.id }.toSet()
        copies.filterKeys { it !in localIds }.toMap().forEach { (id, copy) ->
            remote { Spotify.removeFromLibrary(listOf("spotify:playlist:${copy.remoteId}")).getOrThrow() }
            copies.remove(id); save(SpotifyExportedPlaylistsKey, copies.toMap())
        }
        local.forEach { playlist ->
            val songs = database.playlistSongs(playlist.id).first()
            val matches = songs.mapNotNull { item ->
                tracks.resolve(item.song)?.let { item.song.id to it } ?: run { missing += item.song.id; null }
            }.toMap()
            val uris = SpotifyExportPolicy.playlistUris(songs.map { it.song.id }, matches) ?: return@forEach
            val fingerprint = fingerprint(playlist.title, uris)
            var copy = copies[playlist.id]
            if (copy?.fingerprint == fingerprint) return@forEach
            if (copy == null) {
                copy = PlaylistCopy(remote { Spotify.createPrivatePlaylist(playlist.title) })
                copies[playlist.id] = copy; save(SpotifyExportedPlaylistsKey, copies.toMap())
            }
            val readyCopy = requireNotNull(copy)
            remote { Spotify.updateOwnedPlaylist(readyCopy.remoteId, playlist.title, uris) }
            copies[playlist.id] = readyCopy.copy(fingerprint = fingerprint)
            save(SpotifyExportedPlaylistsKey, copies.toMap())
        }
    }

    companion object {
        internal fun fingerprint(name: String, uris: List<String>): String = MessageDigest.getInstance("SHA-256")
            .digest((name + "\u0000" + uris.joinToString("\u0000")).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
