@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.ui.component.MusicArtwork

/** The leading entry gets editorial emphasis; every rank retains its actual counts and duration. */
@Composable
internal fun StatsRankingRow(rank: Int, title: String, subtitle: String, image: String?, shape: Shape) {
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360 ||
        androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val lead = rank == 1
    Row(Modifier.fillMaxWidth().padding(vertical = if (lead) 12.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 24.dp))
        MusicArtwork(image, artist = shape == CircleShape,
            modifier = Modifier.size(if (lead && !compact) 80.dp else 48.dp).clip(shape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (lead) 8.dp else 4.dp)) {
            Text(title, style = if (lead) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (lead) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = if (lead) 3 else 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
