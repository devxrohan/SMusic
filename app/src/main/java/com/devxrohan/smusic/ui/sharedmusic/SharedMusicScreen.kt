package com.devxrohan.smusic.ui.sharedmusic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import androidx.compose.ui.res.stringResource

/**
 * New, SMusic-styled result screen for the share-to-identify flow (SMusic' 837-line
 * SharedMusicScreen.kt was not ported verbatim — see ACCOUNT_INTEGRATION_REPORT.md for why).
 */
@Composable
fun SharedMusicScreen(
    viewModel: SharedMusicViewModel,
    onOpenInSMusic: (query: String) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                is SharedMusicUiState.Idle, is SharedMusicUiState.Processing -> {
                    CircularProgressIndicator()
                    Text(
                        text = (s as? SharedMusicUiState.Processing)?.message
                            ?: stringRes(R.string.find_music),
                        modifier = Modifier.padding(top = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                is SharedMusicUiState.Success -> {
                    val result = s.result
                    if (!result.coverArtUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = result.coverArtUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp))
                        }
                    }

                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    Text(
                        text = result.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Button(
                        onClick = { onOpenInSMusic("${result.title} ${result.artist}") },
                        modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ui_play_in_smusic))
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                        Text(stringRes(R.string.close))
                    }
                }

                is SharedMusicUiState.NoMatch -> {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp))
                    Text(
                        text = stringResource(R.string.ui_no_matching_song_found),
                        modifier = Modifier.padding(top = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringRes(R.string.close))
                    }
                }

                is SharedMusicUiState.NoAudio -> {
                    Text(stringResource(R.string.ui_this_clip_has_no_audio_track), textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringRes(R.string.close))
                    }
                }

                is SharedMusicUiState.UrlShared -> {
                    Text(stringResource(R.string.ui_shared_a_link_not_a_video), textAlign = TextAlign.Center)
                    Text(
                        text = s.url,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Resolving audio directly from a shared link (e.g. downloading a " +
                            "Reel/Short from its URL) was not ported from SMusic — see " +
                            "ACCOUNT_INTEGRATION_REPORT.md. Share the video/audio file itself instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringRes(R.string.close))
                    }
                }

                is SharedMusicUiState.Error -> {
                    Text(s.message, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringRes(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun stringRes(id: Int) = androidx.compose.ui.res.stringResource(id)
