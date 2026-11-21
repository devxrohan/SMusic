package com.devxrohan.smusic.spotify

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.devxrohan.smusic.utils.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import java.io.File

/** The upstream repository keeps its DataStore contract; credentials stay encrypted outside backups. */
internal object SpotifyCredentialStore {
    private val sensitive = setOf(SpotifySpDcKey.name, SpotifySpKeyKey.name, SpotifyAccessTokenKey.name)
    @Volatile private var instance: DataStore<Preferences>? = null
    fun get(context: Context): DataStore<Preferences> = instance ?: synchronized(this) {
        instance ?: run {
            val disk = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { File(context.noBackupFilesDir, "spotify_session.preferences_pb") })
            object : DataStore<Preferences> {
                override val data = disk.data.map(::decode)
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    val result = disk.updateData { previous ->
                        val updated = transform(decode(previous)).toMutablePreferences()
                        updated.asMap().forEach { (key, value) ->
                            if (key.name in sensitive && value is String && value.isNotEmpty()) {
                                val encrypted = CryptoManager.encrypt(value)
                                check(encrypted.startsWith(CryptoManager.ENC_PREFIX)) { "Could not securely store Spotify credentials" }
                                updated[androidx.datastore.preferences.core.stringPreferencesKey(key.name)] = encrypted
                            }
                        }
                        updated.toPreferences()
                    }
                    return decode(result)
                }
            }.also { instance = it }
        }
    }
    private fun decode(preferences: Preferences): Preferences = preferences.toMutablePreferences().apply {
        preferences.asMap().forEach { (key, value) ->
            if (key.name in sensitive && value is String) {
                val plain = if (value.startsWith(CryptoManager.ENC_PREFIX)) CryptoManager.decrypt(value) else ""
                this[androidx.datastore.preferences.core.stringPreferencesKey(key.name)] = plain.takeUnless { it.startsWith(CryptoManager.ENC_PREFIX) }.orEmpty()
            }
        }
    }.toPreferences()
}

internal fun spotifyReportException(error: Throwable) {
    timber.log.Timber.w("Spotify request failed (%s)", error.javaClass.simpleName)
}

internal fun spotifyErrorMessage(error: Throwable): String = when (error) {
    is kotlinx.serialization.SerializationException -> "Spotify returned an unexpected response. Please try again."
    is java.io.IOException -> "Could not reach Spotify. Check your connection and try again."
    else -> error.message ?: "Spotify could not complete this request."
}

internal fun clearSpotifyWebSession(context: Context) {
    val manager = android.webkit.CookieManager.getInstance()
    listOf("https://spotify.com", "https://open.spotify.com", "https://accounts.spotify.com").forEach { url ->
        manager.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter(String::isNotBlank).forEach { name ->
            manager.setCookie(url, "$name=; Max-Age=0; Path=/; Secure")
            manager.setCookie(url, "$name=; Max-Age=0; Domain=.spotify.com; Path=/; Secure")
        }
    }
    manager.flush()
}
