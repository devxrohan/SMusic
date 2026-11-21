package com.devxrohan.smusic.utils

import java.net.URI

private val coverDimensions = Regex("=w\\d+-h\\d+.*$")
private val avatarDimensions = Regex("=s\\d+.*$")

/** A common CDN variant lets browsing, Classic and media notifications share the disk cache. */
internal fun cachedArtworkUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return url
    // Not every video has a max-resolution cover; hqdefault is the dependable shared variant.
    if (host == "ytimg.com" || host.endsWith(".ytimg.com")) {
        return url.replace("/maxresdefault.jpg", "/hqdefault.jpg")
    }
    if (!(host == "googleusercontent.com" || host.endsWith(".googleusercontent.com") ||
          host == "ggpht.com" || host.endsWith(".ggpht.com"))) return url
    return url.replace(coverDimensions, "=w512-h512-p-l90-rj")
        .replace(avatarDimensions, "=s512")
}
