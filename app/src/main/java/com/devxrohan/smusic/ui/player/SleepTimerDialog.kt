package com.devxrohan.smusic.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import com.devxrohan.smusic.ui.component.DottedSlider as Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.devxrohan.smusic.R
import com.devxrohan.smusic.playback.SleepTimer
import com.devxrohan.smusic.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** All player shortcuts observe the service-owned timer through this countdown. */
@Composable
fun rememberSleepTimerTimeLeft(timer: SleepTimer): Long {
    var remaining by remember(timer) { mutableLongStateOf(0L) }
    LaunchedEffect(timer, timer.triggerTime, timer.pauseWhenSongEnd, timer.tracksRemaining) {
        remaining = 0L
        if (timer.tracksRemaining > 0) return@LaunchedEffect
        while (isActive && timer.isActive) {
            remaining = if (timer.pauseWhenSongEnd) {
                (timer.player.duration.coerceAtLeast(0L) - timer.player.currentPosition).coerceAtLeast(0L)
            } else {
                (timer.triggerTime - System.currentTimeMillis()).coerceAtLeast(0L)
            }
            delay(1_000L)
        }
    }
    return remaining
}

/** One Material dialog for the Classic icon, other player styles and the menu. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(timer: SleepTimer, onDismiss: () -> Unit) {
    var minutes by rememberSaveable { mutableFloatStateOf(30f) }
    var mode by rememberSaveable { mutableStateOf(if (timer.tracksRemaining > 0) "TRACKS" else if (timer.pauseWhenSongEnd) "END" else "TIME") }
    var tracks by rememberSaveable { mutableStateOf(timer.tracksRemaining.takeIf { it > 0 } ?: 3) }
    val remaining = rememberSleepTimerTimeLeft(timer)
    PlayerActionSheet(
        onDismiss = onDismiss,
        icon = R.drawable.bedtime,
        title = stringResource(R.string.sleep_timer),
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (timer.isActive) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Timer running", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(when { timer.tracksRemaining > 0 -> "${timer.tracksRemaining} tracks remaining"; timer.pauseWhenSongEnd -> stringResource(R.string.end_of_song); else -> makeTimeString(remaining) }, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                com.devxrohan.smusic.ui.component.ExpressiveChoiceRow(
                    labels = listOf("Time", "Tracks", "Song end"),
                    selectedIndex = listOf("TIME", "TRACKS", "END").indexOf(mode),
                    onSelect = { mode = listOf("TIME", "TRACKS", "END")[it] },
                )
                when (mode) {
                    "TIME" -> {
                        Text(pluralStringResource(R.plurals.minute, minutes.roundToInt(), minutes.roundToInt()), style = MaterialTheme.typography.headlineLarge)
                        Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 5f..120f, steps = (120 - 5) / 5 - 1)
                        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 15, 30, 60).forEach { value ->
                                com.devxrohan.smusic.ui.component.ExpressivePreset("$value min", minutes.roundToInt() == value, { minutes = value.toFloat() })
                            }
                        }
                    }
                    "TRACKS" -> {
                        Slider(value = tracks.toFloat(), onValueChange = { tracks = it.roundToInt().coerceIn(1, 100) },
                            valueRange = 1f..100f, steps = 98,
                            colors = SliderDefaults.colors(),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Completed tracks before stopping" })
                        Text(if (tracks == 1) "1 track" else "$tracks tracks", style = MaterialTheme.typography.headlineLarge)
                        Text("Only completed tracks count. Skipping a song keeps your timer unchanged.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Text("Playback pauses when the current song finishes.", style = MaterialTheme.typography.bodyLarge)
                }
                if (timer.isActive) {
                    TextButton(onClick = { timer.clear(); onDismiss() }) {
                        Text(stringResource(R.string.cancel_sleep_timer))
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            Button(onClick = {
                when (mode) { "TRACKS" -> timer.startTrackCount(tracks); "END" -> timer.start(-1); else -> timer.start(minutes.roundToInt()) }
                onDismiss()
            }) {
                Text(if (timer.isActive) "Update timer" else "Start timer")
            }
        },
    )
}

internal fun sleepTimerLabel(timer: SleepTimer, remaining: Long): String =
    if (timer.tracksRemaining > 0) "${timer.tracksRemaining} ${if (timer.tracksRemaining == 1) "track" else "tracks"}" else makeTimeString(remaining)
