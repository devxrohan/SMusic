package com.devxrohan.smusic.spotify

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Web-player client-token flow adapted from the supplied Stash 0.9.125 source (GPL-3.0). */
@Singleton
class SpotifyWebSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SpotifyLibraryRepository,
) {
    data class Headers(val token: String, val version: String)
    private val mutex = Mutex()
    private var cached: Headers? = null
    private var expiresAt = 0L
    private var shell: String? = null
    private var version: String? = null
    private var deviceId = UUID.randomUUID().toString()
    private var lastHashFetch = 0L
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun headers(): Headers = withContext(Dispatchers.IO) {
        mutex.withLock {
            cached?.takeIf { System.currentTimeMillis() < expiresAt }?.let { return@withLock it }
            val clientId = repository.webClientId()
            val html = shell ?: runCatching { read("https://open.spotify.com") }.getOrDefault("").also { shell = it }
            val clientVersion = version ?: extractVersion(html).orEmpty().ifBlank { "1.2.87.311.g2db0c2c4" }.also { version = it }
            val body = buildJsonObject { putJsonObject("client_data") {
                put("client_version", clientVersion); put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown"); put("device_model", "unknown"); put("os", "windows")
                    put("os_version", "NT 10.0"); put("device_id", deviceId); put("device_type", "computer")
                }
            } }
            // Use the platform TLS transport for web-player client token requests.
            val response = read("https://clienttoken.spotify.com/v1/clienttoken", body.toString())
            val root = json.parseToJsonElement(response).jsonObject
            val granted = root["granted_token"]?.jsonObject ?: error("Spotify did not grant a client token")
            val token = granted["token"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank) ?: error("Spotify client token is empty")
            expiresAt = System.currentTimeMillis() + ((granted["expires_after_seconds"]?.jsonPrimitive?.longOrNull ?: 3600L).coerceIn(60, 86400) - 30) * 1000
            Headers(token, clientVersion).also { cached = it }
        }
    }

    suspend fun invalidate() = mutex.withLock {
        cached = null
        expiresAt = 0L
    }

    suspend fun refreshMutationHashes(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (System.currentTimeMillis() - lastHashFetch < 600_000) return@withLock false
            lastHashFetch = System.currentTimeMillis()
            val html = read("https://open.spotify.com").also { shell = it }
            val bundle = Regex("src=\"(https://[^\"]*/web-player/web-player\\.[a-f0-9]+\\.js)\"").find(html)?.groupValues?.get(1) ?: return@withLock false
            val uri = URI(bundle)
            if (uri.scheme != "https" || !(uri.host == "scdn.co" || uri.host.endsWith(".scdn.co") || uri.host == "spotifycdn.com" || uri.host.endsWith(".spotifycdn.com"))) return@withLock false
            val hashes = extractHashes(read(bundle))
            var changed = false
            for (operation in listOf("addToLibrary", "removeFromLibrary", "searchDesktop", "addToPlaylist")) {
                val hash = hashes[operation] ?: continue
                if (hash != SpotifyHashProvider.getHash(operation)) {
                    SpotifyHashProvider.updateHashes(mapOf(operation to SpotifyHashProvider.RemoteHashEntry(hash = hash)), SpotifyHashProvider.HashSource.REMOTE)
                    changed = true
                }
            }
            changed
        }
    }

    private fun read(url: String, body: String? = null): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw Spotify.SpotifyException(status, "Spotify client session failed (HTTP $status)")
            connection.getHeaderFields().values.flatten().firstOrNull { it.startsWith("sp_t=") }?.substringAfter("sp_t=")?.substringBefore(';')?.let { deviceId = it }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    companion object {
        internal const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"
        internal fun extractVersion(html: String): String? {
            val pattern = Regex("\"clientVersion\"\\s*:\\s*\"([^\"]+)\"")
            pattern.find(html)?.let { return it.groupValues[1] }
            val encoded = Regex("<script id=\"appServerConfig\"[^>]*>([A-Za-z0-9+/=\\s]+)</script>").find(html)?.groupValues?.get(1) ?: return null
            val decoded = runCatching { String(Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull() ?: return null
            return pattern.find(decoded)?.groupValues?.get(1)
        }
        internal fun extractHashes(bundle: String): Map<String, String> =
            Regex("\"([A-Za-z0-9_]+)\",\"(?:query|mutation)\",\"([0-9a-f]{64})\"")
                .findAll(bundle).associate { it.groupValues[1] to it.groupValues[2] }
    }
}
