package com.devxrohan.smusic.ui.screens.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.SongSortType
import com.devxrohan.smusic.ui.component.LikeSourceSelector
import com.devxrohan.smusic.ui.component.SortHeader

@Composable
internal fun PlaylistSortControls(sortType: SongSortType, descending: Boolean,
    onSort: (SongSortType) -> Unit, onDescending: (Boolean) -> Unit,
    likeSource: String? = null, onLikeSource: (String) -> Unit = {}, availableSources: Set<String> = setOf("all", "smusic")) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SortHeader(sortType, descending, onSort, onDescending,
            sortTypeText = { when (it) {
                SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                SongSortType.NAME -> R.string.sort_by_name
                SongSortType.ARTIST -> R.string.sort_by_artist
                SongSortType.PLAY_TIME -> R.string.sort_by_play_time
            } }, modifier = if (likeSource == null) Modifier.weight(1f) else Modifier.widthIn(max = 112.dp))
        if (likeSource != null) LikeSourceSelector(likeSource, onLikeSource, Modifier.weight(1f), availableSources)
    }
}
