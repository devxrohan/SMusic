package com.devxrohan.smusic.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.CONTENT_TYPE_HEADER
import com.devxrohan.smusic.constants.CONTENT_TYPE_PLAYLIST
import com.devxrohan.smusic.constants.GridItemSize
import com.devxrohan.smusic.constants.GridItemsSizeKey
import com.devxrohan.smusic.constants.GridThumbnailHeight
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.LibraryViewType
import com.devxrohan.smusic.constants.PlaylistSortDescendingKey
import com.devxrohan.smusic.constants.PlaylistSortType
import com.devxrohan.smusic.constants.PlaylistSortTypeKey
import com.devxrohan.smusic.constants.PlaylistViewTypeKey
import com.devxrohan.smusic.constants.YtmSyncKey
import com.devxrohan.smusic.db.entities.Playlist
import com.devxrohan.smusic.db.entities.PlaylistEntity
import com.devxrohan.smusic.ui.component.CreatePlaylistDialog
import com.devxrohan.smusic.ui.component.HideOnScrollFAB
import com.devxrohan.smusic.ui.component.LibraryHeroFavoriteTile
import com.devxrohan.smusic.ui.component.LibraryPlaylistGridItem
import com.devxrohan.smusic.ui.component.LibraryPlaylistListItem
import com.devxrohan.smusic.ui.component.LocalMenuState
import com.devxrohan.smusic.ui.component.PlaylistGridItem
import com.devxrohan.smusic.ui.component.PlaylistListItem
import com.devxrohan.smusic.ui.component.SortHeader
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.viewmodels.LibraryPlaylistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    onLocalClick: () -> Unit = {},
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,

    ) {
    val menuState = LocalMenuState.current
    LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    var viewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSortTypeKey,
        PlaylistSortType.LAST_UPDATED
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSortDescendingKey,
        true
    )
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val playlists by viewModel.allPlaylists.collectAsState()

    val importPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = "Import Spotify"
            ),
            songCount = 0,
            thumbnails = emptyList(),
        )

    val importYouTubePlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = "Import YouTube"
            ),
            songCount = 0,
            thumbnails = emptyList(),
        )

    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    var showYouTubeImportDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    LaunchedEffect(isLoggedIn, ytmSync) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing
        )
    }

    val shortcuts = listOf(
        Triple(importPlaylist.playlist.name, R.drawable.spotify, "import"),
        Triple(importYouTubePlaylist.playlist.name, R.drawable.youtube_logo, "import_yt"),
    )
    val summary = pluralStringResource(R.plurals.n_playlist, playlists.size, playlists.size)
    val pullRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (ytmSync && allowSyncing) {
                        isRefreshing = true
                        coroutineScope.launch {
                            viewModel.sync()
                            isRefreshing = false
                        }
                    }
                },
            ),
    ) {
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "filter", contentType = CONTENT_TYPE_HEADER) {
                filterContent()
            }

            item(key = "controls", contentType = CONTENT_TYPE_HEADER) {
                PlaylistControlCard(
                    summary = summary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                PlaylistSortType.NAME -> R.string.sort_by_name
                                PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                                PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "shortcuts", contentType = CONTENT_TYPE_HEADER) {
                PlaylistShortcutGrid(
                    entries = shortcuts,
                    onClick = { route ->
                        when (route) {
                            "local" -> onLocalClick()
                            "import" -> showSpotifyImportDialog = true
                            "import_yt" -> showYouTubeImportDialog = true
                            else -> navController.navigate(route)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "playlist_section_header", contentType = CONTENT_TYPE_HEADER) {
                PlaylistSectionHeaderCard(
                    title = stringResource(R.string.playlists),
                    supportingText = summary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            items(
                items = playlists,
                key = { it.id },
                contentType = { CONTENT_TYPE_PLAYLIST },
            ) { playlist ->
                LibraryPlaylistListItem(
                    navController = navController,
                    menuState = menuState,
                    coroutineScope = coroutineScope,
                    playlist = playlist,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }

        PullToRefreshDefaults.Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )

        HideOnScrollFAB(
            lazyListState = lazyListState,
            icon = R.drawable.add,
            onClick = {
                showCreatePlaylistDialog = true
            },
        )
    }
    if (showSpotifyImportDialog) {
        SpotifyImportDialog(onDismiss = { showSpotifyImportDialog = false })
    }
    if (showYouTubeImportDialog) {
        YouTubeImportDialog(onDismiss = { showYouTubeImportDialog = false })
    }
}

@Composable
private fun PlaylistControlCard(
    summary: String,
    modifier: Modifier = Modifier,
    controls: @Composable RowScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            controls()
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistShortcutGrid(
    entries: List<Triple<String, Int, String>>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        entries.forEach { entry ->
            LibraryHeroFavoriteTile(
                title = entry.first,
                iconRes = entry.second,
                badgeText = "SMusic",
                accentColor = shortcutAccentColor(entry.third),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onClick(entry.third) })
            )
        }
    }
}

@Composable
private fun shortcutAccentColor(route: String): Color =
    when {
        route.contains("downloaded") -> MaterialTheme.colorScheme.primary
        route.contains("cached") -> MaterialTheme.colorScheme.tertiary
        route.contains("top_playlist") -> MaterialTheme.colorScheme.secondary
        route == "local" -> MaterialTheme.colorScheme.primary
        route == "import" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

@Composable
private fun PlaylistSectionHeaderCard(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
