package com.devxrohan.smusic.utils

import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.pages.AlbumPage
import com.devxrohan.smusic.innertube.pages.ArtistPage

/** Artist/album metadata is reused across app screens, scoped to the current account and locale. */
internal object MusicPageRequests {
    private data class Key(val id: String, val cookie: String?, val locale: String, val authenticated: Boolean)
    private val artists = RequestCoalescer<Key, ArtistPage>()
    private val albums = RequestCoalescer<Key, AlbumPage>()
    private fun key(id: String) = Key(id, YouTube.cookie, YouTube.locale.toString(), YouTube.useLoginForBrowse)
    suspend fun artist(id: String, force: Boolean = false): Result<ArtistPage> =
        artists.load(key(id), force) { YouTube.artist(id) }
    suspend fun album(id: String, force: Boolean = false): Result<AlbumPage> =
        albums.load(key(id), force) { YouTube.album(id) }
}
