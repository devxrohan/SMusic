package com.devxrohan.smusic.customization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.devxrohan.smusic.db.entities.LocalItem
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.YTItem

data class HomePreviewTrack(val id: String, val title: String, val artist: String, val thumbnail: String?)

/** A small in-memory view of Home's existing content; preview never starts its own feed sync. */
object HomePreviewCatalog {
    var sections by mutableStateOf<Map<String, List<HomePreviewTrack>>>(emptyMap())
    fun local(item: LocalItem) = HomePreviewTrack(item.id, item.title,
        (item as? Song)?.artists?.joinToString { it.name }.orEmpty(), item.thumbnailUrl)
    fun online(item: YTItem) = HomePreviewTrack(item.id, item.title,
        (item as? SongItem)?.artists?.joinToString { it.name }.orEmpty(), item.thumbnail)
}
