package com.devxrohan.smusic.innertube.models.body

import com.devxrohan.smusic.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String
)
