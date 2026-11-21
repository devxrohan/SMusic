package com.devxrohan.smusic.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import kotlin.math.abs
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppAudioFxSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val service = playerConnection.service

    val bypassAllAudioEffects by service.bypassAllAudioEffects.collectAsState()
    val audioFxAndDjEnabled by service.audioFxAndDjEnabled.collectAsState()

    val boostPercent by service.audioBoostPercent.collectAsState()
    val boostEnabled by service.audioBoostEnabled.collectAsState()

    val echoEnabled by service.echoEnabled.collectAsState()
    val echoDelayMs by service.echoDelayMs.collectAsState()
    val echoFeedback by service.echoFeedback.collectAsState()
    val echoWetMix by service.echoWetMix.collectAsState()
    val echoPingPong by service.echoPingPong.collectAsState()

    val djFilterSweep by service.djFilterSweep.collectAsState()
    val djFlangerEnabled by service.djFlangerEnabled.collectAsState()
    val djFlangerRate by service.djFlangerRate.collectAsState()
    val djFlangerDepth by service.djFlangerDepth.collectAsState()
    val djSaturation by service.djSaturation.collectAsState()

    val djTempoSpeed by service.djTempoSpeed.collectAsState()
    val djPitch by service.djPitch.collectAsState()
    val djTurntableLinked by service.djTurntableLinked.collectAsState()
    val eightDEnabled by service.eightDEnabled.collectAsState()
    val eightDLevel by service.eightDLevel.collectAsState()
    val spatialSoundEnabled by service.spatialSoundEnabled.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Master, 1: Echo, 2: Mixer, 3: Turntable


    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = sheetShape,
        // Keep the sheet fully filled by its dark container. The thin translucent border was
        // rendering as a stray pale outline around/above the sheet on some Android themes.
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Audio FX + STUDIO DJ SUITE and Reset Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dj_console),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ui_audio_fx_amp_dj_studio),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.ui_professional_sound_sculpting),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.4.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // Reset Pill Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)), RoundedCornerShape(50))
                        .clickable {
                            service.resetAudioFx()
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.refresh),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.ui_reset_all),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            if (bypassAllAudioEffects) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.auto_awesome),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ui_pure_audio_bypass_active),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ui_all_dj_effects_audio_filters_and),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { service.setBypassAllAudioEffects(false) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                        ) {
                            Text(stringResource(R.string.ui_disable_pure_audio_bypass), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (audioFxAndDjEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, if (audioFxAndDjEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ui_studio_fx_engine),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (audioFxAndDjEnabled) "Active • Custom DSP processing enabled" else "Turned Off • All options reset to pure source playback",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (audioFxAndDjEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = audioFxAndDjEnabled,
                            onCheckedChange = { enabled ->
                                service.setAudioFxAndDjEnabled(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !bypassAllAudioEffects && audioFxAndDjEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Studio Navigation Tabs
                    StudioTabs(
                selectedTab = selectedTab,
                onTabSelect = { selectedTab = it },
                echoActive = echoEnabled,
                filterActive = abs(djFilterSweep) > 0.05f || djFlangerEnabled,
                pitchActive = djTempoSpeed != 1.0f || djPitch != 1.0f
            )

            Spacer(modifier = Modifier.height(18.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: MASTER FX
                    AudioFrequencyMeters(service)

                    // Master Boost Card
                    MasterBoostCard(
                        boostPercent = boostPercent,
                        boostEnabled = boostEnabled,
                        onBoostPercentChange = { percent ->
                            service.setAudioBoostPercent(percent)
                            if (!boostEnabled && percent > 100) {
                                service.setAudioBoostEnabled(true)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // DJ Presets Section
                    DjPresetsRow(
                        onSelectPreset = { preset ->
                            service.applyDjPreset(preset)
                        }
                    )
                }

                1 -> {
                    // TAB 1: ECHO & DELAY
                    EchoDelaySection(
                        enabled = echoEnabled,
                        delayMs = echoDelayMs,
                        feedback = echoFeedback,
                        wetMix = echoWetMix,
                        pingPong = echoPingPong,
                        onToggle = { service.setEchoEnabled(it) },
                        onDelayMsChange = { service.setEchoDelayMs(it) },
                        onFeedbackChange = { service.setEchoFeedback(it) },
                        onWetMixChange = { service.setEchoWetMix(it) },
                        onPingPongChange = { service.setEchoPingPong(it) }
                    )
                }

                2 -> {
                    // TAB 2: DJ MIXER & FILTERS
                    DjMixerSection(
                        filterSweep = djFilterSweep,
                        flangerEnabled = djFlangerEnabled,
                        flangerRate = djFlangerRate,
                        flangerDepth = djFlangerDepth,
                        saturation = djSaturation,
                        eightDEnabled = eightDEnabled,
                        eightDLevel = eightDLevel,
                        onFilterSweepChange = { service.setDjFilterSweep(it) },
                        onFlangerToggle = { service.setDjFlangerEnabled(it) },
                        onFlangerRateChange = { service.setDjFlangerRate(it) },
                        onFlangerDepthChange = { service.setDjFlangerDepth(it) },
                        onSaturationChange = { service.setDjSaturation(it) },
                        onEightDToggle = { service.setEightDEnabled(it) },
                        onEightDLevelChange = { service.setEightDLevel(it) },
                        spatialSoundEnabled = spatialSoundEnabled,
                        onSpatialSoundToggle = { service.setSpatialSoundEnabled(it) }
                    )
                }

                3 -> {
                    // TAB 3: TURNTABLE & TEMPO
                    DjTurntableSection(
                        speed = djTempoSpeed,
                        pitch = djPitch,
                        linked = djTurntableLinked,
                        onSpeedChange = { s ->
                            if (djTurntableLinked) {
                                service.setDjTempoAndPitch(s, s)
                            } else {
                                service.setDjTempoAndPitch(s, djPitch)
                            }
                        },
                        onPitchChange = { p ->
                            if (djTurntableLinked) {
                                service.setDjTempoAndPitch(p, p)
                            } else {
                                service.setDjTempoAndPitch(djTempoSpeed, p)
                            }
                        },
                        onLinkedToggle = { service.setDjTurntableLinked(it) },
                        onReset = { service.setDjTempoAndPitch(1.0f, 1.0f) }
                    )
                }
            }
        }
    }
}
}
}
