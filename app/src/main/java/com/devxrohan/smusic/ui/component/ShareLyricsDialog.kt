/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AppFont
import com.devxrohan.smusic.constants.AppFontKey
import com.devxrohan.smusic.lyrics.LyricsEntry
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.utils.LyricsShareRenderer
import com.devxrohan.smusic.utils.LyricsVideoRenderer
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.launch

/** What a producing screen hands [ShareLyricsDialog]: the picked text, plus the
 * same lines' own timing when the screen has it (the lyrics screen does — the
 * overflow-menu entry point doesn't, and passes an empty list), which is what
 * lets "Video" mode animate in sync instead of just evenly-paced. */
data class ShareLyricsData(
    val text: String,
    val entries: List<LyricsEntry> = emptyList(),
    val songTitle: String,
    val artists: String,
)

private enum class ShareMode { CARD, STORY, VIDEO }

/** Share actions for lyrics that have already been selected on the lyrics screen. */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLyricsDialog(
    data: ShareLyricsData,
    mediaMetadata: MediaMetadata?,
    onDismiss: () -> Unit,
) {
    val (lyricsText, entries, songTitle, artists) = data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val selectedText = lyricsText.trim()

    val appFontKey by rememberPreference(AppFontKey, defaultValue = AppFont.APPLE.key)
    val appFont = remember(appFontKey) { AppFont.fromKey(appFontKey) }
    var mode by remember { mutableStateOf(ShareMode.CARD) }
    var image by remember(selectedText, songTitle, artists) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(selectedText, songTitle, artists) { mutableStateOf(false) }
    var saved by remember(selectedText, songTitle, artists) { mutableStateOf(false) }

    var videoUri by remember(selectedText, songTitle, artists) { mutableStateOf<Uri?>(null) }
    var videoHasAudio by remember { mutableStateOf(true) }
    var videoGenerating by remember { mutableStateOf(false) }
    var videoProgress by remember { mutableFloatStateOf(0f) }
    var videoStage by remember { mutableStateOf("") }
    var videoFailed by remember { mutableStateOf(false) }
    var videoPreview by remember(selectedText, songTitle, artists) { mutableStateOf<Bitmap?>(null) }

    suspend fun generateVideo(): Uri? {
        videoGenerating = true
        videoFailed = false
        videoProgress = 0f
        val result = runCatching {
            LyricsVideoRenderer.render(
                context = context,
                entries = entries,
                fallbackText = selectedText,
                songTitle = songTitle,
                artistName = artists,
                artworkUrl = mediaMetadata?.thumbnailUrl,
                appFont = appFont,
                mediaMetadata = mediaMetadata,
                onProgress = { p -> videoProgress = p.fraction; videoStage = p.stage },
            )
        }.onFailure { videoFailed = true }.getOrNull()
        videoGenerating = false
        videoUri = result?.uri
        videoHasAudio = result?.hasAudio ?: true
        if (result != null && !result.hasAudio) {
            Toast.makeText(context, "Video created without audio — couldn't load this song's audio", Toast.LENGTH_LONG).show()
        }
        return result?.uri
    }

    // A still preview of the video's first frame — same backdrop/brand/artwork
    // as Card/Story, with the fixed-position fade lyric treatment.
    LaunchedEffect(selectedText, songTitle, artists, mediaMetadata?.thumbnailUrl, appFont, mode) {
        if (mode != ShareMode.VIDEO || selectedText.isBlank()) return@LaunchedEffect
        videoPreview = runCatching {
            LyricsVideoRenderer.renderPreview(
                context = context,
                entries = entries,
                fallbackText = selectedText,
                songTitle = songTitle,
                artistName = artists,
                artworkUrl = mediaMetadata?.thumbnailUrl,
                appFont = appFont,
            )
        }.getOrNull()
    }

    LaunchedEffect(selectedText, songTitle, artists, mediaMetadata?.thumbnailUrl, appFont, mode) {
        if (mode == ShareMode.VIDEO) return@LaunchedEffect
        image = null
        failed = false
        saved = false
        if (selectedText.isBlank()) return@LaunchedEffect
        image = runCatching {
            val cardText = selectedText.lines().filter { it.isNotBlank() }.take(5).joinToString("\n")
            LyricsShareRenderer.render(
                context = context,
                lyricsText = cardText,
                songTitle = songTitle,
                artistName = artists,
                artworkUrl = mediaMetadata?.thumbnailUrl,
                appFont = appFont,
                story = mode == ShareMode.STORY,
            )
        }.onFailure { failed = true }.getOrNull()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.share_lyrics),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            )

            val bitmap = image
            val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: (9f / 16f)
            val previewModifier = Modifier
                .fillMaxWidth((0.72f * ratio).coerceIn(0.4f, 0.9f))
                .heightIn(max = 300.dp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(20.dp))
            when {
                mode == ShareMode.VIDEO -> Box(modifier = previewModifier, contentAlignment = Alignment.Center) {
                    val preview = videoPreview
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.share_lyrics_image),
                            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    }
                    if (videoGenerating || videoUri != null || videoFailed) {
                        Box(
                            modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)).clip(RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                when {
                                    videoGenerating -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.5.dp,
                                            color = Color.White,
                                            progress = { videoProgress },
                                        )
                                        Text(videoStage, style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    }
                                    videoUri != null -> {
                                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                        Text("Video ready", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    }
                                    videoFailed -> {
                                        Text("Couldn't create video", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_lyrics_image),
                    modifier = previewModifier,
                )
                !failed -> Box(
                    modifier = previewModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = mode == ShareMode.CARD,
                    onClick = { mode = ShareMode.CARD },
                    label = { Text("Card") },
                    leadingIcon = {
                        Icon(Icons.Filled.CropPortrait, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                FilterChip(
                    selected = mode == ShareMode.STORY,
                    onClick = { mode = ShareMode.STORY },
                    label = { Text("Story") },
                    leadingIcon = {
                        Icon(Icons.Filled.StayCurrentPortrait, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                FilterChip(
                    selected = mode == ShareMode.VIDEO,
                    onClick = { mode = ShareMode.VIDEO },
                    label = { Text("Video") },
                    leadingIcon = {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            if (mode == ShareMode.VIDEO) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val uri = videoUri ?: generateVideo() ?: return@launch
                                runCatching {
                                    context.contentResolver.openInputStream(uri)?.close()
                                    saved = true
                                    Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !videoGenerating,
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(painterResource(R.drawable.download), contentDescription = null, modifier = Modifier.size(17.dp))
                            Text(
                                text = if (videoUri != null) "Saved" else if (videoGenerating) "Creating…" else "Create video",
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val uri = videoUri ?: generateVideo() ?: return@launch
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                            }
                        },
                        enabled = !videoGenerating,
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(17.dp))
                            Text(if (videoGenerating) "Creating…" else "Share video", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                if (videoGenerating) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { videoProgress },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            val songLink = mediaMetadata?.id?.let { "https://play.smusic.app/song?id=$it" }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, buildString {
                                    append("\"").append(selectedText).append("\"\n\n")
                                    append(songTitle).append(" - ").append(artists)
                                    songLink?.let { append("\n").append(it) }
                                })
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_lyrics)))
                            onDismiss()
                        },
                        enabled = selectedText.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Share text", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val drawn = image ?: return@OutlinedButton
                            scope.launch {
                                runCatching {
                                    LyricsShareRenderer.saveToFile(context, drawn, "lyrics_saved_${System.currentTimeMillis()}")
                                    saved = true
                                    Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Failed to save image: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = image != null,
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(painterResource(R.drawable.download), contentDescription = null, modifier = Modifier.size(17.dp))
                            Text(if (saved) "Saved" else "Save image", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    Button(
                        onClick = {
                            val drawn = image ?: return@Button
                            scope.launch {
                                runCatching {
                                    val uri = LyricsShareRenderer.saveToFile(context, drawn, "lyrics_${System.currentTimeMillis()}")
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_as_image)))
                                }.onFailure {
                                    failed = true
                                    Toast.makeText(context, "Failed to create image: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = image != null,
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(17.dp))
                            Text("Share image", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
