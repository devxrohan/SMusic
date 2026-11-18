package com.devxrohan.smusic.ui.component

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AppFont
import com.devxrohan.smusic.constants.AppFontKey
import com.devxrohan.smusic.lyrics.LyricsEntry
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.utils.LyricsCardStyle
import com.devxrohan.smusic.utils.LyricsShareRenderer
import com.devxrohan.smusic.utils.LyricsVideoRenderer
import com.devxrohan.smusic.utils.LyricsVideoStyle
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Selected text and its original song-relative timestamps. */
data class ShareLyricsData(
    val text: String,
    val entries: List<LyricsEntry> = emptyList(),
    val songTitle: String,
    val artists: String,
)

internal enum class ShareMode { CARD, STORY, VIDEO }
internal sealed interface LyricsPreview {
    data object Loading : LyricsPreview
    data class Ready(val bitmap: Bitmap) : LyricsPreview
    data object Failed : LyricsPreview
}

@Composable
fun ShareLyricsDialog(data: ShareLyricsData, mediaMetadata: MediaMetadata?, onDismiss: () -> Unit) {
    if (data.text.isBlank()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val appFontKey by rememberPreference(AppFontKey, defaultValue = AppFont.APPLE.key)
    // Repeated verses can have identical text but different audio timestamps.
    key(data, mediaMetadata?.id, mediaMetadata?.thumbnailUrl, appFontKey) {
        LyricsShareSheet(data, mediaMetadata, AppFont.fromKey(appFontKey), onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsShareSheet(data: ShareLyricsData, metadata: MediaMetadata?, font: AppFont, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(ShareMode.CARD) }
    var style by remember { mutableStateOf(LyricsCardStyle.CLASSIC) }
    var videoStyle by remember { mutableStateOf(LyricsVideoStyle.LYRICS) }
    var retry by remember { mutableIntStateOf(0) }
    var saved by remember(mode, style) { mutableStateOf(false) }
    var videoUri by remember(videoStyle) { mutableStateOf<Uri?>(null) }
    var videoJob by remember { mutableStateOf<Job?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var imageBusy by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf(false) }
    var silentVideo by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var stage by remember { mutableStateOf("") }
    val text = data.text.trim()
    val preview by key(mode, style, videoStyle, retry) {
        produceState<LyricsPreview>(LyricsPreview.Loading) {
            value = LyricsPreview.Loading
            suspend fun render(artwork: String?): Bitmap = if (mode == ShareMode.VIDEO) {
                LyricsVideoRenderer.renderPreview(context, data.entries, text, data.songTitle, data.artists, artwork, font, style = videoStyle)
            } else {
                LyricsShareRenderer.render(context, text.lines().filter { it.isNotBlank() }.joinToString("\n"),
                    data.songTitle, data.artists, artwork, font, story = mode == ShareMode.STORY, style = style)
            }
            try {
                // Text is ready immediately; an unavailable artwork request never owns the loading state.
                value = LyricsPreview.Ready(render(null))
                metadata?.thumbnailUrl?.let { url ->
                    val completed = render(url)
                    coroutineContext.ensureActive()
                    value = LyricsPreview.Ready(completed)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (value !is LyricsPreview.Ready) value = LyricsPreview.Failed
            }
        }

    }

    val designPreviews by produceState<List<Bitmap?>>(List(if (mode == ShareMode.VIDEO) LyricsVideoStyle.entries.size else LyricsCardStyle.entries.size) { null }, mode, text, data.songTitle, metadata?.thumbnailUrl, font) {
        val rendered = MutableList<Bitmap?>(if (mode == ShareMode.VIDEO) LyricsVideoStyle.entries.size else LyricsCardStyle.entries.size) { null }
        // Only the current selection is rendered; there are no sample songs or placeholder passages.
        repeat(rendered.size) { index ->
            try {
                rendered[index] = if (mode == ShareMode.VIDEO) {
                    LyricsVideoRenderer.renderPreview(context, data.entries, text, data.songTitle, data.artists,
                        metadata?.thumbnailUrl, font, style = LyricsVideoStyle.entries[index])
                } else {
                    LyricsShareRenderer.render(context, text.lines().filter { it.isNotBlank() }.joinToString("\n"),
                        data.songTitle, data.artists, metadata?.thumbnailUrl, font,
                        story = mode == ShareMode.STORY, style = LyricsCardStyle.entries[index])
                }
                value = rendered.toList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed option does not block the selected full preview or other designs.
            }
        }
    }

    fun message(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()
    fun share(uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("lyrics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_lyrics)))
    }
    fun exportImage(bitmap: Bitmap, send: Boolean) {
        if (imageBusy) return
        imageBusy = true
        scope.launch {
            try {
                val uri = LyricsShareRenderer.saveToFile(context, bitmap, "lyrics_${System.currentTimeMillis()}")
                if (send) share(uri, "image/png") else {
                    saved = true
                    message(R.string.lyrics_share_image_saved)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message(R.string.lyrics_share_save_error)
            } finally {
                imageBusy = false
            }
        }
    }

    fun exportVideo(send: Boolean) {
        if (exporting || videoJob?.isActive == true) return
        videoJob = scope.launch {
            exporting = true
            videoError = false
            try {
                val uri = videoUri ?: LyricsVideoRenderer.render(context, data.entries, text, data.songTitle,
                    data.artists, metadata?.thumbnailUrl, font, metadata, style = videoStyle,
                    onProgress = { update -> progress = update.fraction; stage = update.stage })
                    .also { result -> videoUri = result.uri; silentVideo = !result.hasAudio }.uri
                if (send) share(uri, "video/mp4") else message(R.string.lyrics_share_video_saved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                videoError = true
            } finally {
                exporting = false
                videoJob = null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.share_lyrics), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(data.songTitle, style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                LyricsShareFormatPicker(mode, !exporting && !imageBusy, onModeChange = { mode = it })
                LyricsSharePreview(preview, onRetry = { retry++ })
                if (mode != ShareMode.VIDEO) {
                    LyricsShareDesignPicker(style, previews = designPreviews,
                        enabled = !imageBusy, onSelect = { style = it })
                } else {
                    LyricsVideoDesignPicker(videoStyle, previews = designPreviews, enabled = !exporting, onSelect = { videoStyle = it })
                    if (exporting) {
                        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stage, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { videoJob?.cancel() }) { Text(stringResource(R.string.lyrics_share_cancel_export)) }
                        }
                    } else if (videoUri != null) {
                        Text(stringResource(if (silentVideo) R.string.lyrics_share_no_audio else R.string.lyrics_share_video_ready),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (videoError) Text(stringResource(R.string.lyrics_share_video_error), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
            }
            val bitmap = (preview as? LyricsPreview.Ready)?.bitmap
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            if (mode == ShareMode.VIDEO) exportVideo(true) else bitmap?.let { exportImage(it, true) }
                        }, shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            enabled = !exporting && !imageBusy && (mode == ShareMode.VIDEO || bitmap != null),
                            modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                            Icon(painterResource(R.drawable.share), null, Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(if (mode == ShareMode.VIDEO) R.string.lyrics_share_share_video else R.string.lyrics_share_share_image),
                                style = MaterialTheme.typography.titleMedium)
                        }
                        FilledTonalButton(onClick = {
                            if (mode == ShareMode.VIDEO) exportVideo(false) else bitmap?.let { exportImage(it, false) }
                        }, shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            enabled = !exporting && !imageBusy && (mode == ShareMode.VIDEO || bitmap != null),
                            contentPadding = PaddingValues(16.dp), modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)) {
                            Icon(painterResource(R.drawable.download),
                                stringResource(if (mode == ShareMode.VIDEO) R.string.lyrics_share_save_video else R.string.lyrics_share_save_image),
                                Modifier.size(24.dp))
                            if (saved) { Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lyrics_share_saved)) }
                        }
                    }
                    if (mode != ShareMode.VIDEO) TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, buildString {
                                append('"').append(text).append("\"\n\n").append(data.songTitle).append(" - ").append(data.artists)
                                metadata?.id?.let { append("\nhttps://play.smusic.app/song?id=$it") }
                            })
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_lyrics)))
                    }, modifier = Modifier.align(Alignment.CenterHorizontally), enabled = text.isNotBlank()) {
                        Text(stringResource(R.string.lyrics_share_share_text))
                    }
                }
            }
        }
    }
}
