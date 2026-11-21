package com.devxrohan.smusic.spotify

internal object SpotifySyncPolicy {
    fun availableLikeSources(spotifyConnected: Boolean, youtubeConnected: Boolean): Set<String> = buildSet {
        add("all"); add("smusic")
        if (spotifyConnected) add("spotify")
        if (youtubeConnected) add("youtube")
    }
    fun activeLikeSource(selected: String, available: Set<String>): String = selected.takeIf { it in available } ?: "all"

    fun likedIds(local: Set<String>, spotify: Set<String>, youtube: Set<String>, source: String, smusic: Set<String> = emptySet()): Set<String> =
        when (source) {
            "smusic" -> smusic
            "spotify" -> spotify
            "youtube" -> youtube
            else -> local + spotify + youtube + smusic
        }

    fun removalKeys(links: Map<String, String>, kind: String, localId: String): Set<String> =
        links.filter { (key, value) -> key.startsWith("$kind:") && value == localId }.keys

    data class PlaylistTrackRef(val playlistId: String, val mapId: Int, val spotifyId: String)

    fun spotifyTrackId(sourceId: String?): String? = sourceId?.split(':')
        ?.takeIf { it.size == 3 && it[0] == "spotify" }?.get(1)?.takeIf { it.isNotBlank() }

    fun playlistTrackToken(playlistId: String, mapId: Int, sourceId: String?): String? {
        val trackId = spotifyTrackId(sourceId) ?: return null
        return if (playlistId.startsWith("SP:")) "$playlistId|$mapId|$trackId" else null
    }

    fun parsePlaylistTrackToken(token: String): PlaylistTrackRef? {
        val parts = token.split('|')
        if (parts.size != 3 || !parts[0].startsWith("SP:") || parts[2].isBlank()) return null
        return PlaylistTrackRef(parts[0], parts[1].toIntOrNull() ?: return null, parts[2])
    }

    fun playlistTrackBlockKey(playlistId: String, trackId: String) = "playlist_track:${playlistId.removePrefix("SP:")}:$trackId"
}
