package com.devxrohan.smusic.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.pages.HistoryPage
import com.devxrohan.smusic.constants.HistorySource
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    val database: MusicDatabase,
) : ViewModel() {
    var historySource = MutableStateFlow(HistorySource.LOCAL)

    private val today = LocalDate.now()
    private val thisMonday = today.with(DayOfWeek.MONDAY)
    private val lastMonday = thisMonday.minusDays(7)

    val historyPage = mutableStateOf<HistoryPage?>(null)
    val historyLoading = MutableStateFlow(false)
    val historyError = MutableStateFlow<String?>(null)

    val events =
        database
            .events()
            // Let the slide-in finish before the (big) history list arrives, exactly like Pulse does,
            // so the list never lands in the middle of the animation.
            .onStart { delay(350) }
            .map { events ->
                events
                    .groupBy {
                        val date = it.event.timestamp.toLocalDate()
                        val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
                        when {
                            daysAgo == 0 -> DateAgo.Today
                            daysAgo == 1 -> DateAgo.Yesterday
                            date >= thisMonday -> DateAgo.ThisWeek
                            date >= lastMonday -> DateAgo.LastWeek
                            else -> DateAgo.Other(date.withDayOfMonth(1))
                        }
                    }.toSortedMap(
                        compareBy { dateAgo ->
                            when (dateAgo) {
                                DateAgo.Today -> 0L
                                DateAgo.Yesterday -> 1L
                                DateAgo.ThisWeek -> 2L
                                DateAgo.LastWeek -> 3L
                                is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
                            }
                        },
                    ).mapValues { entry ->
                        entry.value.distinctBy { it.song.id }
                    }
            }.flowOn(Dispatchers.Default) // grouping a big history must not run on the main thread
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        fetchRemoteHistory()
    }

    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyLoading.value = true
            historyError.value = null
            historyPage.value = null
            try {
                var lastError: Throwable? = null
                repeat(3) { attempt ->
                    val result = YouTube.musicHistory()
                    if (result.isSuccess) {
                        val page = result.getOrThrow()
                        historyPage.value = page
                        if (page.sections.isNullOrEmpty()) {
                            historyError.value = "YouTube returned an empty history. Check that the connected account has Watch history enabled."
                        }
                        return@launch
                    }
                    lastError = result.exceptionOrNull()
                    if (attempt < 2) delay(500L * (attempt + 1))
                }
                lastError?.let {
                    historyError.value = it.message?.takeIf(String::isNotBlank)
                        ?: "Couldn't load YouTube Music history. Check your connection and sign in again."
                    reportException(it)
                }
            } catch (error: Throwable) {
                historyError.value = error.message?.takeIf(String::isNotBlank)
                    ?: "Couldn't load YouTube Music history. Check your connection and sign in again."
                reportException(error)
            } finally {
                historyLoading.value = false
            }
        }
    }
}

sealed class DateAgo {
    data object Today : DateAgo()

    data object Yesterday : DateAgo()

    data object ThisWeek : DateAgo()

    data object LastWeek : DateAgo()

    class Other(
        val date: LocalDate,
    ) : DateAgo() {
        override fun equals(other: Any?): Boolean {
            if (other is Other) return date == other.date
            return super.equals(other)
        }

        override fun hashCode(): Int = date.hashCode()
    }
}
