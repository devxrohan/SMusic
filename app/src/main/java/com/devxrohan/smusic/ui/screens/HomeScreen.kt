package com.devxrohan.smusic.ui.screens

import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.AccountPhotoUrlKey
import com.devxrohan.smusic.constants.AccountEmailKey
import com.devxrohan.smusic.constants.AccountNameKey
import com.devxrohan.smusic.constants.AccountChannelHandleKey
import com.devxrohan.smusic.constants.VisitorDataKey
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.devxrohan.smusic.BuildConfig
import com.devxrohan.smusic.checkForUpdates
import com.devxrohan.smusic.isNewerVersion
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devxrohan.smusic.innertube.models.AlbumItem
import com.devxrohan.smusic.innertube.models.ArtistItem
import com.devxrohan.smusic.innertube.models.PlaylistItem
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.innertube.models.YTItem
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.GridThumbnailHeight
import com.devxrohan.smusic.constants.ListItemHeight
import com.devxrohan.smusic.constants.ListThumbnailSize
import com.devxrohan.smusic.constants.ThumbnailCornerRadius
import com.devxrohan.smusic.constants.HomeHeaderStyle
import com.devxrohan.smusic.constants.HomeHeaderStyleKey
import com.devxrohan.smusic.constants.AppFont
import com.devxrohan.smusic.constants.AppFontKey
import com.devxrohan.smusic.db.entities.Album
import com.devxrohan.smusic.db.entities.Artist
import com.devxrohan.smusic.db.entities.LocalItem
import com.devxrohan.smusic.db.entities.Playlist
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.extensions.togglePlayPause
import com.devxrohan.smusic.models.toMediaMetadata
import com.devxrohan.smusic.playback.queues.LocalAlbumRadio
import com.devxrohan.smusic.playback.queues.YouTubeAlbumRadio
import com.devxrohan.smusic.playback.queues.YouTubeQueue
import com.devxrohan.smusic.ui.component.AlbumGridItem
import com.devxrohan.smusic.ui.component.ArtistGridItem
import com.devxrohan.smusic.ui.component.LocalMenuState
import com.devxrohan.smusic.ui.component.NavigationTitle
import com.devxrohan.smusic.ui.component.SongGridItem
import com.devxrohan.smusic.ui.component.SongListItem
import com.devxrohan.smusic.ui.component.YouTubeGridItem
import com.devxrohan.smusic.ui.component.shimmer.GridItemPlaceHolder
import com.devxrohan.smusic.ui.component.shimmer.ShimmerHost
import com.devxrohan.smusic.ui.component.shimmer.TextPlaceholder
import com.devxrohan.smusic.ui.menu.AlbumMenu
import com.devxrohan.smusic.ui.menu.ArtistMenu
import com.devxrohan.smusic.ui.menu.SongMenu
import com.devxrohan.smusic.ui.menu.YouTubeAlbumMenu
import com.devxrohan.smusic.ui.menu.YouTubeArtistMenu
import com.devxrohan.smusic.ui.component.CircleIconButton
import com.devxrohan.smusic.ui.menu.YouTubePlaylistMenu
import com.devxrohan.smusic.ui.menu.YouTubeSongMenu
import com.devxrohan.smusic.ui.utils.SnapLayoutInfoProvider
import com.devxrohan.smusic.ui.utils.highQualityThumbnail
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.signOutYouTubeAccount
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.core.net.toUri
import com.devxrohan.smusic.ui.component.LocalUserName
import com.devxrohan.smusic.viewmodels.StatsViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
)
{
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val youtubeQuickPicks by viewModel.youtubeQuickPicks.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val pulseShelves by viewModel.pulseShelves.collectAsState()
    val pulseDiscover by viewModel.pulseDiscover.collectAsState()
    val pulseYtShelves by viewModel.pulseYtShelves.collectAsState()

    val (appFontKey, _) = rememberPreference(AppFontKey, defaultValue = AppFont.LINOTTE.key)
    val appFont = remember(appFontKey) { AppFont.fromKey(appFontKey) }

    val (homeHeaderStyle, _) = rememberEnumPreference(
        HomeHeaderStyleKey,
        defaultValue = HomeHeaderStyle.SMUSIC
    )

    val (quickPicksCardWidth, _) = rememberPreference(
        com.devxrohan.smusic.constants.QuickPicksCardWidthKey,
        defaultValue = 196f
    )
    val (quickPicksCardHeight, _) = rememberPreference(
        com.devxrohan.smusic.constants.QuickPicksCardHeightKey,
        defaultValue = 310f
    )

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()
    val (homeCookie, _) = rememberPreference(InnerTubeCookieKey, defaultValue = "")
    val isYouTubeHome = runCatching {
        homeCookie.isNotBlank() && "SAPISID" in com.devxrohan.smusic.innertube.utils.parseCookieString(homeCookie)
    }.getOrDefault(false)

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (it.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    com.devxrohan.smusic.playback.queues.PulseRadioQueue(
                                        it.toMediaMetadata(),
                                        playerConnection.service.pulseEngine,
                                        database,
                                    ),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                    showLibraryAction = false,
                                )
                            }
                        },
                    ),
                isActive = it.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            navController.navigate("album/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            navController.navigate("artist/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )

            is Playlist -> {}
        }
    }

    val ytGridItem: @Composable (YTItem, Boolean) -> Unit = { item, isSimilarTo ->
        YouTubeGridItem(
            item = item,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            modifier = Modifier
                .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    onClick = {
                        when (item) {
                            is SongItem -> playerConnection.playQueue(
                                YouTubeQueue(
                                    item.endpoint ?: WatchEndpoint(
                                        videoId = item.id
                                    ), item.toMediaMetadata()
                                )
                            )

                            is AlbumItem -> navController.navigate("album/${item.id}")
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                is SongItem -> YouTubeSongMenu(
                                    song = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is AlbumItem -> YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is ArtistItem -> YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss
                                )

                                is PlaylistItem -> YouTubePlaylistMenu(
                                    playlist = item,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    }
                )
        )
    }

    LaunchedEffect(quickPicks, youtubeQuickPicks) {
        // A provider-only refresh must reset the carousel too, otherwise the new first card
        // appears only after the user manually scrolls back to the start.
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    // Main container Box that holds everything (same pattern as ExploreScreen)
    Box(modifier = Modifier.fillMaxSize()) {

        // Content
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh
                ),
            contentAlignment = Alignment.TopStart
        ) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
            val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = quickPicksLazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }
            val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = forgottenFavoritesLazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }

            // FIXED: Added proper windowInsetsPadding to move content down
            LazyColumn(
                state = lazylistState,
                contentPadding = LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Top)
                    )
            )
            {
                // ModernHomeTopBarInline is now inside the LazyColumn
                item {
                    ModernHomeTopBarInline(
                        navController = navController,
                        homeHeaderStyle = homeHeaderStyle
                    )
                }

                if (isLoading && quickPicks.isNullOrEmpty() && homePage == null) {
                    item(key = "home_first_paint_skeleton") {
                        ShimmerHost(
                            modifier = Modifier.animateItem()
                        ) {
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .width(250.dp),
                            )
                            LazyRow {
                                items(4) { GridItemPlaceHolder() }
                            }
                        }
                    }
                }

                if (quickPicks.orEmpty().isNotEmpty()) {
                    item {
                        val picks = quickPicks.orEmpty()
                        val distinctPicks = remember(picks) { picks.distinctBy { it.id } }
                        if (homeHeaderStyle == HomeHeaderStyle.FEATURED) {
                            Text(
                                text = stringResource(R.string.ui_listen_now),
                                fontFamily = appFont.getFontFamily(),
                                fontWeight = FontWeight.W800,
                                fontSize = 34.sp,
                                letterSpacing = (-0.8).sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        val extraLargeQuickPicks = homeHeaderStyle == HomeHeaderStyle.NONE
                        val quickPicksRowState = rememberLazyListState()
                        val cardWidth = if (extraLargeQuickPicks) quickPicksCardWidth.dp else 160.dp
                        val rowHeight = if (extraLargeQuickPicks) quickPicksCardHeight.dp else 220.dp
                        // When the refreshed list changes, show its new first item immediately.
                        // Do not wrap the LazyRow in AnimatedContent: that created a second,
                        // competing fade/slide animation and left the old item visually pinned.
                        LaunchedEffect(distinctPicks) {
                            quickPicksRowState.scrollToItem(0)
                        }
                        LazyRow(
                            state = quickPicksRowState,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .padding(top = if (extraLargeQuickPicks) 12.dp else 10.dp, bottom = if (extraLargeQuickPicks) 18.dp else 14.dp)
                        ) {
                            // SMusic Quick Picks only: YouTube's own Quick Picks live in their own row.
                            for (quickPickIndex in 0 until distinctPicks.size) {
                                if (quickPickIndex < distinctPicks.size) {
                                    item(key = distinctPicks[quickPickIndex].id) {
                                        val originalSong = distinctPicks[quickPickIndex]
                            val song by database.song(originalSong.id).collectAsState(initial = originalSong)
                            val isActive = song!!.id == mediaMetadata?.id
                            val itemInfo = quickPicksRowState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == originalSong.id }
                            val viewportCenter = (quickPicksRowState.layoutInfo.viewportStartOffset + quickPicksRowState.layoutInfo.viewportEndOffset) / 2f
                            val itemCenter = itemInfo?.let { it.offset + it.size / 2f } ?: viewportCenter
                            val distance = if (itemInfo != null) {
                                (kotlin.math.abs(itemCenter - viewportCenter) / (quickPicksRowState.layoutInfo.viewportEndOffset - quickPicksRowState.layoutInfo.viewportStartOffset).coerceAtLeast(1)).coerceIn(0f, 1f)
                            } else 1f
                            val cardScale = 0.88f + (1f - distance) * 0.12f
                            val cardAlpha = 0.62f + (1f - distance) * 0.38f

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = cardScale
                                        scaleY = cardScale
                                        alpha = cardAlpha
                                    }
                                    .width(cardWidth)
                                    .fillMaxHeight()
                                    .shadow(elevation = 6.dp, shape = MaterialTheme.shapes.large)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                                        onClick = {
                                            if (isActive) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(YouTubeQueue.radio(song!!.toMediaMetadata()))
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song!!,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                    showLibraryAction = false,
                                                )
                                            }
                                        }
                                    )
                            ) {
                                // Placeholder background — shows behind the
                                // thumbnail so a missing/failed thumbnailUrl
                                // never just leaves blank space.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                if (!song!!.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        placeholder = painterResource(R.drawable.music_note),
                                        error = painterResource(R.drawable.music_note),
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(song!!.thumbnailUrl?.highQualityThumbnail())
                                            .crossfade(true)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .diskCacheKey(song!!.thumbnailUrl?.highQualityThumbnail())
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.75f)
                                                )
                                            )
                                        )
                                )

                                if (isActive && isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(10.dp)
                                            .size(28.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.volume_up),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(if (extraLargeQuickPicks) 14.dp else 12.dp)
                                ) {
                                    Text(
                                        text = song!!.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song!!.artists.joinToString { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                                    }
                                }
                            }
                        }
                    }
                }

                keepListening?.takeIf { it.isNotEmpty() }?.let { keepListening ->
                    item {
                        NavigationTitle(
                            title = stringResource(R.string.keep_listening),
                            modifier = Modifier.animateItem()
                        )
                    }

                    item {
                        val rows = if (keepListening.size > 6) 2 else 1
                        LazyHorizontalGrid(
                            state = rememberLazyGridState(),
                            rows = GridCells.Fixed(rows),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((GridThumbnailHeight + with(LocalDensity.current) {
                                    MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                                            MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                                }) * rows)
                                .animateItem()
                        ) {
                            items(keepListening, key = { it.id }) {
                                localGridItem(it)
                            }
                        }
                    }
                }

                // YouTube Music's own Quick Picks: separate from SMusic's, below Keep Listening,
                // and only while a YouTube account is connected.
                if (isYouTubeHome && youtubeQuickPicks.isNotEmpty()) {
                    item(key = "youtube_quick_picks_title") {
                        NavigationTitle(
                            title = stringResource(R.string.quick_picks),
                            label = stringResource(R.string.ui_youtube_music),
                            modifier = Modifier.animateItem()
                        )
                    }
                    item(key = "youtube_quick_picks_row") {
                        LazyRow(
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier.animateItem()
                        ) {
                            items(youtubeQuickPicks, key = { "yt_quick_${it.id}" }) { ytGridItem(it, false) }
                        }
                    }
                }

                // Keep the Home rhythm balanced by mode. Account mode starts with a Pulse-ranked shelf
                // and alternates it with account-derived related shelves; guest mode keeps Similar To -> Pulse.
                val similarRows = similarRecommendations.orEmpty()
                val accountPulseCount = if (isYouTubeHome) pulseYtShelves.size else 0
                val interleavedCount = maxOf(similarRows.size, pulseShelves.size, accountPulseCount)
                for (index in 0 until interleavedCount) {
                    if (isYouTubeHome && index < pulseYtShelves.size) {
                        val shelf = pulseYtShelves[index]
                        if (shelf.items.isNotEmpty()) {
                            item(key = "pulse_yt_${shelf.id}") {
                                NavigationTitle(title = shelf.title, label = shelf.label, modifier = Modifier.animateItem())
                            }
                            item(key = "pulse_yt_${shelf.id}_row") {
                                LazyRow(
                                    contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                                    modifier = Modifier.animateItem()
                                ) {
                                    items(shelf.items, key = { "pulse_yt_${shelf.id}_${it.id}" }) { ytGridItem(it, false) }
                                }
                            }
                        }
                    }
                    if (index < similarRows.size) {
                        val recommendation = similarRows[index]
                        item(key = "similar_title_${index}_${recommendation.title.id}") {
                            NavigationTitle(
                                label = stringResource(R.string.similar_to),
                                title = recommendation.title.title,
                                thumbnail = recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                                    {
                                        val shape =
                                            if (recommendation.title is Artist) CircleShape else RoundedCornerShape(
                                                ThumbnailCornerRadius
                                            )
                                        AsyncImage(
                                            placeholder = painterResource(R.drawable.music_note),
                                            error = painterResource(R.drawable.music_note),
                                            model = thumbnailUrl.highQualityThumbnail(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(ListThumbnailSize)
                                                .clip(shape)
                                        )
                                    }
                                },
                                onClick = {
                                    when (val title = recommendation.title) {
                                        is Song -> title.album?.id?.let { navController.navigate("album/$it") }
                                        is Album -> navController.navigate("album/${title.id}")
                                        is Artist -> navController.navigate("artist/${title.id}")
                                        is Playlist -> {}
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                        item(key = "similar_row_${index}_${recommendation.title.id}") {
                            LazyRow(
                                contentPadding = WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal)
                                    .asPaddingValues(),
                                modifier = Modifier.animateItem()
                            ) {
                                items(recommendation.items, key = { "similar_${index}_${it.id}" }) { item ->
                                    ytGridItem(item, true)
                                }
                            }
                        }
                    }

                    if (!isYouTubeHome && index < pulseShelves.size) {
                        val shelf = pulseShelves[index]
                        item(key = "pulse_${shelf.id}") {
                            NavigationTitle(
                                title = shelf.title,
                                label = shelf.reason.takeIf { it.isNotBlank() },
                                modifier = Modifier.animateItem()
                            )
                        }
                        item(key = "pulse_row_${shelf.id}") {
                            LazyRow(
                                contentPadding = WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal)
                                    .asPaddingValues(),
                                modifier = Modifier.animateItem()
                            ) {
                                items(shelf.items, key = { "pulse_${shelf.id}_${it.id}" }) {
                                    localGridItem(it)
                                }
                            }
                        }
                    }
                }

                if (pulseDiscover.isNotEmpty()) {
                    item(key = "pulse_discover") {
                        NavigationTitle(
                            title = stringResource(R.string.pulse_shelf_discover),
                            modifier = Modifier.animateItem()
                        )
                    }
                    item(key = "pulse_discover_row") {
                        LazyRow(
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier.animateItem()
                        ) {
                            items(pulseDiscover, key = { it.id }) { ytGridItem(it, false) }
                        }
                    }
                }

                val accountSections = homePage?.sections.orEmpty()
                accountSections.forEach { it ->
                    item {
                        NavigationTitle(
                            title = it.title,
                            label = it.label,
                            thumbnail = it.thumbnail?.let { thumbnailUrl ->
                                {
                                    val shape =
                                        if (it.endpoint?.isArtistEndpoint == true) CircleShape else RoundedCornerShape(
                                            ThumbnailCornerRadius
                                        )
                                    AsyncImage(
                                        placeholder = painterResource(R.drawable.music_note),
                                        error = painterResource(R.drawable.music_note),
                                        model = thumbnailUrl.highQualityThumbnail(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(ListThumbnailSize)
                                            .clip(shape)
                                    )
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }

                    item {
                        LazyRow(
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier.animateItem()
                        ) {
                            items(it.items, key = { it.id }) { item -> ytGridItem(item, false) }
                        }
                    }
                }

                forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                    item {
                        NavigationTitle(
                            title = stringResource(R.string.forgotten_favorites),
                            modifier = Modifier.animateItem()
                        )
                    }

                    item {
                        // take min in case list size is less than 4
                        val rows = min(4, forgottenFavorites.size)
                        LazyHorizontalGrid(
                            state = forgottenFavoritesLazyGridState,
                            rows = GridCells.Fixed(rows),
                            flingBehavior = rememberSnapFlingBehavior(
                                forgottenFavoritesSnapLayoutInfoProvider
                            ),
                            contentPadding = WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight * rows)
                                .animateItem()
                        ) {
                            items(
                                items = forgottenFavorites,
                                key = { it.id }
                            ) { originalSong ->
                                val song by database.song(originalSong.id)
                                    .collectAsState(initial = originalSong)

                                SongListItem(
                                    song = song!!,
                                    showInLibraryIcon = true,
                                    isActive = song!!.id == mediaMetadata?.id,
                                    isPlaying = isPlaying,
                                    modifier = Modifier
                                        .width(horizontalLazyGridItemWidth)
                                        .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                                            onClick = {
                                                if (song!!.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(YouTubeQueue.radio(song!!.toMediaMetadata()))
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song!!,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss,
                                                        showLibraryAction = false,
                                                    )
                                                }
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }

            PullToRefreshDefaults.LoadingIndicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    }
}
@Composable
fun ModernHomeTopBarInline(
    navController: NavController,
    homeHeaderStyle: HomeHeaderStyle,
) {
    val currentVersion = BuildConfig.VERSION_NAME
    var showUpdateIcon by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val appContext = context.applicationContext

    LaunchedEffect(Unit) {
        try {
            val latestVersion = withContext(Dispatchers.IO) { checkForUpdates(appContext) }
            showUpdateIcon = latestVersion?.let { isNewerVersion(it, currentVersion) } ?: false
        } catch (_: Exception) {
            showUpdateIcon = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
    ) {
        // SMusic-style top bar: wordmark left, action icons right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SMusic wordmark
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.smusic_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Text(
                    text = "S",
                    style = MaterialTheme.typography.headlineMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFE53935),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Text(
                    text = stringResource(R.string.ui_music_2),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black
                )
            }

            // Action buttons — bell shows a badge when there's at least
            // one new developer announcement the person hasn't opened yet.
            val context = LocalContext.current
            val hasUnseenAnnouncement by produceState(initialValue = false) {
                context.dataStore.data.collect {
                    value = com.devxrohan.smusic.update.AppNotificationManager.hasUnseenAnnouncements(context) ||
                        // A pending app update shows the badge until the user has seen it.
                        com.devxrohan.smusic.update.AppNotificationManager.hasUnseenUpdate(context)
                }
            }
            val (accountName, _) = rememberPreference(AccountNameKey, defaultValue = "")
            val (accountEmail, _) = rememberPreference(AccountEmailKey, defaultValue = "")
            val (accountPhotoUrl, _) = rememberPreference(AccountPhotoUrlKey, defaultValue = "")
            val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, defaultValue = "")
            val hasYouTubeAccount = remember(innerTubeCookie) {
                innerTubeCookie.isNotBlank() && "SAPISID" in com.devxrohan.smusic.innertube.utils.parseCookieString(innerTubeCookie)
            }
            val profileLabel = if (!hasYouTubeAccount) "SMusic profile" else accountName.ifBlank { accountEmail.ifBlank { "YouTube profile" } }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bare icon buttons: no circular surface or ripple behind the glyph.
                Box {
                    Box(
                        modifier = Modifier.size(36.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { navController.navigate("announcement") }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painter = painterResource(R.drawable.notifications), contentDescription = stringResource(R.string.notifications), modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (hasUnseenAnnouncement) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(8.dp).background(androidx.compose.ui.graphics.Color(0xFFE53935), CircleShape))
                    }
                }
                Box(
                    modifier = Modifier.size(36.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { navController.navigate("explore") }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painter = painterResource(R.drawable.explore_outlined), contentDescription = stringResource(R.string.explore), modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showProfileMenu = true }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasYouTubeAccount && accountPhotoUrl.startsWith("https://")) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(accountPhotoUrl).crossfade(true).build(),
                                contentDescription = profileLabel,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(painter = painterResource(R.drawable.person), contentDescription = profileLabel, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = showProfileMenu,
                        onDismissRequest = { showProfileMenu = false },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp,
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp),
                    ) {
                        val accent = MaterialTheme.colorScheme.primary
                        Column(
                            modifier = Modifier
                                .width(288.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Header card: same tinted gradient + 18dp corners as the Home greeting card.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(accent.copy(alpha = 0.14f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f))
                                        )
                                    )
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(46.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                    if (hasYouTubeAccount && accountPhotoUrl.startsWith("https://")) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current).data(accountPhotoUrl).crossfade(true).build(),
                                            contentDescription = profileLabel,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.person), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = profileLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (hasYouTubeAccount) accountEmail.ifBlank { "YouTube Music connected" } else "Listening on this device",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                if (hasYouTubeAccount) {
                                    Icon(painterResource(R.drawable.check_circle), "Connected", Modifier.size(20.dp), tint = accent)
                                }
                            }

                            @Composable
                            fun ProfileRow(icon: Int, title: String, subtitle: String? = null, destructive: Boolean = false, onClick: () -> Unit) {
                                val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(onClick = onClick)
                                        .padding(horizontal = 10.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    // The YouTube logo keeps its own colours (same as the "Import YouTube" playlist row). Tinting it
                                    // turned the picture into a flat single-colour shape.
                                    val iconTint = when {
                                        icon == R.drawable.youtube_logo -> androidx.compose.ui.graphics.Color.Unspecified
                                        destructive -> tint
                                        else -> accent
                                    }
                                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = iconTint)
                                    Column(Modifier.weight(1f)) {
                                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tint)
                                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!destructive) Icon(painterResource(R.drawable.chevron_right), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (hasYouTubeAccount) {
                                ProfileRow(R.drawable.account, stringResource(R.string.ui_account_amp_sync), stringResource(R.string.ui_feed_library_and_pulse_together)) {
                                    showProfileMenu = false; navController.navigate("account")
                                }
                            } else {
                                ProfileRow(R.drawable.youtube_logo, stringResource(R.string.ui_add_youtube_account), stringResource(R.string.ui_bring_in_your_feed_likes_and)) {
                                    showProfileMenu = false; navController.navigate("login")
                                }
                            }
                            ProfileRow(R.drawable.stats, stringResource(R.string.ui_listening_stats), stringResource(R.string.ui_your_pulse_taste_profile)) {
                                showProfileMenu = false; navController.navigate("stats")
                            }
                            ProfileRow(R.drawable.history, stringResource(R.string.history)) {
                                showProfileMenu = false; navController.navigate("history")
                            }
                            ProfileRow(R.drawable.settings, stringResource(R.string.settings)) {
                                showProfileMenu = false; navController.navigate("settings")
                            }
                            if (hasYouTubeAccount) {
                                HorizontalDivider(Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ProfileRow(R.drawable.logout, stringResource(R.string.ui_sign_out_of_youtube), stringResource(R.string.ui_your_songs_and_history_stay), destructive = true) {
                                    showProfileMenu = false
                                    // Only the account session goes; all songs, likes, history and Pulse data stay.
                                    scope.launch { runCatching { signOutYouTubeAccount(context) } }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (homeHeaderStyle == HomeHeaderStyle.SMUSIC) {
            // SMusic-style greeting card. The greeting is optional and intentionally
            // contains no decorative emoji so the Home layout stays clean.
            val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
            val greeting = when {
                hour < 12 -> "Good Morning"
                hour < 17 -> "Good Afternoon"
                else -> "Good Evening"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            )
                        )
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_smusic),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = TextUnit(3f, TextUnitType.Sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.ui_what_would_you_like_to_listen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}



@Composable
fun AnimatedBeatsRing(
    isPlaying: Boolean,
    songColors: List<Color>,
    modifier: Modifier = Modifier
) {
    if (!isPlaying) return

    val infiniteTransition = rememberInfiniteTransition(label = "beats_anim")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val barAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar_height"
    )

    Canvas(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val radius = size.minDimension / 2
        val barCount = 40
        val angleStep = 360f / barCount

        for (i in 0 until barCount) {

            val angle = Math.toRadians((i * angleStep).toDouble())
            val dynamicHeight = radius * barAnim * (0.5f + (i % 5) * 0.1f)

            val startX = center.x + (radius - 12f) * cos(angle).toFloat()
            val startY = center.y + (radius - 12f) * sin(angle).toFloat()

            val endX = center.x + (radius - 12f + dynamicHeight * 0.25f) * cos(angle).toFloat()
            val endY = center.y + (radius - 12f + dynamicHeight * 0.25f) * sin(angle).toFloat()

            drawLine(
                brush = Brush.linearGradient(songColors),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
    }
}
