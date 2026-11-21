/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
// Adapted for SMusic on 2026-11-21; preserves the upstream account protocol.

package com.devxrohan.smusic.spotify

import com.devxrohan.smusic.utils.dataStore
import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.devxrohan.smusic.R
import com.devxrohan.smusic.spotify.SpotifyAccessTokenExpiresAtKey
import com.devxrohan.smusic.spotify.SpotifyAccessTokenKey
import com.devxrohan.smusic.spotify.SpotifyAccountAvatarUrlKey
import com.devxrohan.smusic.spotify.SpotifyAccountNameKey
import com.devxrohan.smusic.spotify.SpotifyLibraryPlaylistsCacheKey
import com.devxrohan.smusic.spotify.SpotifySpDcKey
import com.devxrohan.smusic.spotify.SpotifySpKeyKey
import com.devxrohan.smusic.spotify.models.SpotifyPlaylist
import com.devxrohan.smusic.spotify.models.SpotifyPlaylistTracksRef
import com.devxrohan.smusic.spotify.models.SpotifyTrack
import com.devxrohan.smusic.spotify.clearSpotifyWebSession as clearWebAuthSession

import com.devxrohan.smusic.spotify.spotifyReportException as reportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyLibraryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val credentialStore = SpotifyCredentialStore.get(context)

        private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
        val playlists: StateFlow<List<SpotifyPlaylist>> = _playlists.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val tokenRefreshMutex = Mutex()

        suspend fun restoreCachedPlaylists() {
            withContext(Dispatchers.IO) {
                if (_playlists.value.isNotEmpty()) return@withContext
                val cached =
                    credentialStore.data
                        .first()[SpotifyLibraryPlaylistsCacheKey]
                        .orEmpty()
                if (cached.isBlank()) return@withContext
                runCatching {
                    spotifyCacheJson.decodeFromString(
                        ListSerializer(SpotifyPlaylist.serializer()),
                        cached,
                    )
                }.onSuccess { playlists ->
                    _playlists.value = playlists
                }.onFailure { error ->
                    reportException(error)
                    credentialStore.edit { prefs ->
                        prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    }
                }
            }
        }

        suspend fun restoreSession(): SpotifyAccountSession =
            withContext(Dispatchers.IO) {
                val prefs = credentialStore.data.first()
                val token = prefs[SpotifyAccessTokenKey].orEmpty()
                val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                val accountName = prefs[SpotifyAccountNameKey].orEmpty()
                val avatarUrl = prefs[SpotifyAccountAvatarUrlKey]

                if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                    Spotify.accessToken = token
                    return@withContext SpotifyAccountSession(
                        isAuthenticated = true,
                        accountName = accountName,
                        accountAvatarUrl = avatarUrl,
                    )
                }

                val spDc = prefs[SpotifySpDcKey].orEmpty()
                if (spDc.isBlank()) return@withContext SpotifyAccountSession()

                refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty())
                    .fold(
                        onSuccess = {
                            val refreshed = credentialStore.data.first()
                            SpotifyAccountSession(
                                isAuthenticated = true,
                                accountName = refreshed[SpotifyAccountNameKey].orEmpty(),
                                accountAvatarUrl = refreshed[SpotifyAccountAvatarUrlKey],
                            )
                        },
                        onFailure = {
                            if (it is CancellationException) throw it
                            reportException(it)
                            SpotifyAccountSession()
                        },
                    )
            }

        suspend fun connectWithCookies(
            spDc: String,
            spKey: String,
        ): SpotifyAccountSession =
            withContext(Dispatchers.IO) {
                var credentialsChanged = false
                credentialStore.edit { prefs ->
                    credentialsChanged =
                        prefs[SpotifySpDcKey] != spDc || prefs[SpotifySpKeyKey].orEmpty() != spKey
                    prefs[SpotifySpDcKey] = spDc
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                    if (spKey.isNotBlank()) {
                        prefs[SpotifySpKeyKey] = spKey
                    } else {
                        prefs.remove(SpotifySpKeyKey)
                    }
                    if (credentialsChanged) {
                        prefs.remove(SpotifyAccessTokenKey)
                        prefs.remove(SpotifyAccessTokenExpiresAtKey)
                    }
                }
                if (credentialsChanged) {
                    Spotify.accessToken = null
                    context.dataStore.edit { preferences ->
                        preferences[com.devxrohan.smusic.constants.SpotifyWriteBackKey] = false
                        listOf(SpotifyPendingLikesKey, SpotifyPendingPlaylistAddsKey, SpotifySavedIntentKey, SpotifyExportAccountKey,
                            SpotifySentLikesKey, SpotifySentSavedKey, SpotifyExportedSavedKey,
                            SpotifyExportedPlaylistsKey).forEach(preferences::remove)
                        preferences.remove(SpotifyOwnedLikesKey)
                        preferences.remove(SpotifyLikedCatalogIdsKey)
                        preferences.remove(SpotifyLikedCatalogCompleteAtKey)
                        preferences.remove(SpotifyLikedCatalogFetchedAtKey)
                    }
                }
                _playlists.value = emptyList()
                _errorMessage.value = null
                refreshAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
                val prefs = credentialStore.data.first()
                SpotifyAccountSession(
                    isAuthenticated = true,
                    accountName = prefs[SpotifyAccountNameKey].orEmpty(),
                    accountAvatarUrl = prefs[SpotifyAccountAvatarUrlKey],
                )
            }

        suspend fun logout() {
            withContext(Dispatchers.IO) {
                context.dataStore.edit { it[com.devxrohan.smusic.constants.SpotifyWriteBackKey] = false }
                credentialStore.edit { prefs ->
                    prefs.remove(SpotifySpDcKey)
                    prefs.remove(SpotifySpKeyKey)
                    prefs.remove(SpotifyAccessTokenKey)
                    prefs.remove(SpotifyAccessTokenExpiresAtKey)
                    prefs.remove(SpotifyAccountNameKey)
                    prefs.remove(SpotifyAccountAvatarUrlKey)
                    prefs.remove(SpotifyLibraryPlaylistsCacheKey)
                }
                _playlists.value = emptyList()
                _errorMessage.value = null
                Spotify.accessToken = null
                runCatching { clearWebAuthSession(context) }
                    .onFailure(::reportException)
            }
        }

        suspend fun refreshPlaylists(): List<SpotifyPlaylist> =
            withContext(Dispatchers.IO) {
                _isRefreshing.value = true
                _errorMessage.value = null
                try {
                    ensureAuthenticated()
                    refreshProfile()
                    val blocked = context.dataStore.data.first()[SpotifyBlockedItemsKey].orEmpty()
                    val loaded = fetchAllPlaylists().filterNot { "playlist:${it.id}" in blocked }
                    _playlists.value = loaded
                    credentialStore.edit { prefs ->
                        prefs[SpotifyLibraryPlaylistsCacheKey] =
                            spotifyCacheJson.encodeToString(
                                ListSerializer(SpotifyPlaylist.serializer()),
                                loaded,
                            )
                    }
                    loaded
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reportException(error)
                    _errorMessage.value = spotifyErrorMessage(error)
                    _playlists.value
                } finally {
                    _isRefreshing.value = false
                }
            }

        suspend fun playlist(playlistId: String): SpotifyPlaylist =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                spotifyCallWithTokenRetry {
                    Spotify.playlist(playlistId).getOrThrow()
                }
            }

        suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> =
            withContext(Dispatchers.IO) {
                ensureAuthenticated()
                val tracks = ArrayList<SpotifyTrack>()
                var offset = 0
                val limit = 50

                while (true) {
                    val page =
                        spotifyCallWithTokenRetry {
                            Spotify
                                .playlistTracks(
                                    playlistId = playlistId,
                                    limit = limit,
                                    offset = offset,
                                ).getOrThrow()
                        }
                    if (page.items.isEmpty()) break
                    val pageTracks = page.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
                    tracks += pageTracks
                    offset += page.items.size
                    if (offset >= page.total || page.items.size < limit) break
                }

                tracks
            }

        data class LikedSnapshot(val tracks: List<SpotifyTrack>, val complete: Boolean)
        suspend fun likedSongs(): List<SpotifyTrack> = likedSnapshot().tracks

        internal suspend fun likedSnapshot(): LikedSnapshot = withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val tracks = ArrayList<SpotifyTrack>()
            var offset = 0
            var total = 0
            var totalKnown = true
            var allParsed = true
            while (true) {
                val page = spotifyCallWithTokenRetry { Spotify.likedSongs(limit = 50, offset = offset).getOrThrow() }
                total = maxOf(total, page.total)
                totalKnown = totalKnown && page.totalKnown
                allParsed = allParsed && page.items.size == page.rawCount
                tracks += page.items.map { it.track }
                if (page.rawCount == 0) break
                offset += page.rawCount
                if ((page.totalKnown && offset >= page.total) || (!page.totalKnown && page.rawCount < 50)) break
            }
            val complete = SpotifyMirrorPolicy.completeListing(totalKnown, allParsed, tracks.map { it.id }.toSet(), total)
            LikedSnapshot(tracks.filterNot(SpotifyTrack::isLocal).distinctBy { it.id }, complete)
        }

        internal suspend fun blockedItems(): Set<String> = context.dataStore.data.first()[SpotifyBlockedItemsKey].orEmpty()

        suspend fun removePlaylist(id: String, blockFutureSync: Boolean) = withContext(Dispatchers.IO) {
            if (blockFutureSync) context.dataStore.edit { prefs ->
                prefs[SpotifyBlockedItemsKey] = prefs[SpotifyBlockedItemsKey].orEmpty() + "playlist:$id"
            }
            _playlists.value = _playlists.value.filterNot { it.id == id }
            credentialStore.edit { prefs -> prefs[SpotifyLibraryPlaylistsCacheKey] = spotifyCacheJson.encodeToString(ListSerializer(SpotifyPlaylist.serializer()), _playlists.value) }
        }

        suspend fun savedArtists(): List<com.devxrohan.smusic.spotify.models.SpotifyArtist> = withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val result = mutableListOf<com.devxrohan.smusic.spotify.models.SpotifyArtist>()
            var offset = 0
            do {
                val page = spotifyCallWithTokenRetry { Spotify.myArtists(offset = offset).getOrThrow() }
                result += page.items; offset += page.limit.coerceAtLeast(1)
            } while (offset < page.total)
            result.distinctBy { it.id }
        }

        suspend fun savedAlbums(): List<com.devxrohan.smusic.spotify.models.SpotifyAlbum> = withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val result = mutableListOf<com.devxrohan.smusic.spotify.models.SpotifyAlbum>()
            var offset = 0
            do {
                val page = spotifyCallWithTokenRetry { Spotify.myAlbums(offset = offset).getOrThrow() }
                result += page.items; offset += page.limit.coerceAtLeast(1)
            } while (offset < page.total)
            result.distinctBy { it.id }
        }

        internal suspend fun webClientId(): String {
            var preferences = credentialStore.data.first()
            if (preferences[SpotifyClientIdKey].isNullOrBlank()) {
                refreshAccessToken(preferences[SpotifySpDcKey].orEmpty(), preferences[SpotifySpKeyKey].orEmpty(),
                    rejectedAccessToken = preferences[SpotifyAccessTokenKey]).getOrThrow()
                preferences = credentialStore.data.first()
            }
            return preferences[SpotifyClientIdKey]?.takeIf(String::isNotBlank) ?: error("Spotify did not return its web client ID")
        }

        internal suspend fun <T> accountRequest(block: suspend () -> T): T {
            ensureAuthenticated()
            return spotifyCallWithTokenRetry(block)
        }

        private suspend fun ensureAuthenticated() {
            val prefs = credentialStore.data.first()
            val token = prefs[SpotifyAccessTokenKey].orEmpty()
            val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                Spotify.accessToken = token
                return
            }

            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) {
                throw IllegalStateException(context.getString(R.string.spotify_not_connected))
            }
            refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
        }

        private suspend fun refreshAccessToken(
            spDc: String,
            spKey: String,
            rejectedAccessToken: String? = null,
        ): Result<Unit> =
            try {
                tokenRefreshMutex.withLock {
                    val prefs = credentialStore.data.first()
                    val storedAccessToken = prefs[SpotifyAccessTokenKey].orEmpty()
                    val storedExpiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
                    val credentialsMatch =
                        prefs[SpotifySpDcKey].orEmpty() == spDc &&
                            prefs[SpotifySpKeyKey].orEmpty() == spKey
                    val canReuseStoredToken =
                        credentialsMatch &&
                            storedAccessToken.isNotBlank() &&
                            storedExpiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS &&
                            (rejectedAccessToken == null || storedAccessToken != rejectedAccessToken)

                    if (canReuseStoredToken) {
                        Spotify.accessToken = storedAccessToken
                        return@withLock Result.success(Unit)
                    }

                    val token = SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
                    Spotify.accessToken = token.accessToken
                    credentialStore.edit { prefs ->
                        prefs[SpotifyAccessTokenKey] = token.accessToken
                        prefs[SpotifyClientIdKey] = token.clientId
                        prefs[SpotifyAccessTokenExpiresAtKey] = token.accessTokenExpirationTimestampMs
                    }
                    refreshProfile()
                    Result.success(Unit)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }

        private suspend fun refreshProfile() {
            Spotify
                .me()
                .onSuccess { user ->
                    credentialStore.edit { prefs ->
                        prefs[SpotifyAccountNameKey] = user.displayName.orEmpty()
                        user.images
                            .firstOrNull()
                            ?.url
                            ?.let { prefs[SpotifyAccountAvatarUrlKey] = it }
                            ?: prefs.remove(SpotifyAccountAvatarUrlKey)
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }

        private suspend fun fetchAllPlaylists(): List<SpotifyPlaylist> {
            val playlists = ArrayList<SpotifyPlaylist>()
            var offset = 0
            val limit = 50

            while (true) {
                val page =
                    spotifyCallWithTokenRetry {
                        Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
                    }
                playlists += page.items
                offset += limit
                if (offset >= page.total || page.items.isEmpty()) break
            }

            return playlists
        }

        private suspend fun <T> spotifyCallWithTokenRetry(block: suspend () -> T): T =
            Spotify.accessToken.let { rejectedAccessToken ->
                runCatching { block() }
                    .getOrElse { error ->
                        if ((error as? Spotify.SpotifyException)?.statusCode != 401) throw error
                        val prefs = credentialStore.data.first()
                        val spDc = prefs[SpotifySpDcKey].orEmpty()
                        if (spDc.isBlank()) throw error
                        refreshAccessToken(
                            spDc = spDc,
                            spKey = prefs[SpotifySpKeyKey].orEmpty(),
                            rejectedAccessToken = rejectedAccessToken,
                        ).getOrThrow()
                        block()
                    }
            }

        companion object {
            private const val TOKEN_EXPIRY_GRACE_MS = 60_000L
            private val spotifyCacheJson =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
        }
    }

data class SpotifyAccountSession(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
)
