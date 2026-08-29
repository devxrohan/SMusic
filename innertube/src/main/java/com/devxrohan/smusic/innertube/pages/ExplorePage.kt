package com.devxrohan.smusic.innertube.pages

import com.devxrohan.smusic.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
