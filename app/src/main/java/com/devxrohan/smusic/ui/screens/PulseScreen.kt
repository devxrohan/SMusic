package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import com.devxrohan.smusic.pulse.PulseArtistInsight
import com.devxrohan.smusic.pulse.PulseStage
import com.devxrohan.smusic.pulse.PulseSummary
import com.devxrohan.smusic.ui.component.IconButton
import com.devxrohan.smusic.ui.utils.backToMain
import com.devxrohan.smusic.viewmodels.PulseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseScreen(
    navController: NavController,
    viewModel: PulseViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val blocked by viewModel.blocked.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.ui_pulse)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().let {
                PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = it.calculateBottomPadding() + 20.dp)
            },
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    stringResource(R.string.ui_your_music_understood),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ui_pulse_learns_from_what_you_actually),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val s = summary
            if (s == null) {
                item {
                    PulseCard(stringResource(R.string.ui_pulse_is_learning)) {
                        Text(
                            if (loaded) stringResource(R.string.pulse_will_learn)
                            else stringResource(R.string.pulse_reading_history),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                item { ProfileHero(s) }
                item { TasteIdentity(s) }
                if (s.smartMixes.isNotEmpty()) item { SmartMixPreview(s.smartMixes) }
                item { DiscoveryCard(s.discoveryAppetite) }
                if (s.reasonHighlights.isNotEmpty()) item { WhyPulseCard(s.reasonHighlights) }
                if (s.drift >= 0.4) item { TasteShiftCard() }
                if (s.topArtists.isNotEmpty()) item { ArtistsCard(stringResource(R.string.ui_artists_you_keep_coming_back_to), s.topArtists) }
                if (s.rising.isNotEmpty()) item { ArtistsCard(stringResource(R.string.ui_rising_in_your_rotation), s.rising) }
                if (s.tasteGroups.isNotEmpty()) item { TasteGroupsCard(s.tasteGroups) }
                if (blocked.isNotEmpty()) item { BlockedCard(blocked, viewModel) }
                item {
                    PulseCard(stringResource(R.string.advanced)) {
                        Text(
                            stringResource(R.string.ui_pulse_rebuilds_its_profile_from_your),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = viewModel::rebuild,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.pulse_rebuild)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(s: PulseSummary) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.ui_listening_personality), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(s.personality.ifBlank { "Balanced Listener" }, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(stageLabel(s.stage), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                progress = { s.confidence.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.ui_taste_confidence), style = MaterialTheme.typography.labelLarge)
                Text("${(s.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.ui_meaningful_listens_learned, s.totalPlays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TasteIdentity(s: PulseSummary) {
    PulseCard(stringResource(R.string.ui_your_taste)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            s.tasteGroups.flatten().distinct().take(8).forEach { label ->
                AssistChip(
                    onClick = {},
                    label = { Text(label) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
        if (s.tasteGroups.flatten().isEmpty()) {
            Text(stringResource(R.string.ui_pulse_is_still_finding_the_patterns), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmartMixPreview(mixes: List<String>) {
    PulseCard(stringResource(R.string.ui_smart_mixes)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            mixes.forEach { mix ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(mix, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(R.string.ui_built_from_your_taste), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(appetite: Double) {
    val label = when {
        appetite >= 0.6 -> stringResource(R.string.pulse_appetite_explorer)
        appetite >= 0.35 -> stringResource(R.string.pulse_appetite_balanced)
        else -> stringResource(R.string.pulse_appetite_loyal)
    }
    PulseCard(stringResource(R.string.ui_discovery_appetite)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ui_familiar), style = MaterialTheme.typography.labelMedium)
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.ui_new), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(9.dp))
        LinearProgressIndicator(progress = { appetite.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WhyPulseCard(reasons: List<String>) {
    PulseCard(stringResource(R.string.ui_why_pulse)) {
        reasons.take(4).forEach { reason ->
            Text("•  $reason", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun TasteShiftCard() {
    PulseCard(stringResource(R.string.ui_your_taste_lately)) {
        Text(
            stringResource(R.string.ui_your_recent_listening_is_moving_away),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TasteGroupsCard(groups: List<List<String>>) {
    PulseCard(stringResource(R.string.ui_taste_groups)) {
        groups.take(4).forEach { group ->
            Text(group.joinToString("  ·  "), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 5.dp))
        }
    }
}

@Composable
private fun ArtistsCard(title: String, artists: List<PulseArtistInsight>) {
    PulseCard(title) {
        artists.take(8).forEachIndexed { i, a ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${i + 1}. ${a.name}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun BlockedCard(blocked: List<PulseArtistInsight>, viewModel: PulseViewModel) {
    PulseCard(stringResource(R.string.ui_hidden_artists)) {
        blocked.forEach { artist ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(artist.name, style = MaterialTheme.typography.bodyLarge)
                androidx.compose.material3.TextButton(onClick = { viewModel.unblock(artist.id) }) {
                    Text(stringResource(R.string.pulse_unhide))
                }
            }
        }
    }
}

@Composable
private fun PulseCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(9.dp))
            content()
        }
    }
}

@Composable
private fun stageLabel(stage: PulseStage) = when (stage) {
    PulseStage.COLD_START -> stringResource(R.string.pulse_stage_cold)
    PulseStage.EARLY -> stringResource(R.string.pulse_stage_early)
    PulseStage.LEARNING -> stringResource(R.string.pulse_stage_learning)
    PulseStage.PERSONALIZED -> stringResource(R.string.pulse_stage_personalized)
    PulseStage.DEEP_PERSONALIZED -> stringResource(R.string.pulse_stage_deep)
}
