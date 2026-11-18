package com.devxrohan.smusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.media3.common.C
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.PlayerBackgroundStyle
import com.devxrohan.smusic.constants.PlayerHorizontalPadding
import com.devxrohan.smusic.constants.SwipeThumbnailKey
import com.devxrohan.smusic.constants.ThumbnailCornerRadius
import com.devxrohan.smusic.ui.component.dataStore
import kotlinx.coroutines.flow.map
import com.devxrohan.smusic.ui.utils.highQualityThumbnail
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    onOpenFullscreenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    isPlayerExpanded: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    showPlayingFrom: Boolean = true,
    artworkScale: Float = 0.85f,
    useCustomCornerRadius: Boolean = false,
    textColor: Color? = null,
    // How much space is reserved around the artwork square before it's sized.
    // Default matches the original hardcoded value so every existing caller
    // (the normal swipeable player) keeps its current size unchanged; only a
    // caller that explicitly passes a smaller value gets a bigger square.
    artworkPadding: Dp = PlayerHorizontalPadding,
    // Optional fixed square size for layouts (such as Classic) that must not
    // change artwork dimensions when the available vertical space differs.
    artworkSize: Dp? = null,
) {


    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val currentView = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val error by playerConnection.error.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val swipeThumbnail by rememberPreference(SwipeThumbnailKey, true)
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val cornerRadiusFlow = remember(context) {
        context.dataStore.data.map {
            it[androidx.datastore.preferences.core.floatPreferencesKey("thumbnail_corner_radius")] ?: 16f
        }
    }
    val customCornerRadius by cornerRadiusFlow.collectAsState(initial = 16f)

    val effectiveShape = if (useCustomCornerRadius) RoundedCornerShape(customCornerRadius.dp) else shape

    val playerBackground = PlayerBackgroundStyle.DEFAULT

    val textBackgroundColor = textColor ?: when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
        PlayerBackgroundStyle.BLUR -> Color.White
        PlayerBackgroundStyle.GRADIENT -> Color.White
        PlayerBackgroundStyle.FLUID -> Color.White
    }

    val thumbnailLazyGridState = rememberLazyGridState()

    val timeline = playerConnection.player.currentTimeline
    val currentIndex = playerConnection.player.currentMediaItemIndex
    val shuffleModeEnabled = playerConnection.player.shuffleModeEnabled
    val previousMediaMetadata = if (swipeThumbnail && !timeline.isEmpty) {
        val previousIndex = timeline.getPreviousWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (previousIndex != C.INDEX_UNSET) {
            try {
                playerConnection.player.getMediaItemAt(previousIndex)
            } catch (e: Exception) { null }
        } else null
    } else null

    val nextMediaMetadata = if (swipeThumbnail && !timeline.isEmpty) {
        val nextIndex = timeline.getNextWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (nextIndex != C.INDEX_UNSET) {
            try {
                playerConnection.player.getMediaItemAt(nextIndex)
            } catch (e: Exception) { null }
        } else null
    } else null

    val currentMediaItem = try {
        playerConnection.player.currentMediaItem
    } catch (e: Exception) { null }

    val mediaItems = listOfNotNull(previousMediaMetadata, currentMediaItem, nextMediaMetadata)
    val currentMediaIndex = mediaItems.indexOf(currentMediaItem)

    val horizontalLazyGridItemWidthFactor = 1f
    val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
        SnapLayoutInfoProvider(
            lazyGridState = thumbnailLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
            },
            velocityThreshold = 500f
        )
    }

    val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
    val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }

    LaunchedEffect(itemScrollOffset) {
        if (!thumbnailLazyGridState.isScrollInProgress || !swipeThumbnail || itemScrollOffset != 0 || currentMediaIndex < 0) return@LaunchedEffect

        val swipedForward = currentItem > currentMediaIndex
        val swipedBackward = currentItem < currentMediaIndex
        val indexBeforeSeek = playerConnection.player.currentMediaItemIndex

        if (swipedForward && canSkipNext) {
            playerConnection.player.seekToNext()
        } else if (swipedBackward && canSkipPrevious) {
            playerConnection.player.seekToPreviousMediaItem()
        } else if (swipedForward || swipedBackward) {
            // The carousel moved but there was nothing to skip to right then
            // (most commonly: the current song was still loading, so
            // canSkipNext/canSkipPrevious hadn't caught up yet). Snap it back
            // immediately instead of leaving it showing a song that isn't
            // the one actually playing.
            thumbnailLazyGridState.animateScrollToItem(currentMediaIndex)
            return@LaunchedEffect
        } else {
            return@LaunchedEffect
        }

        // The seek call above can still be a no-op (e.g. the player was mid-
        // transition and ignored it). Verify it actually moved the player;
        // if it didn't, the carousel must not be left out of sync with what's
        // really playing.
        delay(400)
        if (playerConnection.player.currentMediaItemIndex == indexBeforeSeek) {
            thumbnailLazyGridState.animateScrollToItem(currentMediaIndex)
        }
    }

    LaunchedEffect(mediaMetadata, canSkipPrevious, canSkipNext) {
        val index = maxOf(0, currentMediaIndex)
        if (index >= 0 && index < mediaItems.size) {
            try {
                thumbnailLazyGridState.animateScrollToItem(index)
            } catch (e: Exception) {
                thumbnailLazyGridState.scrollToItem(index)
            }
        }
    }

    LaunchedEffect(playerConnection.player.currentMediaItemIndex) {
        val index = mediaItems.indexOf(currentMediaItem)
        if (index >= 0 && index != currentItem) {
            thumbnailLazyGridState.scrollToItem(index)
        }
    }

    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }
    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center),
        ) {
            error?.let { playbackError ->
                PlaybackError(
                    error = playbackError,
                    retry = { playerConnection.player.prepare() },
                )
            }
        }

        AnimatedVisibility(
            visible = error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showPlayingFrom) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = if (artworkSize != null) 10.dp else 32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.playing_from),
                            style = MaterialTheme.typography.titleMedium,
                            color = textBackgroundColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val playingFrom = queueTitle ?: mediaMetadata?.album?.title
                        if (!playingFrom.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playingFrom,
                                style = MaterialTheme.typography.titleMedium,
                                color = textBackgroundColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    // Classic Player uses the full available content width so its artwork
                    // aligns with the title/artist column below. The normal swipeable player
                    // keeps the side peek carousel behaviour unchanged.
                    val horizontalLazyGridItemWidth = if (artworkSize != null) {
                        maxWidth
                    } else {
                        maxWidth * horizontalLazyGridItemWidthFactor
                    }

                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = swipeThumbnail && isPlayerExpanded,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = mediaItems,
                            key = { item ->
                                item.mediaId.ifEmpty { "unknown_${item.hashCode()}" }
                            }
                        ) { item ->
                            var skipMultiplier by remember { mutableIntStateOf(1) }
                            var lastTapTime by remember { mutableLongStateOf(0L) }

                            Box(
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                onOpenFullscreenLyrics()
                                            },
                                            onDoubleTap = { offset ->
                                                if (offset.x < size.width / 2) {
                                                    playerConnection.player.seekBack()
                                                } else {
                                                    playerConnection.player.seekForward()
                                                }
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // The artwork must stay perfectly square. Letting Compose's own
                                // aspectRatio modifier compute that size — instead of manually
                                // computing a Dp value — is what actually fixes this correctly:
                                // it automatically picks the largest square that fits inside
                                // whichever of this grid cell's own width/height is smaller, using
                                // this cell's real bounds rather than a value calculated from the
                                // wrong reference (an earlier version compared against the full
                                // screen width here instead of this cell's own width, which made
                                // the artwork noticeably smaller than it should be on cells that
                                // are narrower than the full screen, i.e. basically always, since
                                // this carousel deliberately shows a sliver of the next item at
                                // each edge). This also keeps the earlier overlap fix (the square
                                // never exceeds the space actually available for it, so it can't
                                // spill over the title/artist row below it on short screens).
                                BoxWithConstraints(contentAlignment = Alignment.Center) {
                                    // A fixed dp size alone can exceed the artwork cell on short
                                    // phones or landscape layouts. Cap it to the real cell bounds
                                    // so the square stays consistent where possible, but never
                                    // collides with the header, metadata, or controls.
                                    val safeArtworkSize = artworkSize?.coerceAtMost(
                                        minOf(maxWidth, maxHeight).coerceAtLeast(1.dp)
                                    )
                                    Box(
                                        modifier = if (safeArtworkSize != null) {
                                            Modifier.size(safeArtworkSize)
                                        } else {
                                            Modifier
                                                .padding(artworkPadding)
                                                .aspectRatio(1f)
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {

                                    // 🎵 Circular Album Art
                                    AsyncImage(
                                        placeholder = painterResource(R.drawable.music_note),
                                        error = painterResource(R.drawable.music_note),
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(item.mediaMetadata.artworkUri?.toString()?.highQualityThumbnail())
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize(artworkScale)
                                            .clip(effectiveShape)
                                    )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = seekDirection,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }
    }
}

@ExperimentalFoundationApi
fun SnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = { layoutSize, itemSize ->
        (layoutSize / 2f - itemSize / 2f)
    },
    velocityThreshold: Float = 1000f,
): SnapLayoutInfoProvider = object : SnapLayoutInfoProvider {
    private val layoutInfo: LazyGridLayoutInfo
        get() = lazyGridState.layoutInfo

    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f
    override fun calculateSnapOffset(velocity: Float): Float {
        val bounds = calculateSnappingOffsetBounds()

        if (abs(velocity) < velocityThreshold) {
            if (abs(bounds.start) < abs(bounds.endInclusive))
                return bounds.start

            return bounds.endInclusive
        }

        return when {
            velocity < 0 -> bounds.start
            velocity > 0 -> bounds.endInclusive
            else -> 0f
        }
    }

    fun calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
        var lowerBoundOffset = Float.NEGATIVE_INFINITY
        var upperBoundOffset = Float.POSITIVE_INFINITY

        layoutInfo.visibleItemsInfo.fastForEach { item ->
            val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)

            if (offset <= 0 && offset > lowerBoundOffset) {
                lowerBoundOffset = offset
            }

            if (offset >= 0 && offset < upperBoundOffset) {
                upperBoundOffset = offset
            }
        }

        return lowerBoundOffset.rangeTo(upperBoundOffset)
    }
}

fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyGridLayoutInfo,
    item: LazyGridItemInfo,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float,
): Float {
    val containerSize =
        layoutInfo.singleAxisViewportSize - layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding

    val desiredDistance = positionInLayout(containerSize.toFloat(), item.size.width.toFloat())
    val itemCurrentPosition = item.offset.x.toFloat()

    return itemCurrentPosition - desiredDistance
}

private val LazyGridLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width
