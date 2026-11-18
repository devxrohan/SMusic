package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.ClassicArtworkHeightKey
import com.devxrohan.smusic.constants.ClassicArtworkSizeKey
import com.devxrohan.smusic.constants.ClassicShowAudioQualityKey
import com.devxrohan.smusic.constants.ClassicShowLyricsLineKey
import com.devxrohan.smusic.constants.ClassicShowPlayingFromKey
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
internal fun ClassicPlayerOptions() {
    val (quality, changeQuality) = rememberPreference(ClassicShowAudioQualityKey, true)
    val (lyrics, changeLyrics) = rememberPreference(ClassicShowLyricsLineKey, true)
    val (size, changeSize) = rememberPreference(ClassicArtworkSizeKey, 86)
    val (height, changeHeight) = rememberPreference(ClassicArtworkHeightKey, 86)
    val (source, changeSource) = rememberPreference(ClassicShowPlayingFromKey, true)
    SettingsGeneralCategory(title = stringResource(R.string.classic_player), items = listOf(
        { SwitchPreference(title = { Text(stringResource(R.string.classic_show_quality)) },
            icon = { Icon(painterResource(R.drawable.sliders), null) },
            checked = quality, onCheckedChange = changeQuality) },
        { SwitchPreference(title = { Text(stringResource(R.string.classic_show_lyrics)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = lyrics, onCheckedChange = changeLyrics) },
        { SwitchPreference(title = { Text(stringResource(R.string.classic_show_source)) },
            icon = { Icon(painterResource(R.drawable.queue_music), null) },
            checked = source, onCheckedChange = changeSource) },
        { ClassicArtworkDimension(stringResource(R.string.classic_artwork_width), size, 70f..90f, changeSize) },
        { ClassicArtworkDimension(stringResource(R.string.classic_artwork_height), height, 70f..100f, changeHeight) },
    ))
}

@Composable
private fun ClassicArtworkDimension(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onChange: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat().coerceIn(range.start, range.endInclusive)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${draft.roundToInt()}%", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = draft, onValueChange = { draft = it },
            onValueChangeFinished = { onChange(draft.roundToInt()) }, valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            modifier = Modifier.fillMaxWidth())
    }
}
