/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.devxrohan.smusic.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.filterExplicit
import com.devxrohan.smusic.innertube.models.filterVideo
import com.devxrohan.smusic.innertube.pages.SearchSummaryPage
import com.devxrohan.smusic.constants.HideExplicitKey
import com.devxrohan.smusic.constants.HideVideoKey
import com.devxrohan.smusic.models.ItemsPage
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.getSuspend
import com.devxrohan.smusic.utils.get
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = savedStateHandle.get<String>("query")!!
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    init {
        viewModelScope.launch {
            val musicProvider = context.dataStore.getSuspend(com.devxrohan.smusic.constants.MusicProviderKey, "YT")
            filter.collect { filter ->
                if (filter == null) {
                    if (summaryPage == null) {
                        if (musicProvider == "JIOSAAVN") {
                            com.devxrohan.smusic.jiosaavn.JioSaavnApi.searchSongs(query)
                                .onSuccess { songs ->
                                    summaryPage = SearchSummaryPage(
                                        summaries = listOf(
                                            com.devxrohan.smusic.innertube.pages.SearchSummary(
                                                title = "Songs",
                                                items = songs
                                            )
                                        )
                                    )
                                }.onFailure {
                                    reportException(it)
                                }
                        } else {
                            YouTube
                                .searchSummary(query)
                                .onSuccess {
                                    summaryPage = it.filterExplicit(context.dataStore.getSuspend(HideExplicitKey, false)).filterVideo(context.dataStore.getSuspend(HideVideoKey, false))
                                }.onFailure {
                                    reportException(it)
                                }
                        }
                    }
                } else {
                    if (viewStateMap[filter.value] == null) {
                        if (musicProvider == "JIOSAAVN") {
                            if (filter == YouTube.SearchFilter.FILTER_SONG) {
                                com.devxrohan.smusic.jiosaavn.JioSaavnApi.searchSongs(query)
                                    .onSuccess { songs ->
                                        viewStateMap[filter.value] = ItemsPage(songs, null)
                                    }.onFailure {
                                        reportException(it)
                                    }
                            } else {
                                viewStateMap[filter.value] = ItemsPage(emptyList(), null)
                            }
                        } else {
                            YouTube
                                .search(query, filter)
                                .onSuccess { result ->
                                    viewStateMap[filter.value] =
                                        ItemsPage(
                                            result.items
                                                .distinctBy { it.id }
                                                .filterExplicit(
                                                    context.dataStore.getSuspend(HideExplicitKey, false)
                                                ).filterVideo(context.dataStore.getSuspend(HideVideoKey, false)),
                                            result.continuation,
                                        )
                                }.onFailure {
                                    reportException(it)
                                }
                        }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val filter = filter.value?.value
        viewModelScope.launch {
            if (filter == null) return@launch
            val viewState = viewStateMap[filter] ?: return@launch
            val continuation = viewState.continuation
            if (continuation != null) {
                val searchResult =
                    YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                viewStateMap[filter] = ItemsPage(
                    (viewState.items + searchResult.items).distinctBy { it.id },
                    searchResult.continuation
                )
            }
        }
    }
}

