package com.devxrohan.smusic.utils

import android.content.Context
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.spotify.SpotifyCredentialStore
import com.devxrohan.smusic.spotify.SpotifySpDcKey
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest

/** Account-write approval belongs to this installation and is never restored from a backup. */
internal object AccountWriteConsent {
    private fun file(context: Context, spotify: Boolean) = File(context.noBackupFilesDir, if (spotify) "spotify-write-consent" else "youtube-write-consent")
    private suspend fun credential(context: Context, spotify: Boolean): String = if (spotify)
        SpotifyCredentialStore.get(context).data.first()[SpotifySpDcKey].orEmpty()
        else parseCookieString(context.dataStore.data.first()[InnerTubeCookieKey].orEmpty())["SAPISID"].orEmpty()

    suspend fun approve(context: Context, spotify: Boolean) {
        val token = credential(context, spotify)
        check(token.isNotBlank()) { "Reconnect the account before enabling account writes" }
        file(context, spotify).writeText(fingerprint(spotify, token))
    }
    fun revoke(context: Context, spotify: Boolean) { file(context, spotify).delete() }
    suspend fun isApproved(context: Context, spotify: Boolean): Boolean = runCatching {
        val token = credential(context, spotify)
        token.isNotBlank() && file(context, spotify).readText() == fingerprint(spotify, token)
    }.getOrDefault(false)

    internal fun fingerprint(spotify: Boolean, credential: String): String = MessageDigest.getInstance("SHA-256")
        .digest((if (spotify) "spotify:$credential" else "youtube:$credential").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
