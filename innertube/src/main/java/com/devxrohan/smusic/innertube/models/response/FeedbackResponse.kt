package com.devxrohan.smusic.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackResponse(
    val feedbackResponses: List<Item>? = null,
) {
    @Serializable
    data class Item(
        val isProcessed: Boolean? = null,
    )
}
