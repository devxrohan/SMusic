package com.devxrohan.smusic.innertube.models.body

import com.devxrohan.smusic.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
