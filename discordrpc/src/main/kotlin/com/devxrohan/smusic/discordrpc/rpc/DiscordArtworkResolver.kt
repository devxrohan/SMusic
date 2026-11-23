package com.devxrohan.smusic.discordrpc.rpc

import java.net.URI
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares resolved assets across progress updates and bounds failed-request retries. */
internal class DiscordArtworkResolver(
    private val fetch: suspend (applicationId: String, url: String) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Cached(val image: String, val expiresAt: Long)
    private val cache = LinkedHashMap<Pair<String, String>, Cached>()
    private val mutex = Mutex()

    suspend fun resolve(image: String, applicationId: String?): String? {
        if (image.startsWith("mp:") && image.length > 3) return image
        val uri = runCatching { URI(image.trim()) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        val url = uri.toASCIIString()
        val id = applicationId?.takeIf { it.isNotBlank() && it.length <= 20 && it.all(Char::isDigit) } ?: return url
        return mutex.withLock {
            val key = id to url
            cache[key]?.takeIf { clock() < it.expiresAt }?.let { return@withLock it.image }
            val path = fetch(id, url)?.removePrefix("mp:")?.takeIf { it.startsWith("external/") }
            val resolved = path?.let { "mp:$it" } ?: url
            cache.entries.removeAll { it.value.expiresAt <= clock() }
            cache[key] = Cached(resolved, clock() + if (path != null) 6 * 60 * 60_000L else 30_000L)
            while (cache.size > 128) cache.remove(cache.keys.first())
            resolved
        }
    }
}
