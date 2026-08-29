package com.devxrohan.smusic.innertube.models.body

import com.devxrohan.smusic.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueBody(
    val context: Context,
    val videoIds: List<String>?,
    val playlistId: String?,
)
