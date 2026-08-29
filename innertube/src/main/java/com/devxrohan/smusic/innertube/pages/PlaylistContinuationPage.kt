package com.devxrohan.smusic.innertube.pages

import com.devxrohan.smusic.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
