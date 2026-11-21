package com.devxrohan.smusic.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.ui.component.rememberArtworkFlower

private data class AboutFeature(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val description: Int)
private val features = listOf(
    AboutFeature(R.drawable.play, R.string.feature_streaming_title, R.string.feature_streaming_desc),
    AboutFeature(R.drawable.download, R.string.feature_downloads_title, R.string.feature_downloads_desc),
    AboutFeature(R.drawable.queue_music, R.string.about_local_title, R.string.about_local_desc),
    AboutFeature(R.drawable.radio, R.string.about_pulse_title, R.string.about_pulse_desc),
    AboutFeature(R.drawable.stats, R.string.about_stats_title, R.string.about_stats_desc),
    AboutFeature(R.drawable.lyrics, R.string.feature_lyrics_title, R.string.feature_lyrics_desc),
    AboutFeature(R.drawable.share, R.string.about_share_title, R.string.about_share_desc),
    AboutFeature(R.drawable.mic, R.string.feature_recognition_title, R.string.feature_recognition_desc),
    AboutFeature(R.drawable.bedtime, R.string.about_timer_title, R.string.about_timer_desc),
    AboutFeature(R.drawable.speed_dial_pin, R.string.speed_dial, R.string.about_speed_desc),
    AboutFeature(R.drawable.grid_view, R.string.about_home_title, R.string.about_home_desc),
    AboutFeature(R.drawable.equalizer, R.string.feature_equalizer_title, R.string.feature_equalizer_desc),
    AboutFeature(R.drawable.palette, R.string.feature_themes_title, R.string.feature_themes_desc),
    AboutFeature(R.drawable.play, R.string.feature_player_styles_title, R.string.feature_player_styles_desc),
    AboutFeature(R.drawable.tune, R.string.feature_navbar_title, R.string.feature_navbar_desc),
    AboutFeature(R.drawable.queue_music, R.string.feature_import_title, R.string.feature_import_desc),
    AboutFeature(R.drawable.restore, R.string.feature_backup_title, R.string.feature_backup_desc),
    AboutFeature(R.drawable.update, R.string.feature_updates_title, R.string.feature_updates_desc),
)

@Composable
internal fun AboutFeatures() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.features), style = MaterialTheme.typography.titleLarge)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxWidth().height(360.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(features, key = { _, item -> item.title }, contentType = { _, _ -> "feature" }) { index, item ->
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, rememberArtworkFlower()),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(item.icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(item.description), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (index < features.lastIndex) HorizontalDivider(Modifier.padding(start = 70.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
