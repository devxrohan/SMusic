package com.devxrohan.smusic.innertube.pages

import com.devxrohan.smusic.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
