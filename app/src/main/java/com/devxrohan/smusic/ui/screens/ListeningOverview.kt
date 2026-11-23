@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import com.devxrohan.smusic.ui.component.appButtonShapes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.ui.component.DashboardEntrance
import com.devxrohan.smusic.ui.component.DashboardNumber
import com.devxrohan.smusic.ui.component.MusicArtwork
import java.util.concurrent.TimeUnit

@Composable
internal fun ListeningOverview(
    listenedMs: Long,
    plays: Int,
    counts: List<Pair<String, Int>>,
    artworks: List<String>,
    period: String,
    onPeriodClick: () -> Unit,
    artworkCaption: String? = null,
    motionEnabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale >= 1.3f || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 400
    val artwork = artworks.firstOrNull { it.isNotBlank() }
    DashboardEntrance {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Overview", style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(shapes = appButtonShapes(), onClick = onPeriodClick) {
                    Text(period)
                    Spacer(Modifier.width(4.dp))
                    Icon(painterResource(R.drawable.expand_more), null, modifier = Modifier.size(20.dp))
                }
            }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = 36.dp, topEnd = 12.dp, bottomEnd = 36.dp, bottomStart = 12.dp),
                color = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
            ) {
                Box {
                    StatsOrbitBackdrop(Modifier.matchParentSize(), enabled = motionEnabled)
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (plays > 0) {
                                Text("Listening time", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
                                DashboardNumber(listenedMs, { value ->
                                    val minutes = TimeUnit.MILLISECONDS.toMinutes(value.coerceAtLeast(0))
                                    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
                                }, MaterialTheme.typography.displaySmall)
                                Text("$plays plays", style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
                            } else {
                                Text("No listens yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Text("Play songs with listening history enabled to see your stats.",
                                    style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryContainer)
                            }
                        }
                        if (!largeText && artwork != null) {
                            Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                MusicArtwork(artwork, Modifier.size(80.dp).clip(MaterialTheme.shapes.large))
                                artworkCaption?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }
            }
            if (plays > 0) {
                if (largeText) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        counts.forEach { (label, count) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                                DashboardNumber(count.toLong(), { it.toString() }, MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        counts.forEach { (label, count) ->
                            Column(Modifier.weight(1f).clip(MaterialTheme.shapes.large)
                                .background(scheme.surfaceContainerHigh).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DashboardNumber(count.toLong(), { it.toString() }, MaterialTheme.typography.titleLarge)
                                Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
