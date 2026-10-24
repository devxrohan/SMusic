/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.devxrohan.smusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.SearchSuggestions
import com.devxrohan.smusic.innertube.models.YTItem
import com.devxrohan.smusic.innertube.models.filterExplicit
import com.devxrohan.smusic.innertube.models.filterVideo
import com.devxrohan.smusic.constants.HideExplicitKey
import com.devxrohan.smusic.constants.HideVideoKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.SearchHistory
import com.devxrohan.smusic.db.migrateSearchHistoryToLocalOnly
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.getSuspend
import com.devxrohan.smusic.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        // Recent searches are local only (Room), the same in guest and account mode.
        // First run after the update: merge what older builds kept in their separate stores.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { migrateSearchHistoryToLocalOnly(context, database) }
        }

        viewModelScope.launch {
            query
                .flatMapLatest { queryText ->
                    database.searchHistory(queryText).map { rows ->
                        // Typing shows a few matches, an empty box shows the whole recent list.
                        if (queryText.isBlank()) rows.take(MAX_RECENT) else rows.take(3)
                    }
                }
                .distinctUntilChanged()
                .collect { history ->
                    _viewState.value = _viewState.value.copy(history = history)
                }
        }

        // Suggestions flow: fetches from network independently
        viewModelScope.launch {
            query
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        flowOf<SearchSuggestions?>(null)
                    } else {
                        flow<SearchSuggestions?> {
                            emit(null) // clear stale suggestions immediately
                            emit(YouTube.searchSuggestions(query).getOrNull())
                        }
                    }
                }.collect { result ->
                    val history = _viewState.value.history
                    _viewState.value = _viewState.value.copy(
                        suggestions = result
                            ?.queries
                            ?.filter { s -> history.none { it.query == s } }
                            .orEmpty(),
                        items = result
                            ?.recommendedItems
                            ?.filterExplicit(context.dataStore.getSuspend(HideExplicitKey, false))
                            ?.filterVideo(context.dataStore.getSuspend(HideVideoKey, false))
                            .orEmpty(),
                    )
                }
        }
    }

    /** The list is a live Room flow, so there is nothing to refresh when the screen comes back. */
    fun refreshHistoryOnReturn() {}

    fun removeHistory(queryText: String) {
        val target = queryText.trim()
        // Drop it from the visible list immediately, then delete the row(s) from Room.
        _viewState.value = _viewState.value.copy(
            history = _viewState.value.history.filterNot { it.query.trim().equals(target, ignoreCase = true) }
        )
        viewModelScope.launch(Dispatchers.IO) {
            database.searchHistory().first()
                .filter { it.query.trim().equals(target, ignoreCase = true) }
                .forEach { database.delete(it) }
        }
    }

    fun recordSearch(queryText: String) {
        if (context.dataStore.get(com.devxrohan.smusic.constants.PauseSearchHistoryKey, false)) return
        val clean = queryText.trim()
        if (clean.isBlank()) return
        database.query { insert(SearchHistory(query = clean)) }
    }

    private companion object {
        const val MAX_RECENT = 20
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
)
