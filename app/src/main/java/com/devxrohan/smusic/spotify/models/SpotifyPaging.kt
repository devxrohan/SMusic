/* GPL-3.0; copyright and upstream attribution are recorded in README.md. */
// Adapted for SMusic on 2026-11-21; preserves the upstream account protocol.

package com.devxrohan.smusic.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPaging<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val href: String? = null,
    val rawCount: Int = items.size,
    val totalKnown: Boolean = true,
)
