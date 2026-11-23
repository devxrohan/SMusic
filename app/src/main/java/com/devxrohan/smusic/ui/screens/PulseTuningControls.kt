package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AvoidQueueRepeatsKey
import com.devxrohan.smusic.ui.component.DottedSlider
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
internal fun PulseTuningControls(variety: Double, cooldownHours: Double, enabled: Boolean,
    onChange: (Double, Double) -> Unit) {
    var varietyValue by remember(variety) { mutableFloatStateOf(variety.toFloat()) }
    var cooldownValue by remember(cooldownHours) { mutableFloatStateOf(cooldownHours.toFloat()) }
    val (matchLanguage, changeMatchLanguage) = rememberPreference(com.devxrohan.smusic.constants.PulseMatchLanguageKey, true)
    val (avoidRepeats, changeAvoidRepeats) = rememberPreference(AvoidQueueRepeatsKey, true)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Artist variety", style = MaterialTheme.typography.titleSmall)
            Text("${(varietyValue * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        Text("Space out familiar artists and their collaborations without losing your taste.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DottedSlider(value = varietyValue, onValueChange = { varietyValue = it }, enabled = enabled, steps = 9,
            onValueChangeFinished = { onChange(varietyValue.toDouble(), cooldownValue.toDouble()) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recent-play cooldown", style = MaterialTheme.typography.titleSmall)
            Text(if (cooldownValue == 0f) "Off" else "${cooldownValue.roundToInt()}h", style = MaterialTheme.typography.labelLarge)
        }
        Text("Lower the priority of songs you just heard. This does not hide them from your library.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DottedSlider(value = cooldownValue, onValueChange = { cooldownValue = it }, enabled = enabled,
            valueRange = 0f..48f, steps = 7,
            onValueChangeFinished = { onChange(varietyValue.toDouble(), cooldownValue.toDouble()) })
        SwitchPreference(title = { Text("Match the radio language") },
            description = "Avoid confident language mismatches using saved lyrics. Unknown languages remain eligible; no extra requests.",
            icon = { Icon(painterResource(R.drawable.discover_tune), null) },
            checked = matchLanguage, onCheckedChange = changeMatchLanguage)
        SwitchPreference(title = { Text("Avoid queue repeats") },
            description = "Do not automatically add songs already queued or heard in this queue.",
            icon = { Icon(painterResource(R.drawable.queue_music), null) },
            checked = avoidRepeats, onCheckedChange = changeAvoidRepeats)
    }
}
