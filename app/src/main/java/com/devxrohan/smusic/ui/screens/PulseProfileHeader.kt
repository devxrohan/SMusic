@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.pulse.PulseSummary
import com.devxrohan.smusic.ui.component.DashboardEntrance
import com.devxrohan.smusic.ui.component.ArtworkHero
import com.devxrohan.smusic.ui.component.appButtonShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PulseProfileHeader(
    summary: PulseSummary?,
    loaded: Boolean,
    busy: Boolean,
    onExplore: () -> Unit,
    onPlay: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val plays = summary?.totalPlays ?: 0
    val topArtist = summary?.listeningArtist
    val artist = topArtist?.name
    val artwork = topArtist?.thumbnail
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val discovery = when (summary?.discoveryMode) {
        "FAMILIAR" -> stringResource(R.string.pulse_discovery_familiar)
        "BALANCED" -> stringResource(R.string.pulse_discovery_balanced)
        "ADVENTUROUS" -> stringResource(R.string.pulse_discovery_adventurous)
        else -> stringResource(R.string.pulse_discovery_automatic)
    }

    DashboardEntrance {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                color = scheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = 12.dp, topEnd = 36.dp, bottomEnd = 12.dp, bottomStart = 36.dp),
            ) {
                Column {
                    if (!largeText) {
                        ArtworkHero(artwork, discovery,
                            modifier = Modifier.fillMaxWidth().height(200.dp), artist = artist != null)
                    }
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.pulse_profile_heading),
                            style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                        if (!artist.isNullOrBlank()) {
                            Text(artist, style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            when {
                                !loaded -> stringResource(R.string.pulse_profile_loading)
                                plays > 0 -> pluralStringResource(R.plurals.pulse_profile_listens, plays, plays)
                                else -> stringResource(R.string.pulse_profile_empty)
                            }, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        )
                        if (largeText && plays > 0) Text(discovery,
                            style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = largeText || maxWidth < 360.dp
                if (onPlay == null) {
                    PulseExploreAction(onExplore, Modifier.fillMaxWidth())
                } else if (stacked) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsePlaybackAction(onPlay, loaded && !busy, Modifier.fillMaxWidth())
                        PulseExploreAction(onExplore, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PulsePlaybackAction(onPlay, loaded && !busy, Modifier.weight(1f))
                        PulseExploreAction(onExplore, Modifier.weight(1f))
                    }
                }
            }
            if (!loaded || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PulsePlaybackAction(onPlay: () -> Unit, enabled: Boolean, modifier: Modifier) {
    Button(onClick = onPlay, enabled = enabled,
        shapes = appButtonShapes(ButtonDefaults.MediumContainerHeight),
        modifier = modifier.heightIn(min = 56.dp)) {
        Icon(painterResource(R.drawable.play), null, Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.pulse_play_action))
    }
}

@Composable
private fun PulseExploreAction(onExplore: () -> Unit, modifier: Modifier) {
    Button(onClick = onExplore, shapes = appButtonShapes(ButtonDefaults.MediumContainerHeight),
        modifier = modifier.heightIn(min = 56.dp)) {
        Text(stringResource(R.string.pulse_browse_action))
    }
}
