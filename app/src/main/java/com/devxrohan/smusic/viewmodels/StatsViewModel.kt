package com.devxrohan.smusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.constants.statToPeriod
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.ui.component.NamePreferenceManager
import com.devxrohan.smusic.ui.screens.OptionStats
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
@Inject
constructor(
    val database: MusicDatabase,
    @param:ApplicationContext private val context: Context,
    private val namePreferenceManager: NamePreferenceManager,
) : ViewModel() {
    val selectedOption = MutableStateFlow(OptionStats.CONTINUOUS)
    val indexChips = MutableStateFlow(com.devxrohan.smusic.constants.StatPeriod.entries.lastIndex)

    val mostPlayedSongsStats =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedSongsStats(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                Long.MAX_VALUE
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedSongs =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedSongs(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                Long.MAX_VALUE
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedArtists =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedArtists(
                        statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                Long.MAX_VALUE
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedAlbums =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database.mostPlayedAlbums(
                    statToPeriod(selection, t),
                    limit = -1,
                    toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            Long.MAX_VALUE
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val firstEvent =
        database
            .firstEvent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun observeMetadata() = withContext(Dispatchers.IO) {
        coroutineScope {
            launch {
                mostPlayedArtists.collect { artists ->
                    artists
                        .map { it.artist }
                        .filter {
                            it.isYouTubeArtist && (it.thumbnailUrl == null || Duration.between(
                                it.lastUpdateTime,
                                LocalDateTime.now()
                            ) > Duration.ofDays(10))
                        }.forEach { artist ->
                            com.devxrohan.smusic.utils.MusicPageRequests.artist(artist.id).onSuccess { artistPage ->
                                database.query {
                                    update(artist, artistPage)
                                }
                            }
                        }
                }
            }
            launch {
                mostPlayedAlbums.collect { albums ->
                    albums
                        .filter {
                            it.album.songCount == 0
                        }.forEach { album ->
                            com.devxrohan.smusic.utils.MusicPageRequests.album(album.id)
                                .onSuccess { albumPage ->
                                    database.query {
                                        update(album.album, albumPage, album.artists)
                                    }
                                }.onFailure {
                                    reportException(it)
                                    if (it.message?.contains("NOT_FOUND") == true) {
                                        database.query {
                                            delete(album.album)
                                        }
                                    }
                                }
                        }
                }
            }
        }
    }
}
