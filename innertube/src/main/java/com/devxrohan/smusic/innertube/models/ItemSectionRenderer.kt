package com.devxrohan.smusic.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ItemSectionRenderer(
    val contents: List<Content>?,
    val trackingParams: String?,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
    )
}

fun List<ItemSectionRenderer.Content>.getItems(): List<MusicResponsiveListItemRenderer> =
    mapNotNull { it.musicResponsiveListItemRenderer }
