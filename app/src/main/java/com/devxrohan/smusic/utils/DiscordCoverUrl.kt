package com.devxrohan.smusic.utils

import java.net.URI

internal object DiscordCoverUrl {
    fun forSong(songId: String, thumbnail: String?): String? {
        val uri = thumbnail?.let { runCatching { URI(it.trim()) }.getOrNull() }
        val publicImage = uri?.takeIf { it.scheme in listOf("https", "http") && !it.host.isNullOrBlank() && it.userInfo == null }
        val youtubeId = songId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        val youtubeImage = publicImage?.host?.let { it == "i.ytimg.com" || it == "img.youtube.com" } == true
        return when {
            youtubeId != null && (publicImage == null || youtubeImage) -> "https://i.ytimg.com/vi/$youtubeId/hqdefault.jpg"
            publicImage != null -> publicImage.toASCIIString().let { url ->
                if (publicImage.host.endsWith(".googleusercontent.com") || publicImage.host.endsWith(".ggpht.com"))
                    url.replace(Regex("=w\\d+-h\\d+[^?]*"), "=w480-h480-p-l90-rj")
                        .replace(Regex("=s\\d+"), "=s480")
                else url
            }
            else -> null
        }
    }
}
