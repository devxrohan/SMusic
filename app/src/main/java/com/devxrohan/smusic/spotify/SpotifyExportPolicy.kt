package com.devxrohan.smusic.spotify

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object SpotifyExportPolicy {
    fun pendingLikes(current: Set<String>, pending: Map<String, Boolean>, sent: Map<String, Boolean>): Map<String, Boolean> =
        (current.associateWith { true } + pending).filter { (id, wanted) -> id in pending || sent[id] != wanted }

    fun acknowledge(pending: Map<String, Boolean>, completed: Map<String, Boolean>): Map<String, Boolean> =
        pending.filterNot { (id, wanted) -> completed[id] == wanted }

    fun playlistUris(sourceIds: List<String>, matches: Map<String, String>): List<String>? {
        if (sourceIds.any { matches[it].isNullOrBlank() }) return null
        return sourceIds.map { "spotify:track:${matches.getValue(it)}" }
    }

    fun privatePlaylistBody(name: String) = buildJsonObject { put("name", name); put("public", false) }
}
