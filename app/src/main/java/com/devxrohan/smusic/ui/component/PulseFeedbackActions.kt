package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.devxrohan.smusic.R
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.pulse.PulseFeedbackTrack
import kotlinx.coroutines.launch

internal data class PulseFeedbackAction(val busy: Boolean, val error: String?, val submit: (Boolean) -> Unit)

/** Persist feedback before dismissal so closing the sheet cannot cancel the write. */
@Composable
internal fun rememberPulseFeedback(tracks: List<PulseFeedbackTrack>, onDismiss: () -> Unit): PulseFeedbackAction? {
    val engine = LocalPlayerConnection.current?.service?.pulseEngine ?: return null
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dismiss by rememberUpdatedState(onDismiss)
    return PulseFeedbackAction(busy, error) { positive ->
        if (!busy) scope.launch {
            busy = true
            error = null
            try {
                if (engine.onTasteFeedback(tracks, positive)) dismiss()
                else error = "Could not save feedback. Try again."
            } finally { busy = false }
        }
    }
}

@Composable
internal fun PulseFeedbackActions(tracks: List<PulseFeedbackTrack>, onDismiss: () -> Unit) {
    val action = rememberPulseFeedback(tracks, onDismiss) ?: return
    Column(Modifier.fillMaxWidth()) {
        listOf(true, false).forEach { positive ->
            ListItem(
                headlineContent = { Text(stringResource(if (positive) R.string.pulse_more_like_this else R.string.pulse_not_for_me)) },
                leadingContent = { Icon(painterResource(if (positive) R.drawable.pulse_waveform else R.drawable.block), null) },
                supportingContent = if (positive && action.error != null) ({ Text(action.error, color = MaterialTheme.colorScheme.error) }) else null,
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clickable(enabled = !action.busy) { action.submit(positive) },
            )
        }
    }
}

internal fun LazyGridScope.PulseFeedbackGridItems(action: PulseFeedbackAction?) {
    if (action == null) return
    GridMenuItem(icon = R.drawable.pulse_waveform, title = R.string.pulse_more_like_this,
        enabled = !action.busy, subtitle = action.error) { action.submit(true) }
    GridMenuItem(icon = R.drawable.block, title = R.string.pulse_not_for_me,
        enabled = !action.busy) { action.submit(false) }
}
