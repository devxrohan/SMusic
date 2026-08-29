package com.devxrohan.smusic.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchSuggestionsSectionRenderer(
    val contents: List<Content>? = null,
) {
    @Serializable
    data class Content(
        val searchSuggestionRenderer: SearchSuggestionRenderer? = null,
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    ) {
        @Serializable
        data class SearchSuggestionRenderer(
            val suggestion: Runs? = null,
            val navigationEndpoint: NavigationEndpoint? = null,
        )
    }
}
