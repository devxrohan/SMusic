package com.devxrohan.smusic.viewmodels

import androidx.lifecycle.ViewModel
import com.devxrohan.smusic.innertube.models.BrowseEndpoint
import com.devxrohan.smusic.innertube.pages.MoodAndGenres
import com.devxrohan.smusic.utils.ExplorePrefetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Expose shared Explore sections and request artwork only for visible cards. */
@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor() : ViewModel() {
    /** null = loading with nothing to show (skeleton); empty = failed with nothing to show. */
    val moodAndGenres: StateFlow<List<MoodAndGenres>?> = ExplorePrefetcher.sections

    /** keyOf(endpoint) -> artwork URL. Kept separate so artwork arriving never rebuilds the grid. */
    val artwork: StateFlow<Map<String, String>> = ExplorePrefetcher.artwork

    fun keyOf(endpoint: BrowseEndpoint) = ExplorePrefetcher.keyOf(endpoint)

    fun retry() = ExplorePrefetcher.retry()

    /** Called by the screen for the cards that are on (or next to) the screen. */
    fun requestArtwork(items: List<MoodAndGenres.Item>) = ExplorePrefetcher.prioritize(items)
}
