package com.devxrohan.smusic.spotify

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.constants.SpotifyWriteBackKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.utils.AccountWriteConsent
import com.devxrohan.smusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Explicit app additions target the original Spotify playlist, never a replacement copy. */
@Singleton
class SpotifyPlaylistEdits @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val repository: SpotifyLibraryRepository,
    private val resolver: SpotifyOutboundTrackResolver,
) {
    @Serializable
    internal data class Addition(val id: String, val playlistId: String, val songIds: List<String>,
        val trackIds: List<String> = emptyList(), val beforeCount: Int? = null, val localMapIds: List<Int> = emptyList())

    private suspend fun allowed() = context.dataStore.data.first()[SpotifyWriteBackKey] == true && AccountWriteConsent.isApproved(context, true)
    private fun decode(value: String?) = if (value.isNullOrBlank()) emptyList() else Json.decodeFromString<List<Addition>>(value)
    suspend fun pendingCount(): Int = decode(context.dataStore.data.first()[SpotifyPendingPlaylistAddsKey]).size

    suspend fun enqueue(localId: String, songIds: List<String>) {
        if (!localId.startsWith("SP:") || songIds.isEmpty() || !allowed()) return
        val localRows = database.playlistSongs(localId).first().takeLast(songIds.size)
        val mapIds = if (localRows.map { it.song.id } == songIds) localRows.map { it.map.id } else emptyList()
        val additions = songIds.chunked(100).mapIndexed { index, ids ->
            Addition(UUID.randomUUID().toString(), localId.removePrefix("SP:"), ids, localMapIds = mapIds.drop(index * 100).take(ids.size))
        }
        context.dataStore.edit { it[SpotifyPendingPlaylistAddsKey] = Json.encodeToString(decode(it[SpotifyPendingPlaylistAddsKey]) + additions) }
        spotifyServices(context).likeDispatcher().requestDelivery()
    }

    suspend fun flush(): Int {
        var unmatched = 0
        for (initial in decode(context.dataStore.data.first()[SpotifyPendingPlaylistAddsKey])) {
            if (!allowed()) throw CancellationException("Spotify uploads were switched off")
            if (database.playlist("SP:${initial.playlistId}").first() == null) {
                discard(initial.id)
                continue
            }
            var addition = initial
            if (addition.trackIds.isEmpty()) {
                val matches = addition.songIds.map { id -> database.song(id).first()?.let { resolver.resolve(it) } }
                if (matches.any { it == null }) { unmatched += matches.count { it == null }; continue }
                addition = addition.copy(trackIds = matches.filterNotNull())
            }
            suspend fun <T> remote(block: suspend () -> T): T = repository.accountRequest {
                if (!allowed()) throw CancellationException("Spotify uploads were switched off")
                block()
            }
            if (addition.beforeCount == null) {
                val count = remote { Spotify.playlist(addition.playlistId).getOrThrow().tracks?.total }
                    ?: error("Spotify playlist size is unavailable")
                addition = addition.copy(beforeCount = count)
                replace(addition)
            } else {
                // A timeout/process death may have occurred after Spotify accepted the append.
                val tail = remote { Spotify.playlistTracks(addition.playlistId, limit = 100, offset = addition.beforeCount!!).getOrThrow() }
                val ids = tail.items.map { it.track?.id.orEmpty() }
                if (alreadyApplied(ids, addition.trackIds)) { acknowledge(addition); continue }
                check(tail.total == addition.beforeCount && ids.isEmpty()) { "Spotify playlist changed while an addition was pending" }
            }
            remote { Spotify.addTracksToPlaylist(addition.playlistId, addition.trackIds.map { "spotify:track:$it" }).getOrThrow() }
            acknowledge(addition)
        }
        return unmatched
    }

    private suspend fun replace(addition: Addition) { context.dataStore.edit { prefs ->
        prefs[SpotifyPendingPlaylistAddsKey] = Json.encodeToString(decode(prefs[SpotifyPendingPlaylistAddsKey]).map { if (it.id == addition.id) addition else it })
    } }
    private suspend fun acknowledge(addition: Addition) {
        val maps = database.playlistSongs("SP:${addition.playlistId}").first().associateBy { it.map.id }
        addition.localMapIds.forEachIndexed { index, id ->
            val row = maps[id] ?: return@forEachIndexed
            val trackId = addition.trackIds.getOrNull(index) ?: return@forEachIndexed
            val position = requireNotNull(addition.beforeCount) + index
            val key = "spotify:$trackId:$position"
            database.transaction {
                update(row.map.copy(setVideoId = key, position = position))
                maps.values.filter { it.map.id != id && it.map.setVideoId == key }.forEach { delete(it.map) }
            }
        }
        discard(addition.id)
    }
    private suspend fun discard(id: String) { context.dataStore.edit { prefs ->
        prefs[SpotifyPendingPlaylistAddsKey] = Json.encodeToString(decode(prefs[SpotifyPendingPlaylistAddsKey]).filterNot { it.id == id })
    } }
    companion object {
        internal fun alreadyApplied(tail: List<String>, expected: List<String>) = expected.isNotEmpty() && tail.take(expected.size) == expected
    }
}
