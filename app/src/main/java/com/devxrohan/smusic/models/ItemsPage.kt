package com.devxrohan.smusic.models

import com.devxrohan.smusic.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
