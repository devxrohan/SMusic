package com.devxrohan.smusic.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devxrohan.smusic.R
import com.devxrohan.smusic.playback.MusicService

/** Fast meter updates stay within this subtree and stop collecting when its tab is hidden. */
@Composable
internal fun AudioFrequencyMeters(service: MusicService) {
    val stats by service.visualizerManager.stats.collectAsStateWithLifecycle()
    LaunchedEffect(service) { service.ensureVisualizer() }
    Column {
        // Captured values are normalized energy, not calibrated dB/SPL measurements.
        Text(if (stats.captured) "Live frequency energy" else "Decorative visualizer · audio capture unavailable",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioVisualizerDial(
                title = stringResource(R.string.ui_bass),
                captured = stats.captured,
                energy = stats.bass,
                isPeak = stats.isBassPeak,
                accentDefaultColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            AudioVisualizerDial(
                title = stringResource(R.string.ui_mid),
                captured = stats.captured,
                energy = stats.mid,
                isPeak = false,
                accentDefaultColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            AudioVisualizerDial(
                title = stringResource(R.string.ui_treble),
                captured = stats.captured,
                energy = stats.treble,
                isPeak = false,
                accentDefaultColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
