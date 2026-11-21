@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.generator

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.generator.GenerateMode
import com.devxrohan.smusic.ui.component.pressFeedback
import com.devxrohan.smusic.ui.component.rememberArtworkFlower
import com.devxrohan.smusic.utils.rememberPreference

@Composable
internal fun AiStudioHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.ui_smusic_ai_studio),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.ui_choose_a_mode_build_your_next),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer,
                rememberArtworkFlower()), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
internal fun AiStudioModePicker(selected: GenerateMode, enabled: Boolean, onSelect: (GenerateMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ui_discovery_engines), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(GenerateMode.entries.toList(), key = { it.name }) { mode ->
                AiStudioMode(mode, mode == selected, enabled, onSelect)
            }
        }
    }
}

@Composable
private fun AiStudioMode(mode: GenerateMode, selected: Boolean, enabled: Boolean, onSelect: (GenerateMode) -> Unit) {
    val reduced by rememberPreference(DisableAnimationsKey, false)
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 28.dp else 18.dp,
        if (reduced) snap() else MaterialTheme.motionScheme.fastSpatialSpec(), label = "studioModeShape")
    val color by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        if (reduced) snap() else MaterialTheme.motionScheme.fastEffectsSpec(), label = "studioModeColor")
    val foreground = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    val shape = when (mode) {
        GenerateMode.MY_MIX, GenerateMode.SMART_AI -> rememberArtworkFlower()
        GenerateMode.SONG_RADIO, GenerateMode.SIMILAR_ARTISTS -> MaterialShapes.Clover4Leaf.toShape()
        else -> MaterialShapes.SoftBurst.toShape()
    }
    Surface(
        Modifier.width(124.dp).pressFeedback(interaction, enabled),
        shape = RoundedCornerShape(corner), color = color, contentColor = foreground,
    ) {
        Column(
            Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton,
                interactionSource = interaction, indication = ripple(), onClick = { onSelect(mode) })
                .heightIn(min = 128.dp).padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(44.dp).background(
                if (selected) scheme.primary else scheme.secondaryContainer, shape),
                contentAlignment = Alignment.Center) {
                Icon(studioModeIcon(mode), null, Modifier.size(22.dp),
                    tint = if (selected) scheme.onPrimary else scheme.onSecondaryContainer)
            }
            Text(mode.label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun AiStudioProgress(message: String) {
    val reduced by rememberPreference(DisableAnimationsKey, false)
    Dialog(onDismissRequest = {}, properties = DialogProperties(
        dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (reduced) {
                    Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer,
                        rememberArtworkFlower()), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                } else LoadingIndicator(Modifier.size(64.dp))
                Text(stringResource(R.string.ui_building_your_playlist), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(message.ifBlank { "Finding songs and preparing your playlist…" },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private fun studioModeIcon(mode: GenerateMode): ImageVector = when (mode) {
    GenerateMode.MY_MIX, GenerateMode.SMART_AI -> Icons.Filled.AutoAwesome
    GenerateMode.SONG_RADIO -> Icons.Filled.MusicNote
    GenerateMode.SIMILAR_ARTISTS -> Icons.Filled.People
    GenerateMode.TAG -> Icons.Filled.Sell
    GenerateMode.NEVER_HEARD -> Icons.Filled.Explore
    GenerateMode.TOP -> Icons.AutoMirrored.Filled.TrendingUp
    GenerateMode.RECENT -> Icons.Filled.History
}
