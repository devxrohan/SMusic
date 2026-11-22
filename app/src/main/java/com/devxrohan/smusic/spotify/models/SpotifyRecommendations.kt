/* GPL-3.0; copyright and upstream attribution are recorded in README.md. */
// Adapted for SMusic on 2026-11-21; preserves the upstream account protocol.

package com.devxrohan.smusic.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyRecommendations(
    val tracks: List<SpotifyTrack> = emptyList(),
    val seeds: List<SpotifyRecommendationSeed> = emptyList(),
)

@Serializable
data class SpotifyRecommendationSeed(
    val id: String? = null,
    val type: String? = null,
    val href: String? = null,
)
