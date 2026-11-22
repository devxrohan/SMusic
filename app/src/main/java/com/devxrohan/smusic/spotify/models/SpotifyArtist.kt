/* GPL-3.0; copyright and upstream attribution are recorded in README.md. */
// Adapted for SMusic on 2026-11-21; preserves the upstream account protocol.

package com.devxrohan.smusic.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyArtist(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    val genres: List<String> = emptyList(),
    val popularity: Int? = null,
    val uri: String? = null,
)
