@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import com.devxrohan.smusic.utils.writeBackWithRetry
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.lerp
import androidx.compose.animation.animateColorAsState
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalDownloadUtil
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.DarkModeKey
import com.devxrohan.smusic.constants.DefaultPlayPauseButtonShape
import com.devxrohan.smusic.constants.DefaultSmallButtonsShape
import com.devxrohan.smusic.constants.PlayPauseButtonShapeKey
import com.devxrohan.smusic.constants.PlayerBackgroundStyle
import com.devxrohan.smusic.constants.PlayerButtonsStyle
import com.devxrohan.smusic.constants.PlayerButtonsStyleKey
import com.devxrohan.smusic.constants.PlayerHorizontalPadding
import com.devxrohan.smusic.constants.PlayerScreenStyle
import com.devxrohan.smusic.constants.PlayerScreenStyleKey
import com.devxrohan.smusic.constants.QueuePeekHeight
import com.devxrohan.smusic.constants.ShowLyricsKey
import com.devxrohan.smusic.constants.SliderStyle
import com.devxrohan.smusic.constants.SliderStyleKey
import com.devxrohan.smusic.constants.SmallButtonsShapeKey
import com.devxrohan.smusic.extensions.togglePlayPause
import com.devxrohan.smusic.extensions.toggleRepeatMode
import com.devxrohan.smusic.db.entities.LyricsEntity
import com.devxrohan.smusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.playback.ExoDownloadService
import com.devxrohan.smusic.ui.component.SongDetailsDialog
import com.devxrohan.smusic.ui.component.BottomSheet
import com.devxrohan.smusic.ui.component.BottomSheetState
import com.devxrohan.smusic.ui.component.bottomSheetDraggable
import com.devxrohan.smusic.ui.component.LocalMenuState
import com.devxrohan.smusic.ui.component.LocalBottomSheetPageState
import com.devxrohan.smusic.ui.component.PlayerSliderTrack
import com.devxrohan.smusic.ui.component.ResizableIconButton
import com.devxrohan.smusic.ui.component.rememberBottomSheetState
import com.devxrohan.smusic.ui.menu.PlayerMenu
import com.devxrohan.smusic.ui.menu.AddToPlaylistDialog
import com.devxrohan.smusic.innertube.YouTube
import androidx.compose.runtime.rememberCoroutineScope
import com.devxrohan.smusic.ui.screens.settings.DarkMode
import com.devxrohan.smusic.ui.theme.PlayerColorExtractor
import com.devxrohan.smusic.ui.theme.PlayerSliderColors
import com.devxrohan.smusic.ui.theme.extractGradientColors
import com.devxrohan.smusic.utils.getPlayPauseShape
import com.devxrohan.smusic.utils.getSmallButtonShape
import com.devxrohan.smusic.utils.makeTimeString
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.ui.utils.highQualityThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.saket.squiggles.SquigglySlider

