package com.devxrohan.smusic.spotify

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SpotifyLibraryEntryPoint {
    fun bridge(): SpotifyLibraryBridge
    fun webSession(): SpotifyWebSession
    fun playlistEdits(): SpotifyPlaylistEdits
    fun likeDispatcher(): SpotifyLikeDispatcher
}

internal fun spotifyLibraryBridge(context: Context): SpotifyLibraryBridge =
    EntryPointAccessors.fromApplication(context.applicationContext, SpotifyLibraryEntryPoint::class.java).bridge()

internal fun spotifyServices(context: Context): SpotifyLibraryEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, SpotifyLibraryEntryPoint::class.java)
