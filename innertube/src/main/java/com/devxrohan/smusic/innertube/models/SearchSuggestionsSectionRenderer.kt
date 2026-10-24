package com.devxrohan.smusic.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchSuggestionsSectionRenderer(
    val contents: List<Content>? = null,
) {
    @Serializable
    data class Content(
        val searchSuggestionRenderer: SearchSuggestionRenderer? = null,
        val historySuggestionRenderer: HistorySuggestionRenderer? = null,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    ) {
        @Serializable
        data class SearchSuggestionRenderer(
            val suggestion: Runs? = null,
            val navigationEndpoint: NavigationEndpoint? = null,
        )

        /** Present only for suggestions backed by the signed-in account's search history. */
        @kotlinx.serialization.Serializable
        data class HistorySuggestionRenderer(
            val suggestion: Runs? = null,
            val navigationEndpoint: NavigationEndpoint? = null,
            val serviceEndpoint: ServiceEndpoint? = null,
        ) {
            @kotlinx.serialization.Serializable
            data class ServiceEndpoint(
                val feedbackEndpoint: FeedbackEndpoint? = null,
            ) {
                @kotlinx.serialization.Serializable
                data class FeedbackEndpoint(
                    val feedbackToken: String? = null,
                )
            }
        }
    }
}
