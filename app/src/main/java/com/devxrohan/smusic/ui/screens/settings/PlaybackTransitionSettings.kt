package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.constants.CrossfadeDurationKey
import com.devxrohan.smusic.constants.CrossfadeEnabledKey
import com.devxrohan.smusic.constants.PreferMusicOnlyKey
import com.devxrohan.smusic.ui.component.DottedSlider
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
internal fun PlaybackTransitionSettings() {
    val (preferAudio, changePreferAudio) = rememberPreference(PreferMusicOnlyKey, false)
    val (seconds, changeSeconds) = rememberPreference(CrossfadeDurationKey, 6)
    val (enabled, setEnabled) = rememberPreference(CrossfadeEnabledKey, false)
    var preview by remember(seconds) { mutableFloatStateOf(seconds.coerceIn(1, 12).toFloat()) }
    Column {
        SwitchPreference(title = { Text("Prefer music-only versions") },
            description = "Use a matching audio release for music videos. Keeps the original if no reliable match is found. Local and downloaded songs stay unchanged.",
            checked = preferAudio, onCheckedChange = changePreferAudio)
        SwitchPreference(title = { Text("Crossfade") }, checked = enabled, onCheckedChange = setEnabled)
        if (enabled) Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Duration", style = MaterialTheme.typography.titleMedium)
                Text(if (preview < 1) "Off" else "${preview.roundToInt()} s", style = MaterialTheme.typography.labelLarge)
            }
            DottedSlider(value = preview, onValueChange = { preview = it }, valueRange = 1f..12f,
                steps = 10, onValueChangeFinished = { changeSeconds(preview.roundToInt()) })
            Text("Overlap the end of one song with the next. Silence skipping pauses near a crossfade. Sleep timers keep exact track boundaries.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
