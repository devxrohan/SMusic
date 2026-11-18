package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.graphics.Bitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.utils.LyricsCardStyle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsShareFormatPicker(mode: ShareMode, enabled: Boolean, onModeChange: (ShareMode) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        ShareMode.entries.forEachIndexed { index, item ->
            ToggleButton(checked = mode == item, onCheckedChange = { onModeChange(item) }, enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    ShareMode.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                Text(stringResource(when (item) {
                    ShareMode.CARD -> R.string.lyrics_share_card
                    ShareMode.STORY -> R.string.lyrics_share_story
                    ShareMode.VIDEO -> R.string.lyrics_share_video
                }))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsSharePreview(preview: LyricsPreview, onRetry: () -> Unit) {
    val availableHeight = (LocalConfiguration.current.screenHeightDp * 0.28f).coerceIn(180f, 280f).dp
    Box(Modifier.fillMaxWidth().height(availableHeight), contentAlignment = Alignment.Center) {
        when (preview) {
            is LyricsPreview.Ready -> Image(preview.bitmap.asImageBitmap(), stringResource(R.string.share_lyrics_image),
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
            LyricsPreview.Loading -> LoadingIndicator(Modifier.size(48.dp))
            LyricsPreview.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lyrics_share_preview_error), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.lyrics_share_retry)) }
            }
        }
    }
}

@Composable
internal fun LyricsShareDesignPicker(
    selectedStyle: LyricsCardStyle,
    previews: List<Bitmap?>,
    enabled: Boolean,
    onSelect: (LyricsCardStyle) -> Unit,
) {
    LyricsDesignOptions(
        labels = LyricsCardStyle.entries.map { stringResource(styleLabel(it)) },
        previews = previews,
        selectedIndex = selectedStyle.ordinal,
        enabled = enabled,
        onSelect = { onSelect(LyricsCardStyle.entries[it]) },
    )
}

@Composable
internal fun LyricsVideoDesignPicker(
    selectedStyle: com.devxrohan.smusic.utils.LyricsVideoStyle,
    previews: List<Bitmap?>,
    enabled: Boolean,
    onSelect: (com.devxrohan.smusic.utils.LyricsVideoStyle) -> Unit,
) {
    val styles = com.devxrohan.smusic.utils.LyricsVideoStyle.entries
    LyricsDesignOptions(
        labels = listOf(R.string.lyrics_share_video_lyrics,
            R.string.lyrics_share_video_record, R.string.lyrics_share_video_spotlight).map { stringResource(it) },
        previews = previews,
        selectedIndex = selectedStyle.ordinal,
        enabled = enabled,
        onSelect = { onSelect(styles[it]) },
    )
}

@Composable
private fun LyricsDesignOptions(labels: List<String>, previews: List<Bitmap?>, selectedIndex: Int,
    enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.lyrics_share_design), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.forEachIndexed { index, label ->
                val checked = selectedIndex == index
                Surface(onClick = { onSelect(index) }, enabled = enabled,
                    modifier = Modifier.width(104.dp).semantics {
                        role = Role.RadioButton
                        selected = checked
                        contentDescription = label
                    },
                    shape = RoundedCornerShape(if (checked) 24.dp else 14.dp),
                    color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(if (checked) 2.dp else 1.dp,
                        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    val bitmap = previews.getOrNull(index)
                    val aspect = bitmap?.let { it.width.toFloat() / it.height }.let { it ?: 1f }
                    Box(Modifier.padding(4.dp).fillMaxWidth().aspectRatio(aspect), contentAlignment = Alignment.Center) {
                        previews.getOrNull(index)?.let { bitmap ->
                            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                        } ?: CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

private fun styleLabel(style: LyricsCardStyle): Int = when (style) {
    LyricsCardStyle.CLASSIC -> R.string.lyrics_share_classic
    LyricsCardStyle.PAPER -> R.string.lyrics_share_paper
    LyricsCardStyle.DARK -> R.string.lyrics_share_dark
}
