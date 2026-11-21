package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.pulse.PulseArtistInsight
import com.devxrohan.smusic.ui.component.MusicArtwork

@Composable
internal fun PulseArtistRotation(artists: List<PulseArtistInsight>, onArtist: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().height((artists.size.coerceAtMost(4) * 76).dp)) {
        itemsIndexed(artists, key = { _, artist -> artist.id }, contentType = { _, _ -> "artist" }) { index, artist ->
            Row(Modifier.fillMaxWidth().height(76.dp).clip(MaterialTheme.shapes.medium)
                .clickable { onArtist(artist.id) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}", Modifier.width(20.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                MusicArtwork(artist.thumbnail, Modifier.size(48.dp).clip(CircleShape), artist = true)
                Text(artist.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun PulseTasteConnections(groups: List<List<String>>) {
    groups.forEachIndexed { index, members ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${index + 1}".padStart(2, '0'), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                members.filter(String::isNotBlank).distinct().forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (index < groups.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
