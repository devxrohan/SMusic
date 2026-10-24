package com.devxrohan.smusic.innertube.models.body

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackBody(
    val feedbackTokens: List<String>,
)
