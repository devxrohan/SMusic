package com.devxrohan.smusic.innertube.models.body

import com.devxrohan.smusic.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
