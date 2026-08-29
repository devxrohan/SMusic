package com.devxrohan.smusic.models

import com.devxrohan.smusic.innertube.models.YTItem
import com.devxrohan.smusic.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
