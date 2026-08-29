package com.devxrohan.smusic.innertube.models.body

import com.devxrohan.smusic.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)