internal val SpotifyFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    onOpenFullscreenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    docked: Boolean = false,
    collapsedOffsetY: () -> androidx.compose.ui.unit.Dp = { 0.dp },
    dockedBottomBleed: () -> Float = { 0f },
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val clipboardManager = LocalClipboardManager.current

    var showFullscreenLyrics by remember { mutableStateOf(false) }
    val playerConnection = LocalPlayerConnection.current ?: return

    val playerScreenStyle by rememberEnumPreference<PlayerScreenStyle>(
        PlayerScreenStyleKey,
        PlayerScreenStyle.CLASSIC
    )

    val playerBackground = PlayerBackgroundStyle.DEFAULT

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val pureBlack = false
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }
    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }
    val useBlackBackground =
        remember(isSystemInDarkTheme, darkTheme, pureBlack) {
            val useDarkTheme =
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            useDarkTheme && pureBlack
        }
    val backgroundColor = if (useBlackBackground && state.value > state.collapsedBound) {
        lerp(MaterialTheme.colorScheme.surfaceContainer, Color.Black, state.progress)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    // Audio format is consumed by multiple player styles in this parent scope.
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val primaryArtistId = mediaMetadata?.artists?.firstOrNull { it.id != null }?.id
    val primaryArtist by remember(primaryArtistId) {
        primaryArtistId?.let(database::artist) ?: flowOf(null)
    }.collectAsState(initial = null)
    val spotifyArtistThumbnailUrl =
        primaryArtist?.thumbnailUrl ?: currentSong?.artists?.firstOrNull()?.thumbnailUrl
    var spotifyLyricsEntity by remember { mutableStateOf<LyricsEntity?>(null) }
    val automix by playerConnection.service.automixItems.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val playerVolume = playerConnection.service.playerVolume.collectAsState()

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex = playerConnection.player.currentMediaItemIndex
    val nextMediaMetadata = remember(queueWindows, currentWindowIndex) {
        if (currentWindowIndex + 1 < queueWindows.size) {
            val nextItem = queueWindows[currentWindowIndex + 1].mediaItem
            // Try to extract our domain MediaMetadata. If not possible, we could at least map the artwork.
            // SMusic maps LocalMediaItem data to its MediaMetadata using extensions or we can just fetch it.
            // We just need the thumbnail URL. For simplicity, we can get it from the DB or assume it's attached.
            com.devxrohan.smusic.models.MediaMetadata(
                id = nextItem.mediaId,
                title = nextItem.mediaMetadata.title?.toString() ?: "",
                artists = emptyList(),
                duration = 0,
                thumbnailUrl = nextItem.mediaMetadata.artworkUri?.toString()
            )
        } else null
    }

    val showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.SLIM)

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }

    var changeColor by remember {
        mutableStateOf(false)
    }

    // Animations for background effects
    var backgroundImageUrl by remember { mutableStateOf<String?>(null) }
    val blurRadius by animateDpAsState(
        targetValue = if (state.isExpanded && playerBackground == PlayerBackgroundStyle.BLUR) 150.dp else 0.dp,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "blurRadius"
    )

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (state.isExpanded && playerBackground != PlayerBackgroundStyle.DEFAULT) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "backgroundAlpha"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = when {
            !state.isExpanded -> 0f
            playerBackground == PlayerBackgroundStyle.BLUR -> 0.3f
            playerBackground == PlayerBackgroundStyle.GRADIENT && gradientColors.size >= 2 -> 0.2f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "overlayAlpha"
    )

    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )

    if (!canSkipNext && automix.isNotEmpty()) {
        playerConnection.service.addToQueueAutomix(automix[0], 0)
    }

    // Observe service-owned lyrics to prevent independent player-style fetches racing each other.
    LaunchedEffect(playerScreenStyle, mediaMetadata?.id, currentLyrics) {
        val metadata = mediaMetadata
        if ((playerScreenStyle != PlayerScreenStyle.SPOTIFY && playerScreenStyle != PlayerScreenStyle.GALAXY) || metadata == null) {
            spotifyLyricsEntity = null
            return@LaunchedEffect
        }
        spotifyLyricsEntity = currentLyrics
        if (currentLyrics?.lyrics.isNullOrBlank() || currentLyrics?.lyrics == LYRICS_NOT_FOUND) {
            runCatching { playerConnection.service.ensureLyricsFetched(metadata) }
        }
    }

    // Obtener el color del tema antes de LaunchedEffect
    val surfaceColor = MaterialTheme.colorScheme.surface
    val fallbackColorArgb = surfaceColor.toArgb()

    LaunchedEffect(mediaMetadata, playerBackground, fallbackColorArgb) {
        // Update image URL for smooth transitions
        backgroundImageUrl = mediaMetadata?.thumbnailUrl?.highQualityThumbnail()

        if (useBlackBackground && playerBackground != PlayerBackgroundStyle.BLUR) {
            gradientColors = listOf(Color.Black, Color.Black)
        }
        if (useBlackBackground && playerBackground != PlayerBackgroundStyle.GRADIENT) {
            gradientColors = listOf(Color.Black, Color.Black)
        } else if (playerBackground == PlayerBackgroundStyle.GRADIENT) {
            withContext(Dispatchers.IO) {
                val result = runCatching {
                    context.imageLoader
                        .execute(
                            ImageRequest
                                .Builder(context)
                                .data(mediaMetadata?.thumbnailUrl?.highQualityThumbnail())
                                .allowHardware(false)
                                .build(),
                        ).drawable as? BitmapDrawable
                }.getOrNull()

                result?.bitmap?.let { rawBitmap ->
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && rawBitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                        rawBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: rawBitmap
                    } else rawBitmap
                    val palette = Palette.from(bitmap)
                        .maximumColorCount(8)
                        .resizeBitmapArea(100 * 100)
                        .generate()

                    val extractedColors = PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColorArgb
                    )

                    withContext(Dispatchers.Main) {
                        gradientColors = extractedColors
                    }
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    var spotifyColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }

    LaunchedEffect(mediaMetadata, fallbackColorArgb) {
        val metadata = mediaMetadata ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val result = runCatching {
                context.imageLoader
                    .execute(
                        ImageRequest
                            .Builder(context)
                            .data(metadata.thumbnailUrl?.highQualityThumbnail())
                            .allowHardware(false)
                            .build(),
                    ).drawable as? BitmapDrawable
            }.getOrNull()

            result?.bitmap?.let { rawBitmap ->
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && rawBitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                    rawBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: rawBitmap
                } else rawBitmap
                val palette = Palette.from(bitmap)
                    .maximumColorCount(8)
                    .resizeBitmapArea(100 * 100)
                    .generate()

                val extractedColors = PlayerColorExtractor.extractGradientColors(
                    palette = palette,
                    fallbackColor = fallbackColorArgb
                )

                withContext(Dispatchers.Main) {
                    spotifyColors = extractedColors
                }
            }
        }
    }

    val spotifySurfaceColor = spotifyColors.firstOrNull() ?: Color(0xFF311000)
    val spotifyHeaderColor = spotifyColors.getOrNull(1) ?: Color(0xFF260300)

    val TextBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
            PlayerBackgroundStyle.BLUR -> Color.White
            else -> {
                val whiteContrast =
                    if (gradientColors.size >= 2) {
                        ColorUtils.calculateContrast(
                            gradientColors.first().toArgb(),
                            Color.White.toArgb(),
                        )
                    } else {
                        2.0
                    }
                val blackContrast: Double =
                    if (gradientColors.size >= 2) {
                        ColorUtils.calculateContrast(
                            gradientColors.last().toArgb(),
                            Color.Black.toArgb(),
                        )
                    } else {
                        2.0
                    }
                if (gradientColors.size >= 2 &&
                    whiteContrast < 2f &&
                    blackContrast > 2f
                ) {
                    changeColor = true
                    Color.Black
                } else if (whiteContrast > 2f && blackContrast < 2f) {
                    changeColor = true
                    Color.White
                } else {
                    changeColor = false
                    Color.White
                }
            }
        }

    val icBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
            PlayerBackgroundStyle.BLUR -> Color.Black
            else -> {
                val whiteContrast =
                    if (gradientColors.size >= 2) {
                        ColorUtils.calculateContrast(
                            gradientColors.first().toArgb(),
                            Color.White.toArgb(),
                        )
                    } else {
                        2.0
                    }
                val blackContrast: Double =
                    if (gradientColors.size >= 2) {
                        ColorUtils.calculateContrast(
                            gradientColors.last().toArgb(),
                            Color.Black.toArgb(),
                        )
                    } else {
                        2.0
                    }
                if (gradientColors.size >= 2 &&
                    whiteContrast < 2f &&
                    blackContrast > 2f
                ) {
                    changeColor = true
                    Color.White
                } else if (whiteContrast > 2f && blackContrast < 2f) {
                    changeColor = true
                    Color.Black
                } else {
                    changeColor = false
                    Color.Black
                }
            }
        }

    // Sistema de color scheme con PRIMARY y TERTIARY colors
    val (textButtonColor, iconButtonColor) = when (playerButtonsStyle) {
        PlayerButtonsStyle.DEFAULT ->
            if (useDarkTheme) Pair(Color.White, Color.Black)
            else Pair(Color.Black, Color.White)
        PlayerButtonsStyle.PRIMARY -> Pair(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary
        )
        PlayerButtonsStyle.TERTIARY -> Pair(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary
        )
    }

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata?.id ?: "")
        .collectAsState(initial = null)

    val sleepTimerEnabled = playerConnection.service.sleepTimer.isActive
    val sleepTimerTimeLeft = rememberSleepTimerTimeLeft(playerConnection.service.sleepTimer)

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            timer = playerConnection.service.sleepTimer,
            onDismiss = { showSleepTimerDialog = false },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val smallButtonsShapeState = rememberPreference(
        key = SmallButtonsShapeKey,
        defaultValue = DefaultSmallButtonsShape
    )

    val smallButtonShape = remember(smallButtonsShapeState.value) {
        getSmallButtonShape(smallButtonsShapeState.value)
    }

    val playPauseShapeState = rememberPreference(
        key = PlayPauseButtonShapeKey,
        defaultValue = DefaultPlayPauseButtonShape
    )

    val playPauseShape = remember(playPauseShapeState.value) {
        getPlayPauseShape(playPauseShapeState.value)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "play_pause_rotation")
    val playPauseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 9000, // 9 seconds for a full rotation
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Forma dinámica: cuando está reproduciendo usa la forma seleccionada
    // Cuando está en pausa usa Square
    val currentPlayPauseShape = remember(isPlaying, playPauseShape) {
        if (isPlaying) {
            playPauseShape
        } else {
            MaterialShapes.Square
        }
    }

    // Function to create the modifier for small buttons
    val smallButtonModifier = @Composable {
        Modifier
            .size(42.dp)
            .clip(smallButtonShape.toShape())
            .background(textButtonColor)
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    var showDetailsDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showDetailsDialog) {
        SongDetailsDialog(
            mediaMetadata = mediaMetadata,
            onDismiss = { showDetailsDialog = false },
            containerColor = if (useBlackBackground) Color.Black else AlertDialogDefaults.containerColor,
        )
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            mediaMetadata?.let { metadata ->
                database.transaction {
                    insert(metadata)
                }
                coroutineScope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { writeBackWithRetry { YouTube.addToPlaylist(it, metadata.id) } }
                }
                listOf(metadata.id)
            } ?: emptyList()
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )

    // Only Classic exposes a collapsed queue strip that can be swiped upward.
    // Every other player style opens its queue exclusively through its own queue button;
    // keeping both bounds at zero removes the hidden sheet's gesture/peek hit target.
    val classicQueueSwipeEnabled = playerScreenStyle == PlayerScreenStyle.CLASSIC
    val queuePeekBound = if (classicQueueSwipeEnabled) {
        QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }
    val queueSheetState =
        rememberBottomSheetState(
            dismissedBound = queuePeekBound,
            collapsedBound = queuePeekBound,
            expandedBound = state.expandedBound,
        )

    val bottomSheetBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT ->
            MaterialTheme.colorScheme.surfaceContainer
        else ->
            if (useBlackBackground) Color.Black
            else MaterialTheme.colorScheme.surfaceContainer
    }

    BottomSheet(
        state = state,
        modifier = modifier,
        shape = RoundedCornerShape(
            topStart = if (!state.isExpanded) 16.dp else 0.dp,
            topEnd = if (!state.isExpanded) 16.dp else 0.dp
        ),
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bottomSheetBackgroundColor)
            ) {
                when (playerBackground) {
                    PlayerBackgroundStyle.BLUR -> {
                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(480)).togetherWith(fadeOut(tween(480)))
                            },
                            label = "blurBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(modifier = Modifier.alpha(backgroundAlpha)) {
                                    AsyncImage(
                                        placeholder = painterResource(R.drawable.music_note),
                                        error = painterResource(R.drawable.music_note),
                                        model = ImageRequest.Builder(context)
                                            .data(thumbnailUrl.highQualityThumbnail())
                                            .allowHardware(false)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .blur(if (useDarkTheme) 150.dp else 100.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.GRADIENT -> {
                        AnimatedContent(
                            targetState = gradientColors,
                            transitionSpec = {
                                fadeIn(tween(480)).togetherWith(fadeOut(tween(480)))
                            },
                            label = "gradientBackground"
                        ) { colors ->
                            if (colors.isNotEmpty()) {
                                val gradientColorStops = if (colors.size >= 3) {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.5f to colors[1],
                                        1.0f to colors[2]
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.6f to colors[0].copy(alpha = 0.7f),
                                        1.0f to Color.Black
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .alpha(backgroundAlpha)
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops))
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                    else -> {
                        PlayerBackgroundStyle.DEFAULT
                    }
                }
            }
        },
        onDismiss = {
            playerConnection.service.clearAutomix()
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContentOffsetY = collapsedOffsetY,
        collapsedContent = {
            MiniPlayer(
                position = position,
                duration = duration,
                state = state,
                docked = docked,
                dockedBottomBleed = dockedBottomBleed,
            )
        },
    ) {
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            var prevBounce by remember { mutableIntStateOf(0) }
            var nextBounce by remember { mutableIntStateOf(0) }
            var playBounce by remember { mutableIntStateOf(0) }
            var shuffleBounce by remember { mutableIntStateOf(0) }
            var likeBounce by remember { mutableIntStateOf(0) }
            var repeatBounce by remember { mutableIntStateOf(0) }

            val prevScale by animateFloatAsState(if (prevBounce % 2 == 1) 1.22f else 1f, spring(), label = "prevBounce")
            val nextScale by animateFloatAsState(if (nextBounce % 2 == 1) 1.22f else 1f, spring(), label = "nextBounce")
            val playScale by animateFloatAsState(if (playBounce % 2 == 1) 1.15f else 1f, spring(), label = "playBounce")
            val shuffleScale by animateFloatAsState(if (shuffleBounce % 2 == 1) 1.20f else 1f, spring(), label = "shuffleBounce")
            val likeScale by animateFloatAsState(if (likeBounce % 2 == 1) 1.20f else 1f, spring(), label = "likeBounce")
            val repeatScale by animateFloatAsState(if (repeatBounce % 2 == 1) 1.20f else 1f, spring(), label = "repeatBounce")

            val playPauseRoundness by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 36.dp,
                animationSpec = tween(durationMillis = 90, easing = LinearEasing),
                label = "playPauseRoundness",
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                AnimatedContent(
                    targetState = mediaMetadata.title,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "",
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onBackgroundColor,
                        modifier =
                            Modifier
                                .clickable(enabled = mediaMetadata.album != null) {
                                    navController.navigate("album/${mediaMetadata.album!!.id}")
                                    state.collapseSoft()
                                },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                mediaMetadata.artists.fastForEachIndexed { index, artist ->
                    AnimatedContent(
                        targetState = artist.name,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "",
                    ) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            color = onBackgroundColor,
                            maxLines = 1,
                            modifier =
                                Modifier.clickable(enabled = artist.id != null) {
                                    navController.navigate("artist/${artist.id}")
                                    state.collapseSoft()
                                },
                        )
                    }

                    if (index != mediaMetadata.artists.lastIndex) {
                        AnimatedContent(
                            targetState = ", ",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "",
                        ) { comma ->
                            Text(
                                text = comma,
                                style = MaterialTheme.typography.titleMedium,
                                color = onBackgroundColor,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                Box(
                    modifier = smallButtonModifier()
                        .clickable {
                            playerConnection.service.startRadioSeamlessly()
                        },
                ) {
                    Image(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconButtonColor),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                Box(
                    modifier = smallButtonModifier()
                        .clickable {
                            if (download?.state == Download.STATE_COMPLETED) {
                                DownloadService.sendRemoveDownload(
                                    context,
                                    ExoDownloadService::class.java,
                                    mediaMetadata.id,
                                    false,
                                )
                            } else {
                                database.transaction {
                                    insert(mediaMetadata)
                                }
                                val downloadRequest =
                                    DownloadRequest
                                        .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                        .setCustomCacheKey(mediaMetadata.id)
                                        .setData(mediaMetadata.title.toByteArray())
                                        .build()
                                DownloadService.sendAddDownload(
                                    context,
                                    ExoDownloadService::class.java,
                                    downloadRequest,
                                    false,
                                )
                            }
                        },
                ) {
                    if (download?.state == Download.STATE_DOWNLOADING) {
                        val progress = download!!.percentDownloaded / 100f
                        val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = FastOutSlowInEasing
                            ),
                            label = "downloadProgress"
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                        ) {
                            val strokeWidth = 3.dp.toPx()
                            val radius = (size.minDimension - strokeWidth) / 2
                            val center = Offset(size.width / 2, size.height / 2)

                            // Background circle (gray)
                            drawCircle(
                                color = iconButtonColor.copy(alpha = 0.3f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokeWidth)
                            )

                            // Progress circle
                            if (animatedProgress > 0f) {
                                drawArc(
                                    color = iconButtonColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    topLeft = Offset(
                                        center.x - radius,
                                        center.y - radius
                                    ),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }

                    val iconResource = when (download?.state) {
                        Download.STATE_COMPLETED -> R.drawable.offline
                        Download.STATE_DOWNLOADING -> R.drawable.pause
                        Download.STATE_FAILED -> R.drawable.error
                        else -> R.drawable.download
                    }

                    val iconAlpha by animateFloatAsState(
                        targetValue = if (download?.state == Download.STATE_DOWNLOADING) 0.8f else 1f,
                        animationSpec = tween(durationMillis = 200),
                        label = "iconAlpha"
                    )

                    val iconScale by animateFloatAsState(
                        targetValue = if (download?.state == Download.STATE_DOWNLOADING) 0.9f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "iconScale"
                    )

                    Image(
                        painter = painterResource(iconResource),
                        contentDescription = when (download?.state) {
                            Download.STATE_COMPLETED -> stringResource(R.string.download)
                            Download.STATE_DOWNLOADING -> stringResource(R.string.downloading)
                            Download.STATE_FAILED -> stringResource(R.string.download_errorup)
                            else -> stringResource(R.string.download)
                        },
                        colorFilter = ColorFilter.tint(
                            when (download?.state) {
                                Download.STATE_FAILED -> MaterialTheme.colorScheme.error
                                else -> iconButtonColor
                            }
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .scale(iconScale)
                            .alpha(iconAlpha),
                    )

                    // Small progress text
                    if (download?.state == Download.STATE_DOWNLOADING) {
                        val progress = (download!!.percentDownloaded).toInt()
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.labelSmall,
                            color = iconButtonColor,
                            fontSize = 8.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = (-2).dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.size(12.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(smallButtonShape.toShape())
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    AnimatedContent(
                        label = "sleepTimer",
                        targetState = sleepTimerEnabled,
                    ) { sleepTimerEnabled ->
                        if (sleepTimerEnabled) {
                            Text(
                                text = sleepTimerLabel(playerConnection.service.sleepTimer, sleepTimerTimeLeft),
                                style = MaterialTheme.typography.labelLarge,
                                color = onBackgroundColor,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = playerConnection.service.sleepTimer::clear)
                                ,
                            )
                        } else {
                            Box(
                                modifier = smallButtonModifier()
                                    .clickable {
                                        showSleepTimerDialog = true
                                    },
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.bedtime),
                                    colorFilter = ColorFilter.tint(iconButtonColor),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(24.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = smallButtonModifier()
                        .clickable {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = { showDetailsDialog = true },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                ) {
                    Image(
                        painter = painterResource(R.drawable.more_horiz),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconButtonColor),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            when (sliderStyle) {
                SliderStyle.DEFAULT -> {
                    Slider(
                        value = (sliderPosition ?: position).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = {
                            sliderPosition = it.toLong()
                        },
                        onValueChangeFinished = {
                            sliderPosition?.let {
                                playerConnection.player.seekTo(it)
                                position = it
                            }
                            sliderPosition = null
                        },
                        colors = PlayerSliderColors.getSliderColors(
                            textButtonColor = TextBackgroundColor,
                            playerBackground = playerBackground,
                            useDarkTheme = useDarkTheme
                        ),
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    )
                }

                SliderStyle.SQUIGGLY -> {
                    SquigglySlider(
                        value = (sliderPosition ?: position).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = {
                            sliderPosition = it.toLong()
                        },
                        onValueChangeFinished = {
                            sliderPosition?.let {
                                playerConnection.player.seekTo(it)
                                position = it
                            }
                            sliderPosition = null
                        },
                        colors = PlayerSliderColors.getSliderColors(
                            textButtonColor = TextBackgroundColor,
                            playerBackground = playerBackground,
                            useDarkTheme = useDarkTheme
                        ),
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                        squigglesSpec =
                            SquigglySlider.SquigglesSpec(
                                amplitude = if (isPlaying) (2.dp).coerceAtLeast(2.dp) else 0.dp,
                                strokeWidth = 3.dp,
                            ),
                    )
                }

                SliderStyle.SLIM -> {
                    Slider(
                        value = (sliderPosition ?: position).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = {
                            sliderPosition = it.toLong()
                        },
                        onValueChangeFinished = {
                            sliderPosition?.let {
                                playerConnection.player.seekTo(it)
                                position = it
                            }
                            sliderPosition = null
                        },
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = PlayerSliderColors.getSliderColors(
                                    textButtonColor = TextBackgroundColor,
                                    playerBackground = playerBackground,
                                    useDarkTheme = useDarkTheme
                                )
                            )
                        },
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding + 4.dp),
            ) {
                Text(
                    text = makeTimeString(sliderPosition ?: position),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = R.drawable.skip_previous,
                        enabled = canSkipPrevious,
                        color = TextBackgroundColor,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .scale(prevScale)
                                .align(Alignment.Center),
                        onClick = { prevBounce++; playerConnection.seekToPrevious() },
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier =
                        Modifier
                            .size(85.dp)
                            .scale(playScale)
                            .rotate(if (isPlaying) playPauseRotation else 0f)
                            .clip(currentPlayPauseShape.toShape())
                            .background(textButtonColor)
                            .clickable {
                                playBounce++
                                if (playbackState == STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                ) {
                    Image(
                        painter =
                            painterResource(
                                if (playbackState ==
                                    STATE_ENDED
                                ) {
                                    R.drawable.replay
                                } else if (isPlaying) {
                                    R.drawable.pause
                                } else {
                                    R.drawable.play
                                }
                            ),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconButtonColor),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                                .rotate(if (isPlaying) -playPauseRotation else 0f),
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = R.drawable.skip_next,
                        enabled = canSkipNext,
                        color = TextBackgroundColor,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .scale(nextScale)
                                .align(Alignment.Center),
                        onClick = { nextBounce++; playerConnection.seekToNext() },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                ResizableIconButton(
                    icon = if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                    color = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else TextBackgroundColor,
                    modifier = Modifier.size(32.dp).padding(4.dp).scale(shuffleScale),
                    onClick = {
                        shuffleBounce++
                        playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                    }
                )

                ResizableIconButton(
                    icon = if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                    color = TextBackgroundColor,
                    modifier = Modifier.size(32.dp).padding(4.dp).scale(likeScale),
                    onClick = { likeBounce++; playerConnection.toggleLike() },
                )

                ResizableIconButton(
                    icon = when (repeatMode) {
                        Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                        else -> throw IllegalStateException()
                    },
                    color = TextBackgroundColor,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(4.dp)
                        .scale(repeatScale)
                        .alpha(if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f),
                    onClick = {
                        repeatBounce++
                        playerConnection.player.toggleRepeatMode()
                    },
                )
            }
        }
        val immersiveControlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            val isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED
            val codecLabel = remember(currentFormat) {
                currentFormat?.mimeType
                    ?.substringAfter("/", missingDelimiterValue = "")
                    ?.uppercase()
                    ?.let { codec ->
                        when {
                            codec.contains("MP4A") -> "MP4"
                            codec.contains("AAC") -> "AAC"
                            codec.contains("OPUS") -> "OPUS"
                            codec.contains("VORBIS") -> "VORBIS"
                            codec.contains("FLAC") -> "LOSSLESS"
                            codec.isBlank() -> null
                            else -> codec
                        }
                    }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = mediaMetadata.title,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "modernTitle",
                        ) { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clickable(enabled = mediaMetadata.album != null) {
                                        navController.navigate("album/${mediaMetadata.album!!.id}")
                                        state.collapseSoft()
                                    },
                            )
                        }

                        AnimatedContent(
                            targetState = mediaMetadata.artists.joinToString { it.name },
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "modernArtists",
                        ) { artists ->
                            Text(
                                text = artists,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier,
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentSong?.song?.liked == true) Color.White.copy(alpha = 0.16f)
                                else Color.Transparent
                            )
                            .clickable(onClick = playerConnection::toggleLike)
                    ) {
                        Image(
                            painter = painterResource(
                                if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border
                            ),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(
                                if (currentSong?.song?.liked == true) Color.White else Color.White.copy(alpha = 0.75f)
                            ),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = mediaMetadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = { showDetailsDialog = true },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                    ) {
                        Image(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.75f)),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                when (sliderStyle) {
                    SliderStyle.DEFAULT -> {
                        Slider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            colors = PlayerSliderColors.getSliderColors(
                                textButtonColor = Color.White,
                                playerBackground = playerBackground,
                                useDarkTheme = true
                            )
                        )
                    }

                    SliderStyle.SQUIGGLY -> {
                        SquigglySlider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            colors = PlayerSliderColors.getSliderColors(
                                textButtonColor = Color.White,
                                playerBackground = playerBackground,
                                useDarkTheme = true
                            ),
                            squigglesSpec = SquigglySlider.SquigglesSpec(
                                amplitude = if (isPlaying) (2.dp).coerceAtLeast(2.dp) else 0.dp,
                                strokeWidth = 3.dp,
                            ),
                        )
                    }

                    SliderStyle.SLIM -> {
                        Slider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = {
                                sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                            track = { sliderState ->
                                PlayerSliderTrack(
                                    sliderState = sliderState,
                                    colors = PlayerSliderColors.getSliderColors(
                                        textButtonColor = Color.White,
                                        playerBackground = playerBackground,
                                        useDarkTheme = true
                                    )
                                )
                            }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = makeTimeString(sliderPosition ?: position),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                    codecLabel?.let { label ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.graphic_eq),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.78f)),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                )
                            }
                        }
                    }
                    Text(
                        text = if (duration != C.TIME_UNSET) "-${makeTimeString((duration - (sliderPosition ?: position)).coerceAtLeast(0L))}" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ResizableIconButton(
                        icon = R.drawable.skip_previous,
                        enabled = canSkipPrevious,
                        color = Color.White.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
                        modifier = Modifier.size(52.dp),
                        onClick = playerConnection::seekToPrevious,
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable {
                                if (playbackState == STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                    ) {
                        if (isLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(44.dp),
                            )
                        } else {
                            Image(
                                painter = painterResource(
                                    if (playbackState == STATE_ENDED) {
                                        R.drawable.replay
                                    } else if (isPlaying) {
                                        R.drawable.pause
                                    } else {
                                        R.drawable.play
                                    },
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(52.dp),
                            )
                        }
                    }

                    ResizableIconButton(
                        icon = R.drawable.skip_next,
                        enabled = canSkipNext,
                        color = Color.White.copy(alpha = if (canSkipNext) 1f else 0.4f),
                        modifier = Modifier.size(52.dp),
                        onClick = playerConnection::seekToNext,
                    )
                }
            }
        }

        val selectedControlsContent =
            if (playerScreenStyle == PlayerScreenStyle.MODERN) immersiveControlsContent else controlsContent

        // Animated background effects
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background with blurred image
            AnimatedVisibility(
                visible = playerBackground == PlayerBackgroundStyle.BLUR && backgroundImageUrl != null,
                enter = fadeIn(tween(360)),
                exit = fadeOut(tween(240))
            ) {
                AsyncImage(
                    model = backgroundImageUrl?.highQualityThumbnail(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurRadius)
                        .alpha(backgroundAlpha)
                )
            }

            // Animated gradient background
            AnimatedVisibility(
                visible = playerBackground == PlayerBackgroundStyle.GRADIENT && gradientColors.size >= 2,
                enter = fadeIn(tween(480)),
                exit = fadeOut(tween(360))
            ) {
                val animatedGradientColors = gradientColors.map { color ->
                    androidx.compose.animation.animateColorAsState(
                        targetValue = color,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "gradientColor"
                    ).value
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(backgroundAlpha)
                        .background(
                            Brush.verticalGradient(
                                colors = if (animatedGradientColors.isNotEmpty()) animatedGradientColors else gradientColors
                            )
                        )
                )
            }

            // Animated fluid background
            AnimatedVisibility(
                visible = playerBackground == PlayerBackgroundStyle.FLUID,
                enter = fadeIn(tween(480)),
                exit = fadeOut(tween(360))
            ) {
                FluidBackground(
                    modifier = Modifier.alpha(backgroundAlpha)
                )
            }

            // Animated dark overlay
            AnimatedVisibility(
                visible = overlayAlpha > 0f,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                )
            }

            // Additional overlay for lyrics
            if (playerBackground != PlayerBackgroundStyle.DEFAULT && showLyrics) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha = animateFloatAsState(
                                    targetValue = if (state.isExpanded) 0.4f else 0f,
                                    animationSpec = tween(durationMillis = 500),
                                    label = "lyricsOverlay"
                                ).value
                            )
                        )
                )
            }
        }

        if (playerScreenStyle == PlayerScreenStyle.PAPER) {
            var volume by remember { mutableFloatStateOf(playerConnection.player.volume) }

            PaperPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                repeatMode = repeatMode,
                shuffleModeEnabled = shuffleModeEnabled,
                volume = volume,
                onVolumeChange = {
                    volume = it
                    playerConnection.player.volume = it
                },
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let { playerConnection.player.seekTo(it) }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = playerConnection::seekToNext,
                onShuffle = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                onRepeat = { playerConnection.player.toggleRepeatMode() },
                onOpenLyrics = onOpenFullscreenLyrics,
                onOpenQueue = queueSheetState::expandSoft,
                onCollapse = state::collapseSoft,
                onOpenMenu = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        } else if (playerScreenStyle == PlayerScreenStyle.LIQUID) {
            LiquidPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                repeatMode = repeatMode,
                shuffleModeEnabled = shuffleModeEnabled,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let { playerConnection.player.seekTo(it) }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = playerConnection::seekToNext,
                onShuffle = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                onRepeat = { playerConnection.player.toggleRepeatMode() },
                onOpenLyrics = onOpenFullscreenLyrics,
                onOpenQueue = queueSheetState::expandSoft,
                onCollapse = state::collapseSoft,
                onOpenArtist = { artistId ->
                    navController.navigate("artist/$artistId")
                    state.collapseSoft()
                },
                onOpenMenu = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                currentLyrics = currentLyrics,
                database = database,
                gradientColors = gradientColors,
            )
        } else if (playerScreenStyle == PlayerScreenStyle.CLOUDGLOW) {
            CloudGlowPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                repeatMode = repeatMode,
                shuffleModeEnabled = shuffleModeEnabled,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let { playerConnection.player.seekTo(it) }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = playerConnection::seekToNext,
                onShuffle = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                onRepeat = { playerConnection.player.toggleRepeatMode() },
                onOpenLyrics = onOpenFullscreenLyrics,
                onOpenQueue = queueSheetState::expandSoft,
                onCollapse = state::collapseSoft,
                onOpenArtist = { artistId ->
                    navController.navigate("artist/$artistId")
                    state.collapseSoft()
                },
                onOpenMenu = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                currentLyrics = spotifyLyricsEntity,
                database = database,
                gradientColors = gradientColors,
            )
        } else if (playerScreenStyle == PlayerScreenStyle.FROST) {
            FrostPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                repeatMode = repeatMode,
                shuffleModeEnabled = shuffleModeEnabled,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let { playerConnection.player.seekTo(it) }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = playerConnection::seekToNext,
                onShuffle = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                onRepeat = { playerConnection.player.toggleRepeatMode() },
                onOpenLyrics = onOpenFullscreenLyrics,
                onOpenQueue = queueSheetState::expandSoft,
                onCollapse = state::collapseSoft,
                onOpenArtist = { artistId ->
                    navController.navigate("artist/$artistId")
                    state.collapseSoft()
                },
                onOpenMenu = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                currentLyrics = spotifyLyricsEntity,
                database = database,
                gradientColors = gradientColors,
            )
        } else if (playerScreenStyle == PlayerScreenStyle.FOLD) {
            FoldPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let { playerConnection.player.seekTo(it) }
                    sliderPosition = null
                },
                onPlayPause = {
                    if (playbackState == androidx.media3.common.Player.STATE_IDLE || playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        playerConnection.player.seekToDefaultPosition()
                        playerConnection.player.playWhenReady = true
                    } else if (isPlaying) {
                        playerConnection.player.pause()
                    } else {
                        playerConnection.player.play()
                    }
                },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = { playerConnection.player.seekToNext() },
                onCollapse = state::collapseSoft,
                shuffleModeEnabled = shuffleModeEnabled,
                onShuffleClick = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                repeatMode = repeatMode,
                onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                onLyricsClick = onOpenFullscreenLyrics,
                onQueueClick = { queueSheetState.expandSoft() },
                onMenuClick = {
                    mediaMetadata?.let { metadata ->
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = metadata,
                                navController = navController,
                                playerBottomSheetState = state,
                                onShowDetailsDialog = { showDetailsDialog = true },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    }
                }
            )
        } else if (playerScreenStyle == PlayerScreenStyle.SPOTIFY) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                val spotifyScrollState = rememberScrollState()
                SpotifyPlayerBackdrop(
                    thumbnailUrl = mediaMetadata?.thumbnailUrl,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                        .verticalScroll(spotifyScrollState)
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 24.dp)
                        .padding(bottom = queueSheetState.collapsedBound + 28.dp),
                ) {
                    Spacer(Modifier.height(22.dp))

                    mediaMetadata?.let { metadata ->
                        SpotifyPlayerContent(
                            mediaMetadata = metadata,
                            position = sliderPosition ?: position,
                            duration = duration,
                            isPlaying = isPlaying,
                            isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            repeatMode = repeatMode,
                            sliderStyle = sliderStyle,
                            lyricsPreview = (spotifyLyricsEntity ?: currentLyrics)?.lyrics,
                            onSeek = { sliderPosition = it },
                            onSeekFinished = {
                                sliderPosition?.let { playerConnection.player.seekTo(it) }
                                sliderPosition = null
                            },
                            onPlayPause = { playerConnection.player.togglePlayPause() },
                            onPrevious = { playerConnection.player.seekToPrevious() },
                            onNext = playerConnection::seekToNext,
                            onShuffle = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                            onRepeat = { playerConnection.player.toggleRepeatMode() },
                            onOpenLyrics = onOpenFullscreenLyrics,
                            onOpenQueue = queueSheetState::expandSoft,
                            onStartRadio = playerConnection.service::startRadioSeamlessly,
                            onCollapse = state::collapseSoft,
                            onOpenArtist = { artistId ->
                                navController.navigate("artist/$artistId")
                                state.collapseSoft()
                            },
                            onOpenMenu = {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = metadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = { showDetailsDialog = true },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            isLiked = currentSong?.song?.liked == true,
                            onLike = playerConnection::toggleLike,
                            onAddToPlaylist = { showChoosePlaylistDialog = true },
                            onShowDetails = { showDetailsDialog = true },
                            shuffleModeEnabled = shuffleModeEnabled,
                            currentSongArtists = currentSong?.artists ?: emptyList(),
                            surfaceColor = spotifySurfaceColor,
                        )
                    }
                }

                mediaMetadata?.let { metadata ->
                    AnimatedVisibility(
                        visible = spotifyScrollState.value > 980,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180)),
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        SpotifyCollapsedHeader(
                            mediaMetadata = metadata,
                            surfaceColor = spotifyHeaderColor,
                            isLiked = currentSong?.song?.liked == true,
                            onLike = playerConnection::toggleLike,
                            onOpenMenu = {
                                menuState.show {
                                    PlayerMenu(
                                        mediaMetadata = metadata,
                                        navController = navController,
                                        playerBottomSheetState = state,
                                        onShowDetailsDialog = { showDetailsDialog = true },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        } else if (playerScreenStyle == PlayerScreenStyle.GROOVE) {
            GroovePlayer(
                state = state,
                playerConnection = playerConnection,
                mediaMetadata = mediaMetadata,
                playbackState = playbackState,
                duration = duration,
                position = position,
                sliderPosition = sliderPosition,
                onSliderPositionChange = { sliderPosition = it },
                onSliderPositionChangeFinished = {
                    sliderPosition?.let {
                        playerConnection.player.seekTo(it)
                    }
                    sliderPosition = null
                },
                isPlaying = isPlaying,
                isLoading = playbackState != Player.STATE_READY && playbackState != Player.STATE_ENDED,
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = { playerConnection.player.seekToNext() },
                onCollapse = state::collapseSoft,
                shuffleModeEnabled = shuffleModeEnabled,
                onShuffleClick = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                repeatMode = repeatMode,
                onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                onLyricsClick = onOpenFullscreenLyrics,
                onMenuClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            )
        } else if (playerScreenStyle == PlayerScreenStyle.POPSY) {
            PopsyPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let {
                        playerConnection.player.seekTo(it)
                    }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = { playerConnection.player.seekToNext() },
                onCollapse = state::collapseSoft,
                onMenuClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                isLiked = currentSong?.song?.liked == true,
                onLikeClick = playerConnection::toggleLike,
                onAddToPlaylistClick = { showChoosePlaylistDialog = true },
                shuffleModeEnabled = shuffleModeEnabled,
                onShuffleClick = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                repeatMode = repeatMode,
                onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                onLyricsClick = onOpenFullscreenLyrics,
                onQueueClick = { queueSheetState.expandSoft() }
            )
        } else if (playerScreenStyle == PlayerScreenStyle.MINIMAL) {
            MinimalPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                nextMediaMetadata = nextMediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let {
                        playerConnection.player.seekTo(it)
                    }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = { playerConnection.player.seekToNext() },
                onCollapse = state::collapseSoft,
                onMenuClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                isLiked = currentSong?.song?.liked == true,
                onLikeClick = playerConnection::toggleLike,
                repeatMode = repeatMode,
                onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                onQueueClick = { queueSheetState.expandSoft() }
            )
        } else if (playerScreenStyle == PlayerScreenStyle.COLOURFULL) {
            ColourfullPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                sliderStyle = sliderStyle,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let {
                        playerConnection.player.seekTo(it)
                    }
                    sliderPosition = null
                },
                onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = { playerConnection.player.seekToNext() },
                onCollapse = state::collapseSoft,
                onMenuClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                isLiked = currentSong?.song?.liked == true,
                onLikeClick = playerConnection::toggleLike,
                repeatMode = repeatMode,
                onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                onQueueClick = { queueSheetState.expandSoft() }
            )
        } else if (playerScreenStyle == PlayerScreenStyle.APPLE) {
            ApplePlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let { playerConnection.player.seekTo(it) }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = { playerConnection.player.seekToNext() },
                onCollapse = state::collapseSoft,
                onMenuClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                isLiked = currentSong?.song?.liked == true,
                onLikeClick = playerConnection::toggleLike,
                onQueueClick = { queueSheetState.expandSoft() },
                onShareClick = {
                    mediaMetadata?.let { metadata ->
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://play.smusic.app/song?id=${metadata.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                shuffleModeEnabled = shuffleModeEnabled,
                onShuffleClick = {
                    playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                },
                repeatMode = repeatMode,
                onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                onOpenFullscreenLyrics = onOpenFullscreenLyrics,
            )
        } else if (playerScreenStyle == PlayerScreenStyle.IOS_STYLED) {
            IosStyledPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let { playerConnection.player.seekTo(it) }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = { playerConnection.player.seekToNext() },
                onCollapse = state::collapseSoft,
                onMenuClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                isLiked = currentSong?.song?.liked == true,
                onLikeClick = playerConnection::toggleLike,
                onQueueClick = { queueSheetState.expandSoft() },
                onShareClick = {
                    mediaMetadata?.let { metadata ->
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://play.smusic.app/song?id=${metadata.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                shuffleModeEnabled = shuffleModeEnabled,
                onShuffleClick = {
                    playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                },
                repeatMode = repeatMode,
                onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                playerConnection = playerConnection,
                navController = navController,
                menuState = menuState,
                nextUpMetadata = nextMediaMetadata,
                currentFormat = currentFormat,
                playerVolume = playerVolume.value,
                onVolumeChange = { playerConnection.service.playerVolume.value = it },
            )
        } else if (playerScreenStyle == PlayerScreenStyle.GALAXY) {
            GalaxyPlayer(
                state = state,
                mediaMetadata = mediaMetadata,
                position = sliderPosition ?: position,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = playbackState != STATE_READY && playbackState != STATE_ENDED,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onSeek = { sliderPosition = it },
                onSeekFinished = {
                    sliderPosition?.let { playerConnection.player.seekTo(it) }
                    sliderPosition = null
                },
                onPlayPause = { playerConnection.player.togglePlayPause() },
                onPrevious = { playerConnection.player.seekToPrevious() },
                onNext = { playerConnection.player.seekToNext() },
                onCollapse = state::collapseSoft,
                onMenuClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata ?: return@show,
                            navController = navController,
                            playerBottomSheetState = state,
                            onShowDetailsDialog = { showDetailsDialog = true },
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                isLiked = currentSong?.song?.liked == true,
                onLikeClick = playerConnection::toggleLike,
                onQueueClick = { queueSheetState.expandSoft() },
                onPlayQueueIndex = { index ->
                    playerConnection.player.seekTo(index, 0)
                },
                onShareClick = {
                    mediaMetadata?.let { metadata ->
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://play.smusic.app/song?id=${metadata.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                shuffleModeEnabled = shuffleModeEnabled,
                onShuffleClick = {
                    playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                },
                repeatMode = repeatMode,
                onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                queueWindows = queueWindows.map { it.mediaItem },
                currentWindowIndex = currentWindowIndex,
                lyrics = (spotifyLyricsEntity ?: currentLyrics)?.lyrics
            )
        } else if (playerScreenStyle == PlayerScreenStyle.MODERN) {
            val playbackOutputName = rememberPlaybackOutputName()

            Box(modifier = Modifier.fillMaxSize()) {
                ImmersivePlayerBackdrop(
                    thumbnailUrl = mediaMetadata?.thumbnailUrl,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 58.dp)
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                ) {
                    mediaMetadata?.let {
                        selectedControlsContent(it)
                    }

                    Spacer(Modifier.height(34.dp))

                    ImmersiveBottomActions(
                        textColor = Color.White,
                        onOpenQueue = queueSheetState::expandSoft,
                        onOpenLyrics = onOpenFullscreenLyrics,
                        onDeviceClick = {
                            Toast.makeText(context, playbackOutputName, Toast.LENGTH_SHORT).show()
                        },
                        deviceName = playbackOutputName,
                    )
                }
            }
        } else if (playerScreenStyle == PlayerScreenStyle.CLASSIC) {
            ClassicPlayerScreen(
                mediaMetadata = mediaMetadata,
                position = position,
                duration = duration,
                sliderPosition = sliderPosition,
                onSliderPositionChange = { sliderPosition = it },
                isPlaying = isPlaying,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                queueSheetState = queueSheetState,
                bottomSheetState = state,
                onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                navController = navController,
                dominantColor = spotifyColors.firstOrNull() ?: Color(0xFF595959),
            )
        } else when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                Row(
                    modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                            .padding(top = queueSheetState.collapsedBound)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        val screenWidth = LocalConfiguration.current.screenWidthDp
                        val thumbnailSize = (screenWidth * 0.4).dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                                modifier = Modifier.size(thumbnailSize)
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(1f)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    ) {
                        Spacer(Modifier.weight(1f))

                        mediaMetadata?.let {
                            selectedControlsContent(it)
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                            .padding(bottom = queueSheetState.collapsedBound)
                            .bottomSheetDraggable(state),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection)
                        ) {
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                            )
                        }
                    }

                    mediaMetadata?.let {
                        selectedControlsContent(it)
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }

        if (playerScreenStyle == PlayerScreenStyle.APPLE) {
            AppleQueue(
                state = queueSheetState
            )
        } else if (classicQueueSwipeEnabled || !queueSheetState.isCollapsed) {
            val bgCol = if (useBlackBackground) Color.Black else MaterialTheme.colorScheme.surfaceContainer

            Queue(
                state = queueSheetState,
                playerBottomSheetState = state,
                navController = navController,
                backgroundColor = bgCol,
                onBackgroundColor = onBackgroundColor,
                textBackgroundColor = TextBackgroundColor,
            )
        }
    }
}
