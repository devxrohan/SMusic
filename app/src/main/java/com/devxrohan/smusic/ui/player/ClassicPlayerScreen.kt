@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import com.devxrohan.smusic.constants.ClassicArtworkHeightKey
import com.devxrohan.smusic.constants.ClassicArtworkSizeKey
import com.devxrohan.smusic.constants.ClassicShowAudioQualityKey
import com.devxrohan.smusic.constants.ClassicShowLyricsLineKey
import com.devxrohan.smusic.constants.ClassicShowPlayingFromKey
import com.devxrohan.smusic.utils.rememberPreference
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.animation.animateColorAsState
import com.devxrohan.smusic.models.toMediaMetadata
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalDownloadUtil
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.DownloadQuality
import com.devxrohan.smusic.constants.DownloadQualityKey
import com.devxrohan.smusic.constants.SliderStyle
import com.devxrohan.smusic.constants.SliderStyleKey
import com.devxrohan.smusic.extensions.togglePlayPause
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.playback.ExoDownloadService
import com.devxrohan.smusic.ui.component.AudioQualityTag
import com.devxrohan.smusic.ui.component.AudioPipelineDialog
import com.devxrohan.smusic.ui.component.SongDetailsDialog
import com.devxrohan.smusic.ui.component.BottomSheetState
import com.devxrohan.smusic.ui.component.bottomSheetDraggable
import com.devxrohan.smusic.ui.component.LocalMenuState
import com.devxrohan.smusic.ui.menu.PlayerMenu
import com.devxrohan.smusic.ui.menu.DownloadQualityDialog
import androidx.compose.runtime.rememberCoroutineScope
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.makeTimeString
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.ui.utils.highQualityThumbnail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import me.saket.squiggles.SquigglySlider
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ClassicPlayerScreen(
    mediaMetadata: MediaMetadata?,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    onSliderPositionChange: (Long?) -> Unit,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    queueSheetState: BottomSheetState,
    bottomSheetState: BottomSheetState,
    onOpenFullscreenLyrics: () -> Unit,
    navController: NavController,
    dominantColor: Color,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    // Keep Classic's system-back behavior consistent with the other player styles.
    BackHandler(enabled = !bottomSheetState.isCollapsed && !bottomSheetState.isDismissed) {
        bottomSheetState.collapseSoft()
    }
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.SLIM)
    val showQuality by rememberPreference(ClassicShowAudioQualityKey, true)
    val showLyricsLine by rememberPreference(ClassicShowLyricsLineKey, true)
    val showSource by rememberPreference(ClassicShowPlayingFromKey, true)
    val artworkSizePercent by rememberPreference(ClassicArtworkSizeKey, 86)
    val artworkHeightPercent by rememberPreference(ClassicArtworkHeightKey, 86)
    val artworkZoom by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.82f,
        animationSpec = tween(420), label = "classicArtworkZoom",
    )
    val screenConfiguration = LocalConfiguration.current
    val compactClassicLayout = screenConfiguration.screenHeightDp < 820 || screenConfiguration.screenWidthDp < 360
    // Keep the cover broad on regular phones. Metadata uses the exact same inset,
    // so title/artist and artwork edges remain aligned on every device.
    val classicHorizontalPadding = if (screenConfiguration.screenWidthDp < 360) 14.dp else 18.dp
    // Match the artwork's left/right edges to the title and artist column below.
    // Classic also bypasses the carousel side-peek width in Thumbnail, so the
    // cover can use the full metadata content width consistently on each phone.
    val classicArtworkPadding = 0.dp
    // Keep at least 5% side margins, including after restoring an older oversized preference.
    val hiddenArtworkRows = (if (showLyricsLine) 0 else 1) + (if (showSource) 0 else 1)
    val artworkWidthFraction = (artworkSizePercent.coerceIn(70, 90) + hiddenArtworkRows * 2).coerceAtMost(90) / 100f
    val artworkHeightFraction = (artworkHeightPercent.coerceIn(70, 100) + hiddenArtworkRows * 3).coerceAtMost(100) / 100f
    val classicArtworkSize = screenConfiguration.screenWidthDp.dp * artworkWidthFraction
    val classicArtworkHeight = screenConfiguration.screenWidthDp.dp * artworkHeightFraction
    // Match the title/artist and seek bar edges to the unchanged artwork width.
    val classicContentHorizontalPadding = classicHorizontalPadding + 8.dp
    // Give the cover more of the vertical budget on compact phones. The artwork
    // remains a true square and its horizontal edges stay aligned with metadata.
    val artworkToMetadataGap = if (compactClassicLayout) 6.dp else 12.dp
    val metadataToLyricsGap = if (compactClassicLayout) 14.dp else 18.dp
    val controlsGap = if (compactClassicLayout) 6.dp else 10.dp
    var classicPendingSeekPosition by remember { mutableStateOf<Long?>(null) }

    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val isLiked = currentSong?.song?.liked == true
    val download by LocalDownloadUtil.current.getDownload(mediaMetadata?.id ?: "")
        .collectAsState(initial = null)
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    var showClassicDetailsDialog by rememberSaveable { mutableStateOf(false) }
    var showClassicAudioPipelineDialog by rememberSaveable { mutableStateOf(false) }
    var showClassicDownloadQualityDialog by rememberSaveable { mutableStateOf(false) }
    var classicDownloadQuality by rememberEnumPreference(DownloadQualityKey, DownloadQuality.HIGH)
    val classicCoroutineScope = rememberCoroutineScope()
    val shuffleModeEnabled = playerConnection.player.shuffleModeEnabled
    val repeatMode = playerConnection.player.repeatMode

    // Keep the Classic Player download action identical to the quality flow in PlayerMenu.
    // The selected quality is persisted through the same independent download-quality key.
    if (showClassicDownloadQualityDialog) {
        DownloadQualityDialog(
            selectedQuality = classicDownloadQuality,
            onQualitySelected = { classicDownloadQuality = it },
            onDismiss = { showClassicDownloadQualityDialog = false },
            onConfirm = {
                val metadata = mediaMetadata
                showClassicDownloadQualityDialog = false
                if (metadata != null) {
                    classicCoroutineScope.launch {
                        context.dataStore.edit {
                            it[DownloadQualityKey] = classicDownloadQuality.name
                        }
                        database.transaction { insert(metadata) }
                        val request = DownloadRequest
                            .Builder(metadata.id, metadata.id.toUri())
                            .setCustomCacheKey(metadata.id)
                            .setData(metadata.title.toByteArray())
                            .build()
                        DownloadService.sendAddDownload(
                            context,
                            ExoDownloadService::class.java,
                            request,
                            false,
                        )
                    }
                }
            },
        )
    }

    val accent by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(600),
        label = "classicAccent"
    )

    // The Classic player is always a dark gradient. In light mode the system status-bar icons are
    // dark, so they vanish into it. Force light icons while the player is open, then restore.
    val classicView = androidx.compose.ui.platform.LocalView.current
    val classicSheetOpen by remember(bottomSheetState) {
        androidx.compose.runtime.derivedStateOf { bottomSheetState.progress > 0.5f }
    }
    androidx.compose.runtime.DisposableEffect(classicSheetOpen, classicView) {
        var ctx = classicView.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (ctx as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, classicView) }
        val original = controller?.isAppearanceLightStatusBars
        if (classicSheetOpen && controller != null) controller.isAppearanceLightStatusBars = false
        onDispose {
            if (controller != null && original != null) controller.isAppearanceLightStatusBars = original
        }
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerValue by remember { mutableFloatStateOf(30f) }

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd
        ) {
            playerConnection.service.sleepTimer.isActive
        }
    var sleepTimerTimeLeft by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.bedtime), contentDescription = null) },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(onClick = {
                    showSleepTimerDialog = false
                    playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(R.plurals.minute, sleepTimerValue.roundToInt(), sleepTimerValue.roundToInt()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )
                    OutlinedButton(onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(-1)
                    }) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            },
        )
    }

    if (showClassicDetailsDialog) {
        SongDetailsDialog(
            mediaMetadata = mediaMetadata,
            onDismiss = { showClassicDetailsDialog = false },
        )
    }

    if (showClassicAudioPipelineDialog) {
        AudioPipelineDialog(
            currentFormat = currentFormat,
            mediaMetadata = mediaMetadata,
            onDismiss = { showClassicAudioPipelineDialog = false },
        )
    }


    Box(modifier = Modifier.fillMaxSize()) {
        // Classic keeps its normal layout. The cover is only a soft background layer;
        // it never becomes the foreground/fullscreen artwork and never creates top/bottom bands.
        mediaMetadata?.thumbnailUrl?.let { artworkUrl ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl.highQualityThumbnail())
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(42.dp)
                    .alpha(0.34f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            lerp(Color.Black, accent, 0.48f).copy(alpha = 0.52f),
                            lerp(Color.Black, accent, 0.22f).copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.68f),
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(bottom = queueSheetState.collapsedBound)
                .bottomSheetDraggable(bottomSheetState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Keep the artwork as the normal Classic thumbnail. The background image
            // above is decorative only and never replaces this foreground artwork.
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                // Preserve the existing artwork size whenever it fits, but constrain it
                // to the actual available area on shorter or narrower devices.
                val responsiveArtworkSize = minOf(classicArtworkSize, maxWidth).coerceAtLeast(1.dp)
                val responsiveArtworkHeight = minOf(classicArtworkHeight, maxHeight).coerceAtLeast(1.dp)
                Thumbnail(
                    sliderPositionProvider = { sliderPosition },
                    onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                    shape = RoundedCornerShape(24.dp),
                    artworkScale = artworkZoom,
                    showPlayingFrom = showSource,
                    useCustomCornerRadius = true,
                    textColor = Color.White,
                    // Smaller inset than the shared default (32dp) so the
                    // square itself gets noticeably more room, consistently
                    // on every screen size, without dropping the "Playing
                    // from" lines above it.
                    artworkPadding = classicArtworkPadding,
                    artworkSize = responsiveArtworkSize,
                    artworkHeight = responsiveArtworkHeight,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Keep metadata (title, artist, like button) clearly below the artwork.
            // This fixed gap also reserves space when Thumbnail fills its weighted area.
            // Density-independent dp, so this reads the same physical size on any phone.
            Spacer(Modifier.height(artworkToMetadataGap))

            Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = classicContentHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Start the title block exactly at the thumbnail's left edge (Spotify-style).
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = mediaMetadata?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE)
                            .clickable(enabled = mediaMetadata?.album != null, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                mediaMetadata?.album?.let { navController.navigate("album/${it.id}") }
                                bottomSheetState.collapseSoft()
                            }
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            playerConnection.toggleLike()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (showLyricsLine) {
                // Visible breathing room below the title/artist row before lyrics.
                Spacer(Modifier.height(metadataToLyricsGap))

                ClassicLyricLineStrip(
                    mediaMetadata = mediaMetadata,
                    position = position,
                    duration = (mediaMetadata?.duration?.toLong() ?: 0L) * 1000L,
                    sliderPosition = sliderPosition,
                    isPlaying = isPlaying,
                    onClick = onOpenFullscreenLyrics,
                    modifier = Modifier.fillMaxWidth(),
                )

            }

            // The lyric strip is bottom-aligned; start the seek bar immediately
            // after it so the visible track sits close to the lyric line.
            Spacer(Modifier.height(0.dp))

            val classicSliderColors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                activeTickColor = Color.White,
                inactiveTickColor = Color.White.copy(alpha = 0.35f),
                disabledThumbColor = Color.White.copy(alpha = 0.35f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.35f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f),
            )
            ClassicSeekBar(
                progress = (sliderPosition ?: position).toFloat() / duration.coerceAtLeast(1L).toFloat(),
                style = sliderStyle,
                isPlaying = isPlaying,
                colors = classicSliderColors,
                onSeek = { fraction ->
                    val newPosition = (fraction.coerceIn(0f, 1f) * duration.coerceAtLeast(0L)).toLong()
                    classicPendingSeekPosition = newPosition
                    onSliderPositionChange(newPosition)
                },
                onSeekFinished = {
                    (classicPendingSeekPosition ?: sliderPosition)?.let { target ->
                        playerConnection.player.seekTo(target.coerceIn(0L, duration.coerceAtLeast(0L)))
                    }
                    classicPendingSeekPosition = null
                    onSliderPositionChange(null)
                },
                // The Slider/SquigglySlider primitives reserve generous empty
                // space above and below their track for touch target size
                // (~44–48dp), which is what made the lyric line read as
                // floating between the title row and the seek bar instead of
                // sitting right above it. Tightening the height compresses
                // that invisible padding symmetrically — the track/thumb
                // colors, shape and interaction logic inside ClassicSeekBar
                // are untouched, so this can't conflict with any slider style.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    makeTimeString(sliderPosition ?: position),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                if (showQuality) {
                    AudioQualityTag(
                        currentFormat = currentFormat,
                        mediaMetadata = mediaMetadata,
                        tint = Color.White,
                        onClick = { showClassicAudioPipelineDialog = true },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Text(
                    makeTimeString(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Spacer(Modifier.height(controlsGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    tint = if (shuffleModeEnabled) accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled }
                )
                Icon(
                    painter = painterResource(R.drawable.classic_skip_previous),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (canSkipPrevious) 1f else 0.35f),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(enabled = canSkipPrevious, interactionSource = remember { MutableInteractionSource() }, indication = null) { playerConnection.player.seekToPrevious() }
                )
                Box(
                    modifier = Modifier
                        .size(if (compactClassicLayout) 68.dp else 72.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { playerConnection.player.togglePlayPause() },
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.classic_pause else R.drawable.classic_play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.classic_skip_next),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (canSkipNext) 1f else 0.35f),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(enabled = canSkipNext, interactionSource = remember { MutableInteractionSource() }, indication = null) { playerConnection.player.seekToNext() }
                )
                Icon(
                    painter = painterResource(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                            else -> R.drawable.repeat
                        }
                    ),
                    contentDescription = null,
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            playerConnection.player.repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                )
            }

            Spacer(Modifier.height(if (compactClassicLayout) 8.dp else 12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compactClassicLayout) 6.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpenFullscreenLyrics() }
                )
                if (sleepTimerEnabled) {
                    Text(
                        text = makeTimeString(sleepTimerTimeLeft),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = playerConnection.service.sleepTimer::clear)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.bedtime),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showSleepTimerDialog = true }
                    )
                }
                val classicDownloadScale by animateFloatAsState(
                    targetValue = if (download?.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING) 0.88f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "classicDownloadScale",
                )
                val classicDownloadAlpha by animateFloatAsState(
                    targetValue = if (download?.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING) 0.78f else 1f,
                    animationSpec = tween(220),
                    label = "classicDownloadAlpha",
                )
                Icon(
                    painter = painterResource(
                        when (download?.state) {
                            androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> R.drawable.offline
                            androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> R.drawable.pause
                            androidx.media3.exoplayer.offline.Download.STATE_FAILED -> R.drawable.error
                            else -> R.drawable.download
                        }
                    ),
                    contentDescription = stringResource(R.string.download),
                    tint = if (download?.state == androidx.media3.exoplayer.offline.Download.STATE_FAILED) MaterialTheme.colorScheme.error else Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(classicDownloadScale)
                        .alpha(classicDownloadAlpha)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            mediaMetadata?.let { metadata ->
                                if (download?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                                    androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
                                        context, com.devxrohan.smusic.playback.ExoDownloadService::class.java, metadata.id, false,
                                    )
                                } else if (download?.state != androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING) {
                                    showClassicDownloadQualityDialog = true
                                }
                            }
                        }
                )
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { queueSheetState.expandSoft() }
                )
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            currentSong?.let { song ->
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = song.toMediaMetadata(),
                                        navController = navController,
                                        playerBottomSheetState = bottomSheetState,
                                        onShowDetailsDialog = { showClassicDetailsDialog = true },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
        }
    }
}
